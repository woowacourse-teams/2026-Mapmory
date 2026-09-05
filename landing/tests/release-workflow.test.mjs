import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import test from "node:test";

const workflow = readFileSync(new URL("../../.github/workflows/landing-release.yml", import.meta.url), "utf8");
const script = readFileSync(new URL("../scripts/deploy-ec2.sh", import.meta.url), "utf8").replace(/\r\n/g, "\n");
const bash = process.platform === "win32" ? "C:/Program Files/Git/bin/bash.exe" : "/bin/bash";
const buildspec = readFileSync(new URL("../buildspec.yml", import.meta.url), "utf8");
const resources = JSON.parse(readFileSync(new URL("../codedeploy/aws-resources.json", import.meta.url), "utf8"));
const hook = readFileSync(new URL("../codedeploy/activate.sh", import.meta.url), "utf8");
const packager = readFileSync(new URL("../scripts/package-codedeploy.sh", import.meta.url), "utf8");

test("release validation runs for every release PR with the ruleset's unique check name", () => {
  assert.match(workflow, /pull_request:\s+branches: \[landing-release\]/);
  assert.match(workflow, /push:\s+branches: \[landing-release\]/);
  assert.doesNotMatch(workflow, /paths(?:-ignore)?:/);
  assert.match(workflow, /name: Landing release validation/);
  assert.ok(workflow.indexOf("run: npm run build") < workflow.indexOf("run: npm test"));
});

test("CodePipeline automatically deploys only the tested landing artifact after the protected release merge", () => {
  const pipeline = resources.codePipeline;
  assert.deepEqual(pipeline.stages.map(stage => stage.name), ["Source", "Build", "Deploy"]);
  assert.ok(pipeline.stages.every(stage => stage.actions.length === 1));
  const [source, build, deploy] = pipeline.stages.map(stage => stage.actions[0]);
  assert.equal(source.actionTypeId.provider, "GitHub");
  assert.equal(source.actionTypeId.version, "1");
  assert.equal(source.configuration.Owner, "woowacourse-teams");
  assert.equal(source.configuration.Branch, "landing-release");
  assert.equal(source.configuration.Repo, "2026-Mapmory");
  assert.equal(source.configuration.PollForSourceChanges, "false");
  assert.equal(source.namespace, "SourceVariables");
  assert.equal(build.configuration.ProjectName, "mapmory-landing-build");
  assert.deepEqual(build.inputArtifacts, source.outputArtifacts);
  assert.deepEqual(build.outputArtifacts, [{name: "BuildArtifact"}]);
  assert.deepEqual(deploy.inputArtifacts, build.outputArtifacts);
  assert.deepEqual(JSON.parse(build.configuration.EnvironmentVariables), [{
    name: "SOURCE_COMMIT_ID", value: "#{SourceVariables.CommitId}", type: "PLAINTEXT",
  }]);
  assert.equal(deploy.actionTypeId.provider, "CodeDeploy");
  assert.equal(deploy.configuration.ApplicationName, "mapmory-landing");
  assert.equal(deploy.configuration.DeploymentGroupName, "mapmory-landing-production");
  assert.equal(pipeline.executionMode, "SUPERSEDED");
  assert.equal(pipeline.pipelineType, "V2");
  assert.doesNotMatch(workflow, /id-token:|configure-aws-credentials|secrets\.|\bssh\b.*-|\bscp\b/);
});

test("Recap operator guidance follows the shared automatic landing deployment policy", () => {
  const guidance = readFileSync(new URL("../travel-map-campaign/AGENTS.md", import.meta.url), "utf8");
  assert.match(guidance, /Follow \.\.\/DEPLOYMENT\.md/);
  assert.match(guidance, /V2\/SUPERSEDED/);
  assert.match(guidance, /no AWS manual approval action/);
  assert.match(guidance, /protected landing-release PR merge.*starts production deployment/);
  assert.match(guidance, /main PR merge does not/);
});

test("CodeBuild binds the tested source SHA to a static-only CodeDeploy bundle", () => {
  assert.match(buildspec, /CODEBUILD_RESOLVED_SOURCE_VERSION.*SOURCE_COMMIT_ID/);
  assert.ok(buildspec.indexOf("npm run build") < buildspec.indexOf("npm test"));
  assert.match(buildspec, /CODEBUILD_BUILD_SUCCEEDING/);
  assert.match(packager, /dist\/client/);
  assert.match(packager, /sha.*>.*client\/release\.txt/);
  assert.match(packager, /tar -czf.*appspec\.yml scripts client/);
  assert.match(hook, /served_sha.*==.*sha/);
  assert.doesNotMatch(buildspec, /gradlew|backend\/|npm run dev/);
});

