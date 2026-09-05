# Landing release — institution-managed AWS pipeline

## Shared-account safety (mandatory)

- Before creating or changing any AWS resource, announce the exact targets, sequence, expected cost and operational risks to the user. Report what actually changed afterward. Read-only inspection must stay within Mapmory resources.
- Reuse the institution account and its designated roles. Do not create IAM users, access keys or OIDC roles, modify shared policies, widen SSH/network access, or inspect other teams' resources.
- Every created resource must carry `Service=techcourse`, `Role=techcourse-etc`, `ProjectTeam=Mapmory`. Apply the common tags in `codedeploy/aws-resources.json` to **each** application, deployment group, build project and pipeline; the JSON is a blueprint, not a CLI request.
- Monthly team limits provided by the institution: August $50, September $60, October onward $70. EC2 estimates are not the complete team bill. Check the team's usage before provisioning; CodeBuild minutes, CodePipeline and S3 add cost. Do not create budgets, dashboards or alarms with extra costs without discussion.
- Use `techcourse-project-2026-artifact` for frontend source/build/deployment artifacts. CodePipeline generates a Mapmory-identifiable artifact prefix; the live prefix is `mapmory-landing-rele/` (AWS truncates names). Do not change bucket policy, lifecycle or other prefixes. Do not use `techcourse-project-2026` or the backend artifact bucket for frontend serving/deployment.
- Permissions or bucket-role mismatches must be raised in the institution's technical-review channel. Never work around them by changing shared IAM policies. Most deletion permissions are absent; avoid speculative resources.

## Branch, review and approval

Canonical repository is `woowacourse-teams/2026-Mapmory` (`upstream`). `landing-release` is the landing-only production branch. Its initial SHA is `e91d82c727878da8ab05bee1f330f348b3542df3`, with the same landing tree as merged/reviewed PR #240.

Development starts at `upstream/main` and goes through a landing-only PR to `main`, including CodeRabbit review and all required/path-relevant checks. The existing landing UI, Recap and deployment tooling share this integration line. Do not develop directly against `landing-release`.

