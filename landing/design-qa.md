# Mapmory landing — interactive globe and district-detail QA

> Screenshot evidence is retained locally under `design-qa/` and is intentionally
> excluded from Git. This document records the capture paths and verified results
> without adding binary QA artifacts to the repository history.

## Evidence

- Source visual truth: `qa/reference-jeonnam-district-map.png` (456 × 363 px), supplied by the user as the expected province-detail map state.
- Desktop implementation: `qa/desktop-jeonnam-detail-final.png` (998 × 1000 px browser capture), rendered with a 1440 × 1000 CSS viewport override in the Codex in-app browser.
- Mobile implementation: `qa/mobile-jeonnam-detail-final.png` and `qa/mobile-globe-country-dock.png` (375 × 811 px browser captures), rendered with a 390 × 844 CSS viewport override.
- Focused comparison: the source and the rendered Jeonnam map were opened together at original density. The comparison was limited to the map surface because the source does not include the surrounding landing-page card or photo.
- State: light landing theme with the intentionally dark product-map surface; Jeonnam selected at level 3 and Yeosu highlighted.
- Density normalization: captures were compared by content region and relative map proportions, not raw pixel equality, because the in-app browser surface crops its screenshot output below the explicit CSS viewport width.

## Interaction and technical checks

- Country shortcuts remain visible inside the 3D globe panel at desktop and mobile widths.
- Korea, China, Japan, and Nepal shortcuts update the adjacent card to the correct developer-owned photo with non-zero natural dimensions.
- The Korea flow changes within one card: 17-province map → province city/county/district map → back to 17-province map.
- Seoul, Jeonnam, and Jeju load the exact client district JSON; Mapo-gu, Yeosu, and Jeju City are highlighted respectively.
- The back button returns to the province map without route navigation.
- The mobile checks showed no horizontal overflow (`scrollWidth` did not exceed the layout viewport).
- Browser console errors and warnings: none.
- Frontend tests: 10 passed.
- Production build: passed.

## Required fidelity surfaces

- Fonts and typography: existing Noto Sans KR / Be Vietnam Pro hierarchy is preserved. District labels use compact optical sizes at mobile width so the dense Jeonnam map remains readable.
- Spacing and layout rhythm: the globe selector is contained by the 3D surface. The level-3 map no longer stretches to the photo-card height, eliminating the large empty lower area found in the first comparison.
- Colors and visual tokens: the district surface now uses the source's dark slate fills, muted blue-gray boundaries and labels, and a single mint selected district.
- Image quality and asset fidelity: the existing developer-owned memories and the five new USA memories use the original photographs; no generated or placeholder imagery remains in the active flows.
- Copy and content: Huiok copy preserves the user's notes about umami, acidity, and the long wait in a shorter landing-page voice. Level labels clearly distinguish province selection from district detail.

## Comparison history

1. First focused comparison — blocked.
   - [P2] The level-3 map stretched to the height of the adjacent photo card, leaving a large empty dark area below the map.
   - [P2] Unselected Jeonnam districts were light gray, while the source uses a dark slate map with quiet boundaries.
2. Fixes applied.
   - Changed the detail grid to top alignment so the map keeps its natural canvas height.
   - Changed unselected district fill, stroke, and label colors to the source's dark visual system; kept Yeosu mint.
   - Reduced district label size and outer padding at the mobile breakpoint.
3. Final focused comparison — no actionable P0/P1/P2 differences remain. The rendered map preserves the source hierarchy, region silhouette, label treatment, and active-district emphasis while adding the landing-specific step caption and adjacent memory card.

## Residual test gaps

- The globe bundle still produces Vite's existing large-chunk warning; loading and interaction completed successfully, so this is a performance follow-up rather than a visual blocker.
- The source screenshot covers only the Jeonnam detail state, so other landing sections were checked for consistency and responsiveness rather than pixel fidelity to that source.

## USA west gallery extension

### Evidence

- Source visual truth: `C:/Users/YongSung/Downloads/20251.jpg`, `20709.jpg`, `19812.jpg`, `19937.jpg`, and `19939.jpg`, supplied by the user as five photographs from the same USA trip.
- Desktop implementation: `C:/Users/YongSung/.codex/visualizations/2026/08/23/01a02dce-4d75-7521-a0b5-3c8f63753f9d/desktop-usa-gallery-final.png`, browser capture at the default desktop viewport with the USA selected and the first gallery image active.
- Mobile implementation: `C:/Users/YongSung/.codex/visualizations/2026/08/23/01a02dce-4d75-7521-a0b5-3c8f63753f9d/mobile-usa-gallery-card-final.png`, browser capture with a 390 x 844 CSS viewport override and the USA photo card aligned to the viewport.
- Combined comparison evidence: `C:/Users/YongSung/.codex/visualizations/2026/08/23/01a02dce-4d75-7521-a0b5-3c8f63753f9d/usa-gallery-comparison.png` places all five source photos beside the rendered desktop and mobile states in one image.
- Density normalization: source photographs retain their original 4000 x 3000, 2252 x 4000, 4000 x 3000, 3000 x 4000, and 4000 x 2252 pixels. The browser captures are evaluated by the rendered content box at device scale 1 rather than raw pixel equality because the source is photography, not a UI mock.
- Focused comparison: the photo area, gallery controls, caption treatment, and memory-card copy were readable in the combined evidence, so no additional crop was required.

### Interaction and technical checks

- Selecting `미국` highlights the USA on the globe and opens the `미국 · 서부 여행` memory card.
- All five dot controls and both arrows change the active photograph; each image loaded with the source's non-zero natural dimensions and the count changed from `1 / 5` through `5 / 5`.
- Landscape and portrait photographs use `object-fit: contain`, preserving the full source frame without stretching the memory card.
- Mobile layout showed no horizontal overflow (`scrollWidth` equaled the layout viewport width), and the country selector, arrows, counter, caption, dots, title, description, and photo credit remained usable.
- Browser console errors and warnings: none.

### Required fidelity surfaces

- Fonts and typography: the existing Mapmory hierarchy is preserved; gallery captions and the count remain legible over the photographs at desktop and mobile widths.
- Spacing and layout rhythm: the gallery keeps the existing memory-card proportions. Portrait images no longer expand the card height, while controls remain inside the image surface.
- Colors and visual tokens: dark translucent gallery controls reuse the product-map surface and mint active token without competing with the photographs.
- Image quality and asset fidelity: all five supplied files are present at their original dimensions and shown uncropped with `contain`; no AI-generated or replacement imagery is used.
- Copy and content: the five captions distinguish Bryce Canyon, Antelope Canyon, Las Vegas daytime, the fountain, and the Venetian night, while the card groups them as one USA west trip.

