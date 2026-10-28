// 2.1–2.3: how much text a screen carries, how long the titles run, and whether the words are plain.
import fs from "node:fs/promises";
import path from "node:path";
import { SCENE_COUNT } from "../config.mjs";
import { VIEWPORTS, beatCount, gotoSection, launch, scrollToBeat, visibleTextBlocks } from "../lib/browser.mjs";

const COPY_FILES = ["src/techStory.js", "src/TechStory.jsx"];
// Words a first-time visitor should never have to know (Unbounce: grade 5–7 copy converts 12.9% vs 2.1%).
export const JARGON = ["EXIF", "메타데이터", "서버", "API", "클라우드", "렌더링", "알고리즘", "인덱싱", "캐시", "파싱", "GPS", "좌표", "데이터베이스"];

// 2.1 On a phone, each scene shows two blocks outside the phone (the step label and the title) and at most
// one hint line inside it. Everything else on screen is the picture.
export async function blocks(ctx) {
  const b = await launch(VIEWPORTS.phoneS);
  try {
    await gotoSection(b.page, ctx.url);
    const n = await beatCount(b.page);
    const perScene = [];
    for (let i = 0; i < n; i++) {
      await scrollToBeat(b.page, i, 0.5, 900);
      const all = await visibleTextBlocks(b.page, ".ts");
      const outside = all.filter((t) => t.inStage && !t.inPhone && !t.aria && !/^[*·]+$/.test(t.text));
      const hints = all.filter((t) => t.inPhone && /\bts-hint\b/.test(t.className));
      const ui = all.filter((t) => t.inPhone && !/\bts-hint\b/.test(t.className));
      perScene.push({ scene: i + 1, outside: outside.map((t) => t.text), hints: hints.map((t) => t.text), ui: ui.map((t) => t.text) });
    }
    const bad = perScene.filter((s) => s.outside.length !== 2 || s.hints.length > 1);
    const file = ctx.evidence("copy-blocks.json");
    await fs.writeFile(file, JSON.stringify(perScene, null, 2));
    return { pass: bad.length === 0 && n === SCENE_COUNT, value: `폰 밖 ${perScene.map((s) => s.outside.length).join("/")}, 폰 안 안내 ${perScene.map((s) => s.hints.length).join("/")}`,
      detail: bad.map((s) => `장면 ${s.scene}: 폰 밖 ${s.outside.join(" | ")}; 안내 ${s.hints.join(" | ")}`).join("; "), evidence: [file] };
  } finally { await b.close(); }
}

// 2.2 Every title fits in two lines on the narrowest phone we support (360 px).
export async function titleLines(ctx) {
  const b = await launch(VIEWPORTS.phoneS);
  try {
    await gotoSection(b.page, ctx.url);
    const titles = await b.page.evaluate(() => [...document.querySelectorAll(".ts-copy h3")].map((h) => {
      const cs = getComputedStyle(h); const lh = cs.lineHeight === "normal" ? parseFloat(cs.fontSize) * 1.3 : parseFloat(cs.lineHeight);
      return { text: h.textContent.replace(/\*$/, ""), lines: Math.round(h.getBoundingClientRect().height / lh), fontSize: parseFloat(cs.fontSize) };
    }));
    const bad = titles.filter((t) => t.lines > 2);
    return { pass: bad.length === 0 && titles.length === SCENE_COUNT, value: `줄 수 ${titles.map((t) => t.lines).join("/")}`, detail: bad.map((t) => `${t.lines}줄: ${t.text}`).join("; ") };
  } finally { await b.close(); }
}

// 2.3 No developer words, and only KS X 1001 Hangul (the landing's core font subset).
export async function plainWords(ctx) {
  const ksx = new Set();
  const decoder = new TextDecoder("euc-kr");
  for (let hi = 0xb0; hi <= 0xc8; hi++) for (let lo = 0xa1; lo <= 0xfe; lo++) ksx.add(decoder.decode(new Uint8Array([hi, lo])));
  const problems = [];
  for (const rel of COPY_FILES) {
    const text = await fs.readFile(path.join(ctx.landingRoot, rel), "utf8");
    const strings = [...text.matchAll(/(["'`])((?:\\.|(?!\1).)*)\1/g)].map((m) => m[2]).filter((s) => /[가-힣]/.test(s));
    const jsxText = [...text.matchAll(/>([^<>{}]*[가-힣][^<>{}]*)</g)].map((m) => m[1].trim());
    for (const s of [...strings, ...jsxText]) for (const word of JARGON) if (s.includes(word)) problems.push(`${rel}: "${word}" in "${s.slice(0, 40)}"`);
    const outside = [...new Set(text.match(/[가-힣]/g) ?? [])].filter((c) => !ksx.has(c));
    if (outside.length) problems.push(`${rel}: KS X 1001 밖 글자 ${outside.join("")}`);
  }
  return { pass: problems.length === 0, value: problems.length ? `${problems.length}건` : "금지어 0, 범위 밖 글자 0", detail: problems.join("; ") };
}
