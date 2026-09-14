import assert from "node:assert/strict";
import { cpSync, existsSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const bash = process.platform === "win32" ? "C:/Program Files/Git/bin/bash.exe" : "/bin/bash";
const landing = fileURLToPath(new URL("../", import.meta.url));
const sha = "a".repeat(40);
const put = (root, name, content) => {
  const file = path.join(root, name);
  mkdirSync(path.dirname(file), { recursive: true });
  writeFileSync(file, content);
};

test("main CI validates the real combined static bundle without deploying", () => {
  const config = readFileSync(path.join(landing, "../.github/workflows/landing-cicd.yml"), "utf8");
  assert.match(config, /npm run test:built/);
  assert.match(config, /bash ..\/scripts\/package-codedeploy.sh/);
  assert.doesNotMatch(config, /configure-aws-credentials|\bscp\b|\bssh\b|create-deployment/);
});

test("CI and CodeBuild install, build and test the campaign before packaging", () => {
  for (const name of ["buildspec.yml", "../.github/workflows/landing-release.yml"]) {
    const config = readFileSync(path.join(landing, name), "utf8");
    for (const command of ["ci", "run build", "run test:built"]) {
      assert.ok(config.includes("npm --prefix travel-map-campaign " + command), name + ": " + command);
    }
    assert.ok(config.indexOf("npm --prefix travel-map-campaign run test:built") < config.indexOf("bash scripts/package-codedeploy.sh"), name);
  }
});

for (const scenario of ["success", "missing-recap", "wrong-base", "missing-landing", "existing-recap", "bad-sha"]) {
  test("static-only recap CodeDeploy packaging: " + scenario, { skip: !existsSync(bash) }, (t) => {
    const root = mkdtempSync(path.join(os.tmpdir(), "mapmory-recap-package-"));
    t.after(() => {
      assert.equal(path.dirname(path.resolve(root)), path.resolve(os.tmpdir()));
      assert.ok(path.basename(root).startsWith("mapmory-recap-package-"));
      rmSync(root, { recursive: true, force: true });
    });
    mkdirSync(path.join(root, "scripts"), { recursive: true });
    cpSync(path.join(landing, "scripts/package-codedeploy.sh"), path.join(root, "scripts/package-codedeploy.sh"));
    cpSync(path.join(landing, "codedeploy"), path.join(root, "codedeploy"), { recursive: true });
    if (scenario !== "missing-landing") put(root, "dist/client/index.html", "landing-home");
    put(root, "dist/client/assets/shared.js", "landing-asset");
    if (scenario === "existing-recap") put(root, "dist/client/recap/index.html", "old-recap");
    if (scenario !== "missing-recap") {
      put(root, "travel-map-campaign/dist/recap/index.html",
        '<script src="' + (scenario === "wrong-base" ? "/" : "/recap/") + 'assets/app.js"></script>');
      put(root, "travel-map-campaign/dist/recap/assets/app.js", "campaign-asset");
    }
    put(root, "travel-map-campaign/.env", "PRIVATE_FAKE_VALUE=must-not-ship");
    put(root, "travel-map-campaign/dist/server/index.js", "worker-must-not-ship");
    const output = path.join(root, "bundle.tar.gz");
    const result = spawnSync(bash, [path.join(root, "scripts/package-codedeploy.sh"),
      scenario === "bad-sha" ? "../not-a-sha" : sha, "bundle.tar.gz"], { cwd: root, encoding: "utf8", timeout: 15000 });
    if (scenario !== "success") {
      assert.notEqual(result.status, 0, result.stdout + result.stderr);
      assert.equal(existsSync(output), false);
      return;
    }
    assert.equal(result.status, 0, result.stdout + result.stderr);
    const tar = (...args) => {
      const r = spawnSync("tar", args, { encoding: "utf8", timeout: 10000 });
      assert.equal(r.status, 0, r.stderr);
      return r.stdout;
    };
    const files = tar("-tzf", output).split(/\r?\n/).filter(Boolean);
    assert.ok(files.includes("client/index.html"));
    assert.ok(files.includes("client/recap/index.html"));
    assert.ok(files.includes("client/recap/assets/app.js"));
    assert.ok(files.every((file) => file === "appspec.yml" || file.startsWith("scripts/") || file.startsWith("client/")));
    assert.ok(files.every((file) => !/\.env|node_modules|server\/|backend\//.test(file)));
    assert.equal(tar("-xOzf", output, "client/index.html"), "landing-home");
    assert.equal(tar("-xOzf", output, "client/assets/shared.js"), "landing-asset");
    assert.equal(tar("-xOzf", output, "client/release.txt").trim(), sha);
    assert.equal(tar("-xOzf", output, "client/recap/release.txt").trim(), sha);
    assert.equal(readFileSync(path.join(root, "dist/client/index.html"), "utf8"), "landing-home");
    assert.equal(existsSync(path.join(root, "dist/client/recap")), false);
  });
}