### Comparison history

1. First browser comparison - blocked.
   - [P2] Portrait photographs expanded the gallery image element and made the memory card substantially taller than the adjacent globe panel.
2. Fix applied.
   - Positioned gallery images inside the fixed image surface and set explicit width, height, and `object-fit: contain` so portrait and landscape sources share one stable card geometry.
3. Final combined comparison - no actionable P0/P1/P2 differences remain. Source composition is preserved, the five-photo sequence is obvious, and desktop/mobile hierarchy remains intact.

## Final result

final result: passed

---

# Rejected typography experiment: SUIT — 2026-08-27

## Target and evidence

- Selected typography direction: second displayed typography concept, combined with the third concept's relaxed spacing while preserving the current landing layout.
- Generated type reference: `C:\Users\YongSung\.codex\generated_images\01a03c75-bb1d-7201-bea1-2716a4bced09\exec-a21a1831-e26e-4000-86a9-d2471f7233ce.png`
- Previous desktop baseline: `design-qa/manus-typography-source/mapmory-current-desktop-1280x720.png`
- Previous mobile baseline: `design-qa/manus-typography-source/mapmory-current-mobile-390x844.png`
- SUIT desktop implementation: `design-qa/suit-typography-v1/desktop-1280x720.png`
- SUIT mobile implementation: `design-qa/suit-typography-v1/mobile-390x844.png`

## Applied system

- Primary interface and body: self-hosted `SUIT Variable`, weight range 100–900.
- Hero and section headings: weight 600 with relaxed line height and restrained negative tracking.
- Buttons: weight 650 so controls remain clear without matching the headline's former heavy block tone.
- Memory word and photo-record captions: `Nanum Pen Script` only.
- Existing photography, copy, line breaks, layout, color, interaction, and component geometry were preserved.

## Verification

- Desktop 1280 × 720: hero stays on one line, SUIT is loaded at weight 600, and horizontal overflow is absent.
- Mobile 390 × 844: hero remains two lines, SUIT is loaded at weight 600, and horizontal overflow is absent.
- Computed mobile hero: 32.79 px, 41.32 px line height, −0.79 px tracking.
- Computed desktop hero: 40.96 px, 51.2 px line height, −0.90 px tracking.
- Browser console errors and warnings: none.
- The preview server is bound to `127.0.0.1` only; the previously opened LAN/mobile port remains closed.

## Decision

- Technical QA passed, but the visual result felt too rigid in user review.
- This experiment was rejected and replaced with the third displayed LINE Seed editorial direction.

final result: rejected by user

---

# LINE Seed editorial typography QA — 2026-08-27

## Target and evidence

- Selected typography direction: third displayed typography concept.
- Generated type reference: `C:\Users\YongSung\.codex\generated_images\01a03c75-bb1d-7201-bea1-2716a4bced09\exec-b2f52f37-e78f-4335-bba2-cc457e11d313.png`
- Desktop implementation: `design-qa/line-seed-editorial-v1/desktop-1280x720.png`
- Mobile implementation: `design-qa/line-seed-editorial-v1/mobile-390x844.png`
- Side-by-side comparison: `design-qa/line-seed-editorial-v1/comparison-option3-vs-implementation.png`

## Applied system

- Primary interface and body: self-hosted `LINE Seed Sans KR` Regular/Bold.
- Hero: Bold with 1.24 line height and restrained negative tracking, preserving the selected concept's clear weight without the compressed SUIT tone.
- Body: Regular for a quieter reading rhythm.
- Memory word and photo captions: `Nanum Pen Script` only.
- Existing centered layout, photographs, copy, controls, and interactions are unchanged.

## Verification

- Desktop 1280 × 720: hero remains on one line; computed size 42.88 px, weight 700, line height 53.17 px, tracking −0.64 px.
- Mobile 390 × 844: hero remains two lines; computed size 33.18 px, weight 700, line height 42.14 px, tracking −0.60 px.
- Horizontal overflow: none at both viewports.
- Browser console errors and warnings: none.
- The rejected SUIT runtime asset was removed; only the existing LINE Seed files remain.
- The preview server remains bound to `127.0.0.1` only.

## Final result

final result: passed

---

# Korea multi-add and desktop density QA — 2026-08-27

## Audit scope and evidence

- Desktop initial map before density adjustment: `design-qa/korea-multi-add-spacing-v1/01-desktop-korea-before-add.png`
- Desktop compact map with attached add tray: `design-qa/korea-multi-add-spacing-v1/02-desktop-korea-compact.png`
- Desktop add tray reopened after the first region: `design-qa/korea-multi-add-spacing-v1/03-desktop-second-add-open.png`
- Desktop with two colored regions: `design-qa/korea-multi-add-spacing-v1/04-desktop-two-regions-added.png`
- Mobile with two colored regions: `design-qa/korea-multi-add-spacing-v1/05-mobile-two-regions-added.png`
- Verified viewports: desktop 1280 × 720, mobile 390 × 844

## Numbered flow health

1. Initial Korea map — Healthy: the map and attached photo tray remain in the same surface; desktop canvas height was reduced so the add choices begin inside the first viewport.
2. First region fill — Healthy: the 1,500 ms province-fill motion completes before actions are enabled.
3. Continue adding — Fixed: the completion tray now exposes `다른 지역 추가`; reopening the tray preserves the first colored province.
4. Second region fill — Healthy: a second example can be added without entering level 3, and the progress changes from `1 / 17` to `2 / 17`.
5. Browse an added memory — Healthy: the active region has an explicit memory-view action, while previously added photo cards change to `기억 보기` instead of implying another add.

## Desktop density changes

- Desktop hero no longer forces a viewport-height minimum; its top padding and photo-cluster gap were reduced.
- World, Korea, and final conversion section vertical padding was reduced only above the 900 px breakpoint.
- Korea map canvas maximum height changed from 430/520 px to 340/460 px for compact/regular desktop viewports.
- Mobile spacing and the mobile map aspect ratio remain unchanged.

## Typography list

- Selected primary: `LINE Seed Sans KR` Regular/Bold — body, headings, navigation, buttons, and form controls.
- Selected accent: `Nanum Pen Script` — handwritten `기억` and photo-record captions only.
- Alternative for denser product UI: `SUIT` — clean and compact, but less warm than the selected direction.
- Alternative for softer editorial copy: `Gowun Dodum` — warm and readable, but has less weight range for an interface system.
- Neutral fallback: `Noto Sans KR` — dependable, but visually generic for Mapmory's first impression.
- Removed mixed Latin voice: `Be Vietnam Pro` — no longer used so Korean and Latin interface text share one family.

