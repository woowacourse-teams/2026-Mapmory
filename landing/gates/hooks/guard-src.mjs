#!/usr/bin/env node
// Claude Code PreToolUse hook (see settings.example.json): keeps the stage order for the tech section.
//   before stage 1 (브리프) passes   no edit under landing/src/
//   before stage 2 (문구) passes     only landing/src/techStory.js (the words)
//   from stage 3 on                 anything
// landing/prototypes/ is always open. GATES_BYPASS=1 turns the guard off for one command. Exit 2 blocks the
// tool call and shows the reason; anything else lets it through.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

let input = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => { input += chunk; });
process.stdin.on("end", () => {
  let data = {};
  try { data = JSON.parse(input); } catch { /* not JSON: let it through */ }
  const file = data.tool_input?.file_path || data.tool_input?.notebook_path || "";
  if (!file || process.env.GATES_BYPASS === "1") process.exit(0);
  const gatesDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
  const landing = path.resolve(gatesDir, "..");
  const rel = path.relative(landing, path.resolve(file)).split(path.sep).join("/");
  if (rel.startsWith("..") || !rel.startsWith("src/")) process.exit(0);
  let state = {};
  try { state = JSON.parse(fs.readFileSync(path.join(gatesDir, "state.json"), "utf8")); } catch { /* no run yet */ }
  const passed = (n) => state.stages?.[n]?.passed === true;
  if (!passed(1)) { block(1, "브리프(gates/brief.json)를 쓰고 'npm run gate 1'을 통과한 뒤에 코드를 고칩니다."); }
  if (!passed(2) && rel !== "src/techStory.js") { block(2, "문구 단계에서는 src/techStory.js(문구)만 고칩니다. 시안은 landing/prototypes/ 에서."); }
  process.exit(0);
  function block(stage, why) {
    console.error(`[gates] ${stage}단계가 아직 통과되지 않았습니다. ${why} (급하면 GATES_BYPASS=1)`);
    process.exit(2);
  }
});
