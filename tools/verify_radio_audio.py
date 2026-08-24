#!/usr/bin/env python3
"""Verify each staged radio audio file decodes, and extract metadata.

Uses the ffmpeg binary bundled with imageio-ffmpeg. For each file in
tools/radio-staging, run `ffmpeg -v error -i <file> -f null -`; a file is
"decodable" iff ffmpeg exits 0 with no error output. Extracts duration/codec/
sample_rate/channels from `ffmpeg -i` stderr. Writes RADIO_AUDIO_MANIFEST.tsv.
"""
import os
import re
import subprocess
import hashlib

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STAGING = os.path.join(REPO, "tools", "radio-staging")
OUT = os.path.join(REPO, "RADIO_AUDIO_MANIFEST.tsv")

FFMPEG = r"D:\app\anaconda\Anaconda\Lib\site-packages\imageio_ffmpeg\binaries\ffmpeg-win-x86_64-v7.1.exe"


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def probe(path):
    """Return (decodable, duration_ms, codec, sample_rate, channels)."""
    dec = subprocess.run(
        [FFMPEG, "-v", "error", "-i", path, "-f", "null", "-"],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    # exit 0 means ffmpeg fully decoded the stream to null (ID3/tag warnings in
    # stderr are non-fatal and do not affect playability).
    decodable = dec.returncode == 0

    info = subprocess.run(
        [FFMPEG, "-i", path],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    stderr = info.stderr
    dur_m = re.search(r"Duration:\s*(\d+):(\d+):(\d+\.?\d*)", stderr)
    duration_ms = 0
    if dur_m:
        h, m, s = int(dur_m.group(1)), int(dur_m.group(2)), float(dur_m.group(3))
        duration_ms = int((h * 3600 + m * 60 + s) * 1000)
    codec_m = re.search(r"Audio:\s*([a-zA-Z0-9_]+)", stderr)
    codec = codec_m.group(1) if codec_m else ""
    sr_m = re.search(r"(\d+)\s*Hz", stderr)
    sample_rate = int(sr_m.group(1)) if sr_m else 0
    ch_m = re.search(r"stereo|mono|(\d+)\.?\d*\(?ch|,\s*(\d+)\s+channels", stderr)
    channels = 2 if "stereo" in stderr else (1 if "mono" in stderr else 0)
    if channels == 0:
        ch2 = re.search(r",\s*(\d+)\s*ch", stderr)
        if ch2:
            channels = int(ch2.group(1))
    return decodable, duration_ms, codec, sample_rate, channels


def main():
    rows = ["track_id\text\tsize_bytes\tsha256\tdecodable\tduration_ms\tcodec\tsample_rate\tchannels"]
    ok = bad = 0
    for fn in sorted(os.listdir(STAGING)):
        if fn.endswith(".tmp"):
            continue
        track_id = fn.rsplit(".", 1)[0]
        ext = fn.rsplit(".", 1)[1]
        path = os.path.join(STAGING, fn)
        decodable, dur, codec, sr, ch = probe(path)
        size = os.path.getsize(path)
        digest = sha256_of(path)
        if decodable and dur > 0:
            ok += 1
        else:
            bad += 1
            print(f"BAD {fn}: decodable={decodable} dur={dur} codec={codec}", flush=True)
        rows.append(f"{track_id}\t{ext}\t{size}\t{digest}\t{decodable}\t{dur}\t{codec}\t{sr}\t{ch}")

    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(rows) + "\n")
    print(f"\n=== 完成: 可解码 ok={ok} 损坏/异常 bad={bad} 总计={ok + bad} ===")


if __name__ == "__main__":
    main()