## Verification and limits

- Two consecutive additions were completed on desktop and mobile, with two provinces still colored and `2 / 17` exposed to accessibility APIs.
- Browser console errors and warnings: none.
- No upload, API call, persistence, or database write was introduced; the demo state resets on reload.
- Screenshot review does not replace physical-device touch testing or assistive-technology testing.

## Final result

final result: passed

---

# Map-first Reveal QA — 2026-08-27

## Visual reference and evidence

- Selected Manus reference: `design-qa/map-first-v1/reference-manus-map-first.png`
- Desktop neutral state: `design-qa/map-first-v1/01-desktop-initial-1280x720.png`
- Desktop Seoul reveal complete: `design-qa/map-first-v1/03-desktop-seoul-ready-1280x720.png`
- Desktop Seoul detail opened from the map: `design-qa/map-first-v1/04-desktop-seoul-detail-map-click-1280x720.png`
- Mobile neutral state: `design-qa/map-first-v1/05-mobile-initial-390x844.png`
- Mobile Seoul reveal complete: `design-qa/map-first-v1/09-mobile-seoul-ready-final-390x844.png`
- Mobile Jeju detail: `design-qa/map-first-v1/07-mobile-jeju-detail-390x844.png`
- Mobile Seoul dark theme: `design-qa/map-first-v1/10-mobile-seoul-ready-dark-390x844.png`
- Verified viewports: desktop 1280 × 720, mobile 390 × 844

## Scope and intentional differences

- Applied the selected Map-first Reveal direction only to the Korea level-2 experience. The 3D globe and its country-memory flow remain unchanged.
- Matched the reference hierarchy: neutral Korea map → province fill animation → one attached result tray → user-opened local detail.
- Removed province pins, place-name labels, the separate right-side guide, and duplicated administrative names from the level-2 map.
- Kept the real Mapmory behavior instead of the reference's illustrative photo-on-map state: a colored province opens a separate real memory panel; no photo is pinned directly to the map.
- Preserved the existing Mapmory app header, 1 / 17 progress, actual province geometry, actual district JSON, and actual team-owned memory data.

## Interaction results

1. The neutral state shows the complete Korea boundary without pins or labels and keeps the three real example memories in the attached add tray: Passed.
2. Selecting Seoul keeps level 2 visible while an expanding mint reveal is clipped inside Seoul for 1,500 ms. Competing add controls are disabled and `aria-busy` is exposed during the motion: Passed.
3. After completion, the add tray is replaced by one result tray reading `서울 · 기억 1개 / 서울의 기억이 채워졌어요 / 서울의 기억 보기`: Passed.
4. The colored Seoul province itself is directly clickable. Clicking its actual geometry opens `서울의 기억`, the Seoul district map, and the real 희옥 record: Passed.
5. The explicit result-tray action opens the same detail state, preserving an accessible non-canvas path: Passed.
6. The same path works with Jeju and the real Jeju district data and memory panel: Passed.
7. Mobile level 2 fits in one viewport after focus: demo top 88 px to bottom 703 px; result tray bottom 683 px; no horizontal overflow: Passed.
8. Desktop level 2 fits in 1280 × 720 after focus: demo top 88 px to bottom 685 px; no horizontal overflow: Passed.
9. Dark theme keeps the map boundary, selected province, result tray, text, and CTA legible with no horizontal overflow: Passed.
10. No API request, upload, persistence, or database write was added. All add/reveal state remains browser-memory-only: Passed.

## Console and limitations

- A negative canvas radius error found during the first reveal implementation was fixed by clamping animation progress before drawing. Fresh desktop/mobile/light/dark runs after the fix produced no new console or React overlay errors.
- The retained browser log contains only that pre-fix entry at 2026-08-27 09:39:35 UTC; subsequent final QA ran after 09:45 UTC.
- Physical-phone browser chrome, real touch latency, and assistive-technology behavior remain outside this local browser pass.

## Final result

final result: passed

---

# Globe viewport alignment and scoped onboarding QA — 2026-08-27

## Evidence

- Previous visual direction: `C:\Users\YongSung\.codex\generated_images\01a03c75-bb1d-7201-bea1-2716a4bced09\exec-124eadcc-79da-4ac2-9c9e-806cfe6157f4.png`
- Desktop implementation: `design-qa/globe-onboarding-v3/desktop-onboarding-1440x1024.png`
- Mobile implementation: `design-qa/globe-onboarding-v3/mobile-onboarding-390x844.png`
- Viewports: desktop 1440 × 1024, mobile 390 × 844

## User-feedback changes

- Removed the sticky globe experience and the artificial extra scroll height. The page now follows ordinary document scrolling without locking or pinning.
- Kept `지구본을 돌려 기억을 찾아요.` on one line at both verified viewports.
- Confined the dimmed onboarding layer to the globe canvas instead of covering the full viewport or blocking the adjacent memory panel.
- The earlier full-screen onboarding mock is now only historical reference; the canvas-scoped treatment is an intentional user-directed override.

## Interaction and layout results

- Desktop CTA arrival: experience section top 100 px; heading bottom 203 px; both 520 px panels bottom 747 px inside the 1024 px viewport: Passed.
- Desktop overlay bounds exactly matched the globe canvas bounds: left 123 px, top 280 px, right 785 px, bottom 687 px: Passed.
- After dismissing the guide, a 430 px wheel input moved the experience content by approximately 430 px; no sticky stop remained: Passed.
- Mobile CTA arrival: heading top 155 px and bottom 191 px; complete globe panel bottom 783 px inside the 844 px viewport: Passed.
- Mobile overlay bounds exactly matched the globe canvas bounds: left 9 px, top 337 px, right 366 px, bottom 695 px: Passed.
- Mobile headline rendered at 29 px in one line without clipping; no horizontal overflow was present: Passed.
- Desktop and mobile console errors/warnings: none.
- React error overlay: none.

## Final result

final result: passed

---

# Globe onboarding and compact experience QA — 2026-08-27

## Evidence

