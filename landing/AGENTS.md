# Prototype Instructions

## Shared AWS account operations

- The travel campaign belongs at https://map-mory.com/recap/ beside the existing landing at /. Build/test both apps and package only campaign dist/recap into client/recap. Do not overwrite the current landing UI or reuse its root asset directory for campaign files.
- Start landing changes from upstream/main and integrate through a PR to main. landing-release is a separate production promotion target, not a development branch. Promote only reviewed landing paths and landing-owned workflows; never carry backend/client changes by merging an unrelated integration branch wholesale. main integration does not authorize release promotion or deployment. See DEPLOYMENT.md.
- Reuse the existing Mapmory build project and CodeDeploy resources; verify whether CodePipeline is actually provisioned rather than inferring installation from checked-in configuration. Preserve the manual approval design. No production approval, AWS resource changes or Nginx configuration mutation is implied by preparing a PR.

- Before any AWS resource creation/change, tell the user the exact targets, steps, cost and operational risks. Afterward report what actually changed.
- Follow the institution settings and `DEPLOYMENT.md`: existing project roles only, required Service/Role/ProjectTeam tags on every new resource, no changes to other teams, shared IAM/network settings or backend deployment. Do not infer permissions from the EC2 role; use the existing human AWS console login when needed.

Run the local server yourself and open the preview in the browser available to this environment. Do not give the user server-start instructions when you can run it.

Before making substantial visual changes, use the Product Design plugin's `get-context` skill when the visual source is unclear or no longer matches the current goal. When the user gives durable prototype-specific design feedback, preferences, or decisions, record them in `AGENTS.md`.

When implementing from a selected generated mock, treat that image as the source of truth for layout, component anatomy, density, spacing, color, typography, visible content, and hierarchy.

## Mapmory Landing Decisions

### Current analytics decisions (2026-09-03)

- Follow ANALYTICS_MEASUREMENT_PLAN.md for the current UI, not the historical waitlist funnel. The primary web conversion is an actual App Store or Google Play link click; internal download-section navigation is not a conversion.
- Keep illustrative hero motion and mobile vertical scrolling out of interactive experience events. Record memory opening after the panel is committed to the screen.
- Separate landing/recap, schema revisions, internal QA, and demo/own-photo Recap use. Do not overwrite incoming campaign attribution with internal UTMs or a fixed campaign_name.
- Preserve historical measurements and reports. New tracking code, GA4 console configuration, and live event receipt are separate verification steps; do not report one as proof of the others.

### Current mobile decisions (2026-09-03)

These decisions supersede older mobile scroll-relay guidance below. Desktop choreography remains unchanged.

- At widths up to 560px, use one dedicated mobile hero with the exact headline `여행의 순간을, 나만의 지도로.` and one representative team-owned photograph. Keep the photograph, copy, and square globe renderer in distinct readable zones.
- After the hero globe is ready, play the 1.8-second photo-to-record-to-map reveal once. Reduced-motion mode presents the completed state immediately. Do not add the desktop's long scroll relay or duplicate explanatory scenes on mobile.
- Desktop and mobile share the causal meaning of a photo becoming a record and changing the map, not identical animation mechanics. Mobile's playful interaction is rotating the live globe and opening its memories.
- In the mobile memory panel, browser Back closes the panel without leaving the landing page. Preserve the conspicuous close button, 44px touch targets, and photo captions below the image.
- Keep both public App Store and Google Play destinations reachable from the header and final download section. Do not reactivate the launch waitlist in the visible conversion flow.

### Shared and desktop guidance

