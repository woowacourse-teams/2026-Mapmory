// 4.8 The design stage compares three variants on different axes and keeps the one the cold readers
// recognised best; ties go to less motion, then to higher focus agreement (4.6), then to the variant listed
// first (the approved sketch: no evidence to change it). gates/variants.json:
// { "variants": [ { "key": "base", "name": "기준안", "axis": "글이 폰 아래", "auto": { "4.1": true, ... "4.5": true },
//                   "recognition": 1, "motionMs": 7100, "focusMin": 0.8, "screenshots": [...] }, ... ],
//   "chosen": "base", "reason": "..." }
import fs from "node:fs/promises";
import path from "node:path";

export async function pick(ctx) {
  const file = path.join(ctx.gatesDir, "variants.json");
  const data = JSON.parse(await fs.readFile(file, "utf8").catch(() => "null"));
  if (!data) return { pass: false, value: "variants.json 없음" };
  const problems = [];
  const vs = data.variants ?? [];
  if (vs.length < 3) problems.push(`안이 ${vs.length}개 (3개 필요)`);
  if (new Set(vs.map((v) => v.axis)).size !== vs.length) problems.push("축이 같은 안이 있음");
  const eligible = vs.filter((v) => ["4.1", "4.2", "4.3", "4.4", "4.5"].every((id) => v.auto?.[id] === true));
  if (eligible.length !== vs.length) problems.push(`4.1~4.5를 못 넘은 안: ${vs.filter((v) => !eligible.includes(v)).map((v) => v.key).join(", ")}`);
  const best = [...eligible].sort((a, b) => (b.recognition - a.recognition) || (a.motionMs - b.motionMs) || ((b.focusMin ?? 0) - (a.focusMin ?? 0)) || (vs.indexOf(a) - vs.indexOf(b)))[0];
  if (!best) problems.push("고를 수 있는 안이 없음");
  else if (data.chosen !== best.key) problems.push(`규칙상 ${best.key} (인식 ${best.recognition}, 움직임 ${best.motionMs}ms, 시선 일치 ${best.focusMin ?? "-"})인데 ${data.chosen} 선택`);
  return { pass: problems.length === 0, value: best ? `${vs.length}안, 선택 ${data.chosen} (인식 ${Math.round((best.recognition ?? 0) * 100)}%)` : "선택 없음", detail: problems.join("; "), evidence: [file] };
}