- Source visual truth: `C:\Users\YongSung\.codex\generated_images\01a03c75-bb1d-7201-bea1-2716a4bced09\exec-124eadcc-79da-4ac2-9c9e-806cfe6157f4.png` (1487 × 1058 px). This is the revised full-viewport gray onboarding direction selected after option 1.
- Desktop onboarding: `design-qa/globe-onboarding-v2/desktop-onboarding-1440x1024.png` (1425 × 1013 px browser capture, 1440 × 1024 CSS viewport override).
- Desktop dismissed state: `design-qa/globe-onboarding-v2/desktop-experience-1440x1024.png` (1425 × 1013 px browser capture, 1440 × 1024 CSS viewport override).
- Mobile onboarding: `design-qa/globe-onboarding-v2/mobile-onboarding-390x844.png` (375 × 811 px browser capture, 390 × 844 CSS viewport override).
- Mobile dismissed state: `design-qa/globe-onboarding-v2/mobile-experience-390x844.png` (375 × 811 px browser capture, 390 × 844 CSS viewport override).
- QA captures remain local under the ignored `design-qa/` subdirectory; only paths and results are tracked.
- Density normalization: both browser captures use device scale 1. The in-app browser reserves 15 px horizontally and 11–33 px vertically, so the comparison uses the complete content region and relative component proportions rather than raw pixel equality.
- Full-view comparison evidence: the source and desktop onboarding capture were opened together in one comparison input at original density.
- Focused comparison: no separate crop was required because the guide icon, three text levels, globe silhouette, country controls, and right memory panel remain legible in the full-view pair.

## States and interactions checked

- Hero CTA enters `#experience` and the guide appears when at least 45% of the experience section is visible.
- The guide covers the full viewport with a translucent neutral-gray layer and centers the instruction over the globe.
- Clicking anywhere on the overlay removes it immediately; reloading the same tab does not show it again during that browser session.
- The globe remains draggable after dismissal.
- Country selection sits below the globe on desktop and mobile; the mobile selector begins after the globe shell with no overlap.
- Selecting China highlights China and opens `상하이 · 와이탄` with `황푸강 건너로 번지던 상하이의 밤` in the separate memory panel.
- At 1440 × 1024, the heading, 520 px globe panel, country selector, and 520 px memory card fit in one viewport.
- The desktop experience remains sticky for roughly two 430 px wheel movements, then releases into the Korea section without directly cancelling wheel input.
- At 390 × 844, the globe and country selector fit in one compact card, there is no horizontal overflow, and the memory panel continues below it.
- Desktop and mobile console errors/warnings: none.

## Required fidelity surfaces

- Fonts and typography: the existing LINE Seed Sans KR hierarchy is preserved. The guide uses one 22 px headline, one 13 px instruction, and one quiet 11 px dismissal hint so the new layer reads before the underlying page.
- Spacing and layout rhythm: the globe and memory panel share a 520 px desktop height. Moving the country selector below the globe makes the interaction order read as globe → country → memory without increasing the panel height.
- Colors and visual tokens: the overlay uses neutral gray translucency and the guide reuses the existing deep-navy surface and mint action color. No new gradient or decorative visual system was introduced.
- Image quality and asset fidelity: the default Korea memory now uses the team-owned 제주 coast photo. The hero supporting photos use the team-owned Shanghai Bund and Antelope Canyon assets, strengthening travel identity without generated or placeholder imagery.
- Copy and content: the onboarding only explains existing rotation and visited-country selection. The default Korea copy describes a 제주 travel memory rather than making the product appear to be a ramen map.
- Accessibility: the full overlay is one semantic button with an explicit label, visible focus styling, reduced-motion support, and no permanent wheel or body-scroll lock.

## Comparison history

1. Selected source direction.
   - Full-screen translucent gray onboarding, a compact globe-centered instruction, and click-anywhere dismissal.
2. Product feedback incorporated during implementation.
   - Replaced the mock's ramen memory with 제주 travel imagery.
   - Moved country selection from the top of the globe to a dedicated bottom row.
   - Reduced both product panels to one-screen height and added a two-scroll sticky dwell instead of cancelling wheel events.
3. Final comparison.
   - No actionable P0/P1/P2 differences remain. Overlay hierarchy, opacity, globe focus, and dismissal affordance match the selected direction.
   - The photo and country-control differences from the mock are intentional, user-requested product corrections.

## Follow-up polish

- P3: measure guide dismissal rate and subsequent globe drag/country selection before deciding whether the session-only guide should become shorter or more persistent.
- The existing `react-globe.gl` production chunk warning remains a performance follow-up.

## Final result

final result: passed

---

# Centered hero and scroll-built memory collage QA — 2026-08-27

## Evidence

- Source visual truth: `design-qa/hero-centered-scroll/reference-selected-option-3.png` (1487 × 1058 px), the user's selected third hero direction.
- Fresh desktop state: `design-qa/hero-centered-scroll/desktop-initial-1440x1024.png` (1425 × 1013 px browser capture) with only the centered 제주 record visible.
- Desktop reveal states: `desktop-step-1-1440x1024.png`, `desktop-step-2-1440x1024.png`, and `desktop-all-photos-top-1440x1024.png` (1425 × 1013 px each).
- Dark theme: `desktop-dark-1440x1024.png` (1425 × 1013 px).
- Mobile states: `mobile-initial-390x844.png` and `mobile-all-390x844.png` (375 × 811 px captures).
- Combined comparison: `design-qa/hero-centered-scroll/comparison-reference-vs-implementation.png` (2880 × 1084 px) places the selected reference and the final all-photo implementation together.
- CSS viewport and density: desktop 1440 × 1024, mobile 390 × 844, device scale 1. The in-app browser reserves 15 px horizontally and 11/33 px vertically for its scroll surface, so the resulting content captures are 1425 × 1013 and 375 × 811 px. The combined image fits each artifact proportionally into an equal 1440 × 1024 comparison panel without cropping.
- State alignment: the reference shows the completed three-photo collage, so it is compared with the implementation after both scroll reveals have completed and the user has returned to the hero top.

## States and interactions checked

- A fresh visit begins with one centered 제주 photo. Natural scrolling reveals 합정 first and 여수 second; scrolling is never locked or hijacked.
- Once a photo has been revealed, it remains in the collage when the user scrolls back up, matching the metaphor of accumulated records.
- `기억` retains the readable mint display word and receives a restrained handwritten overlay above it.
- Each photo caption writes on with an experience-led factual title: `파도 소리가 남은 저녁`, `기다림 끝의 따뜻한 한 그릇`, and `함께 나눈 달콤한 한 상자`.
- `지구본 돌려보기` navigates to `#experience`; the section settles 100 px below the floating header.
- 중국 selection marks the country selected and opens the separate `상하이 · 와이탄` memory panel.
- The existing globe drag interaction was preserved unchanged from the preceding passed QA run.
- In the Korea demo, adding the 서울 photo changes progress from 0/17 to 1/17 and 6%, adds the 서울 hotspot, and exposes `기억 보기`. Selecting it opens the 서울 시·군·구 map and 합정·희옥 memory card.
- No horizontal overflow was found at either target viewport (`scrollWidth === clientWidth`).
- Browser console: no errors, warnings, or React overlay; only Vite connection/HMR messages and the React development-tools notice.

