#!/usr/bin/env python3
"""Build a reproducible redistribution-license audit for packaged radio audio."""

from __future__ import annotations

import csv
import hashlib
import html
import json
import re
import time
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSET_DIR = ROOT / "app" / "src" / "main" / "assets" / "radio"
OUT = ROOT / "tools" / "license-snapshots" / "release-catalog-2026-08-20"
UA = "SoundistLicenseAudit/2.0 (open-source redistribution audit)"

PERMISSIVE_MARKERS = (
    "public domain",
    "pdm-owner",
    "cc0",
    "cc by 1.0",
    "cc by 2.0",
    "cc by 2.5",
    "cc by 3.0",
    "cc by 4.0",
    "cc by-sa 1.0",
    "cc by-sa 2.0",
    "cc by-sa 2.5",
    "cc by-sa 3.0",
    "cc by-sa 4.0",
)
BLOCKING_MARKERS = ("cc by-nc", "cc by-nd", "all rights reserved", "copyrighted")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def fetch(url: str, attempts: int = 3) -> bytes:
    error: Exception | None = None
    for attempt in range(attempts):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(request, timeout=60) as response:
                return response.read()
        except Exception as exc:  # pragma: no cover - network behavior
            error = exc
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"fetch failed after {attempts} attempts: {url}: {error}")


def plain(value: str | None) -> str:
    if not value:
        return ""
    value = re.sub(r"<br\s*/?>", " ", value, flags=re.I)
    value = re.sub(r"<[^>]+>", "", value)
    return re.sub(r"\s+", " ", html.unescape(value)).strip()


def source_records() -> dict[str, dict[str, str]]:
    records: dict[str, dict[str, str]] = {}
    for line in (ROOT / "RADIO_DOWNLOAD_URLS.txt").read_text(encoding="utf-8").splitlines():
        parts = line.split(" | ")
        if len(parts) >= 4 and parts[2].startswith("http"):
            records[parts[0]] = {
                "trackId": parts[0],
                "title": parts[1],
                "downloadUrl": parts[2],
                "sourcePage": parts[3],
            }
    return records


def packaged_records() -> list[dict[str, str]]:
    with (ROOT / "RADIO_AUDIO_MANIFEST.tsv").open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def commons_title(source_page: str) -> str:
    path = urllib.parse.urlparse(source_page).path
    marker = "/wiki/"
    if marker not in path:
        raise ValueError(f"not a Commons file page: {source_page}")
    return urllib.parse.unquote(path.split(marker, 1)[1]).replace("_", " ")


def ext(meta: dict, key: str) -> str:
    return plain((meta.get(key) or {}).get("value"))


def classify(license_name: str, license_url: str, page_text: str = "") -> tuple[str, str]:
    combined = f"{license_name} {license_url} {page_text}".lower()
    if any(marker in combined for marker in BLOCKING_MARKERS):
        return "blocked", "license prohibits general open redistribution or rights are reserved"
    if any(marker in combined for marker in PERMISSIVE_MARKERS):
        if "by-sa" in combined:
            return "redistributable-with-sharealike", "attribution and ShareAlike obligations apply"
        if "cc by" in combined or "/by/" in combined:
            return "redistributable-with-attribution", "attribution obligation applies"
        return "redistributable", "public-domain dedication, mark, or public-domain recording"
    return "review-required", "no supported redistribution license was found in the official source metadata"


def audit_commons(records: list[dict[str, str]]) -> dict[str, dict]:
    result: dict[str, dict] = {}
    snapshot_dir = OUT / "wikimedia"
    snapshot_dir.mkdir(parents=True, exist_ok=True)
    for offset in range(0, len(records), 40):
        batch = records[offset : offset + 40]
        titles = [commons_title(item["sourcePage"]) for item in batch]
        query = urllib.parse.urlencode(
            {
                "action": "query",
                "format": "json",
                "formatversion": "2",
                "redirects": "1",
                "prop": "imageinfo",
                "iiprop": "extmetadata|url|sha1|mime|size",
                "titles": "|".join(titles),
            }
        )
        raw = fetch("https://commons.wikimedia.org/w/api.php?" + query)
        (snapshot_dir / f"batch-{offset // 40 + 1:02d}.json").write_bytes(raw)
        payload = json.loads(raw.decode("utf-8"))
        pages = {page.get("title"): page for page in payload.get("query", {}).get("pages", [])}
        normalized = {entry["from"]: entry["to"] for entry in payload.get("query", {}).get("normalized", [])}
        redirects = {entry["from"]: entry["to"] for entry in payload.get("query", {}).get("redirects", [])}
        for item, requested in zip(batch, titles):
            resolved = redirects.get(normalized.get(requested, requested), normalized.get(requested, requested))
            page = pages.get(resolved) or pages.get(requested) or {}
            info = (page.get("imageinfo") or [{}])[0]
            metadata = info.get("extmetadata") or {}
            license_name = ext(metadata, "LicenseShortName") or ext(metadata, "UsageTerms")
            license_url = ext(metadata, "LicenseUrl")
            status, reason = classify(license_name, license_url)
            result[item["trackId"]] = {
                **item,
                "provider": "Wikimedia Commons",
                "resolvedTitle": page.get("title", resolved),
                "license": license_name,
                "licenseUrl": license_url,
                "creator": ext(metadata, "Artist"),
                "credit": ext(metadata, "Credit") or ext(metadata, "Attribution"),
                "copyrighted": ext(metadata, "Copyrighted"),
                "restrictions": ext(metadata, "Restrictions"),
                "sourceFileUrl": info.get("url", ""),
                "sourceSha1": info.get("sha1", ""),
                "status": status,
                "reason": reason,
                "snapshot": f"wikimedia/batch-{offset // 40 + 1:02d}.json",
            }
    return result


