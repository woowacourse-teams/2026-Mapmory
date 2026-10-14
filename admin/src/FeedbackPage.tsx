import { useEffect, useRef, useState } from "react";
import {
  getAdminFeedback, getAdminFeedbackDetail, updateAdminFeedbackStatus,
  type AdminFeedback, type AdminFeedbackCategory, type AdminFeedbackList,
  type AdminFeedbackPlatform, type AdminFeedbackStatus,
} from "./api/adminApi";
import { formatFeedbackDate, validateFeedbackDateRange } from "./utils/feedback";

const statuses: AdminFeedbackStatus[] = ["UNCONFIRMED", "IN_PROGRESS", "COMPLETED", "ON_HOLD", "NOT_IMPLEMENTED"];
const statusLabels: Record<AdminFeedbackStatus, string> = {
  UNCONFIRMED: "미확인", IN_PROGRESS: "반영 중", COMPLETED: "반영 완료", ON_HOLD: "보류", NOT_IMPLEMENTED: "미반영",
};
const categoryLabels: Record<AdminFeedbackCategory, string> = {
  BUG_REPORT: "오류 제보", FEATURE_REQUEST: "기능 제안", USABILITY: "사용성 의견", OTHER: "기타",
};
type Filters = { category: AdminFeedbackCategory | ""; platform: AdminFeedbackPlatform | ""; from: string; to: string };
const emptyFilters: Filters = { category: "", platform: "", from: "", to: "" };
const errorMessage = (cause: unknown, fallback: string) => cause instanceof Error ? cause.message : fallback;

