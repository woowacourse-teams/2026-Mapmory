// A manual criterion is recorded by a person in gates/manual/<id>.json:
// { "pass": true, "by": "티온", "at": "2026-10-28", "device": "iPhone 15 Safari 18", "note": "6장면 확인, mp4 재생됨" }
import fs from "node:fs/promises";
import path from "node:path";

export async function record(ctx) {
  const file = path.join(ctx.gatesDir, "manual", `${ctx.criterion.id}.json`);
  const data = JSON.parse(await fs.readFile(file, "utf8").catch(() => "null"));
  if (!data) return { pass: false, value: "기록 없음", detail: `${ctx.rel(file)} 에 사람이 확인한 결과를 적어야 함` };
  const missing = ["pass", "by", "at"].filter((k) => data[k] === undefined || data[k] === "");
  if (missing.length) return { pass: false, value: `기록 불완전 (${missing.join(", ")})`, evidence: [file] };
  return { pass: data.pass === true, value: `${data.by} · ${data.at}${data.device ? ` · ${data.device}` : ""}${data.note ? ` · ${data.note}` : ""}`, evidence: [file] };
}
