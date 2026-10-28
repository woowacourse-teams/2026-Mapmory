// Scores a judgments file: gates/judgments/<name>.json
// {
//   "criterion": "4.7", "judgedAt": "2026-10-27T11:00:00Z", "material": ["reports/evidence/stage-4/scene-1.png", ...],
//   "question": "이 화면은 무엇을 보여 주나요? 사용자는 무엇을 하면 되나요?",
//   "judges": [ { "id": "j1", "context": "none", "product": { "answer": "...", "correct": true },
//                 "scenes": [ { "scene": 1, "answer": "...", "correct": true, "focus": "사진 한 장" }, ... ] }, ... ]
// }
// A judge is a reader who was given only `material` and `question`, nothing about this project. `correct`
// is marked against the brief's message for that scene by whoever collects the answers, and the raw
// answer stays in the file so the marking can be checked.
import fs from "node:fs/promises";
import path from "node:path";
import { JUDGES, SCENE_COUNT } from "../config.mjs";

export async function score(ctx) {
  const c = ctx.criterion;
  const file = path.join(ctx.gatesDir, "judgments", `${c.judgment}.json`);
  const data = JSON.parse(await fs.readFile(file, "utf8").catch(() => "null"));
  if (!data) return { pass: false, value: "판정 파일 없음", detail: `${ctx.rel(file)} 를 만들어야 함` };
  const minJudges = c.minJudges ?? JUDGES;
  const judges = data.judges ?? [];
  if (judges.length < minJudges) return { pass: false, value: `판정자 ${judges.length}명 (최소 ${minJudges})`, evidence: [file] };
  const threshold = c.threshold ?? 0.8;
  const perScene = [];
  for (let n = 1; n <= SCENE_COUNT; n++) {
    const marks = judges.map((j) => (j.scenes ?? []).find((s) => s.scene === n)).filter(Boolean);
    const rate = marks.length ? marks.filter((m) => m.correct === true).length / judges.length : 0;
    perScene.push({ scene: n, rate, answered: marks.length });
  }
  const product = judges.filter((j) => j.product).length ? judges.filter((j) => j.product?.correct === true).length / judges.length : null;
  const focusAgreement = c.judgment === "focus" ? perScene.map((p) => { const picks = judges.map((j) => (j.scenes ?? []).find((s) => s.scene === p.scene)?.focus).filter(Boolean); const top = mode(picks); return { scene: p.scene, top, rate: picks.length ? picks.filter((x) => x === top).length / judges.length : 0 }; }) : null;
  const rates = focusAgreement ? focusAgreement.map((f) => f.rate) : perScene.map((p) => p.rate);
  const pass = rates.every((r) => r >= threshold) && (product === null || product >= threshold);
  const value = `${focusAgreement ? "같은 요소" : "정답"} 비율 장면별 ${rates.map((r) => Math.round(r * 100)).join("/")}%${product !== null ? `, 제품 ${Math.round(product * 100)}%` : ""} (판정자 ${judges.length}명, 기준 ${Math.round(threshold * 100)}%)`;
  return { pass, value, detail: focusAgreement ? focusAgreement.map((f) => `장면 ${f.scene}: ${f.top ?? "-"} ${Math.round(f.rate * 100)}%`).join("; ") : perScene.filter((p) => p.rate < threshold).map((p) => `장면 ${p.scene} ${Math.round(p.rate * 100)}%`).join("; "), evidence: [file] };
}

const mode = (xs) => { const counts = new Map(); for (const x of xs) counts.set(x, (counts.get(x) ?? 0) + 1); return [...counts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0]; };
