import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("both landing checkouts disable credential persistence without broadening permissions", async () => {
  const workflow = await readFile(new URL("../../../.github/workflows/landing-cicd.yml", import.meta.url), "utf8");
  const checkouts = workflow.split(/\r?\n\s+- name:/).filter((step) => /uses: actions\/checkout@/.test(step));
  assert.equal(checkouts.length, 2);
  for (const step of checkouts) {
    assert.match(step, /uses: actions\/checkout@v7\r?\n\s+with:\r?\n\s+persist-credentials: false/);
    assert.doesNotMatch(step, /token:|ssh-key:|persist-credentials: true/);
  }
  assert.match(workflow, /permissions:\r?\n\s+contents: read/);
  assert.doesNotMatch(workflow, /pull_request_target:|contents: write/);
});

test("campaign installs no longer suppress the default audit report", async () => {
  const config = await readFile(new URL("../.npmrc", import.meta.url), "utf8");
  assert.doesNotMatch(config, /^\s*audit\s*=\s*false\s*$/m);
  assert.match(config, /^fund=false$/m);
});