## Required fidelity surfaces

- Fonts and typography: the centered single-axis headline follows the selected reference. LINE Seed Sans KR remains the readable base; Nanum Pen Script is limited to the `기억` annotation and photo records so the page does not become decorative or noisy.
- Spacing and layout rhythm: header, promise, actions, and photo cluster share one centered axis with generous whitespace. The completed collage keeps the source's dominant center photo and quieter angled side photos while the initial state remains intentionally lean.
- Colors and visual tokens: the restrained light canvas, dark ink, mint accent, subtle border, paper white, and tape treatment align with the reference and remain legible in dark mode.
- Image quality and asset fidelity: all three visible records use Mapmory team-owned photography at stable crops. No generated image, placeholder, CSS-drawn asset, upload, or database interaction was introduced.
- Copy and content: the core Mapmory promise and real launch-notification state remain unchanged. The handwritten titles describe only experiences visible in the supplied photographs and do not imply unsupported functionality.
- Focused comparison: a separate crop was unnecessary because the combined 2880 × 1084 image keeps the headline annotation, both CTA treatments, all three photo crops, handwritten captions, release note, and fold cue readable at original density.

## Comparison history

1. First browser pass — blocked.
   - [P2] The handwritten `기억` duplicate sat directly on top of the base word and weakened headline legibility.
   - [P2] At 390 × 844, side-photo titles were truncated and mostly hidden behind the center card after reveal.
2. Fixes applied.
   - Reduced and raised the handwritten `기억` layer so it reads as an annotation while the base word stays intact.
   - Allowed mobile side captions to wrap and brought revealed side cards above the center card, making the newly added records readable while preserving the taped collage.
   - Made the reveal state cumulative so records do not disappear when the user scrolls back upward.
3. Final full-view comparison — no actionable P0/P1/P2 differences remain.
   - The implementation matches the selected option's centered hierarchy, whitespace, rounded floating header, compact CTA row, and dominant three-photo composition.
   - Emotional handwritten record titles and the one-by-one reveal are intentional user-requested extensions to the static source.

## Follow-up polish

- P3: the browser-owned scrollbar is visible in captures but is not part of the page design and does not affect layout width or interaction.
- The existing `react-globe.gl` large-chunk warning remains a performance follow-up; the globe loads and interacts successfully.

## Final result

final result: passed

---

# Handwritten memory first-paint timing QA — 2026-08-27

## Evidence

- Source visual truth: `design-qa/hero-centered-scroll/reference-selected-option-3.png` (1487 × 1058 px).
- Fresh desktop state: `design-qa/hero-centered-scroll/memory-first/desktop-initial-1440x1024.png` (1425 × 1013 px browser capture).
- Completed desktop photo state: `desktop-all-1440x1024.png` (1425 × 1013 px).
- Fresh mobile state: `mobile-initial-390x844.png` (375 × 811 px).
- Combined comparison: `comparison-reference-vs-memory-first.png` (2880 × 1084 px).
- CSS viewport and density: desktop 1440 × 1024, mobile 390 × 844, device scale 1. The comparison fits the reference and implementation proportionally into equal 1440 × 1024 panels without cropping.

## Timing and interaction checks

- `기억` is handwritten from the first rendered frame; it is no longer delayed by a scroll threshold or by the handwriting-reveal animation.
- The first 제주 photo is eagerly requested with high fetch priority and is also preloaded from the document head.
- On a fresh navigation, the handwritten title had opacity 1 while the 제주 image was complete with a non-zero natural width and the photo surface had opacity 1.
- Scrolling changes only the photo cluster: 합정 then 여수 are added while the handwritten title remains stable.
- Desktop and mobile had no horizontal overflow (`scrollWidth === clientWidth`).
- Browser console errors/warnings and React overlay: none.

## Required fidelity surfaces

- Fonts and typography: the headline keeps the selected centered scale and spacing. Only `기억` uses Nanum Pen Script; the accessible base word remains in the heading while the duplicate handwritten layer is presentation-only.
- Spacing and layout rhythm: hiding the printed visual word does not collapse its reserved headline width, so surrounding copy does not jump when the font loads.
- Colors and visual tokens: the handwritten word uses the existing mint accent and preserves light/dark theme contrast.
- Image quality and asset fidelity: the existing team-owned 제주 photograph is unchanged; preload and fetch-priority hints affect timing only.
- Copy and content: no landing copy or product capability changed.
- Focused comparison: the full combined image keeps the headline word, CTAs, photo captions, and complete collage readable, so no additional crop was needed.

## Comparison history

1. Initial timing review — blocked.
   - [P2] The headline handwriting and photo-caption animations used separate delays, which could make a slower device appear to finish `기억` before the photo experience arrived.
2. Fixes applied.
   - Removed the first-load animation dependency from the headline and rendered the handwritten `기억` immediately.
   - Added document preload plus eager/high-priority loading to the first 제주 photograph.
   - Kept the scroll interaction focused only on adding the two secondary photos.
3. Final desktop/mobile review — no actionable P0/P1/P2 issues remain. The headline and representative photo are present together on entry, and the later scroll still adds records progressively.

## Final result

final result: passed

---

# Natural editorial hero refactor QA — 2026-08-26

## 기준 및 산출물

- 시각적 기준 이미지: `C:\Users\YongSung\.codex\generated_images\01a03c3d-4d65-7953-a731-1efc7fc41422\exec-d7d4ad24-2ba2-45b8-8218-ca002f947ac4.png`
- 기준 목업·구현 비교 이미지: `design-qa/comparison-reference-vs-implementation.png`
- 데스크톱 구현: `design-qa/desktop-1440x1024.png`
- 데스크톱 지구본 체험: `design-qa/desktop-experience-1440x1024.png`
- 데스크톱 다크모드: `design-qa/desktop-dark-1440x1024.png`
- 대한민국 2단계 지도: `design-qa/desktop-korea-level2.png`
- 대한민국 3단계 제주 지도: `design-qa/desktop-korea-level3-jeju.png`
- 모바일 구현: `design-qa/mobile-390x844.png`
- 모바일 지구본 체험: `design-qa/mobile-experience-390x844.png`

## 검수 환경 및 화면 상태