Production promotion is a separate, explicitly requested PR to `landing-release`. Start its worktree from the latest release head, carry only the reviewed `landing/**` and landing-owned workflow changes from a recorded main SHA, and validate that exact combined result. Do not merge `develop` or another integration branch wholesale to obtain landing files. Do not remove newer release-only changes without reconciling them. Keep a record of the source main SHA and the promotion head. Rate-limited or skipped bot reviews are not completed reviews. Use a merge commit and retain the source branch. Never push directly, force-push or delete the release branch. The existing [Landing release ruleset](https://github.com/woowacourse-teams/2026-Mapmory/rules/22160463) requires `Landing release validation`, resolved conversations and the latest base.

GitHub Actions now performs **PR validation only**. The institution-approved deployment path is:

`protected landing-release PR merge → GitHub (version 1) source → CodeBuild build/test → CodeDeploy`

On 2026-09-03 the user explicitly chose **automatic landing-only deployment without an AWS manual approval action**, superseding the earlier approval design. The release PR is the human review checkpoint: check CodeRabbit findings, required/path-relevant CI and the exact source SHA before merging. A merge to `landing-release` now authorizes and triggers production deployment; merging to `main`, `develop` or `backend-release` does not trigger this pipeline. Leave the existing GitHub `landing-production` environment/protection unchanged; it is not used by this deployment path. A failed CodeBuild stops before Deploy. Keep the landing rollback safeguards and never route backend artifacts through this pipeline.

## Configuration and provisioning order

`codedeploy/aws-resources.json` records the desired settings without account credentials. It omits the source OAuth token on purpose. Do not feed it directly to AWS CLI.

1. Inspect existing Mapmory resources and confirm this account/region, the current landing symlink/TLS, the active CodeDeploy agent and the existing backend baseline. Announce the change/risk plan before creating anything.
2. Inspect and reuse the existing `mapmory-landing` CodeDeploy application and `mapmory-landing-production` deployment group. Verify all three tags, existing `codedeploy-project`, and instance selection matching both `Name=ec2-mapmory` and `ProjectTeam=Mapmory`. Verify in-place, no traffic control, `CodeDeployDefault.AllAtOnce`, and rollback on deployment failure. Do not create replacements or start a deployment during inspection; report configuration mismatches before changing them.
3. Inspect and reuse existing `mapmory-landing-build`. Verify all three tags, existing `codebuild-project`, `aws/codebuild/standard:7.0`, Node 24, small Linux build, no privileged mode/VPC, concurrency 1 and timeout 15 minutes. Source/artifacts must be CodePipeline, buildspec `landing/buildspec.yml`, and logs `/aws/codebuild/project-2026` with a `mapmory-landing` stream prefix. Do not create another project or start a build during inspection. The landing CodePipeline also already exists; reuse it.
4. Preserve the live public `VITE_GA_MEASUREMENT_ID`, `VITE_POSTHOG_KEY`, `VITE_POSTHOG_HOST`, and optional `VITE_API_BASE_URL` as CodeBuild project environment variables. They are public client build settings, never private tokens. Check against the current live bundle; do not silently drop analytics because the GitHub repository variables are unset. Set `VITE_LANDING_VERSION=v3`.
5. Inspect and reuse `mapmory-landing-release`, existing `codepipeline-project` and all three tags. The approved type is **V2 / SUPERSEDED**, not PARALLEL; the activation hook also uses a lock. Preserve the frontend artifact bucket. **GitHub (version 1)** targets only the canonical repository's `landing-release` branch. Let the user complete OAuth if needed; never collect credentials or copy another pipeline's masked token. The console-created webhook detects this branch, with polling disabled; verify both to avoid duplicate or missing runs. This legacy webhook is separate from the console's CodeConnections-only Git triggers panel.
6. Preserve source namespace `SourceVariables` and `SOURCE_COMMIT_ID=#{SourceVariables.CommitId}`. Build outputs `BuildArtifact`; only that artifact feeds `DeployLandingOnly` in app `mapmory-landing`, group `mapmory-landing-production`. The stages are exactly Source, Build, Deploy, with no manual approval or backend action.
7. Announce production/configuration changes and chargeable execution before starting them. For the first run, verify the reviewed release SHA and server recovery target before execution. Subsequent protected release merges automatically build/test/deploy. Backend pipeline `mapmory-backend-pipeline` and group `mapmory-prod` remain untouched.

The console created V2 and explicitly disabled V2-to-V1 conversion. The user was informed of usage-based charges and authorized keeping V2 and automatic landing deployment. V2 is **not a fixed $1 monthly plan**; CodeBuild and S3 charges are separate. Do not change pipeline type, add resources or alter shared billing/IAM policies under this approval. Check current [AWS pricing](https://aws.amazon.com/codepipeline/pricing/) before future cost decisions.

The EC2 `ec2-project` role is an instance role, not the human console user. It may lack CodePipeline/CodeBuild creation permission even though the signed-in institution user can provision those services. Use the existing console login; do not request a new administrator account or broaden the instance role.

## Build, deploy and verification

### Travel campaign under /recap/

The campaign is served at `https://map-mory.com/recap/`; the existing landing remains at `/`. Both use the single automatic landing pipeline, existing build project, deployment group and release root. They deploy and roll back together. Do not create a separate campaign pipeline. No new DNS record, certificate, server port, IAM resource or backend change is part of this integration.

CodeBuild and release CI also install/build/test `travel-map-campaign`. Its `dist/recap` is built with Vite base `/recap/`; the packager copies only those static files into `client/recap/`, leaving the main landing HTML/assets intact. Main's Landing CI also checks a real combined archive without deploying it. The root/Sites `dist/client` and Worker output of the campaign are not deployed into that folder. Missing campaign output or a root-base campaign build causes packaging to fail rather than silently publish a landing-only release.

Campaign `npm test` builds its fixtures first; `npm run test:sites` builds the Sites fixtures first. CI and CodeBuild use `npm run test:built` only after their explicit build step, avoiding a second build that could replace configured analytics assets.

Both `/release.txt` and `/recap/release.txt` must contain the same tested SHA. The activation hook checks both, and requires `/recap/` to return the campaign shell rather than the landing fallback. A recap failure restores the previous complete release, just like a landing failure.

Before first production execution, inspect the existing Nginx server block. The existing `root /var/www/mapmory/current;` should serve both sets of files. These locations were installed on 2026-09-03; verify rather than adding duplicates. Do not replace unrelated routes or TLS settings:

```nginx
location = /recap {
    return 308 /recap/$is_args$args;
}
location = /recap/index.html {
    add_header Cache-Control "no-cache" always;
    try_files $uri =404;
}
location ^~ /recap/assets/ {
    try_files $uri =404;
}
location ^~ /recap/ {
    try_files $uri $uri/ /recap/index.html;
}
```

The PR does not apply Nginx configuration. Check `nginx -t` and retain the previous config before any separately approved server change. Verify `/recap` redirects while preserving the query, `/recap/` and a direct app route refresh show the campaign, missing `/recap/assets/*` returns 404, and the unchanged `/` still shows the landing.

Local validation in `landing/`: `npm ci`, `npm run build`, `npm test`. Linux CI and CodeBuild additionally execute the real filesystem activation/rollback fixtures (service/network calls are mocked); Windows skips only those Linux-specific fixtures.

CodeBuild validates that `CODEBUILD_RESOLVED_SOURCE_VERSION` equals the source action's full `SOURCE_COMMIT_ID`, builds and tests before packaging, and refuses to publish a failed build. The bundle root contains only `appspec.yml`, `scripts/activate.sh`, and `client/` (static files including `release.txt`). It never includes backend JARs, `.env` files or server code. CodePipeline retains and passes that exact build artifact directly to deployment after Build succeeds.

The CodeDeploy hook:

- Rejects the wrong application/group, invalid deployment IDs/SHAs, symlinked content and out-of-root previous releases.
- Requires an existing known-good landing release for first-run recovery and serializes activations with `flock`.
- Creates a fresh `/var/www/mapmory/releases/<sha>-<deployment-id>` directory, preserving previous releases.
- Atomically replaces only `/var/www/mapmory/current`, reloads Nginx and verifies local HTTPS **and exact release SHA**.
- Restores the prior symlink on activation/reload/health failures. CodeDeploy also has rollback enabled; a later CodeDeploy rollback gets a new deployment ID, so reusing an old SHA does not collide.
- Never uses an ApplicationStop hook, changes Nginx config, or stops/restarts the backend.

After deployment, verify public `https://map-mory.com/release.txt` against the source SHA, homepage/assets, mobile navigation, Nginx and the unchanged backend PID/health. Public/CDN mismatch needs operator investigation; a successful CodeDeploy local check alone is not a public end-to-end check.

The old `scripts/deploy-ec2.sh` remains available for the already-established manual route. It is not used by CodePipeline. Do not remove SSH secrets or change server access as part of this migration.

## Current rollout state

On 2026-09-03, `mapmory-landing-release` was created as V2/SUPERSEDED with the designated role, frontend bucket and required tags. Source and Build succeeded on reviewed release `bf4bf829c99471caccdadc539757532405c3e7fb`. After explicit user authorization, `DeployLandingOnly` was connected directly after Build. GitHub webhook 673959511 is active for push events and its registration ping returned HTTP 200; this alone is not proof of a future push-to-deploy execution. No shared IAM/network or backend pipeline settings were changed.

Main integration PRs #247/#253 and landing-only release promotion #251 are merged; the reviewed production SHA is `bf4bf829c99471caccdadc539757532405c3e7fb`. Superseded PRs #243/#246 and their source branches remain preserved. The first full pipeline execution is recorded separately from this configuration reference; always compare its source SHA and both public release markers before claiming deployment success.

First full execution `ecbbd3bc-56c1-4791-abb6-04ff3ae7a539` succeeded through Source, Build and Deploy with no approval wait. CodeDeploy `d-OO9WGLQLK` succeeded on the one intended instance. Public root and Recap release markers matched the reviewed SHA; both pages and Recap direct-route refresh returned 200, a missing Recap asset returned 404, and `/recap` redirected with its query intact. The backend PID/configuration and existing unauthenticated API response were unchanged. This first execution was explicitly started with a fixed SHA; a later protected release push remains the end-to-end webhook test.
