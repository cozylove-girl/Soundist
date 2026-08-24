#!/usr/bin/env python3
"""Fetch Wikimedia Commons extmetadata for the 3 license-mismatch tracks, and save snapshots."""
import json
import os
import urllib.parse
import urllib.request
from datetime import datetime, timezone

FILES = {
    "bach-goldberg-aria-cc0": "File:988-aria.lehman1.ogg",
    "cello-saint-saens-swan": "File:20091104 Alisa Weilerstein and Jason Yoder - Saint Saëns' The Swan.ogg",
    "vivaldi-notte-two": "File:Antonio Vivaldi - La Notte - 2.ogg",
}

OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "tools", "license-snapshots")
os.makedirs(OUT_DIR, exist_ok=True)

UA = "SoundistLicenseAudit/1.0 (contact: dev@soundist.app)"

for track, title in FILES.items():
    url = ("https://commons.wikimedia.org/w/api.php?action=query&titles="
           + urllib.parse.quote(title)
           + "&prop=imageinfo&iiprop=extmetadata|url&format=json&formatversion=2")
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=40) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        pages = data["query"]["pages"]
        page = pages[0] if isinstance(pages, list) else list(pages.values())[0]
        imageinfo = (page.get("imageinfo") or [{}])[0]
        meta = imageinfo.get("extmetadata", {})
        # Snapshot: full metadata + fetch timestamp + source page.
        snapshot = {
            "trackId": track,
            "sourcePage": "https://commons.wikimedia.org/wiki/" + title.replace(" ", "_"),
            "fetchedAt": datetime.now(timezone.utc).isoformat(),
            "fileUrl": imageinfo.get("url", ""),
            "extmetadata": meta,
        }
        with open(os.path.join(OUT_DIR, track + ".json"), "w", encoding="utf-8") as f:
            json.dump(snapshot, f, ensure_ascii=False, indent=2)

        def gv(k):
            return (meta.get(k) or {}).get("value", "")

        print("=== %s ===" % track)
        for k in ["LicenseShortName", "License", "Artist", "Credit", "Attribution", "Permission",
                  "UsageTerms", "Copyrighted", "LicenseUrl", "DateTimeOriginal", "ImageDescription"]:
            v = gv(k)
            if v:
                print("  %s: %s" % (k, v))
        print("  snapshot -> %s.json" % track)
    except Exception as e:
        print("=== %s === FAILED: %s" % (track, e))
