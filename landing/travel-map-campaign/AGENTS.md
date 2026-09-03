# Prototype Instructions

Run the local server yourself and open the preview in the browser available to this environment. Do not give the user server-start instructions when you can run it.

Keep one campaign preview at http://127.0.0.1:4174/. Before starting it, inspect the existing listener and its process command/path. Reuse the correct server or stop only a positively identified campaign server before replacing it. Do not pick a new port to work around a collision, leave extra preview servers running, or stop another task's server. Both dev and production preview use strictPort; default to loopback access and expand LAN access only for an explicit device-test request. Reuse the existing browser tab and close agent-created obsolete campaign tabs.

The approved production URL is https://map-mory.com/recap/, not a separate subdomain. Keep the main landing at /. Build a separate dist/recap artifact with /recap/ asset URLs while preserving the existing dist/client Sites/root build. Local production verification uses npm run preview:recap at the same port 4174 and /recap/ path; never start a second server. Keep application code separate, and integrate only static recap files into the landing CodeDeploy bundle. Follow ../DEPLOYMENT.md: reuse the existing single V2/SUPERSEDED CodePipeline with automatic landing-only CodeDeploy and no AWS manual approval action. The protected landing-release PR merge is the human checkpoint and starts production deployment; a main PR merge does not. Preserve backend isolation and release branch protections, and verify live AWS configuration instead of inferring it from the blueprint. Do not deploy or retarget PRs without authority.

Before making substantial visual changes, use the Product Design plugin's `get-context` skill when the visual source is unclear or no longer matches the current goal. When the user gives durable prototype-specific design feedback, preferences, or decisions, record them in `AGENTS.md`.

In the mobile replay, make the causal chain from a selected photo to its mapped location explicit. Never reveal the next location or swap its active photo before the animated route actually reaches that point; connect the active photo card to its map point and label the state as a photo being recorded on the map.

Treat chronologically consecutive photos from one trip as a single playback stop when they remain within 50 meters of the first photo in the group and each neighboring capture is no more than 60 minutes apart. Show only the earliest photo as that stop's representative, keep every analyzed photo in the journey statistics, and never merge separate trips or return visits into the same stop.

When implementing from a selected generated mock, treat that image as the source of truth for layout, component anatomy, density, spacing, color, typography, visible content, and hierarchy.

Build app UI in `src/`. Keep `.openai/hosting.json`, `worker/index.js`, `scripts/prepare-sites-build.mjs`, and `tests/sites-worker.test.mjs` intact so the same local prototype can be handed to Sites. Before a Sites handoff, run `npm run build` and `npm run test:sites`; the build must leave `dist/client/index.html`, `dist/server/index.js`, and `dist/.openai/hosting.json`.
