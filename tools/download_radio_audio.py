#!/usr/bin/env python3
"""Download Soundist radio audio into a STAGING directory (not the APK).

Reads RADIO_DOWNLOAD_URLS.txt (ID | title | url | source-page), extracts the
Android-referenced track IDs from ListeningCatalog.kt (including template
expansion), downloads only those into tools/radio-staging/, skips the 8 already
verified assets, and records SHA-256/size/ext/mime into RADIO_DOWNLOAD_LOG.tsv.

No file is written into the APK. Verified, decodable files are promoted later.
"""
import os
import re
import sys
import hashlib
import subprocess
import urllib.parse
import time

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # apps/android-native
STAGING = os.path.join(REPO, "tools", "radio-staging")
URLS_FILE = os.path.join(REPO, "RADIO_DOWNLOAD_URLS.txt")
CATALOG = os.path.join(REPO, "feature", "listening", "src", "main", "java", "com", "soundist", "feature", "listening", "ListeningCatalog.kt")
LOG_FILE = os.path.join(REPO, "RADIO_DOWNLOAD_LOG.tsv")
ASSET_DIR = os.path.join(REPO, "app", "src", "main", "assets", "radio")

# The 8 already-verified, packaged assets (from ASSET-MANIFEST.md).
VERIFIED = {
    "ambient-relax-background-one", "ambient-sunset-walk", "bach-air", "bach-wtc-prelude",
    "chill-lofi", "chopin-nocturne-55", "lofi-again", "vivaldi-notte-one",
}

MIME_BY_EXT = {"ogg": "audio/ogg", "oga": "audio/ogg", "mp3": "audio/mpeg", "flac": "audio/flac", "wav": "audio/wav"}

os.makedirs(STAGING, exist_ok=True)


def android_track_ids():
    src = open(CATALOG, encoding="utf-8").read()
    ids = set()
    for m in re.finditer(r'(?:commonsTrack|ogaTrack|track|incompetechTrack|openMusicArchiveTrack)\(\s*(?:id\s*=\s*)?\"([a-z0-9-]+)\"', src):
        ids.add(m.group(1))
    for s in ["i", "ii", "iv", "v", "vi", "vii", "viii", "ix", "x"]:
        ids.add(f"ambient-pad-{s}")
    return ids


def parse_urls():
    url_map = {}
    for line in open(URLS_FILE, encoding="utf-8"):
        line = line.strip()
        if not line or line.startswith("#") or "|" not in line:
            continue
        parts = [p.strip() for p in line.split("|")]
        track_id = parts[0]
        title = parts[1]
        url = parts[2]
        source = parts[3] if len(parts) > 3 else ""
        url_map[track_id] = (title, url, source)
    return url_map


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def ext_of(url):
    path = urllib.parse.urlparse(url).path
    m = re.search(r"\.([a-zA-Z0-9]+)$", path)
    return (m.group(1).lower() if m else "ogg")


def main():
    android_ids = android_track_ids()
    url_map = parse_urls()
    to_download = android_ids - VERIFIED
    log_lines = ["track_id\text\ttitle\turl\tsha256\tstatus"]

    ok = skip = fail = missing = 0
    for track_id in sorted(to_download):
        if track_id not in url_map:
            missing += 1
            print(f"MISSING {track_id} (无下载地址)", flush=True)
            log_lines.append(f"{track_id}\t\t\t\t\tmissing_url")
            continue
        title, url, source = url_map[track_id]
        ext = ext_of(url)
        dst = os.path.join(STAGING, f"{track_id}.{ext}")
        if os.path.exists(dst) and os.path.getsize(dst) > 0:
            skip += 1
            log_lines.append(f"{track_id}\t{ext}\t{title}\t{url}\t{sha256_of(dst)}\tskip")
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
            print(f"FAIL {track_id} ({ext}): {url}", flush=True)
            log_lines.append(f"{track_id}\t{ext}\t{title}\t{url}\t\tfail")
            continue
        os.replace(tmp, dst)
        digest = sha256_of(dst)
        ok += 1
        print(f"OK   {track_id} ({ext}) {os.path.getsize(dst)} bytes", flush=True)
        log_lines.append(f"{track_id}\t{ext}\t{title}\t{url}\t{digest}\tok")

    with open(LOG_FILE, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(log_lines) + "\n")
    print(f"\n=== 完成: ok={ok} skip={skip} fail={fail} missing_url={missing} 需下载={len(to_download)} ===")


if __name__ == "__main__":
    main()
