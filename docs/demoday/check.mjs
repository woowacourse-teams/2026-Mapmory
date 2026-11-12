import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { Script } from 'node:vm';

const page = new URL('./index.html', import.meta.url);
const html = readFileSync(page, 'utf8');
const scripts = [...html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)];
const markup = html.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '');
const keyframes = new Set([...markup.matchAll(/@keyframes\s+([\w-]+)/g)].map((m) => m[1]));
for (const [, name] of markup.matchAll(/\banimation:\s*([\w-]+)/g)) {
  assert.ok(name === 'none' || keyframes.has(name), `Missing keyframes for animation ${name}`);
}
const ids = [...markup.matchAll(/\bid="([^"]+)"/g)].map((m) => m[1]);
assert.equal(ids.length, new Set(ids).size, 'HTML IDs must be unique');
for (const [, id] of markup.matchAll(/\bhref="#([^"?]+)"/g)) {
  assert.ok(ids.includes(id), `Missing anchor target #${id}`);
}
for (const [, path] of markup.matchAll(/\b(?:src|poster|href)="(assets\/[^"?#]+)"/g)) {
  assert.ok(existsSync(new URL(path, page)), `Missing asset ${path}`);
}

const schemas = scripts.filter(([_, attrs]) => /type="application\/ld\+json"/.test(attrs));
assert.ok(schemas.length, 'Structured data must be present');
const nodes = schemas.flatMap(([_, __, code]) => {
  const data = JSON.parse(code);
  assert.equal(data['@context'], 'https://schema.org');
  return data['@graph'] ?? [data];
});
const app = nodes.find((node) => node['@type'] === 'MobileApplication');
assert.ok(app, 'Describe the mobile app, not an unrelated page type');
assert.equal(app.applicationCategory, 'TravelApplication');
assert.deepEqual(new Set(app.operatingSystem), new Set(['Android', 'iOS']));
for (const url of app.installUrl) {
  assert.ok(markup.includes(`href="${url}"`), `Schema install URL must be visible: ${url}`);
}
assert.equal(app.installUrl.length, 2, 'Both stores must be linked');
assert.ok(!('aggregateRating' in app), 'Do not invent an app rating');
assert.ok(!('downloadCount' in app), 'Do not invent a download count');
assert.ok(!/올해 여행만|친구와 간 여행만/.test(markup), 'Do not advertise unsupported video filters');

const executable = scripts.filter(([_, attrs]) => !/\bsrc=|application\/ld\+json/.test(attrs));
for (const [index, [_, __, code]] of executable.entries()) {
  new Script(code, { filename: `index.html:inline-${index + 1}` });
}
const code = executable.map(([, , source]) => source).join('\n');
const qrSection = code.match(/\/\* ---------- QR codes ---------- \*\/([\s\S]*?)\/\* ---------- booth mode/)?.[1];
assert.ok(qrSection, 'Store QR generation must remain available');
const qrElements = new Map(ids.map((id) => [id, { id, innerHTML: '' }]));
const qrDestinations = new Map();
const stores = {
  ios: app.installUrl.find((url) => new URL(url).hostname === 'apps.apple.com'),
  android: app.installUrl.find((url) => new URL(url).hostname === 'play.google.com'),
};
for (const [platform, url] of Object.entries(stores)) {
  const id = `qr-hero-${platform}`;
  const link = [...markup.matchAll(/<a\b([^>]*)>([\s\S]*?)<\/a>/g)].find(([, , body]) => body.includes(`id="${id}"`));
  assert.ok(link, `Hero must show a linked ${platform} QR without switching`);
  assert.ok(link[1].includes(`href="${url}"`), `${platform} QR must link to its store`);
}
class QRCode {
  static CorrectLevel = { M: 0 };
  constructor(element, { text }) { qrDestinations.set(element.id, text); }
}
new Script(qrSection).runInNewContext({
  $: (id) => qrElements.get(id), QRCode, STORE: stores,
});
assert.equal(qrDestinations.get('qr-hero-ios'), stores.ios);
assert.equal(qrDestinations.get('qr-hero-android'), stores.android);
const mapSizes = [];
let domesticRegions;
for (const name of ['TOPO', 'KR_TOPO']) {
  const json = code.match(new RegExp(`const ${name} = (\\{[^\\n]+\\});`))?.[1];
  assert.ok(json, `Missing ${name} geometry data`);
  const topology = JSON.parse(json);
  const geometries = Object.values(topology.objects)[0].geometries;
  assert.ok(geometries.length > 0);
  mapSizes.push(geometries.length);
  if (name === 'KR_TOPO') domesticRegions = geometries;
}
const domesticNames = code.match(/  const PROV = [\s\S]+?(?=  const findKr)/)?.[0];
const buildChoices = code.match(/  function buildChips\(\) \{([\s\S]+?)\n  \}\n  if \(krSvg/)?.[1];
assert.ok(domesticNames && buildChoices, 'Region selection must remain available');
const regionChoice = { options: [], replaceChildren(...options) { this.options = options; } };
new Script(`${domesticNames}\nfunction buildChips() {${buildChoices}\n}\nbuildChips();`).runInNewContext({
  KR: domesticRegions, mode: 'kr', regionChoice, KR_PICKS: [], chipsEl: { innerHTML: '' },
  $: () => ({}), Option: class { constructor(text, value) { this.text = text; this.value = value; } },
});
assert.equal(regionChoice.options.length, mapSizes[1]);
assert.equal(new Set(regionChoice.options.map((option) => option.text)).size, mapSizes[1], 'Domestic region labels must be distinguishable');
for (const [value, text] of [['32400', '강원 고성군'], ['38340', '경남 고성군']]) {
  assert.equal(regionChoice.options.find((option) => option.value === value)?.text, text);
}
const render = code.match(/function render\(focus\) \{([\s\S]*?)\n  const BADGES/)?.[1];
assert.ok(render, 'Map rendering must remain available');
assert.ok(!/\/\s*(?:195|230)\b|230곳/.test(render), 'Demo counts must use demo geometry totals');
for (const [index, mode] of ['world', 'kr'].entries()) {
  for (const count of [0, mapSizes[index]]) {
    const elements = new Map();
    const $ = (id) => {
      if (!elements.has(id)) elements.set(id, { style: {}, textContent: '', value: '', setAttribute() {} });
      return elements.get(id);
    };
    new Script(`function render(focus) {${render}\nrender(null);`).runInNewContext({
      $, mode, picked: Array(mode === 'world' ? count : 0).fill('JP'),
      pickedKr: Array(mode === 'kr' ? count : 0).fill('39010'),
      COUNTRIES: Array(mapSizes[0]), KR: Array(mapSizes[1]),
      tryGlobe: null, krSvg: null, krPath: null,
      chipsEl: { querySelectorAll: () => [] },
      featOf: () => ({ properties: { n: '일본' } }), krName: () => '제주시',
      renderBadges() {},
    });
    assert.equal(Number($('try-count').textContent), count, `${mode}: selected count`);
    assert.equal(parseFloat($('try-meter').style.width), count ? 100 : 0, `${mode}: empty/full map progress`);
  }
}
// Exercise the real timeline and global motion handlers without browser APIs.
const timeline = code.match(/\/\* ---------- mobile: timeline switch ---------- \*\/([\s\S]*?)\/\* ---------- section rail/)?.[1];
const motion = code.match(/function updateMotion\(\) \{([\s\S]*?)\n  motionButton\.addEventListener/)?.[1];
assert.ok(timeline && motion, 'Timeline and global motion handlers must remain available');
const box = { dataset: { show: '0' }, querySelectorAll: () => videos };
const videos = [0, 1].map((index) => ({
  dataset: { visible: String(index === 0) }, paused: true,
  pause() { this.paused = true; },
  play() { this.paused = false; return Promise.resolve(); },
  getBoundingClientRect() {
    return box.dataset.show === String(index)
      ? { height: 400, top: 100, bottom: 500 }
      : { height: 0, top: 0, bottom: 0 };
  },
}));
const buttons = [0, 1].map((index) => ({
  dataset: { tl: String(index) },
  setAttribute() {},
  addEventListener(event, handler) { if (event === 'click') this.click = handler; },
}));
const motionContext = {
  $: () => box, innerHeight: 800, motionPaused: false,
  motionButton: { setAttribute() {}, textContent: '' },
  document: {
    hidden: false, documentElement: { classList: { toggle() {} } },
    querySelectorAll: (selector) => selector === '.tl-switch button' ? buttons : videos,
  },
};
new Script(`function updateMotion() {${motion}\n${timeline}`).runInNewContext(motionContext);
const updateMotion = () => new Script('updateMotion()').runInNewContext(motionContext);
const pauseAndResume = () => {
  motionContext.motionPaused = true; updateMotion();
  assert.ok(videos.every((video) => video.paused), 'Global pause must stop both videos');
  motionContext.motionPaused = false; updateMotion();
};
buttons[0].click();
pauseAndResume();
assert.equal(videos[0].paused, false, 'Reselecting the active timeline must preserve resumability');
assert.equal(videos[1].paused, true, 'Hidden timeline must remain paused');
buttons[1].click();
pauseAndResume();
assert.equal(videos[0].paused, true, 'Previous timeline must stay paused after switching');
assert.equal(videos[1].paused, false, 'Newly selected timeline must resume after global pause');
motionContext.motionPaused = true; updateMotion();
buttons[0].click();
assert.ok(videos.every((video) => video.paused), 'Switching timelines must respect global pause');
motionContext.motionPaused = false; updateMotion();
assert.equal(videos[0].paused, false, 'A timeline selected while paused must resume');
motionContext.document.hidden = true; updateMotion();
assert.ok(videos.every((video) => video.paused), 'Background pages must not play timeline videos');

const steps = [...markup.matchAll(/<article\b[^>]*class="step[^>]*>([\s\S]*?)<\/article>/g)];
assert.equal(steps.length, 3, 'Retain all three recording steps');
assert.ok(steps.every(([, body]) => /<button\b[^>]*type="button"/.test(body)), 'Every recording step needs keyboard activation');
assert.ok(/<button\b[^>]*(?:id|class)="[^"]*(?:motion|pause)[^"]*"/.test(markup), 'Provide a visible animation pause control');
console.log(`PASS: anchors, assets, schema, store QR pairs, ${executable.length} scripts, map totals, recording controls and timeline pause/resume (${fileURLToPath(page)})`);
