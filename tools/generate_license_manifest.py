#!/usr/bin/env python3
"""Generate RADIO_LICENSE_MANIFEST.md from the P0 license-verification workflow journal."""
import json
import os

JOURNAL = r"C:\Users\李杰\.claude\projects\d--app-moodist-code-moodist-code-moodist-main\c59ae984-1d6d-44bc-be59-49fb9c914598\subagents\workflows\wf_f6bdd6c3-e7d\journal.jsonl"
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "RADIO_LICENSE_MANIFEST.md")

rows = []
for line in open(JOURNAL, encoding="utf-8"):
    line = line.strip()
    if not line:
        continue
    o = json.loads(line)
    if o.get("type") != "result":
        continue
    for r in (o.get("result") or {}).get("results", []):
        rows.append(r)

# dedup by (trackId, sourcePage)
seen, uniq = set(), []
for r in rows:
    k = (r["trackId"], r["sourcePage"])
    if k in seen:
        continue
    seen.add(k)
    uniq.append(r)
rows = uniq

conf = [r for r in rows if r.get("licenseConfirmed")]
unv = [r for r in rows if not r.get("licenseConfirmed")]
mm = [r for r in rows if any(k in r.get("notes", "").lower() for k in
        ("mismatch", "does not match", "may be wrong", "does not match the declared"))]


def esc(s):
    return (s or "").replace("|", "\\|").replace("\n", " ")


lines = []
lines.append("# Soundist 电台曲目许可清单（RADIO_LICENSE_MANIFEST）")
lines.append("")
lines.append("> 生成时间：2026-08-16 · 由 P0 许可核验工作流（5 子代理抓取 sourcePage）产出")
lines.append("> 结论：82 首中 45 首已确认，37 首因 Wikimedia 源页直连超时未核验（需重验），3 处潜在许可不匹配提示。")
lines.append("")
lines.append("## 汇总")
lines.append("")
lines.append("| 状态 | 数量 |")
lines.append("|---|---|")
lines.append("| 已确认（sourcePage 可见且许可匹配） | %d |" % len(conf))
lines.append("| 未核验（sourcePage 不可达） | %d |" % len(unv))
lines.append("| 潜在许可不匹配（需重点复核） | %d |" % len(mm))
lines.append("")
lines.append("> 关键：commons.wikimedia.org 在本网络直连超时；部分代理改用 MediaWiki API / allorigins 代理后成功核验了 goldberg/diabelli/seasons/brahms/schumann 等，base-APK 代理仍未能核验。未核验的 37 首不得进入 Release，需按可用代理路径重验。")
lines.append("")
lines.append("## 一、已确认（%d 首）" % len(conf))
lines.append("")
lines.append("| trackId | 许可 | 作者 | sourcePage |")
lines.append("|---|---|---|---|")
for r in sorted(conf, key=lambda x: x["trackId"]):
    lic = r.get("licenseOnPage") or r.get("declaredLicense")
    lines.append("| %s | %s | %s | %s |" % (esc(r["trackId"]), esc(lic), esc(r.get("authorOnPage")), esc(r["sourcePage"])))
lines.append("")
lines.append("## 二、未核验（%d 首，不得进入 Release）" % len(unv))
lines.append("")
lines.append("| trackId | 声明许可 | sourcePage | 说明 |")
lines.append("|---|---|---|---|")
for r in sorted(unv, key=lambda x: x["trackId"]):
    lines.append("| %s | %s | %s | %s |" % (esc(r["trackId"]), esc(r.get("declaredLicense")), esc(r["sourcePage"]), esc(r.get("notes", ""))[:100]))
lines.append("")
lines.append("## 三、潜在许可不匹配（重点复核）")
lines.append("")
if mm:
    for r in mm:
        lines.append("- **%s**：声明 `%s`，%s" % (esc(r["trackId"]), esc(r.get("declaredLicense")), esc(r.get("notes", ""))))
else:
    lines.append("（无）")
lines.append("")
lines.append("## 四、发布约束")
lines.append("")
lines.append("1. 仅「已确认」曲目可进入 Release。")
lines.append("2. 37 首「未核验」曲目需通过 MediaWiki API（w/api.php?action=query&prop=imageinfo&iiprop=extmetadata）或 allorigins 代理逐页重验许可后，方可解禁。")
lines.append("3. 「潜在不匹配」曲目（cello-saint-saens-swan、chopin-canon-f-minor、choral-handel-word 等）需人工核对 Wikimedia 文件页实际许可标签。")
lines.append("4. 发布前需冻结每个 sourcePage 的副本作为发行证据。")
lines.append("")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print("written: %d confirmed, %d unverified, %d mismatch-flags -> %s" % (len(conf), len(unv), len(mm), OUT))
