import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

test("standalone test entrypoints build fixtures and CI can test an existing build", () => {
  const { scripts } = JSON.parse(readFileSync(new URL("../package.json", import.meta.url), "utf8"));
  assert.equal(scripts.pretest, "npm run build");
  assert.equal(scripts["pretest:sites"], "npm run build:sites");
  assert.equal(scripts["build:sites"], "vite build && node scripts/prepare-sites-build.mjs");
  assert.equal(scripts.build, "npm run build:sites && npm run build:recap");
  assert.equal(scripts["test:built"], scripts.test);
});