def meta_content(text: str, property_name: str) -> str:
    pattern = rf'<meta[^>]+(?:property|name)=["\']{re.escape(property_name)}["\'][^>]+content=["\']([^"\']+)["\']'
    match = re.search(pattern, text, flags=re.I)
    return html.unescape(match.group(1)).strip() if match else ""


def audit_html(item: dict[str, str], provider: str) -> dict:
    slug = re.sub(r"[^a-z0-9._-]+", "-", item["trackId"].lower()).strip("-")
    folder = OUT / ("opengameart" if provider == "OpenGameArt" else "incompetech")
    folder.mkdir(parents=True, exist_ok=True)
    snapshot = folder / f"{slug}.html"
    raw = snapshot.read_bytes() if snapshot.exists() else fetch(item["sourcePage"])
    text = raw.decode("utf-8", errors="replace")
    if not snapshot.exists():
        snapshot.write_bytes(raw)

    license_name = ""
    license_url = ""
    lower = text.lower()
    candidates = [
        ("CC0 1.0", "https://creativecommons.org/publicdomain/zero/1.0/", ("/publicdomain/zero/1.0", "licenses/cc0")),
        ("CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/", ("licenses/by-sa/4.0",)),
        ("CC BY-SA 3.0", "https://creativecommons.org/licenses/by-sa/3.0/", ("licenses/by-sa/3.0",)),
        ("CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/", ("licenses/by/4.0",)),
        ("CC BY 3.0", "https://creativecommons.org/licenses/by/3.0/", ("licenses/by/3.0",)),
    ]
    for name, url, markers in candidates:
        if any(marker in lower for marker in markers):
            license_name, license_url = name, url
            break

    # OpenGameArt pages may offer several licenses. Multiple incompatible choices require review,
    # unless CC0 is one of the explicit choices (the packaged file can be accepted under CC0).
    found = [(name, url) for name, url, markers in candidates if any(marker in lower for marker in markers)]
    if found and found[0][0] != "CC0 1.0" and len({name for name, _ in found}) > 1:
        license_name = " OR ".join(name for name, _ in found)
        license_url = " OR ".join(url for _, url in found)

    status, reason = classify(license_name, license_url, lower)
    creator = "Kevin MacLeod" if provider == "Incompetech" else ""
    if not creator:
        author_match = re.search(
            r"field-name-author-submitter.*?<span[^>]*class=['\"]username['\"][^>]*>.*?<a[^>]*>(.*?)</a>",
            text,
            flags=re.I | re.S,
        )
        creator = plain(author_match.group(1)) if author_match else ""
    return {
        **item,
        "provider": provider,
        "license": license_name,
        "licenseUrl": license_url,
        "creator": creator,
        "credit": "",
        "status": status,
        "reason": reason,
        "sourcePageSha256": sha256_bytes(raw),
        "snapshot": str(snapshot.relative_to(OUT)).replace("\\", "/"),
        "pageTitle": meta_content(text, "og:title") or item["title"],
    }


def archive_license_texts(rows: list[dict]) -> dict[str, str]:
    folder = OUT / "licenses"
    folder.mkdir(parents=True, exist_ok=True)
    urls = {row.get("licenseUrl", "") for row in rows if row.get("licenseUrl")}
    if any(row.get("license") == "PDM-owner" for row in rows):
        urls.add("https://creativecommons.org/publicdomain/mark/1.0/")
    snapshots: dict[str, str] = {}
    for url in sorted(urls):
        slug = re.sub(r"[^a-z0-9]+", "-", urllib.parse.urlparse(url).path.lower()).strip("-") or "license"
        path = folder / f"{slug}.html"
        try:
            raw = path.read_bytes() if path.exists() else fetch(url)
            if not path.exists():
                path.write_bytes(raw)
            snapshots[url] = str(path.relative_to(OUT)).replace("\\", "/")
        except Exception as exc:
            snapshots[url] = f"FETCH-FAILED: {exc}"
    return snapshots