- 로컬 Vite 개발 서버: `http://127.0.0.1:4173/`
- 뷰포트: 데스크톱 1440 × 1024, 모바일 390 × 844
- 기본 라이트 테마와 다크 테마
- 에디토리얼 첫 화면, 스크롤 유도, `01 · 세계` 섹션, 3D 지구본과 국가별 기억 패널
- 대한민국 17개 시·도 지도, 제주특별자치도 시·군·구 지도와 장소 기억 패널

## 주요 인터랙션 결과

- `지구본 돌려보기` CTA가 `#experience`로 이동하고 고정 헤더 아래에 제목과 조작 안내를 노출함: Passed
- 실제 지구본 캔버스를 드래그했을 때 시점이 회전함: Passed
- 중국 선택 시 `상하이 · 와이탄`, 일본 선택 시 `도쿄` 기록이 별도 기억 패널에서 열림: Passed
- 대한민국 상세지도 링크가 `#korea-detail`로 이동함: Passed
- 17개 시·도 → 제주 시·군·구 → 제주 기억 패널 → 대한민국 지도로 돌아가기 흐름: Passed
- 라이트/다크 테마 전환 후 텍스트, 사진, CTA, 다음 섹션이 깨지지 않음: Passed
- 모바일에서 텍스트, 사진, CTA, 지구본과 조작 안내가 잘리거나 겹치지 않으며 가로 오버플로가 없음: Passed

## 시각 비교

- 선택 목업의 왼쪽 정렬 메시지, 절제된 여행 사진, 민트색 단일 강조색, 첫 화면 아래로 다음 경험이 이어지는 편집 구조를 유지함.
- 기준 목업의 서울 골목 사진 대신 팀 소유 제주 사진을 사용해 실제 자산 원칙을 지킴.
- 목업의 장식용 대형 지구본을 첫 화면에 복제하지 않고 바로 다음 섹션에서 실제 조작 가능한 지구본과 별도 기억 패널을 노출해 제품 흐름을 정확히 유지함.
- 데스크톱에서는 첫 화면 아래에 `01 · 세계` 제목과 지구본 패널 상단이 보이며, 모바일에서는 스크롤 안내가 첫 화면 하단에 노출됨.

## 콘솔 및 오류

- 브라우저 콘솔 오류/경고: 없음
- Vite/React 오류 오버레이: 없음
- 빈 화면, 이미지 로드 실패, 대한민국 상세지도 JSON 로드 실패: 없음

## 발견 및 수정한 문제

- 이번 브라우저 검수에서 추가 P0/P1/P2 문제는 발견되지 않음.
- 기존 diff의 에디토리얼 2열 구조, 명시적인 지구본 CTA·조작 안내, 다음 섹션 유도, 태블릿·모바일 반응형이 실제 브라우저에서 정상 동작함을 확인함.
- 기준 목업과의 차이는 실제 팀 사진과 실제 제품 흐름을 우선하기 위한 의도된 차이이며, 존재하지 않는 기능이나 편집 흐름을 추가하지 않음.

## 최종 결과

**Passed**

---

# Floating header, photo-cluster hero, and add-to-map QA — 2026-08-27

## Evidence

- Source visual truth: `design-qa/polarsteps-layout/reference-polarsteps.png` (1140 × 670 px), supplied by the user as the structural reference for the floating header and center-emphasized product visual.
- Same-state implementation: `design-qa/polarsteps-layout/implementation-1140x670.png` (1125 × 662 px browser content capture), rendered with a 1140 × 670 CSS viewport override and device scale 1. The in-app browser reserves 15 px horizontally and 8 px vertically for its scroll surface.
- Combined comparison: `design-qa/polarsteps-layout/comparison-reference-vs-implementation.png` places the source and implementation side by side at identical 1140 × 670 content dimensions.
- Desktop implementation: `design-qa/polarsteps-layout/desktop-1440x1024-final.png` and `desktop-dark-1440x1024.png` (1425 × 1013 px captures), rendered with a 1440 × 1024 CSS viewport override.
- Mobile implementation: `design-qa/polarsteps-layout/mobile-390x844-final.png` (375 × 811 px capture), rendered with a 390 × 844 CSS viewport override.
- Korea empty, added, and browse states: `korea-empty-1440x1024.png`, `korea-added-1440x1024.png`, `korea-detail-1440x1024.png`, and `korea-mobile-empty-390x844.png`.
- Density normalization: the source is 1140 × 670 px and the implementation content capture is 1125 × 662 px at device scale 1. For the combined comparison only, the implementation was normalized by 1.013× horizontally and 1.012× vertically to the source's 1140 × 670 frame; this compensates only for the in-app browser's reserved scroll surface. The desktop and mobile captures are responsive-state evidence rather than pixel-fidelity inputs.
- Focused comparison: a separate crop was not needed because the equal-size combined image keeps the complete floating header, hero typography, primary visual, and CTAs readable at original density.

## States and interactions checked

- Floating rounded header in desktop/mobile and light/dark themes.
- Three taped team photographs in the first screen, with the main 제주 image centered and 합정·여수 images overlapping as secondary memories.
- Mobile headline, CTAs, captions, release note, photo cluster, and the `아래로 내려 앱 경험해보기` cue without clipping or overlap.
- Korea demo initial state: 0 / 17, no colored province, three team-owned example photo cards.
- `서울특별시 사진을 지도에 추가하기`: progress changes to 1 / 17 and 6%, 서울 is colored, and both a map hotspot and `기억 보기` control appear.
- `서울특별시 기억 보기`: opens the existing 서울 시·군·구 map and 합정·희옥 memory card.
- Browser reload: demo returns to 0 / 17, confirming that the add state is browser memory only. No upload, persistence API, or database write is used.
- Dark-theme toggle: floating header, photos, copy, and CTA contrast remain intact.
- Console: no error or warning entries; only Vite connection messages and the React development-tools notice. No React error overlay appeared.

## Required fidelity surfaces

- Fonts and typography: the existing LINE Seed Sans KR display hierarchy remains clear and editorial. At mobile width the headline was reduced to 34–38 px so all three photo memories and the next-experience cue remain within the first screen.
- Spacing and layout rhythm: the reference's detached rounded header and side-copy/center-visual composition are reflected without copying its phone mockup. The hero visual is allowed to expand beyond the ordinary text column, removing the earlier boxed-in feeling.
- Colors and visual tokens: Mapmory keeps its light canvas and mint accent instead of copying Polarsteps' dark navy/red palette. The header has a visible border, raised surface, blur, and shadow in both Mapmory themes.
- Image quality and asset fidelity: all three hero images and all Korea add-flow images are Mapmory team-owned photographs. No generated image, placeholder, fake upload surface, or CSS-drawn visual asset was introduced.
- Copy and content: the original Mapmory promise and launch-notification CTA remain. The Korea copy now describes the real product loop precisely: add a place-bearing photo, color its province, then reopen the saved memory.

