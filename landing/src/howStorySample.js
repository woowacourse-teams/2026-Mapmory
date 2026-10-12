// Development only: sample measured numbers, so the stats band can be looked at before real ones exist. HowStory
// reads them only when import.meta.env.DEV is true and the URL has ?how-sample; a production build drops this
// module (a test checks that none of these strings reach the bundle). They are not measurements.
// A plain object literal (no calls), so the bundler can drop the module when nothing uses it.
export const HOW_STORY_SAMPLE_METRICS = {
  scanSeconds: 6.2,
  scanLibraryCount: 14213,
  scanSource: "테스트 기기 · 처음 찾을 때 · 2026년 11월 내부 측정",
  taps: 5,
  tapsSource: "테스트 빌드 · 2026년 11월 내부 측정",
};
