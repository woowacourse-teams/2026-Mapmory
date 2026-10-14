import test, { afterEach } from "node:test";
import assert from "node:assert/strict";
import { formatFeedbackDate, validateFeedbackDateRange } from "../src/utils/feedback.ts";

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });
const freshApi = () => import(`../src/api/adminApi.ts?test=${crypto.randomUUID()}`);
const feedback = { feedbackId: 10, category: "BUG_REPORT", content: "의견", platform: "IOS", status: "UNCONFIRMED", createdAt: "2026-10-12T16:21:48" };
const json = (data, status = 200) => new Response(JSON.stringify({ data }), { status, headers: { "Content-Type": "application/json" } });

test("목록 필터와 페이지를 서버에 전달하고 세션 쿠키를 포함한다", async () => {
  globalThis.fetch = async (url, options) => {
    const query = new URL(url).searchParams;
    assert.deepEqual(Object.fromEntries(query), { page: "1", size: "20", status: "ON_HOLD", category: "BUG_REPORT", platform: "IOS", from: "2026-10-13", to: "2026-10-14" });
    assert.equal(options.credentials, "include");
    return json({ feedback: [feedback], page: 1, size: 20, totalElements: 21, totalPages: 2 });
  };
  const api = await freshApi();
  const result = await api.getAdminFeedback({ status: "ON_HOLD", category: "BUG_REPORT", platform: "IOS", from: "2026-10-13", to: "2026-10-14" }, 1);
  assert.equal(result.feedback[0].feedbackId, 10);
});

test("빈 필터는 요청에서 생략하고 취소 신호를 전달한다", async () => {
  const controller = new AbortController();
  globalThis.fetch = async (url, options) => {
    assert.equal(new URL(url).search, "?page=0&size=20");
    assert.equal(options.signal, controller.signal);
    return json({ feedback: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  };
  const api = await freshApi();
  await api.getAdminFeedback({ status: "", category: "", platform: "", from: "", to: "" }, 0, 20, controller.signal);
});

test("상세 조회는 선택한 의견 ID로 호출한다", async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(new URL(url).pathname, "/api/v1/admin/service-feedback/10");
    assert.equal(options.credentials, "include");
    return json(feedback);
  };
  assert.equal((await (await freshApi()).getAdminFeedbackDetail(10)).feedbackId, 10);
});

test("상태 변경은 CSRF 헤더와 enum 문자열을 포함한다", async () => {
  let count = 0;
  globalThis.fetch = async (url, options) => {
    count++;
    if (new URL(url).pathname.endsWith("/csrf")) return json({ headerName: "X-CSRF-TOKEN", token: "test-token" });
    assert.equal(options.method, "PATCH");
    assert.equal(options.credentials, "include");
    assert.equal(options.headers.get("X-CSRF-TOKEN"), "test-token");
    assert.deepEqual(JSON.parse(options.body), { status: "COMPLETED" });
    return json({ ...feedback, status: "COMPLETED" });
  };
  const result = await (await freshApi()).updateAdminFeedbackStatus(10, "COMPLETED");
  assert.equal(result.status, "COMPLETED");
  assert.equal(count, 2);
});

test("서버가 상태 변경을 거부하면 성공 결과로 처리하지 않는다", async () => {
  globalThis.fetch = async (url) => new URL(url).pathname.endsWith("/csrf")
    ? json({ headerName: "X-CSRF-TOKEN", token: "test-token" })
    : new Response(JSON.stringify({ detail: "관리자 로그인이 필요합니다." }), { status: 401 });
  const api = await freshApi();
  await assert.rejects(() => api.updateAdminFeedbackStatus(10, "COMPLETED"), /관리자 로그인이 필요합니다/);
});

test("응답 본문이 없으면 빈 목록으로 오인하지 않는다", async () => {
  globalThis.fetch = async () => new Response("not-json", { status: 200 });
  const api = await freshApi();
  await assert.rejects(() => api.getAdminFeedback({}, 0), /서버 응답/);
});

test("UTC 접수 시각을 한국 날짜와 시간으로 표시한다", () => {
  assert.equal(formatFeedbackDate("2026-10-12T16:21:48"), "2026.10.13 01:21");
  assert.equal(formatFeedbackDate("2026-10-12T16:21:48Z"), "2026.10.13 01:21");
  assert.equal(formatFeedbackDate("2026-10-13T01:21:48+09:00"), "2026.10.13 01:21");
  assert.equal(formatFeedbackDate("2026-10-13T15:00:00Z"), "2026.10.14 00:00");
  assert.equal(formatFeedbackDate("invalid"), "—");
});

test("시작일 이후의 종료일과 단일 날짜를 허용한다", () => {
  assert.equal(validateFeedbackDateRange("2026-10-14", "2026-10-13"), "시작일은 종료일보다 늦을 수 없습니다.");
  assert.equal(validateFeedbackDateRange("2026-10-13", "2026-10-13"), "");
  assert.equal(validateFeedbackDateRange("", "2026-10-13"), "");
});