## Comparison history

1. First browser comparison — blocked.
   - [P2] At 390 × 844, the enlarged three-photo cluster pushed the next-experience cue below the first viewport.
   - [P2] The horizontal example-photo tray exposed a thick native scrollbar that competed with the photo cards.
2. Fixes applied.
   - Tightened mobile headline/action spacing and reduced the photo cluster from 330 px to 270 px while preserving all three taped photographs.
   - Hid the native horizontal scrollbar while retaining touch scrolling and scroll snapping.
3. Final comparison — no actionable P0/P1/P2 differences remain.
   - `mobile-390x844-final.png` shows all primary copy, both CTAs, three photographs, and the next-experience cue without overlap.
   - The 1140 × 670 combined image shows the intended shared structure: a floating centered header, concise side copy, and a dominant center/right product visual. Palette, assets, and CTA differences are intentional Mapmory product constraints.
   - `korea-added-1440x1024.png` and `korea-detail-1440x1024.png` verify the complete add → color → browse loop.

## Follow-up polish

- P3: the three-photo tray intentionally shows the next card partially at mobile width to signal horizontal swiping; a short swipe hint could be user-tested later if discovery is weak.
- The existing large `react-globe.gl` production chunk warning remains a performance follow-up and does not block this visual or interaction QA.

## Final result

final result: passed

---

# Hero scroll performance QA — 2026-08-27

## 발견 원인

- 사진 공개 단계 상태가 최상위 `App`에 있어 첫 스크롤 때 3D 지구본과 이후 섹션까지 함께 다시 렌더링될 수 있었음.
- 숨겨진 대형 사진의 `filter: blur()` 전환이 모바일에서 추가 래스터라이징·합성 비용을 만들 수 있었음.
- 화면 아래의 WebGL 지구본 렌더링 루프가 첫 화면에서도 계속 실행되고 있었음.

## 수정 및 검증

- 사진 공개 상태와 스크롤 리스너를 독립 `HeroSection`으로 이동해 지구본과 대한민국 지도가 다시 렌더링되지 않도록 격리함.
- 사진 모션을 GPU 합성에 적합한 `opacity`와 `transform`만 사용하도록 변경하고, 보조 사진은 비동기 디코딩함.
- 지구본이 화면 밖에 있을 때 `pauseAnimation()`, 체험 구간 120px 안으로 들어오면 `resumeAnimation()`을 호출함.
- 390 × 844 실제 스크롤 입력에서 사진 단계가 `0 → 1 → 2`로 전환되고 가로 오버플로가 없음을 확인함.
- 지구본 구간으로 이동한 뒤 중국 선택과 `상하이 · 와이탄` 별도 기억 패널 전환이 정상 작동함.
- 브라우저 콘솔 오류/경고와 React 오류 오버레이: 없음.
- `npm run build`: passed. 기존 WebGL 대형 청크 경고만 유지됨.
- `npm test`: 10/10 passed.

## Final result

final result: passed

---

# Compact Korea add-to-map QA — 2026-08-27

## Evidence

- Previous Korea layout: `design-qa/desktop-korea-level2.png`
- Compact desktop implementation: `design-qa/korea-compact-v1/desktop-korea-1440x1024.png`
- Compact laptop implementation: `design-qa/korea-compact-v1/laptop-korea-1280x720.png`
- Compact mobile implementation: `design-qa/korea-compact-v1/mobile-korea-390x844.png`
- Verified viewports: desktop 1440 × 1024, laptop 1280 × 720, mobile 390 × 844

## User-feedback changes

- Removed the separate full-width photo tray that pushed the Korea map below the fold.
- Moved the three example-memory add controls into a compact dock directly beneath the Korea map in the same bounded panel.
- Reduced the province-map canvas height while reprojecting the same 17-region boundary data; no map area is cropped or replaced.
- Uses a uniform centered map projection at every height so compact laptop mode does not flatten or stretch the Korea boundary.
- Shortened the section explanation and reduced vertical spacing without changing the real add → color → browse flow.

## Results

- Desktop CTA/navigation arrival: section heading top 133 px; complete demo bottom 992 px inside the 1024 px viewport: Passed.
- Desktop map and add dock are visible together: map top 407 px to bottom 835 px; add dock bottom 973 px: Passed.
- Laptop-height mode keeps the complete section inside 720 px: demo top 234 px to bottom 702 px; map bottom 582 px; add dock bottom 690 px: Passed.
- Mobile arrival: heading top 123 px; map top 417 px to bottom 650 px; add dock bottom 819 px inside the 844 px viewport: Passed.
- Mobile horizontal add tray retains touch scrolling and shows the next example partially as an affordance: Passed.
- Adding the Seoul example changes progress to 1 / 17 and 6%, colors Seoul, exposes a map hotspot, and changes the control to `기억 보기`: Passed.
- Opening `기억 보기` retains the real level-3 flow: 서울특별시 district map, 희옥 memory card, and `대한민국 지도로 돌아가기`: Passed.
- State remains browser-memory-only; no API, upload, or database write was added: Passed.
- Desktop browser console errors/warnings and React error overlay: none.

## Final result

final result: passed

---

# Single-viewport map interaction QA — 2026-08-27

## Evidence

- Mobile globe: `design-qa/single-viewport-v1/world-globe-mobile-390x844.png`
- Mobile country memory: `design-qa/single-viewport-v1/world-memory-mobile-390x844.png`
- Mobile Korea automatic detail: `design-qa/single-viewport-v1/korea-detail-mobile-390x844.png`
- Laptop Korea automatic detail: `design-qa/single-viewport-v1/korea-detail-laptop-1280x720.png`
- Verified viewports: mobile 390 × 844, laptop 1280 × 720

## User-feedback changes

- On mobile and tablet, the world experience initially shows only the interactive globe. Selecting a recorded country replaces that same stage with its memory card; closing the card restores the rotatable globe at the same scroll position.
- Removed native tap highlight, text selection, and focus outline behavior from the WebGL canvas region so a country click does not appear as an unrelated blue browser selection.
- Adding a Korea example holds the level-2 map long enough to show its province filling, then leaves the next step to the user. Selecting `상세지역 보기` opens the real level-3 district map and corresponding memory in the same focused viewport.
- The transition remains local browser state only; no API, upload, or database write was added.

