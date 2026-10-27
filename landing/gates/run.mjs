#!/usr/bin/env node
// Gate runner for the tech section. Usage:
//   node gates/run.mjs <stage>            stage = 1..7 or brief|copy|structure|design|build|qa|launch
//   --url http://localhost:4173/          page under test (a vite preview of the current build)
//   --only 3.3,3.4                        run some criteria only (the stage is not marked passed)
//   --force                               run even though an earlier stage has not passed (recorded)
// Writes gates/reports/stage-<n>.{json,md}, evidence under gates/reports/evidence/stage-<n>/, and
// gates/state.json (which stages passed, when, with which report). Exit code 1 when any criterion fails.
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { JUDGES, SOURCES, STAGES, stageByKeyOrNumber } from "./config.mjs";

const gatesDir = path.dirname(fileURLToPath(import.meta.url));
const landingRoot = path.resolve(gatesDir, "..");
const args = process.argv.slice(2);
const flag = (name, fallback) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : fallback; };
const stage = stageByKeyOrNumber(args.find((a) => !a.startsWith("--") && args[args.indexOf(a) - 1]?.startsWith("--") !== true));
if (!stage) { console.error("usage: node gates/run.mjs <1-7|brief|copy|structure|design|build|qa|launch> [--url URL] [--only ids] [--force]"); process.exit(2); }
const url = flag("--url", process.env.GATES_URL || "http://localhost:4173/");
const only = flag("--only", "")?.split(",").map((s) => s.trim()).filter(Boolean) ?? [];
const force = args.includes("--force");

const statePath = path.join(gatesDir, "state.json");
const state = JSON.parse(await fs.readFile(statePath, "utf8").catch(() => '{"stages":{}}'));
const blocked = STAGES.filter((s) => s.n < stage.n && !state.stages?.[s.n]?.passed).map((s) => `${s.n} ${s.name}`);
if (blocked.length && !force) {
  console.error(`먼저 통과해야 하는 단계: ${blocked.join(", ")}. (--force 로 기록하며 강제 실행)`);
  process.exit(3);
}

const evidenceDir = path.join(gatesDir, "reports", "evidence", `stage-${stage.n}`);
await fs.mkdir(evidenceDir, { recursive: true });
const modules = {
  brief: () => import("./checks/brief.mjs"), judge: () => import("./checks/judge.mjs"), manual: () => import("./checks/manual.mjs"),
  copy: () => import("./checks/copy.mjs"), structure: () => import("./checks/structure.mjs"), visual: () => import("./checks/visual.mjs"),
  variants: () => import("./checks/variants.mjs"), build: () => import("./checks/build.mjs"), qa: () => import("./checks/qa.mjs"),
};
const ctx = { url, landingRoot, gatesDir, evidenceDir, stage, JUDGES, SOURCES, evidence: (name) => path.join(evidenceDir, name), rel: (p) => path.relative(landingRoot, p) };

const rows = [];
for (const criterion of stage.criteria) {
  if (only.length && !only.includes(criterion.id)) continue;
  const [mod, fn] = criterion.check.split(".");
  const started = Date.now();
  let result;
  try {
    const m = await modules[mod]();
    if (typeof m[fn] !== "function") throw new Error(`check ${criterion.check} is not implemented`);
    result = await m[fn]({ ...ctx, criterion });
  } catch (error) {
    result = { pass: false, value: "오류", detail: error?.stack ?? String(error) };
  }
  const row = { id: criterion.id, name: criterion.name, pass: Boolean(result.pass), expected: criterion.pass, value: result.value ?? "",
    detail: result.detail ?? "", evidence: (result.evidence ?? []).map(ctx.rel), kind: criterion.kind, purpose: criterion.purpose,
    sources: criterion.source.map((k) => SOURCES[k]?.title ?? k), derived: criterion.derived ?? null, ms: Date.now() - started };
  rows.push(row);
  console.log(`${row.pass ? "PASS" : "FAIL"}  ${row.id}  ${row.name}\n      기준: ${row.expected}\n      측정: ${row.value}${row.detail && !row.pass ? `\n      ${String(row.detail).split("\n")[0]}` : ""}`);
}

const complete = !only.length;
const passed = complete && rows.every((r) => r.pass);
const at = new Date().toISOString();
const report = { stage: stage.n, key: stage.key, name: stage.name, url, at, complete, passed, forced: force && blocked.length > 0, rows };
await fs.mkdir(path.join(gatesDir, "reports"), { recursive: true });
await fs.writeFile(path.join(gatesDir, "reports", `stage-${stage.n}.json`), JSON.stringify(report, null, 2) + "\n");
await fs.writeFile(path.join(gatesDir, "reports", `stage-${stage.n}.md`), markdown(report));
if (complete) {
  state.stages ??= {};
  state.stages[stage.n] = { key: stage.key, name: stage.name, passed, at, report: `gates/reports/stage-${stage.n}.md`, forced: report.forced };
  state.current = passed ? Math.min(stage.n + 1, 7) : stage.n;
  await fs.writeFile(statePath, JSON.stringify(state, null, 2) + "\n");
}
console.log(`\n${stage.n}단계 ${stage.name}: ${passed ? "통과" : complete ? "실패" : "일부만 측정"} (${rows.filter((r) => r.pass).length}/${rows.length}) → gates/reports/stage-${stage.n}.md`);
process.exit(passed || (!complete && rows.every((r) => r.pass)) ? 0 : 1);

function markdown(r) {
  const esc = (s) => String(s ?? "").replace(/\|/g, "\\|").replace(/\n/g, " ");
  const lines = [`# ${r.stage}단계 ${r.name} 통과 보고서`, "", `측정 시각 ${r.at} · 대상 ${r.url} · 결과 **${r.passed ? "통과" : r.complete ? "실패" : "일부만 측정"}**${r.forced ? " · 앞 단계 미통과 상태에서 강제 실행" : ""}`, "",
    "| 번호 | 기준 | 통과 수치 | 측정값 | 결과 | 근거 | 증거 |", "| --- | --- | --- | --- | --- | --- | --- |"];
  for (const row of r.rows) lines.push(`| ${row.id} | ${esc(row.name)} | ${esc(row.expected)} | ${esc(row.value)} | ${row.pass ? "통과" : "실패"} | ${esc(row.sources.join("; "))}${row.derived ? ` (유도: ${esc(row.derived)})` : ""} | ${row.evidence.map((e) => `\`${e}\``).join(" ") || ""} |`);
  const failed = r.rows.filter((row) => !row.pass && row.detail);
  if (failed.length) { lines.push("", "## 실패 상세", ""); for (const row of failed) lines.push(`- ${row.id}: ${esc(typeof row.detail === "string" ? row.detail : JSON.stringify(row.detail))}`); }
  return lines.join("\n") + "\n";
}