def write_attribution(rows: list[dict], license_snapshots: dict[str, str]) -> None:
    lines = [
        "# Soundist 开放精选：发行许可与署名",
        "",
        "> 本文由 `tools/audit_release_licenses.py` 根据 Android 实际打包资产生成。法律与证据详情保存在仓库，不要求在每张 App 频道卡片重复显示。",
        "",
        "## 发行结论",
        "",
    ]
    counts: dict[str, int] = {}
    for row in rows:
        counts[row["status"]] = counts.get(row["status"], 0) + 1
    lines.extend(f"- `{key}`：{value} 首" for key, value in sorted(counts.items()))
    lines += [
        "",
        "## 必须保留署名或相同许可的录音",
        "",
        "| 曲目 | 创作者/演奏者 | 许可 | 来源 | 打包文件 SHA-256 |",
        "|---|---|---|---|---|",
    ]
    for row in rows:
        if row["status"] not in {"redistributable-with-attribution", "redistributable-with-sharealike"}:
            continue
        creator = (row.get("creator") or row.get("credit") or "来源页未提供简短作者字段").replace("|", "\\|")
        title = row.get("title", row["trackId"]).replace("|", "\\|")
        lines.append(
            f"| {title} (`{row['trackId']}`) | {creator} | {row.get('license', '')} | "
            f"[来源页]({row.get('sourcePage', '')}) | `{row.get('assetSha256', '')}` |"
        )
    lines += [
        "",
        "## 公版与 CC0 来源记录",
        "",
        "这些曲目不强制署名，但仍保留来源页、创建者字段、官方元数据快照和文件哈希，便于发行审计。完整逐曲数据见 `release-license-manifest.json`。",
        "",
        "## 许可证文本快照",
        "",
    ]
    for url, snapshot in sorted(license_snapshots.items()):
        lines.append(f"- {url} → `{snapshot}`")
    lines += [
        "",
        "## 产品内展示原则",
        "",
        "App 频道卡片仅展示标题、创作者与简洁的“开放音乐”状态。完整署名可集中放在“设置 → 关于 → 开放内容与署名”，并随安装包或项目发行物附带本文；不得删除 CC BY / CC BY-SA 署名。",
        "",
    ]
    (ROOT / "RADIO_RELEASE_ATTRIBUTION.md").write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    sources = source_records()
    packaged = packaged_records()
    rows: list[dict] = []
    commons: list[dict[str, str]] = []
    html_sources: list[tuple[dict[str, str], str]] = []

    for asset in packaged:
        track_id = asset["track_id"]
        item = sources.get(track_id)
        if item is None:
            rows.append({"trackId": track_id, "status": "blocked", "reason": "missing source record"})
            continue
        source = urllib.parse.urlparse(item["sourcePage"]).hostname or ""
        if source == "commons.wikimedia.org":
            commons.append(item)
        elif source == "opengameart.org":
            html_sources.append((item, "OpenGameArt"))
        elif source == "incompetech.com":
            html_sources.append((item, "Incompetech"))
        else:
            rows.append({**item, "status": "blocked", "reason": f"unsupported source provider: {source}"})

    commons_results = audit_commons(commons)
    rows.extend(commons_results[item["trackId"]] for item in commons)
    for index, (item, provider) in enumerate(html_sources, start=1):
        print(f"[{index}/{len(html_sources)}] {provider}: {item['trackId']}")
        try:
            rows.append(audit_html(item, provider))
        except Exception as exc:
            rows.append({**item, "provider": provider, "status": "review-required", "reason": str(exc)})

    assets_by_id = {row["track_id"]: row for row in packaged}
    for row in rows:
        asset = assets_by_id.get(row["trackId"])
        if not asset:
            continue
        path = ASSET_DIR / f"{row['trackId']}.{asset['ext']}"
        row["assetPath"] = str(path.relative_to(ROOT)).replace("\\", "/")
        row["assetBytes"] = path.stat().st_size if path.exists() else 0
        row["assetSha256"] = sha256_file(path) if path.exists() else ""
        row["manifestSha256"] = asset["sha256"]
        if not path.exists() or row["assetSha256"].lower() != asset["sha256"].lower():
            row["status"] = "blocked"
            row["reason"] = "packaged asset missing or SHA-256 differs from RADIO_AUDIO_MANIFEST.tsv"

    rows.sort(key=lambda item: item["trackId"])
    license_snapshots = archive_license_texts(rows)
    summary: dict[str, int] = {}
    for row in rows:
        summary[row["status"]] = summary.get(row["status"], 0) + 1
    manifest = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "catalogCount": len(packaged),
        "summary": summary,
        "licenseTextSnapshots": license_snapshots,
        "tracks": rows,
    }
    (OUT / "release-license-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    write_attribution(rows, license_snapshots)
    print(json.dumps(summary, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
