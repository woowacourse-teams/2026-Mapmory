import assert from "node:assert/strict";
import test from "node:test";
import { resolveConfig } from "vite";
import config from "../vite.config.mjs";

test("dev and preview share a loopback-only fixed port without automatic fallback", async () => {
  const resolved = await resolveConfig({ ...config, configFile: false, envFile: false }, "serve");
  for (const mode of [resolved.server, resolved.preview]) {
    assert.equal(mode.host, "127.0.0.1");
    assert.equal(mode.port, 4174);
    assert.equal(mode.strictPort, true);
  }
});