- Default to the light theme and provide an explicit dark-theme toggle.
- Keep the globe as the focal product surface. Use a white globe with neutral land and mint visited countries in the default light theme; preserve the detailed dark treatment in dark mode.
- The globe must rotate, and only activated/visited regions open travel memories.
- Selecting an activated region swaps the adjacent photo and record content.
- Use the real current product flow: place selection opens a separate memory panel. Do not imply photos are pinned directly onto the globe.
- Primary conversion goal is app download through the public Google Play beta listing at `https://play.google.com/store/apps/details?id=com.mapmory.android`.
- While the public Google Play listing is available, show install CTAs instead of the retired launch-notification flow. Keep the existing waitlist implementation dormant as a fallback rather than presenting both conversions at once.
- Lead with travel, then let distinctive places such as a bakery, ramen shop, or dessert shop show how a personal map can trigger memory.
- Keep the first viewport focused on one composed value proposition: a concise headline and conversion CTA beside one light globe and one separate real-memory panel. Do not scatter multiple equal-weight photos around it.
- Make the first viewport understandable without scrolling: state that people choose the places they want to revisit, connect their photos and places into a memory map, and can install the public beta now. Do not imply that people must record every trip.
- Make the memory/location selector's purpose explicit before the user starts interacting.
- Keep the country selector inside the bounded 3D globe surface so it remains visible while the user rotates the globe; use country names as the primary shortcuts.
- Give the 3D globe a visible bounded panel and preserve vertical touch scrolling with `touch-action: pan-y`.
- Use genuine photography only. Every external photo must have a visible photographer/platform credit and source link.
- Prefer Mapmory team-owned photography for every visible memory. Credit it as `Mapmory 개발팀 촬영`; the current world set is Korea/Hapjeong, China/Shanghai, Japan/Tokyo, and Nepal/volunteer work.
- Keep the globe and photo preview concise. The next product-proof step must use the real app's 대한민국/전세계 scope pattern and the same 17-region boundary data as the Kotlin client.
- Do not pin, lock, or hijack scrolling in the globe experience. The globe CTA should align the complete experience stage cleanly in the viewport, and the section title should stay on one line where the viewport allows it.
- Confine the first-use globe instruction overlay to the globe canvas; never dim or block the entire landing viewport.
- Show the globe instruction once on the first 3D experience entry of every page load. Detect entry against the bounded globe panel rather than the longer experience section, dismiss it for the current page only, and never persist its dismissal in session or local storage.
- On mobile, keep the globe as the only default world-experience surface. Let the selected country's gold color and raised polygon motion finish before replacing the globe in-place with the separate memory panel. Make the return-to-globe control unmistakable, and never advance to the Korea experience without an explicit user action.
- The Korea experience is hierarchical in one surface: 17-province map (level 2) → the selected province's real city/county/district boundary map (level 3) → back to the province map. Reuse the client district JSON instead of drawing approximate boundaries.
- Do not show a fake editor, collection form, or recording workflow that differs from the shipping client.
- Preserve the conversion path `3D 지구본 → 대한민국 상세지도 → 지역의 실제 장소 기억 → 내 기억 지도 만들기 → 다운로드`.
- Preserve the lean landing sequence: clear promise, one primary CTA, interactive product proof, and final conversion CTA. Do not add a separate how-it-works section when it only restates the interaction the visitor just completed.
- Keep the landing visually natural, clean, and immediately understandable. Never add or imply a capability that is not present in the shipping Mapmory product.
- Treat the current editorial hero and product-flow direction as the preferred design; user review found it substantially better than the previous landing version.
- On mobile, keep the header, copy, forms, and ordinary content in a centered column with 24px side gutters. Only immersive product-proof surfaces such as the globe and detailed map may expand to an 8px edge gutter.
- On mobile, keep every actionable control at least 44px tall, finish the hero crossfade before the relay stack becomes focal, and balance Korean memory titles so a single character is not orphaned on its own line.
- On mobile, keep the intro copy visually separate from the relay globe: hold the globe in place until the copy is almost fully faded, then move the complete map scene upward so the recording photos remain prominent. Label each moving photo and the relay state as recording-in-progress or recorded so the interaction reads as adding a photo to the memory map rather than decorative stacking.
- On mobile, keep ordinary scene copy and the globe in separate readable zones. The completed travel-record object may deliberately cross into the globe only during the short absorption transition; it must be subordinate to the globe and must never overlap a visible sentence.
- On mobile, preserve only the globe's square 1:1 render surface and circular clipping mask so the 3D sphere stays perfectly round. Size the WebGL renderer to the actual container instead of enforcing a larger minimum; remove the background disc, inset ring, halo shadow, border, and target-pulse ring. Use country color changes as the only map feedback.
- On mobile handoff, show only the completed globe, one short result sentence, and the 3D-experience CTA. Do not restore progress pills, completion cards, or repeated explanations.
- Defer the hero's WebGL globe and world-boundary modules until the browser's first idle window or the relay begins, use a crisp vector loading state instead of enlarging a raster globe capture, and cap mobile WebGL pixel density so the initial render and scrolling stay responsive without a low-resolution first frame.
- On desktop, align the floating rounded header, ordinary copy, forms, and footer to a centered 1080px column. The editorial hero photo cluster may expand to 1240px, while the globe and detailed map may expand to 1180px as immersive product-proof surfaces.
- Keep the header as a clearly separated floating surface with outer margin, a rounded border, and enough contrast in both themes.
- Preserve the approved first viewport from the selected Figma direction at `https://www.figma.com/design/LkDVD6nTjkQ9JT5Rcq5PKQ?node-id=22-2`: it statically combines the travel-recording promise, a light globe, and one separate team-owned memory panel. For scroll-linked motion work, validate choreography in the running prototype first and update Figma only after the motion direction is accepted.
- Keep one focal action per scroll frame. Never scatter all relay photos around the globe, render photos as map pins, or place photo textures inside country shapes.
- Preserve the approved first viewport exactly. After it, use one coherent team-owned 미국 서부 journey to explain the product: a representative photo with a short first-person sentence, two lower-weight supporting photos unfolding around it, the photos and sentence becoming one labeled travel record, and that single record reaching the globe so the destination color deepens. Keep the existing lower interactive globe as the separate retrieval proof.
- Treat the hero plus exactly three post-intro cognitive scenes as four total scenes: one personal moment (the two supporting photos join silently inside this scene), one bundled travel record, and map accumulation. Keep one sentence per scene with a hard maximum of two rendered lines. Do not let a normal wheel step meaningfully skip a cognitive scene, and do not intercept or lock scrolling.
- Use self-hosted LINE Seed Sans KR Regular/Bold as the single interface and body family. Keep display headings in LINE Seed Bold with relaxed line height and restrained tracking so they feel warm and editorial rather than compressed or rigid; use Regular for body copy. Reserve Nanum Pen Script for actual memory words and record captions only; avoid decorative English eyebrow copy and extra font families.
- Keep hero scrolling lightweight: isolate its reveal state from the 3D globe, animate large photos with compositor-friendly opacity/transform only, and pause the globe rendering loop while its surface is offscreen.
- Drive the hero relay from reversible section progress without scroll locking, wheel interception, automatic advancement, API calls, local storage, or database writes. Reduced-motion mode must skip the traveling-photo sequence and keep one stable explanatory composition.
- Keep the representative photo as the same DOM node and crop from the personal-moment scene through bundling and globe absorption. Supporting photos may arrive sequentially inside the first scene, but must not create extra explanatory beats. A typical 120px wheel step must not skip from the personal moment to map accumulation; achieve this through section travel and progress timing, never wheel interception or scroll locking.
- Mirror the backend's `NONE / LOW / MEDIUM / HIGH` count buckets only as a clearly bounded landing preview: 0, 1–2, 3–5, and 6+ memories. The current beta client UI still consumes visited/unvisited state, so never claim that the shipped app already renders density levels.
- Do not count illustrative hero scrolling or photo arrivals as `experience_start`, `memory_open`, or `korea_memory_add`; analytics begins only at the actual interactive proof or an explicit experience CTA.
- The Korea landing demo may mirror the real app's add-to-map loop using team-owned example photos: add an example, color its province, then browse the added memory. Keep this state in browser memory only; never upload a file, call a persistence API, or write to the database from the landing demo.
- Keep the Korea add-to-map controls visually attached to the map so adding a photo and seeing the colored province happen in one viewport on desktop; avoid a separate full-width tray that pushes the map below the fold.
- In the Korea landing demo, let a new example photo finish coloring its province before enabling the next action. Keep level-3 district entry user-directed, then focus the district map and memory panel inside one viewport instead of requiring manual down-and-up scrolling.
- After a Korea province finishes filling, keep a visible `다른 지역 추가` path until every provided example has been added. Returning to the add tray must preserve every previously colored province and keep added examples available as memory-view actions.
- Use the selected Map-first Reveal direction for Korea level 2: no pin or place-name label on the province map, no separate guide panel, and no duplicated administrative labels. Let the province fill and outline carry the map state; after completion, expose one attached tray such as `서울 · 기억 1개 / 서울의 기억 보기`. Keep the colored province itself clickable and preserve an explicit accessible tray action.
- Keep the shared community-memory globe out of the main landing because it can misrepresent Mapmory as a community or travel-statistics product. Treat it only as a future separate experiment or campaign; the executable concept brief lives in `docs/community-memory-globe-experiment.md`.
- Measure the interactive proof by exact active time, distinct memories actually opened, and sequential funnel drop-off. Count time only while the experience is visible and the tab is active; record a memory only after its separate panel has opened, and never use coarse 10/30/60-second milestones as the primary duration measure.
- Send the same allow-listed product events to GA4 and PostHog: use GA4 for acquisition and final conversion, and PostHog for the experience funnel, duration, memory depth, and drop-off dashboard. Keep PostHog autocapture, person profiles, persistent identity, session replay, and GeoIP enrichment disabled by default, and never send waitlist email or consent values to either analytics provider. Standard browser, device, and page context added by the SDK may remain available for aggregate diagnosis.
- Separate team QA traffic with the analytics-only `?internal=1` browser flag and attach `traffic_type=internal` to GA4 and PostHog events until `?internal=0` clears it. Never treat this flag as authentication or access control, and exclude internal traffic from operating dashboards without deleting the raw events.

Build app UI in `src/`. Keep `.openai/hosting.json`, `worker/index.js`, `scripts/prepare-sites-build.mjs`, and `tests/sites-worker.test.mjs` intact so the same local prototype can be handed to Sites. Before a Sites handoff, run `npm run build` and `npm run test:sites`; the build must leave `dist/client/index.html`, `dist/server/index.js`, and `dist/.openai/hosting.json`.
