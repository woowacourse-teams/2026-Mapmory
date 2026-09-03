# Landing release — institution-managed AWS pipeline

## Shared-account safety (mandatory)

- Before creating or changing any AWS resource, announce the exact targets, sequence, expected cost and operational risks to the user. Report what actually changed afterward. Read-only inspection must stay within Mapmory resources.
- Reuse the institution account and its designated roles. Do not create IAM users, access keys or OIDC roles, modify shared policies, widen SSH/network access, or inspect other teams' resources.
- Every created resource must carry `Service=techcourse`, `Role=techcourse-etc`, `ProjectTeam=Mapmory`. Apply the common tags in `codedeploy/aws-resources.json` to **each** application, deployment group, build project and pipeline; the JSON is a blueprint, not a CLI request.
- Monthly team limits provided by the institution: August $50, September $60, October onward $70. EC2 estimates are not the complete team bill. Check the team's usage before provisioning; CodeBuild minutes, CodePipeline and S3 add cost. Do not create budgets, dashboards or alarms with extra costs without discussion.
- Use `techcourse-project-2026` for frontend source/build/deployment artifacts. CodePipeline creates the Mapmory-identifiable `mapmory-landing-release/` artifact prefix automatically. Do not change bucket policy, lifecycle or other prefixes. Do not use the backend artifact bucket for frontend serving/deployment.
- Permissions or bucket-role mismatches must be raised in the institution's technical-review channel. Never work around them by changing shared IAM policies. Most deletion permissions are absent; avoid speculative resources.

## Branch, review and approval

Canonical repository is `woowacourse-teams/2026-Mapmory` (`upstream`). `landing-release` is the landing-only production branch. Its initial SHA is `e91d82c727878da8ab05bee1f330f348b3542df3`, with the same landing tree as merged/reviewed PR #240.

Development starts at `upstream/main` and goes through a landing-only PR to `main`, including CodeRabbit review and all required/path-relevant checks. The existing landing UI, Recap and deployment tooling share this integration line. Do not develop directly against `landing-release`.

