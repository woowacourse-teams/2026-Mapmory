// 5.1–5.7: tests and build, Lighthouse, Core Web Vitals (lab), page weight, axe, console/404, analytics events.
import { execFile } from "node:child_process";
import fs from "node:fs/promises";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import { promisify } from "node:util";
import AxeBuilder from "@axe-core/playwright";
import { chromium } from "playwright";
import { VIEWPORTS, beatCount, flickTo, gotoSection, launch, scrollToBeat, sectionRange } from "../lib/browser.mjs";
import { runAllScenes } from "../lib/interact.mjs";

const run = promisify(execFile);
const KB = (n) => `${Math.round(n / 1024).toLocaleString("en-US")}KB`;

// 5.1 `npm test` all green and `npm run build` succeeds, in this checkout.
export async function testsAndBuild(ctx) {
  const log = [];
  let pass = true, summary = [];
  for (const [name, args] of [["npm test", ["test"]], ["npm run build", ["run", "build"]]]) {
    try {
      const r = await run("npm", args, { cwd: ctx.landingRoot, maxBuffer: 64e6, timeout: 600000 });
      log.push(`## ${name}\n${r.stdout}\n${r.stderr}`);
      const m = r.stdout.match(/# pass (\d+)[\s\S]*?# fail (\d+)/);
      summary.push(m ? `테스트 통과 ${m[1]}, 실패 ${m[2]}` : `${name} 성공`);
      if (m && m[2] !== "0") pass = false;
    } catch (e) {
      pass = false;
      log.push(`## ${name} FAILED\n${e.stdout ?? ""}\n${e.stderr ?? e.message}`);
      summary.push(`${name} 실패`);
    }
  }
  const file = ctx.evidence("build.log");
  await fs.writeFile(file, log.join("\n\n"));
  return { pass, value: summary.join("; "), evidence: [file] };
}

// One Lighthouse run (mobile, simulated throttling, Lighthouse's default device) shared by 5.2 and 5.3.
let lighthouseRun = null;
async function lighthouse(ctx) {
  if (lighthouseRun?.url === ctx.url) return lighthouseRun;
  const require = createRequire(import.meta.url);
  const cli = path.join(path.dirname(require.resolve("lighthouse/package.json")), "cli", "index.js");
  const file = ctx.evidence("lighthouse.json");
  await run(process.execPath, [cli, ctx.url, "--output=json", `--output-path=${file}`, "--quiet", "--form-factor=mobile",
    "--only-categories=performance,accessibility,best-practices", "--chrome-flags=--headless=new --no-sandbox --disable-gpu"],
  { env: { ...process.env, CHROME_PATH: process.env.GATES_CHROME || chromium.executablePath() }, maxBuffer: 64e6, timeout: 420000 });
  const lhr = JSON.parse(await fs.readFile(file, "utf8"));
  lighthouseRun = { url: ctx.url, lhr, file };
  return lighthouseRun;
}

// The same Lighthouse run on the page without this section (gates/baseline-lighthouse.sh builds the branch's
// merge-base and writes evidence/stage-5/baseline-lighthouse.json + baseline.json): it tells the section's share
// of a page-level number from what the page already had. Optional; the criteria stay page-level either way.
async function baseline(ctx) {
  try {
    const lhr = JSON.parse(await fs.readFile(ctx.evidence("baseline-lighthouse.json"), "utf8"));
    const meta = JSON.parse(await fs.readFile(ctx.evidence("baseline.json"), "utf8").catch(() => "{}"));
    return { lhr, label: `기준선(섹션 없는 ${meta.ref ?? "기준 빌드"})`, file: ctx.evidence("baseline-lighthouse.json") };
  } catch { return null; }
}

// 5.2 Lighthouse categories at 90 or more (Lighthouse: 90–100 is "good").
export async function lighthouseScores(ctx) {
  const { lhr, file } = await lighthouse(ctx);
  const scores = (r) => { const score = (k) => Math.round((r.categories[k]?.score ?? 0) * 100); return { performance: score("performance"), accessibility: score("accessibility"), bestPractices: score("best-practices") }; };
  const s = scores(lhr);
  const failing = Object.entries(lhr.audits).filter(([, a]) => a.score !== null && a.score < 0.9 && a.scoreDisplayMode !== "informative").map(([id, a]) => `${id} ${Math.round((a.score ?? 0) * 100)}`).slice(0, 10);
  const base = await baseline(ctx);
  const b = base && scores(base.lhr);
  const detail = [failing.join("; "), b && `${base.label}: 성능 ${b.performance}, 접근성 ${b.accessibility}, 권장 사항 ${b.bestPractices}`].filter(Boolean).join(" — ");
  return { pass: Object.values(s).every((v) => v >= 90), value: `성능 ${s.performance}, 접근성 ${s.accessibility}, 권장 사항 ${s.bestPractices}`, detail, evidence: [file, base?.file].filter(Boolean) };
}

// 5.3 Lab Core Web Vitals from the same run: LCP ≤ 2.5 s, CLS ≤ 0.1, and TBT ≤ 200 ms standing in for INP.
export async function webVitals(ctx) {
  const { lhr, file } = await lighthouse(ctx);
  const vitals = (r) => { const v = (id) => r.audits[id]?.numericValue ?? NaN; return { lcp: v("largest-contentful-paint"), cls: v("cumulative-layout-shift"), tbt: v("total-blocking-time") }; };
  const fmt = ({ lcp, cls, tbt }) => `LCP ${(lcp / 1000).toFixed(2)}s, CLS ${cls.toFixed(3)}, TBT ${Math.round(tbt)}ms`;
  const { lcp, cls, tbt } = vitals(lhr);
  const pass = lcp <= 2500 && cls <= 0.1 && tbt <= 200;
  const lcpEl = lhr.audits["lcp-breakdown-insight"]?.details?.items?.find((i) => i.type === "node")?.nodeLabel;
  const base = await baseline(ctx);
  const detail = [pass ? "" : [lcp > 2500 && "LCP 초과", cls > 0.1 && "CLS 초과", tbt > 200 && "TBT 초과"].filter(Boolean).join(", "), lcpEl && `LCP 요소 "${lcpEl.slice(0, 30)}"`, base && `${base.label}: ${fmt(vitals(base.lhr))}`].filter(Boolean).join(" — ");
  return { pass, value: fmt({ lcp, cls, tbt }), detail, evidence: [file, base?.file].filter(Boolean) };
}

// 5.4 Bytes over the wire on a phone, from the top of the page down through the last scene at a reading pace
// (1000 px/s), under the HTTP Archive 2025 mobile medians: 2,164 KB in all, 646 KB of JavaScript.
export async function pageWeight(ctx) {
  const b = await launch(VIEWPORTS.phone);
  try {
    const cdp = await b.context.newCDPSession(b.page);
    await cdp.send("Network.enable");
    const meta = new Map();
    const byType = {};
    const items = [];
    cdp.on("Network.responseReceived", (e) => meta.set(e.requestId, { type: e.type, url: e.response.url }));
    // the share video is fetched as a blob (so the phone plays it without a range request per seek): count it as media
    const kind = (m) => (/\.(webm|mp4)(\?|$)/.test(m?.url ?? "") ? "Media" : m?.type ?? "Other");
    cdp.on("Network.loadingFinished", (e) => { const m = meta.get(e.requestId); const type = kind(m); byType[type] = (byType[type] ?? 0) + e.encodedDataLength; items.push({ type, url: m?.url ?? "?", bytes: e.encodedDataLength }); });
    await b.page.goto(ctx.url, { waitUntil: "load" });
    await b.page.waitForTimeout(1500);
    await b.page.locator("#how").waitFor();
    const { end } = await sectionRange(b.page);
    await flickTo(b.page, end, 1000);
    await b.page.waitForSelector(".ts.is-live", { timeout: 20000 }).catch(() => {});
    const n = await beatCount(b.page);
    for (let i = 0; i < n; i++) await scrollToBeat(b.page, i, 0.5, 1200);
    await b.page.waitForTimeout(2500);
    const total = Object.values(byType).reduce((s, v) => s + v, 0);
    const js = byType.Script ?? 0;
    // the section's own files: its engine chunks (techStoryEngine + the d3 projection it pulls in) and public/assets/tech
    const own = items.filter((i) => /techStoryEngine|azimuthal|feature-|\/assets\/tech\//.test(i.url));
    const ownBytes = own.reduce((s, i) => s + i.bytes, 0), ownJs = own.filter((i) => i.type === "Script").reduce((s, i) => s + i.bytes, 0);
    const file = ctx.evidence("page-weight.json");
    await fs.writeFile(file, JSON.stringify({ total, byType, section: { bytes: ownBytes, js: ownJs, files: own.map((i) => i.url.split("/").pop()) }, items: items.sort((a, c) => c.bytes - a.bytes) }, null, 2));
    return { pass: total < 2164 * 1024 && js < 646 * 1024, value: `총 ${KB(total)} (JS ${KB(js)}, 이미지 ${KB(byType.Image ?? 0)}, 영상 ${KB(byType.Media ?? 0)}, 글꼴 ${KB(byType.Font ?? 0)})`, detail: `${items.slice(0, 5).map((i) => `${KB(i.bytes)} ${i.url.split("/").pop()}`).join("; ")} — 이 섹션의 몫 ${KB(ownBytes)} (JS ${KB(ownJs)})`, evidence: [file] };
  } finally { await b.close(); }
}

// 5.5 axe (WCAG 2.0 A/AA + 2.2 AA rules) on the section, light and dark, at every scene: no serious or critical hit.
export async function axe(ctx) {
  const hits = [];
  let minor = 0;
  for (const [theme, dark] of [["light", false], ["dark", true]]) {
    const b = await launch({ ...VIEWPORTS.phone, dark });
    try {
      await gotoSection(b.page, ctx.url, { theme });
      const n = await beatCount(b.page);
      for (let i = 0; i < n; i++) {
        await scrollToBeat(b.page, i, 0.5, 900);
        const res = await new AxeBuilder({ page: b.page }).include("#how").withTags(["wcag2a", "wcag2aa", "wcag22aa"]).analyze();
        for (const v of res.violations) {
          if (!["serious", "critical"].includes(v.impact)) { minor += v.nodes.length; continue; }
          for (const node of v.nodes) hits.push(`${theme} 장면 ${i + 1}: ${v.id} (${v.impact}) ${node.html.slice(0, 70)}`);
        }
      }
    } finally { await b.close(); }
  }
  const file = ctx.evidence("axe.json");
  await fs.writeFile(file, JSON.stringify({ hits: [...new Set(hits)], minor }, null, 2));
  const unique = [...new Set(hits)];
  return { pass: unique.length === 0, value: unique.length ? `serious·critical ${unique.length}곳` : `serious·critical 0 (가벼운 지적 ${minor}곳)`, detail: unique.slice(0, 8).join("; "), evidence: [file] };
}

// 5.6 No console error or page error, and no 4xx/5xx for anything the page asks its own origin for, while a
// visitor scrolls the section and tries every scene. Third-party hosts the QA network blocks are reported, not counted.
export async function consoleAnd404(ctx) {
  const b = await launch(VIEWPORTS.phone);
  try {
    await gotoSection(b.page, ctx.url);
    const results = await runAllScenes(b.page, { touch: true });
    await b.page.waitForTimeout(1000);
    const origin = new URL(ctx.url).origin;
    const own = b.failed.filter((f) => f.includes(origin) && !/favicon/.test(f));
    const thirdParty = b.failed.filter((f) => !f.includes(origin));
    const errors = b.errors.filter((e) => !/favicon/.test(e) && !thirdParty.some((t) => e.includes(t.split(" ")[1])));
    const file = ctx.evidence("console.json");
    await fs.writeFile(file, JSON.stringify({ errors: b.errors, failed: b.failed, scenes: results }, null, 2));
    return { pass: errors.length === 0 && own.length === 0, value: `콘솔 오류 ${errors.length}, 같은 출처 4xx/5xx ${own.length}${thirdParty.length ? `, 외부 호스트 실패 ${thirdParty.length} (집계 제외)` : ""}`, detail: [...errors, ...own].slice(0, 6).join("; "), evidence: [file] };
  } finally { await b.close(); }
}

// 5.7 With a fake GA id and all network stubbed, the data layer gets one tech_story_view, tech_story_scene 1–6 and
// one tech_story_interact per scene with its own interaction type, after a visitor reads and tries each scene.
export async function analytics(ctx) {
  const { build } = await import("vite");
  const out = await fs.mkdtemp(path.join(os.tmpdir(), "mapmory-gates-analytics-"));
  const env = { VITE_GA_MEASUREMENT_ID: "G-TEST123", VITE_POSTHOG_KEY: "", VITE_POSTHOG_HOST: "" };
  const saved = Object.fromEntries(Object.keys(env).map((k) => [k, process.env[k]]));
  Object.assign(process.env, env);
  try { await build({ root: ctx.landingRoot, configFile: path.join(ctx.landingRoot, "vite.config.mjs"), logLevel: "warn", build: { outDir: out, emptyOutDir: true } }); }
  finally { for (const [k, v] of Object.entries(saved)) { if (v === undefined) delete process.env[k]; else process.env[k] = v; } }
  const mime = { ".html": "text/html", ".js": "application/javascript", ".css": "text/css", ".json": "application/json", ".svg": "image/svg+xml", ".jpg": "image/jpeg", ".png": "image/png", ".webp": "image/webp", ".avif": "image/avif", ".woff2": "font/woff2", ".mp4": "video/mp4", ".webm": "video/webm", ".ico": "image/x-icon", ".txt": "text/plain", ".webmanifest": "application/manifest+json" };
  const b = await launch(VIEWPORTS.phone);
  try {
    await b.context.route("**/*", async (route) => {
      const url = new URL(route.request().url());
      if (url.hostname === "www.googletagmanager.com") return route.fulfill({ contentType: "application/javascript", body: "/* GA stubbed for the gate */" });
      if (url.hostname !== "map-mory.com") return route.abort();
      const file = path.resolve(out, decodeURIComponent(url.pathname.slice(1)) || "index.html");
      if (!file.startsWith(out + path.sep)) return route.abort();
      try { return route.fulfill({ contentType: mime[path.extname(file)] ?? "application/octet-stream", body: await fs.readFile(file) }); }
      catch { return route.fulfill({ status: 404, body: "missing" }); }
    });
    await gotoSection(b.page, "https://map-mory.com/");
    const n = await beatCount(b.page);
    for (let i = 0; i < n; i++) await scrollToBeat(b.page, i, 0.5, 1300);
    const scenes = await runAllScenes(b.page, { touch: true });
    await b.page.waitForTimeout(1200);
    const events = await b.page.evaluate(() => (window.dataLayer ?? []).map((a) => [...a]).filter(([cmd, name]) => cmd === "event" && String(name).startsWith("tech_story")).map(([, name, p]) => ({ name, scene: p?.scene_index, type: p?.interaction_type })));
    const views = events.filter((e) => e.name === "tech_story_view").length;
    const reached = [...new Set(events.filter((e) => e.name === "tech_story_scene").map((e) => e.scene))].sort();
    const interacts = events.filter((e) => e.name === "tech_story_interact").map((e) => `${e.scene}:${e.type}`);
    const expected = ["1:flip", "2:gather", "3:save", "4:map_toggle", "5:globe_drag", "6:year_pick"];
    const missing = expected.filter((x) => !interacts.includes(x));
    const file = ctx.evidence("analytics-events.json");
    await fs.writeFile(file, JSON.stringify({ events, scenes }, null, 2));
    const pass = views === 1 && reached.length === 6 && missing.length === 0 && interacts.length === 6;
    return { pass, value: `view ${views}, scene ${reached.join(",")}, interact ${interacts.join(" ")}`, detail: missing.length ? `빠진 interact: ${missing.join(", ")}` : (interacts.length > 6 ? "interact가 장면당 1개를 넘음" : ""), evidence: [file] };
  } finally { await b.close(); await fs.rm(out, { recursive: true, force: true }); }
}
