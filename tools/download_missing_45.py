#!/usr/bin/env python3
"""Download the 44 missing classical-track audio files into staging.

These are the template-generated track IDs (webKeyboardCounterpoint /
webClassicalArchitecture / webRomanticPianoDiary) that the earlier download
script's literal-id extraction missed. Reads RADIO_DOWNLOAD_URLS.txt for URLs.
"""
import os
import re
import subprocess
import urllib.parse

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STAGING = os.path.join(REPO, "tools", "radio-staging")
URLS = os.path.join(REPO, "RADIO_DOWNLOAD_URLS.txt")

GOLDBERG = [f"bach-goldberg-cc0-{n:02d}" for n in range(1, 17)] + [f"bach-goldberg-cc0-{n:02d}" for n in range(27, 31)]
DIABELLI = [
    "beethoven-diabelli-05-07", "beethoven-diabelli-08-10", "beethoven-diabelli-11-13",
    "beethoven-diabelli-14", "beethoven-diabelli-15-17", "beethoven-diabelli-18-19",
    "beethoven-diabelli-20-23", "beethoven-diabelli-24", "beethoven-diabelli-25-29",
    "beethoven-diabelli-30", "beethoven-diabelli-31", "beethoven-diabelli-32", "beethoven-diabelli-33",
]
SEASONS = [f"tchaikovsky-seasons-{m}" for m in [
    "january", "february", "march", "april", "may", "june",
    "july", "august", "september", "october", "november", "december",
]]
ALL_45 = GOLDBERG + DIABELLI + SEASONS


def main():
    url_map = {}
    for line in open(URLS, encoding="utf-8"):
        line = line.strip()
        if not line or line.startswith("#") or "|" not in line:
            continue
        p = [x.strip() for x in line.split("|")]
        url_map[p[0]] = (p[2], p[3])

    ok = skip = fail = missing = 0
    for track_id in ALL_45:
        if track_id not in url_map:
            missing += 1
            print(f"MISSING_URL {track_id}", flush=True)
            continue
        url, _ = url_map[track_id]
        path = urllib.parse.urlparse(url).path
        m = re.search(r"\.([a-zA-Z0-9]+)$", path)
        ext = m.group(1).lower() if m else "ogg"
        dst = os.path.join(STAGING, f"{track_id}.{ext}")
        if os.path.exists(dst) and os.path.getsize(dst) > 0:
            skip += 1
            continue
        tmp = dst + ".tmp"
        r = subprocess.run([
            "curl", "-L", "--fail", "-sS",
            "--retry", "3", "--retry-delay", "2", "--retry-all-errors",
            "--connect-timeout", "20", "--max-time", "120",
            "-A", "SoundistRadioIngest/1.0 (contact: admin@soundist.app)",
            "-o", tmp, url,
        ], capture_output=True)
        if r.returncode != 0 or not os.path.exists(tmp) or os.path.getsize(tmp) == 0:
            if os.path.exists(tmp):
                os.remove(tmp)
            fail += 1
            print(f"FAIL {track_id} ({ext})", flush=True)
            continue
        os.replace(tmp, dst)
        ok += 1
        print(f"OK   {track_id} ({ext}) {os.path.getsize(dst)} bytes", flush=True)

    print(f"\n=== 补下载完成: ok={ok} skip={skip} fail={fail} missing_url={missing} 总计={len(ALL_45)} ===")


if __name__ == "__main__":
    main()
