import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import test from "node:test";
import browserslist from "browserslist";
import postcss from "postcss";
import { isFileLoadingAllowed, normalizePath, resolveConfig } from "vite";

const packageRoot = fileURLToPath(new URL("../", import.meta.url));
const fixture = new URL("./fixtures/dependency-security/private.map", import.meta.url);

test("locked toolchain packages stay above the September 2026 security patch floors", async () => {
  const lock = JSON.parse(await readFile(new URL("../package-lock.json", import.meta.url), "utf8"));
  const floors = { vite: "6.4.3", postcss: "8.5.23", browserslist: "4.28.7", nanoid: "3.3.18" };
  for (const [name, floor] of Object.entries(floors)) {
    const entries = Object.entries(lock.packages).filter(([path]) => path.endsWith(`node_modules/${name}`));
    assert.ok(entries.length, `${name} must be present`);
    for (const [path, { version }] of entries) {
      const actual = version.split(".").map(Number);
      const minimum = floor.split(".").map(Number);
      assert.equal(actual[0], minimum[0], `${path}: review security floors when upgrading major versions`);
      assert.ok(actual[1] > minimum[1] || (actual[1] === minimum[1] && actual[2] >= minimum[2]), `${path}@${version} is below ${floor}`);
    }
  }
});

test("PostCSS rejects parent traversal and absolute source maps without from", () => {
  const absolute = normalizePath(fileURLToPath(fixture));
  const from = fileURLToPath(new URL("./fixtures/dependency-security/styles/input.css", import.meta.url));
  for (const [annotation, options] of [["../private.map", { from }], [absolute, {}]]) {
    const root = postcss.parse(`a { color: red }\n/*# sourceMappingURL=${annotation} */`, options);
    assert.equal(root.source.input.map, undefined, `must not read ${annotation}`);
    assert.equal(root.first.first.value, "red");
  }
});

test("PostCSS preserves legitimate CSS and inline or same-directory source maps", async () => {
  const map = await readFile(fixture, "utf8");
  const annotation = `data:application/json;base64,${Buffer.from(map).toString("base64")}`;
  const root = postcss.parse(`a { color: red }\n/*# sourceMappingURL=${annotation} */`);
  assert.equal(root.first.first.value, "red");
  assert.equal(root.source.input.map.consumer().sourcesContent[0], "SECURITY_TEST_CANARY_ONLY");
  const local = postcss.parse("a { color: red }\n/*# sourceMappingURL=private.map */", {
    from: fileURLToPath(new URL("input.css", fixture)),
  });
  assert.equal(local.source.input.map.consumer().sourcesContent[0], "SECURITY_TEST_CANARY_ONLY");
});

test("Browserslist handles inherited-key statistics without crashing or modifying prototypes", () => {
  const expected = browserslist("last 1 chrome version", { stats: {} });
  for (const key of ["toString", "constructor", "__proto__"]) {
    const before = Object.getOwnPropertyDescriptors(Object.prototype);
    const stats = Object.fromEntries([[key, { onekey: 5 }]]);
    assert.deepEqual(browserslist("last 1 chrome version", { stats }), expected);
    assert.deepEqual(Object.getOwnPropertyDescriptors(Object.prototype), before);
  }
  assert.ok(expected.length > 0);
});

test("Nano ID invalid sizes terminate and ordinary IDs retain their length", () => {
  // Isolate upstream regressions so an infinite loop cannot hang the test runner.
  const script = `
    import assert from 'node:assert/strict';
    import { createRequire } from 'node:module';
    import * as insecureModule from 'nanoid/non-secure';
    import * as secureModule from 'nanoid';
    const require = createRequire(import.meta.url);
    for (const [insecure, secure] of [
      [insecureModule, secureModule],
      [require('nanoid/non-secure'), require('nanoid')],
    ]) {
      for (const generate of [insecure.nanoid, insecure.customAlphabet('abc', 6)]) {
        for (const size of [-1, 0]) {
          try { assert.equal(generate(size), ''); }
          catch (error) { if (!(error instanceof RangeError)) throw error; }
        }
        assert.equal(generate(6).length, 6);
      }
      assert.equal(secure.customAlphabet('abc', 0)(), '');
      assert.equal(secure.customRandom('abc', 0, (size) => Buffer.alloc(size))(), '');
      assert.equal(secure.nanoid(6).length, 6);
    }
  `;
  const result = spawnSync(process.execPath, ["--max-old-space-size=32", "--input-type=module", "-e", script], {
    cwd: packageRoot, timeout: 5000, maxBuffer: 64 * 1024, windowsHide: true,
  });
  assert.ifError(result.error);
  assert.equal(result.status, 0, result.stderr?.toString());
});

test("Vite denies protected files and Windows alternate stream/short-name paths", async () => {
  const root = normalizePath(fileURLToPath(new URL("./fixtures/dependency-security/", import.meta.url)));
  const config = await resolveConfig({
    configFile: false, envFile: false, root, logLevel: "silent",
    server: { fs: { strict: true, allow: [root] } },
  }, "serve");
  assert.equal(isFileLoadingAllowed(config, `${root}public.css`), true);
  for (const file of [".env", ".env.production", "credentials.pem"]) {
    assert.equal(isFileLoadingAllowed(config, root + file), false, file);
  }
  if (process.platform === "win32") {
    for (const file of [".env::$DATA", "credentials.pem::$DATA", "public.css:stream", "SECRET~1.TXT"]) {
      assert.equal(isFileLoadingAllowed(config, root + file), false, file);
    }
  }
});
