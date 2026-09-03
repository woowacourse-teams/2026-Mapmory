import assert from "node:assert/strict";
import test from "node:test";
import { createCachedAsyncLoader } from "../src/cachedAsyncLoader.js";

test("shares concurrent requests and preserves the successful cached result", async () => {
  let calls = 0;
  const countries = [{ id: "840" }];
  const load = createCachedAsyncLoader(async () => {
    calls += 1;
    return countries;
  });
  const first = load();
  const second = load();
  assert.equal(first, second);
  assert.equal(await first, countries);
  assert.equal(await load(), countries);
  assert.equal(calls, 1);
});

test("clears a failed request so the next consumer can retry", async () => {
  let calls = 0;
  const load = createCachedAsyncLoader(async () => {
    calls += 1;
    if (calls === 1) throw new Error("temporary chunk failure");
    return ["countries"];
  });
  await assert.rejects(load(), /temporary chunk failure/);
  assert.deepEqual(await load(), ["countries"]);
  assert.equal(calls, 2);
});

test("also clears synchronous loader failures", async () => {
  let calls = 0;
  const load = createCachedAsyncLoader(() => {
    calls += 1;
    if (calls === 1) throw new Error("failed before resolving");
    return [];
  });
  await assert.rejects(load(), /failed before resolving/);
  assert.deepEqual(await load(), []);
  assert.equal(calls, 2);
});
