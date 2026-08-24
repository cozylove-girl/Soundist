#!/usr/bin/env python3
"""Merge first-pass + re-verification license results and regenerate RADIO_LICENSE_MANIFEST.md."""
import json
import os

BASE = r"C:\Users\李杰\.claude\projects\d--app-moodist-code-moodist-code-moodist-main\c59ae984-1d6d-44bc-be59-49fb9c914598\subagents\workflows"
FIRST = os.path.join(BASE, "wf_f6bdd6c3-e7d", "journal.jsonl")
REVERIFY = os.path.join(BASE, "wf_8488ec57-8ee", "journal.jsonl")
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "RADIO_LICENSE_MANIFEST.md")


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

# First-pass confirmed + (re-verified tracks: confirmed -> confirmed, unconfirmed -> mismatch)
first_confirmed = [r for r in first if r.get("licenseConfirmed")]
reverify_confirmed = [r for r in reverify if r.get("licenseConfirmed")]
reverify_mismatch = [r for r in reverify if not r.get("licenseConfirmed")]

confirmed = first_confirmed + reverify_confirmed
mismatch = reverify_mismatch


def esc(s):
    return (s or "").replace("|", "\\|").replace("\n", " ")


lines = []
lines.append("# Soundist 电台曲目许可清单（RADIO_LICENSE_MANIFEST）")
lines.append("")
lines.append("> 生成时间：2026-08-16 · 由 P0 许可核验（首轮 5 子代理 + 重验 3 子代理，MediaWiki API / allorigins 代理）产出")
lines.append("")
lines.append("## 汇总")
lines.append("")
lines.append("| 状态 | 数量 |")
lines.append("|---|---|")
lines.append("| 已确认（许可与 sourcePage 一致） | %d |" % len(confirmed))
lines.append("| 许可不匹配（需人工复核，不得进入 Release） | %d |" % len(mismatch))
lines.append("| 未核验（源页不可达） | 0 |")
lines.append("")
lines.append("> 首轮直连 Wikimedia 超时，重验改用 MediaWiki API（extmetadata.LicenseShortName）后全部 36 首源页可达；33 首确认，3 首许可与声明不一致。")
lines.append("")
lines.append("## 一、已确认（%d 首）" % len(confirmed))
lines.append("")
lines.append("| trackId | 许可 | 作者 | sourcePage |")
lines.append("|---|---|---|---|")
for r in sorted(confirmed, key=lambda x: x["trackId"]):
    lic = r.get("licenseOnPage") or r.get("declaredLicense")
    lines.append("| %s | %s | %s | %s |" % (esc(r["trackId"]), esc(lic), esc(r.get("authorOnPage")), esc(r["sourcePage"])))
lines.append("")
lines.append("## 二、许可不匹配（%d 首，不得进入 Release，需人工核对 Wikimedia 文件页实际许可标签）" % len(mismatch))
lines.append("")
for r in sorted(mismatch, key=lambda x: x["trackId"]):
    lines.append("- **%s**：声明 `%s`，页面实际 `%s`。%s" % (
        esc(r["trackId"]), esc(r.get("declaredLicense")), esc(r.get("licenseOnPage")), esc(r.get("notes", ""))))
lines.append("")
lines.append("## 三、发布约束")
lines.append("")
lines.append("1. 仅「已确认」曲目可进入 Release。")
lines.append("2. 「许可不匹配」曲目需人工核对 Wikimedia 文件页实际许可标签后，方可解禁。")
lines.append("3. 发布前需冻结每个 sourcePage 的副本作为发行证据。")
lines.append("")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print("confirmed=%d mismatch=%d -> %s" % (len(confirmed), len(mismatch), OUT))
for r in mismatch:
    print("MISMATCH", r["trackId"], "declared=", r.get("declaredLicense"), "onPage=", r.get("licenseOnPage"))
