#!/usr/bin/env python3
"""Transcode the packaged radio catalog to quality-tiered Ogg Opus assets.

All tracks are encoded and probed in an ignored staging directory first. The
packaged assets and metadata are replaced only after every track succeeds.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import shutil
import subprocess
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSET_DIR = ROOT / "app" / "src" / "main" / "assets" / "radio"
STAGING_DIR = ROOT / "tools" / "radio-opus-staging"
MANIFEST_PATH = ROOT / "RADIO_AUDIO_MANIFEST.tsv"
CATALOG_PATH = ROOT / "feature" / "listening" / "src" / "main" / "java" / "com" / "soundist" / "feature" / "listening" / "ListeningCatalog.kt"
SELECTORS_TEST_PATH = ROOT / "feature" / "listening" / "src" / "test" / "java" / "com" / "soundist" / "feature" / "listening" / "ListeningSelectorsTest.kt"
RELEASE_MANIFEST_PATH = ROOT / "tools" / "license-snapshots" / "release-catalog-2026-08-20" / "release-license-manifest.json"
OGA_MANIFEST_PATH = ROOT / "tools" / "license-snapshots" / "open-game-art-cc0-2026-08-20" / "manifest.json"
ATTRIBUTION_PATH = ROOT / "RADIO_RELEASE_ATTRIBUTION.md"
ASSET_MANIFEST_PATH = ASSET_DIR / "ASSET-MANIFEST.md"
REPORT_PATH = ROOT / "RADIO_OPUS_TRANSCODE.tsv"


LOW_PREFIXES = (
    "ambient-", "background-", "beats-", "calm-", "chill-", "chilled-",
    "dream-", "end-of-hope", "fairies-", "game-", "into-stars", "lofi-",
    "meditation-", "synth-", "white-lotus", "yd-", "yoiyami-",
    "atlantean-", "exploration", "incompetech-", "joth-", "just-as-soon",
    "kumasi-",
)
MEDIUM_PREFIXES = (
    "cc0-", "electronic-piano", "emotional-piano", "guitar-", "harp-",
    "indieteur-", "jazz-", "modern-piano-", "night-docks-", "solo-piano-",
    "tiny-movement",
)


@dataclass(frozen=True)
class SourceTrack:
    track_id: str
    extension: str
    path: Path
    old_bytes: int
    old_sha256: str
    duration_ms: int
    channels: int
    bitrate_kbps: int


@dataclass(frozen=True)
class EncodedTrack:
    source: SourceTrack
    path: Path
    new_bytes: int
    new_sha256: str
    duration_ms: int
    sample_rate: int
    channels: int


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def find_binary(name: str) -> Path:
    matches = sorted((ROOT / "tools" / "ffmpeg" / "runtime").glob(f"**/{name}.exe"))
    if not matches:
        raise FileNotFoundError(f"{name}.exe not found under tools/ffmpeg/runtime")
    return matches[0]


def target_bitrate(track_id: str, channels: int) -> int:
    if track_id.startswith(LOW_PREFIXES):
        bitrate = 96
    elif track_id.startswith(MEDIUM_PREFIXES):
        bitrate = 112
    else:
        bitrate = 128
    return min(bitrate, 96) if channels == 1 else bitrate


def load_sources() -> list[SourceTrack]:
    with MANIFEST_PATH.open(encoding="utf-8", newline="") as stream:
        rows = list(csv.DictReader(stream, delimiter="\t"))
    sources: list[SourceTrack] = []
    for row in rows:
        track_id = row["track_id"]
        extension = row["ext"]
        path = ASSET_DIR / f"{track_id}.{extension}"
        if not path.is_file():
            raise FileNotFoundError(path)
        if sha256_file(path).lower() != row["sha256"].lower():
            raise RuntimeError(f"source hash differs from manifest: {path.name}")
        channels = int(row["channels"])
        sources.append(
            SourceTrack(
                track_id=track_id,
                extension=extension,
                path=path,
                old_bytes=path.stat().st_size,
                old_sha256=row["sha256"],
                duration_ms=int(row["duration_ms"]),
                channels=channels,
                bitrate_kbps=target_bitrate(track_id, channels),
            )
        )
    if len(sources) != 299:
        raise RuntimeError(f"expected 299 packaged tracks, found {len(sources)}")
    return sources


def probe(ffprobe: Path, path: Path) -> tuple[int, int, int, str]:
    result = subprocess.run(
        [
            str(ffprobe), "-v", "error", "-select_streams", "a:0",
            "-show_entries", "stream=codec_name,sample_rate,channels:format=duration",
            "-of", "json", str(path),
        ],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=True,
    )
    payload = json.loads(result.stdout)
    stream = payload["streams"][0]
    duration_ms = round(float(payload["format"]["duration"]) * 1000)
    return duration_ms, int(stream["sample_rate"]), int(stream["channels"]), stream["codec_name"]


def encode_one(ffmpeg: Path, ffprobe: Path, source: SourceTrack) -> EncodedTrack:
    output = STAGING_DIR / f"{source.track_id}.opus"
    temporary = output.with_suffix(".opus.tmp")
    if output.is_file():
        duration_ms, sample_rate, channels, codec = probe(ffprobe, output)
        tolerance_ms = max(500, round(source.duration_ms * 0.003))
        if (
            codec == "opus"
            and channels == source.channels
            and abs(duration_ms - source.duration_ms) <= tolerance_ms
        ):
            return EncodedTrack(
                source=source,
                path=output,
                new_bytes=output.stat().st_size,
                new_sha256=sha256_file(output),
                duration_ms=duration_ms,
                sample_rate=sample_rate,
                channels=channels,
            )
        output.unlink()
    temporary.unlink(missing_ok=True)
    subprocess.run(
        [
            str(ffmpeg), "-hide_banner", "-loglevel", "error", "-y",
            "-i", str(source.path), "-map", "0:a:0", "-map_metadata", "-1",
            "-vn", "-c:a", "libopus", "-b:a", f"{source.bitrate_kbps}k",
            "-vbr", "on", "-compression_level", "10", "-application", "audio",
            "-frame_duration", "20", "-ar", "48000", "-f", "opus", str(temporary),
        ],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=True,
    )
    duration_ms, sample_rate, channels, codec = probe(ffprobe, temporary)
    if codec != "opus":
        raise RuntimeError(f"unexpected codec for {source.track_id}: {codec}")
    # Ogg Opus pre-skip and source-container rounding can shift the reported
    # duration by a few hundred milliseconds without dropping audible content.
    tolerance_ms = max(500, round(source.duration_ms * 0.003))
    if abs(duration_ms - source.duration_ms) > tolerance_ms:
        raise RuntimeError(
            f"duration mismatch for {source.track_id}: {source.duration_ms} -> {duration_ms} ms"
        )
    if channels != source.channels:
        raise RuntimeError(f"channel mismatch for {source.track_id}: {source.channels} -> {channels}")
    temporary.replace(output)
    return EncodedTrack(
        source=source,
        path=output,
        new_bytes=output.stat().st_size,
        new_sha256=sha256_file(output),
        duration_ms=duration_ms,
        sample_rate=sample_rate,
        channels=channels,
    )


def write_audio_manifest(encoded: list[EncodedTrack]) -> None:
    with MANIFEST_PATH.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        writer.writerow(("track_id", "ext", "size_bytes", "sha256", "decodable", "duration_ms", "codec", "sample_rate", "channels"))
        for item in encoded:
            writer.writerow((item.source.track_id, "opus", item.new_bytes, item.new_sha256, "True", item.duration_ms, "opus", item.sample_rate, item.channels))


def update_catalog(encoded: list[EncodedTrack]) -> None:
    text = CATALOG_PATH.read_text(encoding="utf-8")
    entries = "\n".join(f'    "{item.source.track_id}" to "opus",' for item in encoded)
    replacement = (
        "/** 全量内置曲目（assets/radio/，共 299 首）的 trackId → 扩展名。\n"
        " *  所有打包录音统一为 Ogg Opus，来源与原始格式记录保留在许可清单中。 */\n"
        f"private val baseApkAssets = mapOf(\n{entries}\n)"
    )
    text, count = re.subn(
        r"/\*\* 全量内置曲目.*?private val baseApkAssets = mapOf\(.*?\n\)",
        replacement,
        text,
        count=1,
        flags=re.S,
    )
    if count != 1:
        raise RuntimeError("could not replace baseApkAssets")
    CATALOG_PATH.write_text(text, encoding="utf-8")


def update_selector_test() -> None:
    text = SELECTORS_TEST_PATH.read_text(encoding="utf-8")
    old = '''        // 基础 APK 内置曲目的真实扩展名：mp3 不得被硬编码成 .ogg。
        val mp3Tracks = setOf("ambient-pad-i", "chopin-canon-f-minor", "jazz-avant")
        official.flatMap { it.tracks }.filter { it.id in mp3Tracks }.forEach { assertEquals("asset:///radio/${it.id}.mp3", it.localAssetUri) }
'''
    new = '''        // 基础 APK 内置曲目统一使用经过验证的 Ogg Opus 衍生文件。
        assertTrue(official.flatMap { it.tracks }.all { it.localAssetUri == "asset:///radio/${it.id}.opus" })
'''
    if old not in text:
        raise RuntimeError("expected extension assertion was not found")
    SELECTORS_TEST_PATH.write_text(text.replace(old, new), encoding="utf-8")


def update_release_manifest(encoded_by_id: dict[str, EncodedTrack]) -> None:
    payload = json.loads(RELEASE_MANIFEST_PATH.read_text(encoding="utf-8"))
    for row in payload["tracks"]:
        item = encoded_by_id[row["trackId"]]
        row.setdefault("preTranscodeAssetPath", row.get("assetPath", ""))
        row.setdefault("preTranscodeAssetSha256", row.get("assetSha256", ""))
        row["assetPath"] = f"app/src/main/assets/radio/{item.source.track_id}.opus"
        row["assetBytes"] = item.new_bytes
        row["assetSha256"] = item.new_sha256
        row["manifestSha256"] = item.new_sha256
        row["packagedCodec"] = "opus"
        row["packagedBitrateKbps"] = item.source.bitrate_kbps
    RELEASE_MANIFEST_PATH.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def update_oga_manifest(encoded_by_id: dict[str, EncodedTrack]) -> None:
    payload = json.loads(OGA_MANIFEST_PATH.read_text(encoding="utf-8"))
    for row in payload.get("tracks", payload if isinstance(payload, list) else []):
        track_id = row.get("trackId") or row.get("id")
        if track_id not in encoded_by_id:
            continue
        item = encoded_by_id[track_id]
        if "derivativePath" in row:
            row["derivativePath"] = f"app/src/main/assets/radio/{track_id}.opus"
        if "derivativeSha256" in row:
            row["derivativeSha256"] = item.new_sha256
        if "derivativeBytes" in row:
            row["derivativeBytes"] = item.new_bytes
        row["packagedCodec"] = "opus"
        row["packagedBitrateKbps"] = item.source.bitrate_kbps
    payload["derivativeProcess"] = (
        "Existing reviewed derivative repackaged with FFmpeg libopus; "
        "Ogg Opus VBR; 48000 Hz; per-track bitrate recorded below"
    )
    OGA_MANIFEST_PATH.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def update_attribution(encoded_by_id: dict[str, EncodedTrack]) -> None:
    lines = ATTRIBUTION_PATH.read_text(encoding="utf-8").splitlines()
    updated: list[str] = []
    for line in lines:
        match = re.search(r"\(`([^`]+)`\)", line)
        if match and match.group(1) in encoded_by_id:
            digest = encoded_by_id[match.group(1)].new_sha256
            line = re.sub(r"`[0-9a-f]{64}`(?=\s*\|\s*$)", f"`{digest}`", line)
        updated.append(line)
    ATTRIBUTION_PATH.write_text("\n".join(updated) + "\n", encoding="utf-8")


def write_asset_manifest(encoded: list[EncodedTrack]) -> None:
    lines = [
        "# Soundist packaged radio assets",
        "",
        "These files are copied into the APK and are played through `asset:///radio/...`.",
        "The runtime never streams the provenance URLs recorded in the license manifest.",
        "",
        "本目录包含 299 个随 Android 应用提供的开放精选音频文件，统一编码为 Ogg Opus。",
        "原始来源、许可证、转码前后哈希及逐曲署名见仓库根目录许可资料。",
        "",
        "## Packaged files (299)",
        "",
        "| File | Bytes | Bitrate | SHA-256 | Duration (ms) | Channels |",
        "| --- | ---: | ---: | --- | ---: | ---: |",
    ]
    for item in encoded:
        lines.append(
            f"| {item.source.track_id}.opus | {item.new_bytes} | {item.source.bitrate_kbps} kbps | "
            f"`{item.new_sha256}` | {item.duration_ms} | {item.channels} |"
        )
    ASSET_MANIFEST_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_report(encoded: list[EncodedTrack]) -> None:
    with REPORT_PATH.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        writer.writerow(("track_id", "source_ext", "source_bytes", "source_sha256", "opus_bitrate_kbps", "opus_bytes", "opus_sha256", "duration_ms", "channels"))
        for item in encoded:
            writer.writerow((item.source.track_id, item.source.extension, item.source.old_bytes, item.source.old_sha256, item.source.bitrate_kbps, item.new_bytes, item.new_sha256, item.duration_ms, item.channels))


def apply_assets(encoded: list[EncodedTrack]) -> None:
    expected = {item.source.path.resolve() for item in encoded}
    actual = {path.resolve() for path in ASSET_DIR.iterdir() if path.is_file() and path.name != ASSET_MANIFEST_PATH.name}
    if actual != expected:
        missing = sorted(str(path) for path in expected - actual)
        extra = sorted(str(path) for path in actual - expected)
        raise RuntimeError(f"asset set changed before apply; missing={missing[:3]} extra={extra[:3]}")
    for path in actual:
        path.unlink()
    for item in encoded:
        shutil.move(str(item.path), str(ASSET_DIR / item.path.name))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true", help="replace packaged files after successful validation")
    parser.add_argument("--workers", type=int, default=min(6, max(2, (os.cpu_count() or 4) // 2)))
    args = parser.parse_args()
    ffmpeg = find_binary("ffmpeg")
    ffprobe = find_binary("ffprobe")
    sources = load_sources()
    STAGING_DIR.mkdir(parents=True, exist_ok=True)

    encoded: list[EncodedTrack] = []
    with ThreadPoolExecutor(max_workers=args.workers) as executor:
        futures = {executor.submit(encode_one, ffmpeg, ffprobe, source): source for source in sources}
        for index, future in enumerate(as_completed(futures), start=1):
            item = future.result()
            encoded.append(item)
            if index % 10 == 0 or index == len(sources):
                print(f"encoded {index}/{len(sources)}", flush=True)
    encoded.sort(key=lambda item: item.source.track_id)

    old_total = sum(item.source.old_bytes for item in encoded)
    new_total = sum(item.new_bytes for item in encoded)
    print(f"radio bytes: {old_total} -> {new_total} ({new_total / old_total:.1%})")
    if not args.apply:
        print("validation complete; rerun with --apply to replace packaged assets")
        return

    encoded_by_id = {item.source.track_id: item for item in encoded}
    apply_assets(encoded)
    write_audio_manifest(encoded)
    update_catalog(encoded)
    update_selector_test()
    update_release_manifest(encoded_by_id)
    update_oga_manifest(encoded_by_id)
    update_attribution(encoded_by_id)
    write_asset_manifest(encoded)
    write_report(encoded)
    STAGING_DIR.rmdir()
    print("packaged assets and metadata updated")


if __name__ == "__main__":
    main()