export default function FeedbackPage({ onNotice }: { onNotice: (message: string) => void }) {
  const [status, setStatus] = useState<AdminFeedbackStatus | "">("");
  const [filters, setFilters] = useState<Filters>(emptyFilters);
  const [draft, setDraft] = useState<Filters>(emptyFilters);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [filterError, setFilterError] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [data, setData] = useState<AdminFeedbackList | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retryKey, setRetryKey] = useState(0);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [savingId, setSavingId] = useState<number | null>(null);
  const saving = useRef(false);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setData(null);
    setError("");
    getAdminFeedback({ ...filters, status }, page, 20, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return;
        if (page > 0 && page >= result.totalPages) {
          setPage(Math.max(0, result.totalPages - 1));
          return;
        }
        setData(result);
      })
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setError(errorMessage(cause, "서비스 의견을 불러오지 못했습니다."));
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [status, filters, page, retryKey]);

  const feedback = (data?.feedback ?? []).filter((item) => {
    const normalized = query.trim().toLowerCase();
    return `${item.feedbackId} ${categoryLabels[item.category]} ${item.content} ${item.platform}`.toLowerCase().includes(normalized);
  });
  const changeStatus = async (feedbackId: number, nextStatus: AdminFeedbackStatus) => {
    if (saving.current) return null;
    saving.current = true;
    setSavingId(feedbackId);
    try {
      const updated = await updateAdminFeedbackStatus(feedbackId, nextStatus);
      setRetryKey((value) => value + 1);
      onNotice("처리 상태를 변경했습니다.");
      return updated;
    } catch (cause) {
      onNotice(errorMessage(cause, "처리 상태를 변경하지 못했습니다."));
      return null;
    } finally {
      saving.current = false;
      setSavingId(null);
    }
  };
  const applyFilters = () => {
    const message = validateFeedbackDateRange(draft.from, draft.to);
    setFilterError(message);
    if (message) return;
    setFilters({ ...draft });
    setPage(0);
    setQuery("");
  };
  const resetFilters = () => {
    setDraft(emptyFilters);
    setFilters(emptyFilters);
    setFilterError("");
    setPage(0);
    setQuery("");
  };

  return <div className="page-content list-page">
    <div className="page-heading-row"><div><div className="date-eyebrow">VOICE OF MAPMORY</div><h1>서비스 의견</h1><p>접수된 의견을 확인하고 서비스 개선 상태를 관리합니다.</p></div><button className="secondary-button" onClick={() => window.print()}>목록 인쇄</button></div>
    <div className="feedback-overview"><div className="feedback-overview-copy"><div><strong>조회된 의견 <b>{data ? `${data.totalElements.toLocaleString("ko-KR")}건` : "—"}</b></strong><p>의견은 서비스 개선을 위해 내부에서만 관리됩니다.</p></div></div><span className="overview-date">접수일 · 한국 시간</span></div>
    <div className="panel table-panel feedback-table-panel">
      <div className="feedback-toolbar"><div className="feedback-tabs">
        {(["", ...statuses] as const).map((value) => <button key={value} className={status === value ? "feedback-tab active" : "feedback-tab"} disabled={savingId !== null} onClick={() => { setStatus(value); setPage(0); setQuery(""); }}>{value ? statusLabels[value] : "전체"}{status === value && data && <span>{data.totalElements.toLocaleString("ko-KR")}</span>}</button>)}
      </div><div className="table-actions"><div className="search-field"><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="현재 페이지에서 검색" aria-label="현재 페이지 의견 검색"/></div><button className="filter-button" aria-expanded={filtersOpen} aria-controls="feedback-filters" onClick={() => setFiltersOpen((value) => !value)}>상세 필터</button></div></div>
      {filtersOpen && <form id="feedback-filters" className="feedback-filters" onSubmit={(event) => { event.preventDefault(); applyFilters(); }}>
        <label>유형<select aria-label="유형" value={draft.category} onChange={(event) => setDraft((value) => ({ ...value, category: event.target.value as Filters["category"] }))}><option value="">전체 유형</option>{Object.entries(categoryLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label>
        <label>OS<select aria-label="OS" value={draft.platform} onChange={(event) => setDraft((value) => ({ ...value, platform: event.target.value as Filters["platform"] }))}><option value="">전체 OS</option><option value="IOS">iOS</option><option value="ANDROID">Android</option></select></label>
        <label>접수 시작일<input type="date" value={draft.from} onChange={(event) => setDraft((value) => ({ ...value, from: event.target.value }))}/></label>
        <label>접수 종료일<input type="date" value={draft.to} onChange={(event) => setDraft((value) => ({ ...value, to: event.target.value }))}/></label>
        <button className="secondary-button" type="submit" disabled={savingId !== null}>적용</button><button className="secondary-button" type="button" onClick={resetFilters} disabled={savingId !== null}>초기화</button>
        {filterError && <p className="feedback-filter-error" role="alert">{filterError}</p>}
      </form>}
      {error && <div className="dashboard-error feedback-error" role="alert"><span>{error}</span><button className="secondary-button" onClick={() => setRetryKey((value) => value + 1)}>다시 시도</button></div>}
      {loading && <div className="dashboard-loading" role="status">서비스 의견을 불러오는 중입니다.</div>}
      <div className="table-scroll"><table className="feedback-table"><thead><tr><th>접수일 (KST)</th><th>유형</th><th className="content-column">의견 내용</th><th>OS</th><th>처리 상태</th><th>상세</th></tr></thead><tbody>{!loading && !error && feedback.map((item) => <tr key={item.feedbackId}>
        <td><span className="feedback-date">{formatFeedbackDate(item.createdAt)}</span><small>FB-{String(item.feedbackId).padStart(4, "0")}</small></td>
        <td><span className={`category-tag ${item.category === "BUG_REPORT" ? "bug-category" : item.category === "FEATURE_REQUEST" ? "feature-category" : "other-category"}`}>{categoryLabels[item.category]}</span></td>
        <td className="feedback-content-cell">{item.content}</td><td><span className="os-label">{item.platform === "IOS" ? "iOS" : "Android"}</span></td>
        <td><StatusSelect feedback={item} disabled={savingId !== null} onChange={(next) => { void changeStatus(item.feedbackId, next); }}/>{savingId === item.feedbackId && <small className="feedback-saving" role="status">저장 중</small>}</td>
        <td><button className="row-arrow" disabled={savingId !== null} onClick={() => setSelectedId(item.feedbackId)} aria-label={`FB-${item.feedbackId} 의견 상세`}>›</button></td>
      </tr>)}</tbody></table>{!loading && !error && feedback.length === 0 && <div className="empty-state">{query.trim() ? "현재 페이지에 검색 결과가 없습니다." : "접수된 의견이 없습니다."}</div>}</div>
      <div className="table-footer"><span>조회 총 <strong>{data?.totalElements.toLocaleString("ko-KR") ?? "—"}</strong>건{query.trim() && data ? ` · 현재 페이지 검색 ${feedback.length}건` : ""}</span><div className="pagination"><button disabled={page === 0 || loading || savingId !== null} onClick={() => setPage((value) => value - 1)}>이전</button><span className="current-page">{data ? `${data.totalPages === 0 ? 0 : data.page + 1} / ${data.totalPages}` : "—"}</span><button disabled={!data || page + 1 >= data.totalPages || loading || savingId !== null} onClick={() => setPage((value) => value + 1)}>다음</button></div></div>
    </div>
    <div className="feedback-privacy-note"><p><strong>의견은 내부 개선 목적으로만 사용됩니다.</strong> 제출한 사용자에게 개별 답변은 제공하지 않습니다.</p></div>
    {selectedId !== null && <FeedbackDrawer feedbackId={selectedId} onClose={() => setSelectedId(null)} saving={savingId !== null} onStatusChange={changeStatus}/>}
  </div>;
}

function StatusSelect({ feedback, disabled, onChange }: { feedback: AdminFeedback; disabled: boolean; onChange: (status: AdminFeedbackStatus) => void }) {
  return <select className={`status-select status-${statusLabels[feedback.status].replaceAll(" ", "")}`} value={feedback.status} disabled={disabled} onChange={(event) => { const next = event.target.value as AdminFeedbackStatus; if (next !== feedback.status) onChange(next); }} aria-label={`FB-${feedback.feedbackId} 처리 상태`}>
    {statuses.map((value) => <option key={value} value={value}>{statusLabels[value]}</option>)}
  </select>;
}

function FeedbackDrawer({ feedbackId, onClose, saving, onStatusChange }: {
  feedbackId: number; onClose: () => void; saving: boolean;
  onStatusChange: (id: number, status: AdminFeedbackStatus) => Promise<AdminFeedback | null>;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [detail, setDetail] = useState<AdminFeedback | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    const element = dialog.current;
    element?.showModal();
    return () => element?.close();
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    setDetail(null);
    getAdminFeedbackDetail(feedbackId, controller.signal)
      .then((result) => { if (!controller.signal.aborted) setDetail(result); })
      .catch((cause: unknown) => { if (!controller.signal.aborted) setError(errorMessage(cause, "의견 상세를 불러오지 못했습니다.")); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [feedbackId, retryKey]);

  return <dialog ref={dialog} className="member-drawer feedback-drawer" aria-labelledby="feedback-detail-title" onCancel={(event) => { event.preventDefault(); onClose(); }} onClick={(event) => { const bounds = event.currentTarget.getBoundingClientRect(); if (event.target === event.currentTarget && (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom)) onClose(); }}>
    <div className="drawer-header"><div><span className="date-eyebrow">SERVICE FEEDBACK</span><h2 id="feedback-detail-title">의견 상세 · FB-{feedbackId}</h2></div><button className="icon-button" onClick={onClose} aria-label="의견 상세 닫기">×</button></div>
    {loading && <div className="dashboard-loading" role="status">의견 상세를 불러오는 중입니다.</div>}
    {error && <div className="dashboard-error" role="alert"><p>{error}</p><button className="secondary-button" onClick={() => setRetryKey((value) => value + 1)}>다시 시도</button></div>}
    {detail && <><div className="detail-section"><div className="detail-row"><span>접수일 (한국 시간)</span><strong>{formatFeedbackDate(detail.createdAt)}</strong></div><div className="detail-row"><span>유형</span><strong>{categoryLabels[detail.category]}</strong></div><div className="detail-row"><span>OS</span><strong>{detail.platform === "IOS" ? "iOS" : "Android"}</strong></div><div className="detail-row"><span>처리 상태</span><StatusSelect feedback={detail} disabled={saving} onChange={async (status) => { const result = await onStatusChange(feedbackId, status); if (result) setDetail(result); }}/></div></div><div className="detail-section"><h3>의견 내용</h3><p className="feedback-detail-content">{detail.content}</p></div></>}
    <button className="secondary-button drawer-close" onClick={onClose}>닫기</button>
  </dialog>;
}