test("shared-account resources use approved roles, storage, logs, tags and bounded builds", () => {
  assert.deepEqual(Object.fromEntries(resources.tags.map(({key, value}) => [key, value])), {
    Service: "techcourse", Role: "techcourse-etc", ProjectTeam: "Mapmory",
  });
  assert.equal(resources.codeDeploy.serviceRoleName, "codedeploy-project");
  assert.equal(resources.codeBuild.serviceRoleName, "codebuild-project");
  assert.equal(resources.codePipeline.serviceRoleName, "codepipeline-project");
  assert.equal(resources.codePipeline.artifactStore.location, "techcourse-project-2026-artifact");
  assert.equal(resources.codeBuild.logsConfig.cloudWatchLogs.groupName, "/aws/codebuild/project-2026");
  assert.equal(resources.codeBuild.concurrentBuildLimit, 1);
  assert.equal(resources.codeBuild.timeoutInMinutes, 15);
  assert.equal(resources.codeBuild.environment.privilegedMode, false);
  assert.equal(resources.codeDeploy.ec2TagSet.ec2TagSetList.length, 2);
  assert.equal(resources.codeDeploy.autoRollbackConfiguration.enabled, true);
});

test("CodeDeploy hook is confined to the landing and does not stop backend services", () => {
  assert.match(hook, /APPLICATION_NAME.*mapmory-landing/);
  assert.match(hook, /DEPLOYMENT_GROUP_NAME.*mapmory-landing-production/);
  assert.match(hook, /activate_release \/var\/www\/mapmory/);
  assert.match(hook, /mv -Tf/);
  assert.match(hook, /restore_previous/);
  assert.doesNotMatch(hook, /systemctl (?:restart|stop)|backend|\/opt\/mapmory|rm -rf/);
});

test("deployment shell syntax is valid", { skip: !existsSync(bash) }, () => {
  for (const input of [script, hook, packager]) {
    const result = spawnSync(bash, ["-n"], { input, encoding: "utf8" });
    assert.equal(result.status, 0, result.stderr || String(result.error));
  }
});

test("all local health checks explicitly bypass proxy environment variables", () => {
  const probes = hook.match(/curl --noproxy '\*'/g) || [];
  assert.equal(probes.length, 4);
  assert.equal((hook.match(/--resolve map-mory.com:443:127.0.0.1/g) || []).length, 4);
});

test("CodeDeploy rejects use from the backend group before filesystem access", () => {
  const result = spawnSync(bash, ["-s"], {
    input: hook, encoding: "utf8",
    env: {...process.env, APPLICATION_NAME: "mapmory-backend", DEPLOYMENT_GROUP_NAME: "mapmory-prod"},
  });
  assert.equal(result.status, 2, result.stderr);
  assert.match(result.stderr, /only in the landing/);
});

for (const scenario of ["success", "proxy-env", "reload-failure", "identity-failure", "http-failure", "nginx-failure", "bad-marker", "bad-id", "missing-previous", "outside-previous", "duplicate", "symlink-bundle", "locked", "missing-recap", "bad-recap-marker", "recap-http-failure", "recap-identity-failure", "recap-shell-failure"]) {
  test(`CodeDeploy activation fixture: ${scenario}`, { skip: process.platform === "win32" ? "Linux filesystem/flock fixture runs in CI and CodeBuild" : false }, () => {
    const fixture = fileURLToPath(new URL("./fixtures/codedeploy-activation.sh", import.meta.url));
    const result = spawnSync(bash, [fixture, scenario], {encoding: "utf8", timeout: 15000});
    assert.equal(result.status, 0, result.stdout + result.stderr);
  });
}

test("deployment rejects invalid paths before running remote mutations", { skip: !existsSync(bash) }, () => {
  const sha = "a".repeat(40);
  for (const release of [sha + "-42", sha + "-12345-2"]) {
    const result = spawnSync(bash, ["-s", "--", release, "/tmp/not-a-landing-archive.tar.gz"], {
      input: script, encoding: "utf8",
    });
    assert.equal(result.status, 2, result.stderr || String(result.error));
    assert.match(result.stderr, /Invalid archive path/);
  }
  for (const release of ["../outside", sha + "-12;echo unsafe", sha + "-12-3-4"]) {
    const result = spawnSync(bash, ["-s", "--", release, "/tmp/mapmory-landing-12345-2.tar.gz"], {
      input: script, encoding: "utf8",
    });
    assert.equal(result.status, 2, result.stderr || String(result.error));
    assert.match(result.stderr, /Invalid release id/);
  }
});
