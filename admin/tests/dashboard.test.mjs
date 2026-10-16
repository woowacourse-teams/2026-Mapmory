import test from "node:test";
import assert from "node:assert/strict";
import { dashboardDays, getDashboardRange } from "../src/utils/dashboard.ts";

const now = new Date("2026-10-16T03:00:00Z");
const starts = { 1: "2026-10-16", 7: "2026-10-10", 30: "2026-09-17", 45: "2026-09-02", 60: "2026-08-18" };
for (const days of dashboardDays) {
  test(`최근 ${days}일은 한국 오늘을 포함해 정확히 ${days}개 날짜를 선택한다`, () => {
    const range = getDashboardRange(days, now);
    assert.deepEqual(range, { from: starts[days], to: "2026-10-16" });
    assert.equal((Date.parse(range.to) - Date.parse(range.from)) / 86400000 + 1, days);
  });
}

test("UTC 날짜가 달라도 1일 버튼은 한국 오늘 하루를 선택한다", () => {
  assert.deepEqual(getDashboardRange(1, new Date("2026-10-15T15:30:00Z")), {
    from: "2026-10-16", to: "2026-10-16",
  });
});

test("연도가 바뀌어도 선택한 날짜 수를 유지한다", () => {
  assert.deepEqual(getDashboardRange(7, new Date("2025-12-31T15:00:00Z")), {
    from: "2025-12-26", to: "2026-01-01",
  });
});
