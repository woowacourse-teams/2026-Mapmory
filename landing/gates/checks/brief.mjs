// 1.1 The brief is complete: the purpose, and for each scene the user's question, one message, one
// interaction, the fact it rests on (with where in the app code), and the event that measures it.
import fs from "node:fs/promises";
import path from "node:path";
import { SCENE_COUNT } from "../config.mjs";

// a second sentence shows as a sentence mark with more text after it ("…해요. 그리고…")
const sentenceEnds = (s) => (s.match(/[.!?。](?=\s*\S)/g) ?? []).length + 1;

export async function schema(ctx) {
  const file = path.join(ctx.gatesDir, "brief.json");
  const brief = JSON.parse(await fs.readFile(file, "utf8"));
  const problems = [];
  for (const key of ["goal", "kpi", "visitor", "questions"]) if (!brief.purpose?.[key] || (Array.isArray(brief.purpose[key]) && !brief.purpose[key].length)) problems.push(`purpose.${key} 비어 있음`);
  const scenes = brief.scenes ?? [];
  if (scenes.length !== SCENE_COUNT) problems.push(`장면 수 ${scenes.length} (기대 ${SCENE_COUNT})`);
  scenes.forEach((s, i) => {
    const n = i + 1;
    for (const key of ["step", "question", "message", "interaction", "truth", "event", "purpose"]) if (!s[key]) problems.push(`장면 ${n}: ${key} 비어 있음`);
    if (typeof s.message === "string" && sentenceEnds(s.message.trim() + " ") > 1) problems.push(`장면 ${n}: 메시지가 한 문장이 아님`);
    if (typeof s.message === "string" && s.message.length > 40) problems.push(`장면 ${n}: 메시지 ${s.message.length}자 (40자 초과)`);
    if (typeof s.interaction === "string" && /,|그리고|및/.test(s.interaction)) problems.push(`장면 ${n}: 조작이 하나가 아님`);
    if (!s.truth?.claim || !Array.isArray(s.truth?.evidence) || !s.truth.evidence.length) problems.push(`장면 ${n}: truth.claim 또는 truth.evidence 비어 있음`);
    if (!["A", "B", "C", "D", "E"].includes(s.purpose)) problems.push(`장면 ${n}: purpose는 A~E 중 하나`);
  });
  const questions = new Set(scenes.map((s) => s.question));
  if (questions.size !== scenes.length) problems.push("같은 질문에 답하는 장면이 둘 이상");
  return { pass: problems.length === 0, value: problems.length ? `${problems.length}개 빠짐` : `6장면 × 7항목 채움, 질문 ${questions.size}개`, detail: problems.join("; "), evidence: [file] };
}
