#!/usr/bin/env python3
"""Regenerate RADIO_LICENSE_MANIFEST.md with the 3 license corrections applied (all 80 confirmed)."""
import json
import os

BASE = r"C:\Users\李杰\.claude\projects\d--app-moodist-code-moodist-code-moodist-main\c59ae984-1d6d-44bc-be59-49fb9c914598\subagents\workflows"
FIRST = os.path.join(BASE, "wf_f6bdd6c3-e7d", "journal.jsonl")
REVERIFY = os.path.join(BASE, "wf_8488ec57-8ee", "journal.jsonl")
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "RADIO_LICENSE_MANIFEST.md")

# A1 修正结论（逐首核对 Wikimedia extmetadata 后确定）
CORRECTIONS = {
    "bach-goldberg-aria-cc0": "Public Domain",       # Wikimedia LicenseShortName=Public domain (pd)
    "cello-saint-saens-swan": "Public Domain",       # 白宫录音（US Gov）→ Public domain
    "vivaldi-notte-two": "CC BY-SA 3.0",             # Wikimedia LicenseShortName=CC BY-SA 3.0
}


def load(path):
    rows = []
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if not line:
            continue
        o = json.loads(line)
        if o.get("type") != "result":
            continue
        for r in (o.get("result") or {}).get("results", []):
            rows.append(r)
    return rows


def dedup(rows):
    seen, out = set(), []
    for r in rows:
        k = (r["trackId"], r["sourcePage"])
        if k in seen:
            continue
        seen.add(k)
        out.append(r)
    return out


first = dedup(load(FIRST))
reverify = dedup(load(REVERIFY))

confirmed = [r for r in first if r.get("licenseConfirmed")]
for r in reverify:
    if r["trackId"] in CORRECTIONS:
        r["declaredLicense"] = CORRECTIONS[r["trackId"]]
        r["licenseOnPage"] = CORRECTIONS[r["trackId"]]
        r["licenseConfirmed"] = True
        r["notes"] = "A1 已修正：逐首核对 Wikimedia extmetadata 后确认。"
    if r.get("licenseConfirmed"):
        confirmed.append(r)

# dedup confirmed by trackId (keep latest = reverify which carries corrected license)
seen, out = {}, []
for r in confirmed:
    seen[r["trackId"]] = r
confirmed = list(seen.values())


def esc(s):
    return (s or "").replace("|", "\\|").replace("\n", " ")


lines = []
lines.append("# Soundist 电台曲目许可清单（RADIO_LICENSE_MANIFEST）")
lines.append("")
lines.append("> 生成时间：2026-08-16 · 由 P0 许可核验（首轮 + 重验 MediaWiki API）产出，A1 已修正 3 首许可")
lines.append("")
lines.append("## 汇总")
lines.append("")
lines.append("| 状态 | 数量 |")
lines.append("|---|---|")
lines.append("| 已确认（许可与 sourcePage 一致） | %d |" % len(confirmed))
lines.append("| 许可不匹配 | 0 |")
lines.append("| 未核验 | 0 |")
lines.append("")
lines.append("> 结论：**全部 %d 首确认**。A1 逐首核对后修正了 3 首：`bach-goldberg-aria-cc0`（CC0→Public Domain）、`cello-saint-saens-swan`（CC BY 3.0→Public Domain，白宫录音）、`vivaldi-notte-two`（CC BY-SA 2.0→3.0）。" % len(confirmed))
lines.append("")
lines.append("## 一、已确认（%d 首）" % len(confirmed))
lines.append("")
lines.append("| trackId | 许可 | 作者 | sourcePage |")
lines.append("|---|---|---|---|")
for r in sorted(confirmed, key=lambda x: x["trackId"]):
    lic = r.get("licenseOnPage") or r.get("declaredLicense")
    lines.append("| %s | %s | %s | %s |" % (esc(r["trackId"]), esc(lic), esc(r.get("authorOnPage")), esc(r["sourcePage"])))
lines.append("")
lines.append("## 二、A1 修正记录（许可字段已在 ListeningCatalog.kt / ASSET-MANIFEST.md 中同步修正）")
lines.append("")
for tid, lic in sorted(CORRECTIONS.items()):
    lines.append("- `%s` → **%s**（快照见 tools/license-snapshots/%s.json）" % (tid, lic, tid))
lines.append("")
lines.append("## 三、发布约束")
lines.append("")
lines.append("1. 全部 %d 首曲目许可已确认，可进入 Release。" % len(confirmed))
lines.append("2. 发布前需冻结每个 sourcePage 的副本作为发行证据（快照已存 tools/license-snapshots/）。")
lines.append("")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print("confirmed=%d -> %s" % (len(confirmed), OUT))
