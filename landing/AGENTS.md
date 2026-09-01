# Prototype Instructions

Run the local server yourself and open the preview in the browser available to this environment. Do not give the user server-start instructions when you can run it.

Before making substantial visual changes, use the Product Design plugin's `get-context` skill when the visual source is unclear or no longer matches the current goal. When the user gives durable prototype-specific design feedback, preferences, or decisions, record them in `AGENTS.md`.

When implementing from a selected generated mock, treat that image as the source of truth for layout, component anatomy, density, spacing, color, typography, visible content, and hierarchy.

## Mapmory Landing Decisions

- Default to the light theme and provide an explicit dark-theme toggle.
- Keep the detailed dark globe as the focal product surface in both themes.
- The globe must rotate, and only activated/visited regions open travel memories.
- Selecting an activated region swaps the adjacent photo and record content.
- Use the real current product flow: place selection opens a separate memory panel. Do not imply photos are pinned directly onto the globe.
- Primary conversion goal is app download; until the public store URL is available, keep the final CTA clearly marked as launch preparation.
- While the existing private test remains in progress, do not alter its tester setup. Use one inline email field on the landing page for a one-time public-launch notification, with required collection consent and a 14+ confirmation.
- Lead with travel, then let distinctive places such as a bakery, ramen shop, or dessert shop show how a personal map can trigger memory.
- Keep the first viewport focused on the value proposition and conversion CTA; do not make the headline, globe, and photo compete at once.
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
- On desktop, align the floating rounded header, ordinary copy, forms, and footer to a centered 1080px column. The editorial hero photo cluster may expand to 1240px, while the globe and detailed map may expand to 1180px as immersive product-proof surfaces.
- Keep the header as a clearly separated floating surface with outer margin, a rounded border, and enough contrast in both themes.
- Use the selected centered hero reference at `design-qa/hero-centered-scroll/reference-selected-option-3.png`: a single-axis headline, centered CTAs, and three taped Mapmory team photographs. Show 제주 first, then reveal 합정 and 여수 one at a time during a short natural scroll; never lock or hijack scrolling.
- Express the act of recording with a restrained handwritten `기억` and handwritten photo captions. Show the handwritten headline word from the first paint instead of gating it on scroll or image loading; use experience-led factual captions and honor reduced-motion preferences.
- Use self-hosted LINE Seed Sans KR Regular/Bold as the single interface and body family. Keep display headings in LINE Seed Bold with relaxed line height and restrained tracking so they feel warm and editorial rather than compressed or rigid; use Regular for body copy. Reserve Nanum Pen Script for actual memory words and record captions only; avoid decorative English eyebrow copy and extra font families.
- Keep hero scrolling lightweight: isolate its reveal state from the 3D globe, animate large photos with compositor-friendly opacity/transform only, and pause the globe rendering loop while its surface is offscreen.
- Reveal the second and third hero photos with reversible scroll-distance thresholds near 30px and 120px from the page top. Photos attach one at a time while scrolling down and detach in reverse order while scrolling back up; never lock or block the page scroll.
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
