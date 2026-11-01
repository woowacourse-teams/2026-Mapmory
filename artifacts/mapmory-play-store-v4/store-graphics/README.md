# Mapmory Play Store graphics

Combined review sheet: [preview-all.png](../preview-all.png). This contact sheet is for review, not a store upload asset.

Play Console phone uploads are in `../play-console-phone/`. They are 1080 × 1920 JPEGs; the full 1080 × 2400 store graphic is scaled proportionally into the 9:16 canvas so no headline or app content is cropped.

The seven store graphics are 1080 × 2400 and show the map, travel journal, statistics, album detail, fullscreen photo, share preview, and new-record flow. The screen layouts are based on Mapmory app captures. In screens 02, 04, 05, and 06, photo pixels and the visible sample record titles/locations were updated together so each caption identifies the displayed photo. The USA West record consistently uses Bryce Canyon, Antelope Canyon, and Las Vegas photos; the journal cards identify their Tokyo and Jeju photos. The Korean text keeps the app's existing typeface and layout.

All replacement photos are genuine Mapmory development-team photos already used by the landing page. Sources: `team-usa-bryce-canyon.jpg`, `team-usa-antelope-canyon.jpg`, `team-usa-las-vegas-fountain.jpg`, `team-tokyo-street.jpeg`, and `team-jeju-coast.jpg` in `landing/public/assets/`. Their titles, locations, and photo credits are recorded in `landing/src/App.jsx`. The displayed `2025` year for the USA West trip comes from the landing-page journey record. The Tokyo and Jeju card dates remain sample dates from the captured demo UI.

The base captures came from a temporary emulator running the debug build with an in-memory app container. No account, backend, or personal gallery content was used. `08-photo-picker.png` is the system photo picker; `09-settings.png` is an additional app capture. The record-edit route remained on its photo-library loading state, so it is not presented as a completed editor screen. The SVGs are editable and embed the app captures and app icon as image layers.
