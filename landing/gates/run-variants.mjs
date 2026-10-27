#!/usr/bin/env node
// 4단계 시안 비교: 3안(variants)을 같은 빌드에서 `?tsv=<key>`로 열어 4.1~4.5를 각각 재고, 판정자(4.6·4.7)에게 보여 줄
// 장면 스크린샷 6장(390×844, 라이트)을 찍는다. 결과는 reports/evidence/stage-4/<key>/ 와 variants-auto.json.
//   node gates/run-variants.mjs [--url http://localhost:4173/] [--only base,above] [--shots-only]
// --shots-only retakes the screenshots and keeps the measurements already in variants-auto.json.
// motionMs = 예외를 뺀 전환·애니메이션(유한 반복)의 길이 합. 4.8의 동점 규칙(움직임이 적은 안)에 쓴다.
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { JUDGES, SOURCES, STAGES } from "./config.mjs";
import * as visual from "./checks/visual.mjs";
import { VIEWPORTS, beatCount, gotoSection, launch, scrollToBeat, activeScene } from "./lib/browser.mjs";

const gatesDir = path.dirname(fileURLToPath(import.meta.url));
const landingRoot = path.resolve(gatesDir, "..");
const args = process.argv.slice(2);
const flag = (name, fallback) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : fallback; };
const baseUrl = flag("--url", process.env.GATES_URL || "http://localhost:4173/");
const only = flag("--only", "")?.split(",").map((s) => s.trim()).filter(Boolean) ?? [];
const shotsOnly = args.includes("--shots-only");

export const VARIANTS = [
  { key: "base", name: "기준안", axis: "글이 폰 아래 (폰 틀)", description: "폰 틀 안의 화면이 위, 손글씨 라벨과 제목이 그 아래 띠에" },
  { key: "above", name: "글 먼저", axis: "글이 폰 위", description: "손글씨 라벨과 제목이 헤더 아래 띠에 먼저, 폰은 그 아래" },
  { key: "bare", name: "화면만", axis: "폰 틀 없음", description: "폰 몸체와 노치 없이 화면만 둥근 카드로, 글은 그 아래" },
];

const stage = STAGES.find((s) => s.n === 4);
const file = path.join(gatesDir, "reports", "evidence", "stage-4", "variants-auto.json");
const previous = JSON.parse(await fs.readFile(file, "utf8").catch(() => "null"));
const out = { at: new Date().toISOString(), url: baseUrl, variants: previous?.variants ?? [] };
for (const v of VARIANTS) {
  if (only.length && !only.includes(v.key)) continue;
  const kept = out.variants.find((x) => x.key === v.key);
  if (shotsOnly && !kept) { console.log(`${v.key}: 측정값이 없어 건너뜀 (--shots-only)`); continue; }
  const url = v.key === "base" ? baseUrl : `${baseUrl}${baseUrl.includes("?") ? "&" : "?"}tsv=${v.key}`;
  const evidenceDir = path.join(gatesDir, "reports", "evidence", "stage-4", v.key);
  await fs.mkdir(evidenceDir, { recursive: true });
  const ctx = { url, landingRoot, gatesDir, evidenceDir, stage, JUDGES, SOURCES, evidence: (name) => path.join(evidenceDir, name), rel: (p) => path.relative(landingRoot, p) };
  const auto = {};
  const rows = [];
  for (const c of shotsOnly ? [] : stage.criteria.filter((c) => ["4.1", "4.2", "4.3", "4.4", "4.5"].includes(c.id))) {
    const fn = c.check.split(".")[1];
    let r;
    try { r = await visual[fn]({ ...ctx, criterion: c }); } catch (e) { r = { pass: false, value: "오류", detail: e?.stack ?? String(e) }; }
    auto[c.id] = Boolean(r.pass);
    rows.push({ id: c.id, name: c.name, pass: Boolean(r.pass), value: r.value ?? "", detail: r.detail ?? "" });
    console.log(`${v.key}  ${r.pass ? "PASS" : "FAIL"}  ${c.id}  ${c.name}  ${r.value ?? ""}${r.detail && !r.pass ? `  ${String(r.detail).split("\n")[0]}` : ""}`);
  }
  // motionMs from 4.4's evidence: finite, non-excepted durations
  const motion = JSON.parse(await fs.readFile(path.join(evidenceDir, "motion.json"), "utf8").catch(() => '{"found":[],"exceptions":[]}'));
  const excepted = (f) => motion.exceptions.find((e) => (e.selector.startsWith(".") ? f.sel.includes(e.selector) : f.sel === e.selector));
  const motionMs = motion.found.filter((f) => !excepted(f) && f.iter !== "infinite").reduce((s, f) => s + f.d, 0);
  // the six scenes as a phone visitor sees them (390×844, light), plus the desktop view of scene 1 for the record.
  // Each is taken 3.6 s after arriving, when the entry demo (scene 1 flips a photo for 1.7 s, scene 3 folds the
  // record for 1.8 s) has ended and the screen rests in the state that asks for the visitor's own try.
  const shots = [];
  let verified = true;
  const b = await launch(VIEWPORTS.phone);
  try {
    await gotoSection(b.page, url);
    const n = await beatCount(b.page);
    for (let i = 0; i < n; i++) {
      await scrollToBeat(b.page, i, 0.5, 3600);
      const active = await activeScene(b.page);
      if (active !== i) verified = false;
      const file = path.join(evidenceDir, `scene-${i + 1}.png`);
      await b.page.screenshot({ path: file });
      shots.push(ctx.rel(file));
    }
  } finally { await b.close(); }
  const d = await launch(VIEWPORTS.desktop);
  try { await gotoSection(d.page, url); await scrollToBeat(d.page, 0, 0.5, 1200); await d.page.screenshot({ path: path.join(evidenceDir, "desktop-scene-1.png") }); } finally { await d.close(); }
  const row = shotsOnly ? { ...kept, screenshots: shots, scenesVerified: verified, shotsAt: out.at } : { ...v, url, auto, rows, motionMs, screenshots: shots, scenesVerified: verified };
  out.variants = [...out.variants.filter((x) => x.key !== v.key), row].sort((a, b) => VARIANTS.findIndex((x) => x.key === a.key) - VARIANTS.findIndex((x) => x.key === b.key));
  console.log(`${v.key}: motionMs ${motionMs}, 장면 스크린샷 ${shots.length}장${verified ? "" : " (장면 활성화가 어긋난 스크린샷 있음)"}`);
}
await fs.writeFile(file, JSON.stringify(out, null, 2) + "\n");
console.log(`→ ${path.relative(landingRoot, file)}`);