## Results

- Mobile globe-only state occupies one bounded stage (top 284 px, bottom 783 px, height 499 px) with the memory card hidden: Passed.
- Direct WebGL canvas click opens the China memory in-place; the stage remains 499 px tall and automatically aligns below the fixed header: Passed.
- `기억 닫기` restores the globe in the same stage and immediately allows globe interaction again: Passed.
- Mobile Korea user-opened detail fits inside one viewport after focus: demo top 88 px to bottom 747 px; district map top 216 px to bottom 736 px; no horizontal overflow: Passed.
- Laptop Korea user-opened detail fits inside 720 px: demo top 88 px to bottom 581 px; district map and record card top 201 px to bottom 571 px: Passed.
- Browser console and React error overlay: no errors or warnings; only Vite connection and React DevTools informational messages.

## Final result

final result: passed

---

# Motion pacing and user-controlled progression QA — 2026-08-27

## Evidence

- Direct 3D country selection in progress: `design-qa/motion-pacing-v1/world-direct-selection-mobile-390x844.png`
- Mobile world memory with visible return control: `design-qa/motion-pacing-v1/world-memory-mobile-390x844.png`
- Mobile Korea fill completed: `design-qa/motion-pacing-v1/korea-fill-ready-mobile-390x844.png`
- Mobile Korea detail opened by the user: `design-qa/motion-pacing-v1/korea-detail-user-opened-mobile-390x844.png`
- Laptop Korea fill completed: `design-qa/motion-pacing-v1/korea-fill-ready-laptop-1280x720.png`
- Verified viewports: mobile 390 × 844, laptop 1280 × 720

## Flow and timing results

1. Direct WebGL country click changes the country to gold and raises its polygon for 1,050 ms while the globe stays visible: Passed.
2. The memory panel waits until the globe motion finishes, then opens after 1,170 ms total. At 520 ms the globe is still visible and the memory panel remains hidden: Passed.
3. The mobile memory header now exposes a 112 × 38 px `지구본으로` close control. Closing restores the globe; the separate `대한민국 상세지도 체험하기` link advances only when the user chooses it: Passed.
4. The memory-to-Korea link targets the bounded map demo rather than the section heading, aligning the complete interaction surface below the fixed header: Passed.
5. Adding Seoul keeps level 2 visible during a 1,500 ms fill animation, disables competing add controls, and does not enter the district map automatically: Passed.
6. After the fill finishes, the mobile level-2 demo is aligned from 88 px to 739 px within the 844 px viewport; the enabled action reads `상세지역 보기`: Passed.
7. Selecting `상세지역 보기` is the only action that enters level 3. The focused mobile detail demo remains within 88 px to 747 px with no horizontal overflow: Passed.
8. Laptop 1280 × 720 keeps the completed level-2 demo within 88 px to 555 px: Passed.
9. No API, upload, persistence, or database write was introduced: Passed.

## Accessibility and limits

- Selection and fill states expose `aria-busy`; transition controls are disabled while their visual result is in progress.
- Visual screenshots verify layout, hierarchy, and visible focus targets. They do not replace testing with a physical phone's browser toolbar, touch latency, or assistive technology.

## Final result

final result: passed

---

# Lean content and typography QA — 2026-08-27

## Audit scope and evidence

- User goal: understand Mapmory by trying the globe and Korea map, then register for launch notification if interested.
- Desktop before: `design-qa/lean-content-font-v1/01-desktop-hero-1280x720.png`
- Desktop after: `design-qa/lean-content-font-v1/10-desktop-hero-after-1280x720.png`
- Mobile before: `design-qa/lean-content-font-v1/05-mobile-hero-390x844.png`
- Mobile after: `design-qa/lean-content-font-v1/08-mobile-hero-after-390x844.png`
- Mobile globe before: `design-qa/lean-content-font-v1/06-mobile-world-390x844.png`
- Mobile globe after: `design-qa/lean-content-font-v1/09-mobile-world-after-390x844.png`
- Desktop globe after: `design-qa/lean-content-font-v1/11-desktop-world-after-1280x720.png`
- Desktop Korea after: `design-qa/lean-content-font-v1/12-desktop-korea-after-1280x720.png`
- Verified viewports: desktop 1280 × 720, mobile 390 × 844

## Numbered flow health

1. Hero — Healthy: one primary action (`지구본 돌려보기`), one availability note, and one scroll cue remain. The duplicate hero waitlist CTA and decorative English archive label were removed.
2. World globe — Healthy: the section heading, in-globe manipulation instruction, and recorded-country choices remain. The repeated explanatory paragraph, color legend, and `나라 선택` pill were removed.
3. Korea map — Healthy: the progression label is now `02 · 대한민국`; the repeated side explanation was removed. The real add → fill → open-memory interaction is unchanged.
4. Flow explanation — Removed: the three-card `앱 흐름` section repeated the two interactions immediately above it. Its header-navigation item was removed with it.
5. Launch notification — Healthy: the final conversion section, email input, consent, age confirmation, and retention disclosure remain. Only the decorative English label was removed.

## Typography decision

- Primary interface family: LINE Seed Sans KR Regular and Bold, self-hosted from the official LINE Seed distribution.
- Record accent: Nanum Pen Script only for the handwritten `기억` layer and photo-record captions.
- Removed Noto Sans KR and Be Vietnam Pro from the page font stack, reducing the interface from four visible font voices to two.
- Rationale: LINE Seed keeps Korean and Latin text in one friendly geometric voice and is designed for service readability; the handwriting remains a semantic signal for personal records rather than general decoration.
- License/source: `https://seed.line.me/`, SIL Open Font License 1.1. Local attribution is recorded in `public/assets/fonts/README.md`.

## Measured result

- Desktop document height reduced from 3,930 px to 3,278 px at 1280 × 720: 652 px removed.
- Mobile hero retains the full first-screen composition within 390 × 844 and has no horizontal overflow.
- Mobile globe stage begins at 221 px instead of 284 px and ends at 716 px, leaving the complete interactive surface inside one viewport.
- Desktop and mobile use `LINE Seed Sans KR` for both body and headings; the removed journey route no longer exists in the DOM.
- No product capability, data source, API behavior, waitlist requirement, or database write was changed.

## Accessibility and evidence limits

- The remaining heading order, explicit button labels, canvas region labels, focus styles, consent labels, and waitlist status messaging are preserved.
- Screenshot review confirms hierarchy and reflow, but physical-device font rasterization, touch latency, screen-reader announcements, and zoom behavior still require device/assistive-technology testing.

## Final result

final result: passed