Production promotion is a separate, explicitly requested PR to `landing-release`. Start its worktree from the latest release head, carry only the reviewed `landing/**` and landing-owned workflow changes from a recorded main SHA, and validate that exact combined result. Do not merge `develop` or another integration branch wholesale to obtain landing files. Do not remove newer release-only changes without reconciling them. Keep a record of the source main SHA and the promotion head. Rate-limited or skipped bot reviews are not completed reviews. Use a merge commit and retain the source branch. Never push directly, force-push or delete the release branch. The existing [Landing release ruleset](https://github.com/woowacourse-teams/2026-Mapmory/rules/22160463) requires `Landing release validation`, resolved conversations and the latest base.

GitHub Actions now performs **PR validation only**. The institution-approved deployment path is:

`landing-release → GitHub (version 1) source → CodeBuild → manual approval → CodeDeploy`

The deployment approval lives in CodePipeline's `ApproveLandingProduction` action. This is a deliberate migration of the proposed GitHub deployment job's approval checkpoint, **not approval-free deployment**. Announce/confirm that checkpoint when provisioning. Leave the existing GitHub `landing-production` environment and its protection unchanged; no workflow uses it to deploy in this design. The Mapmory release owner must check CodeRabbit, CI, the source SHA and the latest branch head before approving the AWS action. Never approve a stale execution.

## Configuration and provisioning order

`codedeploy/aws-resources.json` records the desired settings without account credentials. It omits the source OAuth token on purpose. Do not feed it directly to AWS CLI.

1. Inspect existing Mapmory resources and confirm this account/region, the current landing symlink/TLS, the active CodeDeploy agent and the existing backend baseline. Announce the change/risk plan before creating anything.
2. Inspect and reuse the existing `mapmory-landing` CodeDeploy application and `mapmory-landing-production` deployment group. Verify all three tags, existing `codedeploy-project`, and instance selection matching both `Name=ec2-mapmory` and `ProjectTeam=Mapmory`. Verify in-place, no traffic control, `CodeDeployDefault.AllAtOnce`, and rollback on deployment failure. Do not create replacements or start a deployment during inspection; report configuration mismatches before changing them.
3. Inspect and reuse existing `mapmory-landing-build`. Verify all three tags, existing `codebuild-project`, `aws/codebuild/standard:7.0`, Node 24, small Linux build, no privileged mode/VPC, concurrency 1 and timeout 15 minutes. Source/artifacts must be CodePipeline, buildspec `landing/buildspec.yml`, and logs `/aws/codebuild/project-2026` with a `mapmory-landing` stream prefix. Do not create another project or start a build during inspection. Provision only the missing landing CodePipeline after the release and cost approvals below.
4. Preserve the live public `VITE_GA_MEASUREMENT_ID`, `VITE_POSTHOG_KEY`, `VITE_POSTHOG_HOST`, and optional `VITE_API_BASE_URL` as CodeBuild project environment variables. They are public client build settings, never private tokens. Check against the current live bundle; do not silently drop analytics because the GitHub repository variables are unset. Set `VITE_LANDING_VERSION=v3`.
5. Only after this PR's files exist on the reviewed `landing-release` head, create `mapmory-landing-release` using existing `codepipeline-project` and all three tags. Choose V1 / SUPERSEDED to avoid parallel deployments. Select the frontend bucket explicitly, not a new default bucket. Connect **GitHub (version 1)** to the canonical repository's `landing-release` branch using the AWS console authorization flow. Let the user complete OAuth if required; never collect tokens/passwords/MFA in chat or copy another pipeline's masked token. The console-created webhook should detect this branch, with polling disabled; verify both to avoid duplicate or missing runs.
6. Configure source namespace `SourceVariables`. Pass `SOURCE_COMMIT_ID=#{SourceVariables.CommitId}` to CodeBuild as shown in the blueprint. Add the **manual approval action before CodeDeploy**. The creation wizard may not offer approval: do not create an immediately deployable pipeline in that case. Initially omit/disable Deploy, add approval, then enable Deploy only after inspection.
7. Pipeline creation can automatically start its first execution and incur build charges. Announce this before creation. Do not approve production until local/CI tests pass and the exact tested source revision is verified. Backend pipeline `mapmory-backend-pipeline` and group `mapmory-prod` remain untouched.

The current console creation wizard may offer only V2. Its per-action-minute pricing is different from V1's active-month pricing. Do not silently submit V2 under a V1 cost approval. Obtain explicit approval for initial V2 execution charges before creating it without Deploy, apply all required tags immediately, and verify conversion to V1 before connecting production deployment. If conversion is unavailable, stop and request a decision; do not assume V2 is a fixed $1 monthly plan. See the [AWS pricing page](https://aws.amazon.com/codepipeline/pricing/) and [pipeline editing guide](https://docs.aws.amazon.com/codepipeline/latest/userguide/pipelines-edit.html).

The EC2 `ec2-project` role is an instance role, not the human console user. It may lack CodePipeline/CodeBuild creation permission even though the signed-in institution user can provision those services. Use the existing console login; do not request a new administrator account or broaden the instance role.

## Build, deploy and verification

### Travel campaign under /recap/

The campaign is served at `https://map-mory.com/recap/`; the existing landing remains at `/`. Both use the single planned landing pipeline and its manual approval, reusing the existing build project, deployment group and release root. Do not create a separate campaign pipeline. No new DNS record, certificate, server port, IAM resource or backend change is part of this integration.

CodeBuild and release CI also install/build/test `travel-map-campaign`. Its `dist/recap` is built with Vite base `/recap/`; the packager copies only those static files into `client/recap/`, leaving the main landing HTML/assets intact. Main's Landing CI also checks a real combined archive without deploying it. The root/Sites `dist/client` and Worker output of the campaign are not deployed into that folder. Missing campaign output or a root-base campaign build causes packaging to fail rather than silently publish a landing-only release.

Campaign `npm test` builds its fixtures first; `npm run test:sites` builds the Sites fixtures first. CI and CodeBuild use `npm run test:built` only after their explicit build step, avoiding a second build that could replace configured analytics assets.

Both `/release.txt` and `/recap/release.txt` must contain the same tested SHA. The activation hook checks both, and requires `/recap/` to return the campaign shell rather than the landing fallback. A recap failure restores the previous complete release, just like a landing failure.

Before first production approval, inspect the existing Nginx server block. The existing `root /var/www/mapmory/current;` should serve both sets of files. If absent, the operator can add these locations inside that HTTPS server block; do not replace unrelated routes or the existing TLS settings:

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

The PR does not apply Nginx configuration. Check `nginx -t` and retain the previous config before any separately approved server change. Verify `/recap` redirects while preserving the query, `/recap/` and a direct app route refresh show the campaign, missing `/recap/assets/*` returns 404, and the unchanged `/` still shows the landing. Keep the manual production approval in place.

Local validation in `landing/`: `npm ci`, `npm run build`, `npm test`. Linux CI and CodeBuild additionally execute the real filesystem activation/rollback fixtures (service/network calls are mocked); Windows skips only those Linux-specific fixtures.

CodeBuild validates that `CODEBUILD_RESOLVED_SOURCE_VERSION` equals the source action's full `SOURCE_COMMIT_ID`, builds and tests before packaging, and refuses to publish a failed build. The bundle root contains only `appspec.yml`, `scripts/activate.sh`, and `client/` (static files including `release.txt`). It never includes backend JARs, `.env` files or server code. CodePipeline retains and passes that exact build artifact to approval and deployment.

The CodeDeploy hook:

- Rejects the wrong application/group, invalid deployment IDs/SHAs, symlinked content and out-of-root previous releases.
- Requires an existing known-good landing release for first-run recovery and serializes activations with `flock`.
- Creates a fresh `/var/www/mapmory/releases/<sha>-<deployment-id>` directory, preserving previous releases.
- Atomically replaces only `/var/www/mapmory/current`, reloads Nginx and verifies local HTTPS **and exact release SHA**.
- Restores the prior symlink on activation/reload/health failures. CodeDeploy also has rollback enabled; a later CodeDeploy rollback gets a new deployment ID, so reusing an old SHA does not collide.
- Never uses an ApplicationStop hook, changes Nginx config, or stops/restarts the backend.

After first approval, verify public `https://map-mory.com/release.txt` against the source SHA, homepage/assets, mobile navigation, Nginx and the unchanged backend PID/health. Public/CDN mismatch needs operator investigation; a successful CodeDeploy local check alone is not a public end-to-end check.

The old `scripts/deploy-ec2.sh` remains available for the already-established manual route. It is not used by CodePipeline. Do not remove SSH secrets or change server access as part of this migration.

## Current rollout state

Read-only AWS console verification on 2026-09-03 found the landing CodeBuild project and CodeDeploy resources, but no landing CodePipeline; the Mapmory pipeline list contained only the backend pipeline targeting `backend-release`. Do not duplicate existing resources or mistake the blueprint for a running pipeline. The provisioning instructions above are a reference, not authorization to create replacements or start chargeable executions.

Campaign PR #242 is integrated into main. The consolidated main integration carries the landing improvements previously merged to develop in #240 and the deployment/Recap work from #243 and #246. Those overlapping release-targeted PRs are superseded by this main-first route; preserve their branches and review history rather than merging both routes. The public site remains the manually deployed release until a separately authorized production promotion and approval. Verify the exact reviewed landing-release SHA at the manual approval checkpoint when the pipeline is provisioned.
