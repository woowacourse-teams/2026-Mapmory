import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { shareVideo } from "../src/shareVideo.js";

const blob = new Blob(["video"], { type: "video/mp4" });
const createFile = (parts, name, options) => ({ parts, name, type: options.type });
const failure = (name) => Object.assign(new Error(name), { name });

test("native sharing preserves the file and does not also download", async () => {
  let shared;
  const outcome = await shareVideo(blob, {
    createFile,
    shareNavigator: { canShare: () => true, share: async (data) => { shared = data; } },
    download: () => assert.fail("unexpected download"),
  });
  assert.equal(outcome, "shared");
  assert.equal(shared.files[0].parts[0], blob);
  assert.equal(shared.files[0].name, "mapmory-2026-travel-map.mp4");
});

for (const name of ["NotAllowedError", "DataError", "TypeError"]) {
  test("share rejection keeps the rendered video downloadable: " + name, async () => {
    const downloads = [];
    const outcome = await shareVideo(blob, {
      createFile,
      shareNavigator: { canShare: () => true, share: async () => { throw failure(name); } },
      download: (...args) => downloads.push(args),
    });
    assert.equal(outcome, "downloaded");
    assert.deepEqual(downloads, [[blob, "mapmory-2026-travel-map.mp4"]]);
  });
}

test("user cancellation neither downloads nor reports successful sharing", async () => {
  assert.equal(await shareVideo(blob, {
    createFile,
    shareNavigator: { canShare: () => true, share: async () => { throw failure("AbortError"); } },
    download: () => assert.fail("cancelled sharing must not download"),
  }), "cancelled");
});

test("unsupported sharing and failed capability detection fall back to download", async () => {
  for (const shareNavigator of [null, {}, { share() {}, canShare: () => false },
    { share() {}, canShare: () => { throw failure("TypeError"); } }]) {
    let downloaded;
    const webm = new Blob(["video"], { type: "video/webm" });
    assert.equal(await shareVideo(webm, {
      createFile, shareNavigator, download: (...args) => { downloaded = args; },
    }), "downloaded");
    assert.deepEqual(downloaded, [webm, "mapmory-2026-travel-map.webm"]);
  }
});

test("download failures propagate to the retryable screen error", async () => {
  await assert.rejects(shareVideo(blob, {
    shareNavigator: null, download: () => { throw new Error("download failed"); },
  }), /download failed/);
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  const handler = app.slice(app.indexOf("const handleShare"), app.indexOf("const handleSaveImage"));
  assert.match(handler, /await shareVideo\(blob\)/);
  assert.match(handler, /outcome === "cancelled"/);
  assert.match(handler, /status: "error"/);
});
