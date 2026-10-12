import { useEffect, useMemo, useState } from "react";
import { adminApiRequest, getCurrentAdmin, loginAdmin, logoutAdmin } from "./api/adminApi";

type Page = "dashboard" | "members" | "feedback";
type FeedbackStatus = "미확인" | "반영 중" | "반영 완료" | "보류" | "미반영";
type Feedback = {
  id: string;
  category: string;
  content: string;
  platform: "iOS" | "Android";
  createdAt: string;
  status: FeedbackStatus;
};
type Member = {
  id: string;
  name: string;
  provider: "KAKAO" | "GUEST";
  joinedAt: string;
  records: number;
  refresh: "정상" | "갱신 실패";
  lastActive: string;
};
type DashboardSummary = {
  period: { from: string; to: string };
  members: { total: number; guest: number; kakao: number; google: number; newInPeriod: number };
  travelRecords: { total: number; createdInPeriod: number };
  photos: { total: number };
};

const feedbackStatuses: FeedbackStatus[] = ["미확인", "반영 중", "반영 완료", "보류", "미반영"];

const initialFeedback: Feedback[] = [
  { id: "FB-0248", category: "기능 제안", content: "여행 기록을 여러 장 한 번에 등록할 수 있으면 좋겠어요.", platform: "iOS", createdAt: "10.08 14:32", status: "미확인" },
  { id: "FB-0247", category: "사용성·불편", content: "지도에서 지역을 확대할 때 어디까지 확대됐는지 알기 어려워요.", platform: "Android", createdAt: "10.08 12:18", status: "반영 중" },
  { id: "FB-0246", category: "오류 제보", content: "사진 추천에서 날짜가 하루 전으로 표시되는 경우가 있어요.", platform: "iOS", createdAt: "10.07 18:51", status: "미확인" },
  { id: "FB-0245", category: "기능 제안", content: "기록에 같이 간 사람을 메모할 수 있으면 좋겠습니다.", platform: "Android", createdAt: "10.07 16:07", status: "보류" },
  { id: "FB-0244", category: "사용성·불편", content: "기록 목록에서 최신순과 오래된 순을 바꿀 수 있으면 좋겠어요.", platform: "iOS", createdAt: "10.07 09:26", status: "반영 완료" },
  { id: "FB-0243", category: "기타", content: "앱을 잘 사용하고 있어요. 지도 색감이 정말 예뻐요!", platform: "Android", createdAt: "10.06 21:43", status: "미확인" },
];

const members: Member[] = [
  { id: "M-10842", name: "김지우", provider: "KAKAO", joinedAt: "2026.10.08", records: 12, refresh: "정상", lastActive: "방금 전" },
  { id: "M-10841", name: "박서연", provider: "KAKAO", joinedAt: "2026.10.08", records: 4, refresh: "정상", lastActive: "12분 전" },
  { id: "M-10840", name: "게스트 사용자", provider: "GUEST", joinedAt: "2026.10.07", records: 2, refresh: "갱신 실패", lastActive: "어제" },
  { id: "M-10839", name: "이도윤", provider: "KAKAO", joinedAt: "2026.10.07", records: 28, refresh: "정상", lastActive: "어제" },
  { id: "M-10838", name: "게스트 사용자", provider: "GUEST", joinedAt: "2026.10.06", records: 0, refresh: "정상", lastActive: "10.06" },
  { id: "M-10837", name: "최유진", provider: "KAKAO", joinedAt: "2026.10.06", records: 7, refresh: "정상", lastActive: "10.06" },
  { id: "M-10836", name: "정민준", provider: "KAKAO", joinedAt: "2026.10.05", records: 19, refresh: "정상", lastActive: "10.05" },
];

function getDashboardRange(period: string) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());
  const value = (type: string) => parts.find((part) => part.type === type)?.value ?? "0";
  const year = Number(value("year"));
  const month = Number(value("month"));
  const day = Number(value("day"));
  const to = `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
  const fromDate = new Date(Date.UTC(year, month - 1, day));
  fromDate.setUTCDate(fromDate.getUTCDate() - (period === "최근 30일" ? 29 : 6));
  return { from: fromDate.toISOString().slice(0, 10), to };
}

function formatPeriodDate(date: string) {
  const [, month, day] = date.split("-");
  return `${month}.${day}`;
}

function Icon({ name, size = 18 }: { name: string; size?: number }) {
  const common = { width: size, height: size, viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: 1.7, strokeLinecap: "round" as const, strokeLinejoin: "round" as const, "aria-hidden": true as const };
  const paths: Record<string, React.ReactNode> = {
    grid: <><rect x="3.5" y="3.5" width="7" height="7" rx="1.5"/><rect x="13.5" y="3.5" width="7" height="7" rx="1.5"/><rect x="3.5" y="13.5" width="7" height="7" rx="1.5"/><rect x="13.5" y="13.5" width="7" height="7" rx="1.5"/></>,
    users: <><path d="M16 20v-1.8a3.2 3.2 0 0 0-3.2-3.2H6.2A3.2 3.2 0 0 0 3 18.2V20"/><circle cx="9.5" cy="7" r="3.5"/><path d="M17 11a3.5 3.5 0 1 0-.2-7M17 15h.8a3.2 3.2 0 0 1 3.2 3.2V20"/></>,
    chat: <><path d="M20 11.5a7.5 7.5 0 0 1-7.5 7.5H5l-2 2v-8.5a7.5 7.5 0 1 1 15 0Z"/><path d="M8 11.5h.01M12 11.5h.01M16 11.5h.01"/></>,
    search: <><circle cx="10.8" cy="10.8" r="6.8"/><path d="m16 16 4.5 4.5"/></>,
    bell: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/></>,
    chevron: <path d="m9 18 6-6-6-6"/>,
    download: <><path d="M12 3v12m0 0 4-4m-4 4-4-4"/><path d="M4 17v3h16v-3"/></>,
    arrow: <><path d="M7 17 17 7M7 7h10v10"/></>,
    calendar: <><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18"/></>,
    image: <><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="m21 15-5-5L5 21"/></>,
    more: <><circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/></>,
    logout: <><path d="M10 17l5-5-5-5M15 12H3"/><path d="M12 3h6a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3h-6"/></>,
    close: <path d="m6 6 12 12M18 6 6 18"/>,
    filter: <><path d="M4 6h16M7 12h10m-7 6h4"/><circle cx="8" cy="6" r="1" fill="currentColor"/><circle cx="15" cy="12" r="1" fill="currentColor"/></>,
  };
  return <svg {...common}>{paths[name] ?? paths.grid}</svg>;
}

function App() {
  const [authStatus, setAuthStatus] = useState<"checking" | "authenticated" | "anonymous">("checking");
  const [adminUsername, setAdminUsername] = useState("");
  const [authError, setAuthError] = useState("");
  const [page, setPage] = useState<Page>("dashboard");
  const [period, setPeriod] = useState("최근 7일");
  const [feedback, setFeedback] = useState(initialFeedback);
  const [feedbackFilter, setFeedbackFilter] = useState("전체");
  const [feedbackQuery, setFeedbackQuery] = useState("");
  const [memberQuery, setMemberQuery] = useState("");
  const [selectedMember, setSelectedMember] = useState<Member | null>(null);
  const [notice, setNotice] = useState("");

  useEffect(() => {
    let active = true;
    getCurrentAdmin()
      .then((admin) => {
        if (!active) return;
        setAdminUsername(admin.username);
        setAuthStatus("authenticated");
      })
      .catch(() => {
        if (active) setAuthStatus("anonymous");
      });

    return () => {
      active = false;
    };
  }, []);

  const filteredMembers = useMemo(() => members.filter((member) =>
    `${member.id} ${member.name} ${member.provider}`.toLowerCase().includes(memberQuery.toLowerCase()),
  ), [memberQuery]);
  const filteredFeedback = feedback.filter((item) => {
    const matchesStatus = feedbackFilter === "전체" || item.status === feedbackFilter;
    const matchesQuery = `${item.id} ${item.category} ${item.content} ${item.platform}`.toLowerCase().includes(feedbackQuery.toLowerCase());
    return matchesStatus && matchesQuery;
  });

  const showNotice = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 2600);
  };

  const handleLogin = async (username: string, password: string) => {
    setAuthError("");
    try {
      const admin = await loginAdmin(username, password);
      setAdminUsername(admin.username);
      setAuthStatus("authenticated");
    } catch (cause) {
      setAuthError(cause instanceof Error ? cause.message : "로그인에 실패했습니다.");
    }
  };

  const handleLogout = async () => {
    try {
      await logoutAdmin();
      setAdminUsername("");
      setAuthStatus("anonymous");
    } catch (cause) {
      showNotice(cause instanceof Error ? cause.message : "로그아웃에 실패했습니다.");
    }
  };

  if (authStatus === "checking") {
    return <div className="auth-loading">관리자 로그인 상태를 확인하고 있어요.</div>;
  }

  if (authStatus === "anonymous") {
    return <AdminLoginPage error={authError} onLogin={handleLogin} />;
  }

  const pageTitle = page === "dashboard" ? "대시보드" : page === "members" ? "회원 관리" : "서비스 의견";

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-lockup">
          <div className="brand-mark"><span /><span /><span /></div>
          <div><strong>mapmory</strong><small>ADMIN CONSOLE</small></div>
        </div>
        <div className="workspace-label">WORKSPACE</div>
        <nav className="side-nav" aria-label="주 메뉴">
          <button className={page === "dashboard" ? "nav-item active" : "nav-item"} onClick={() => setPage("dashboard")}><Icon name="grid"/><span>대시보드</span></button>
          <button className={page === "members" ? "nav-item active" : "nav-item"} onClick={() => setPage("members")}><Icon name="users"/><span>회원 관리</span></button>
          <button className={page === "feedback" ? "nav-item active" : "nav-item"} onClick={() => setPage("feedback")}><Icon name="chat"/><span>서비스 의견</span><span className="nav-count">8</span></button>
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-help"><div className="help-icon">?</div><div><strong>운영 가이드</strong><span>어드민 사용 방법</span></div><Icon name="arrow" size={15}/></div>
          <button className="profile-button" onClick={handleLogout} title="로그아웃">
            <div className="avatar admin-avatar">{adminUsername.slice(0, 1).toUpperCase() || "M"}</div>
            <div className="profile-copy"><strong>{adminUsername}</strong><span>로그아웃</span></div>
            <Icon name="logout" size={16}/>
          </button>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div className="breadcrumb"><span>Mapmory</span><Icon name="chevron" size={14}/><strong>{pageTitle}</strong></div>
          <div className="topbar-actions">
            <span className="environment-pill"><i /> 운영 환경</span>
            <button className="icon-button notification-button" aria-label="알림" onClick={() => showNotice("새로운 알림이 없습니다.")}><Icon name="bell"/><i /></button>
            <div className="avatar admin-avatar small-avatar">M</div>
          </div>
        </header>
        <div className="content-area">
          {page === "dashboard" && <Dashboard period={period} setPeriod={setPeriod} onNavigate={setPage} onNotice={showNotice} />}
          {page === "members" && <MembersPage query={memberQuery} setQuery={setMemberQuery} list={filteredMembers} onSelect={setSelectedMember} onNotice={showNotice} />}
          {page === "feedback" && <FeedbackPage feedback={filteredFeedback} allFeedback={feedback} filter={feedbackFilter} setFilter={setFeedbackFilter} query={feedbackQuery} setQuery={setFeedbackQuery} onStatusChange={(id, status) => setFeedback((current) => current.map((item) => item.id === id ? { ...item, status } : item))} />}
        </div>
      </main>
      {selectedMember && <MemberDrawer member={selectedMember} onClose={() => setSelectedMember(null)} />}
      {notice && <div className="toast"><span className="toast-check">✓</span>{notice}</div>}
      <div className="demo-ribbon">회원·의견은 시연 데이터</div>
    </div>
  );
}

function AdminLoginPage({ error, onLogin }: { error: string; onLogin: (username: string, password: string) => Promise<void> }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    await onLogin(username, password);
    setSubmitting(false);
  };

  return (
    <main className="login-page">
      <section className="login-panel">
        <div className="login-brand">
          <div className="brand-mark"><span /><span /><span /></div>
          <strong>mapmory</strong>
        </div>
        <div className="login-heading">
          <span className="eyebrow">ADMIN CONSOLE</span>
          <h1>운영자 로그인</h1>
          <p>관리자 계정으로 로그인해 주세요.</p>
        </div>
        <form className="login-form" onSubmit={submit}>
          <label htmlFor="admin-username">아이디</label>
          <input
            id="admin-username"
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            required
          />
          <div className="password-label"><label htmlFor="admin-password">비밀번호</label></div>
          <input
            id="admin-password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
          {error && <p className="login-error" role="alert">{error}</p>}
          <button className="primary-button login-submit" type="submit" disabled={submitting}>
            {submitting ? "로그인 중..." : "로그인"}
          </button>
        </form>
        <div className="login-footnote"><span className="lock-mark">⌑</span> 관리자 전용 페이지입니다.</div>
      </section>
      <aside className="login-side">
        <div className="login-side-copy">
          <span>MAPMORY ADMIN</span>
          <h2>여행의 기억을<br/><em>더 나은 경험으로</em></h2>
          <p>서비스 현황을 확인하고 회원들의 여행 기록을 살펴보세요.</p>
        </div>
        <div className="login-graphic" aria-hidden="true">
          <div className="orbit orbit-outer"/><div className="orbit orbit-inner"/>
          <div className="orbit-center"><div className="brand-mark large-mark"><span/><span/><span/></div></div>
          <i className="orbit-dot dot-a"/><i className="orbit-dot dot-b"/><i className="orbit-dot dot-c"/>
          <div className="graphic-card"><span className="graphic-card-label">기록된 여행</span><strong>Mapmory</strong><span className="graphic-spark">✳</span></div>
        </div>
      </aside>
      <div className="login-decoration decoration-one"/><div className="login-decoration decoration-two"/>
    </main>
  );
}

function Dashboard({ period, setPeriod, onNavigate, onNotice }: { period: string; setPeriod: (period: string) => void; onNavigate: (page: Page) => void; onNotice: (message: string) => void }) {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [retryKey, setRetryKey] = useState(0);
  const range = getDashboardRange(period);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    setSummary(null);
    const query = new URLSearchParams(range);

    adminApiRequest<DashboardSummary>(`/admin/dashboard?${query.toString()}`, { signal: controller.signal })
      .then((data) => {
        setSummary(data);
        setLastUpdated(new Date());
      })
      .catch((cause: unknown) => {
        if (controller.signal.aborted) return;
        setError(cause instanceof Error ? cause.message : "대시보드 데이터를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [range.from, range.to, retryKey]);

  const number = (value: number | undefined) => value === undefined ? "—" : new Intl.NumberFormat("ko-KR").format(value);
  const total = summary?.members.total ?? 0;
  const guest = summary?.members.guest ?? 0;
  const kakao = summary?.members.kakao ?? 0;
  const google = summary?.members.google ?? 0;
  const classifiedMembers = guest + kakao + google;
  const guestPercent = classifiedMembers === 0 ? 0 : (guest / classifiedMembers) * 100;
  const kakaoPercent = classifiedMembers === 0 ? 0 : (kakao / classifiedMembers) * 100;
  const googlePercent = classifiedMembers === 0 ? 0 : (google / classifiedMembers) * 100;
  const unknown = Math.max(0, total - classifiedMembers);
  const donutBackground = total === 0
    ? "#e7ece8"
    : `conic-gradient(#64c1a0 0% ${guest / total * 100}%, #f0ba9c ${guest / total * 100}% ${(guest + kakao) / total * 100}%, #98aadd ${(guest + kakao) / total * 100}% ${classifiedMembers / total * 100}%, #d9dfdb ${classifiedMembers / total * 100}% 100%)`;
  const dateLabel = summary
    ? `${formatPeriodDate(summary.period.from)} – ${formatPeriodDate(summary.period.to)}`
    : `${formatPeriodDate(range.from)} – ${formatPeriodDate(range.to)}`;
  const todayLabel = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "long",
    day: "numeric",
    weekday: "long",
  }).format(new Date());

  return (
    <div className="page-content dashboard-page">
      <div className="page-heading-row">
        <div>
          <div className="date-eyebrow"><span className="live-dot"/> {todayLabel}</div>
          <h1>좋은 오후예요, 운영자님 <span className="wave">✳</span></h1>
          <p>Mapmory의 최근 현황을 확인해 보세요.</p>
        </div>
        <button className="secondary-button" onClick={() => onNotice("리포트 다운로드는 준비 중입니다.")}><Icon name="download" size={16}/> 리포트 내보내기</button>
      </div>

      <div className="period-row">
        <div className="period-tabs">
          {["최근 7일", "최근 30일"].map((item) => (
            <button key={item} className={period === item ? "period-tab active" : "period-tab"} onClick={() => setPeriod(item)}>{item}</button>
          ))}
        </div>
        <div className="date-range"><Icon name="calendar" size={15}/>{dateLabel}<span>한국 시간</span></div>
      </div>

      {error && (
        <div className="dashboard-error" role="alert">
          <span>{error} 백엔드 실행 상태와 주소 설정을 확인해 주세요.</span>
          <button type="button" onClick={() => setRetryKey((value) => value + 1)}>다시 시도</button>
        </div>
      )}
      {loading && <div className="dashboard-loading" role="status">대시보드 데이터를 불러오는 중입니다.</div>}

      <section className="metric-grid" aria-label="주요 지표">
        <MetricCard label="가입 회원" value={number(summary?.members.total)} change={summary ? `+${number(summary.members.newInPeriod)}` : "—"} detail="선택 기간 신규 가입" icon="users" tint="mint" trend="neutral" foot={`게스트 ${number(summary?.members.guest)} · 카카오 ${number(summary?.members.kakao)} · Google ${number(summary?.members.google)}`} />
        <MetricCard label="여행 기록" value={number(summary?.travelRecords.total)} change={summary ? `+${number(summary.travelRecords.createdInPeriod)}` : "—"} detail="선택 기간 생성" icon="grid" tint="peach" trend="neutral" foot="전체 누적 기록" />
        <MetricCard label="등록 사진" value={number(summary?.photos.total)} change="누적" detail="여행 기록에 연결된 사진" icon="image" tint="lavender" trend="neutral" foot="삭제된 사진 제외" />
        <MetricCard label="기간 신규 회원" value={number(summary?.members.newInPeriod)} change={loading ? "—" : period} detail="가입 수" icon="users" tint="lavender" trend="neutral" foot={dateLabel} />
      </section>

      <section className="dashboard-main-grid">
        <div className="panel chart-panel period-summary-panel">
          <div className="panel-heading"><div><h2>선택 기간 집계</h2><p>한국 시간 기준 기간별 생성 수</p></div></div>
          <div className="period-summary-grid">
            <div className="period-summary-item"><span>신규 회원</span><strong>{number(summary?.members.newInPeriod)}<small>명</small></strong></div>
            <div className="period-summary-item"><span>여행 기록 생성</span><strong>{number(summary?.travelRecords.createdInPeriod)}<small>개</small></strong></div>
          </div>
          {!loading && !error && <p className="period-summary-caption">집계 기간 · {dateLabel}</p>}
        </div>
        <div className="panel distribution-panel">
          <div className="panel-heading"><div><h2>회원 구성</h2><p>가입 방식별 누적 회원</p></div><button className="more-button" aria-label="회원 페이지로 이동" onClick={() => onNavigate("members")}><Icon name="arrow" size={16}/></button></div>
          <div className="donut-area">
            <div className="donut" style={{ background: donutBackground }}><div className="donut-center"><strong>{number(summary?.members.total)}</strong><span>전체 회원</span></div></div>
            <div className="donut-caption">
              <span><i className="legend-dot mint-dot"/>게스트<strong>{number(summary?.members.guest)}</strong><small>{guestPercent.toFixed(1)}%</small></span>
              <span><i className="legend-dot peach-dot"/>카카오<strong>{number(summary?.members.kakao)}</strong><small>{kakaoPercent.toFixed(1)}%</small></span>
              <span><i className="legend-dot blue-dot"/>Google<strong>{number(summary?.members.google)}</strong><small>{googlePercent.toFixed(1)}%</small></span>
              {unknown > 0 && <span><i className="legend-dot unknown-dot"/>유형 미지정<strong>{number(unknown)}</strong><small>기존 회원</small></span>}
            </div>
          </div>
          <div className="distribution-footer"><span>유형별 비율은 확인 가능한 회원 기준입니다.</span></div>
        </div>
      </section>
      <footer className="page-footer"><span>Mapmory Admin <b>·</b> 내부 운영 도구</span><span>마지막 데이터 업데이트 <strong>{lastUpdated ? lastUpdated.toLocaleTimeString("ko-KR", { timeZone: "Asia/Seoul", hour: "2-digit", minute: "2-digit" }) : "—"}</strong></span></footer>
    </div>
  );
}

function MetricCard({ label, value, change, detail, icon, tint, trend, foot }: { label: string; value: string; change: string; detail: string; icon: string; tint: string; trend: "up" | "neutral"; foot: string }) {
  return <article className="metric-card"><div className="metric-top"><span>{label}</span><div className={`metric-icon ${tint}`}><Icon name={icon} size={18}/></div></div><div className="metric-value">{value}</div><div className="metric-change"><span className={trend === "up" ? "change-up" : "change-neutral"}>{trend === "up" && <Icon name="arrow" size={12}/>} {change}</span><span>{detail}</span></div><div className="metric-divider"/><div className="metric-foot"><span className="foot-dot"/>{foot}</div></article>;
}

function MembersPage({ query, setQuery, list, onSelect, onNotice }: { query: string; setQuery: (query: string) => void; list: Member[]; onSelect: (member: Member) => void; onNotice: (message: string) => void }) {
  const [provider, setProvider] = useState("전체 유형");
  const visible = provider === "전체 유형" ? list : list.filter((item) => item.provider === provider);
  return <div className="page-content list-page"><div className="page-heading-row"><div><div className="date-eyebrow">MEMBERS</div><h1>회원 관리</h1><p>회원 가입과 계정 상태를 확인할 수 있어요.</p></div><button className="secondary-button" onClick={() => onNotice("회원 내보내기는 API 연결 후 제공됩니다.")}><Icon name="download" size={16}/> 내보내기</button></div>
    <div className="member-summary-row"><div className="member-summary-card"><span>전체 회원</span><strong>1,284</strong><small>지난 기간보다 <b>+84명</b></small></div><div className="member-summary-card"><span>게스트</span><strong>812</strong><small>전체 회원의 63.2%</small></div><div className="member-summary-card"><span>OAuth</span><strong>472</strong><small>전체 회원의 36.8%</small></div><div className="member-summary-card"><span>최근 갱신 실패</span><strong className="alert-value">7</strong><small>최근 7일 기준</small></div></div>
    <div className="panel table-panel"><div className="table-toolbar"><div><h2>회원 목록 <span className="muted-count">1,284</span></h2><p>계정을 선택하면 상세 상태를 볼 수 있어요.</p></div><div className="table-actions"><div className="search-field"><Icon name="search" size={17}/><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="이름 또는 회원 ID 검색" aria-label="회원 검색"/><kbd>⌘ K</kbd></div><select className="select-control" value={provider} onChange={(event) => setProvider(event.target.value)} aria-label="로그인 유형"><option>전체 유형</option><option value="GUEST">게스트</option><option value="KAKAO">OAuth</option></select><button className="filter-button" onClick={() => onNotice("가입일 필터는 API 연결 후 제공됩니다.")}><Icon name="filter" size={16}/> 필터</button></div></div>
      <div className="table-scroll"><table><thead><tr><th>회원</th><th>가입일</th><th>로그인 방식</th><th>여행 기록</th><th>최근 토큰 갱신</th><th>최근 활동</th><th /></tr></thead><tbody>{visible.map((member) => <tr key={member.id} onClick={() => onSelect(member)}><td><div className="member-cell"><div className={`avatar ${member.provider === "GUEST" ? "guest-avatar" : "member-avatar"}`}>{member.provider === "GUEST" ? "G" : member.name.slice(0, 1)}</div><div><strong>{member.name}</strong><span>{member.id}</span></div></div></td><td>{member.joinedAt}</td><td><ProviderBadge provider={member.provider}/></td><td><strong className="record-number">{member.records}</strong><span className="table-suffix">개</span></td><td><span className={member.refresh === "정상" ? "refresh-state" : "refresh-state failed"}><i/>{member.refresh}</span></td><td className="muted-cell">{member.lastActive}</td><td><button className="row-arrow" aria-label={`${member.id} 상세 보기`}><Icon name="chevron" size={15}/></button></td></tr>)}</tbody></table>{visible.length === 0 && <div className="empty-state">검색 결과가 없습니다.</div>}</div>
      <div className="table-footer"><span>총 <strong>1,284</strong>명 중 <strong>{visible.length}</strong>명 표시</span><div className="pagination"><button disabled>이전</button><button className="current-page">1</button><button onClick={() => onNotice("시연용 데이터는 1페이지입니다.")}>2</button><button onClick={() => onNotice("시연용 데이터는 1페이지입니다.")}>3</button><span>…</span><button onClick={() => onNotice("시연용 데이터는 1페이지입니다.")}>129</button><button onClick={() => onNotice("시연용 데이터는 1페이지입니다.")}>다음</button></div></div>
    </div><footer className="page-footer"><span>회원 정보는 운영 지원 목적으로만 확인해 주세요.</span><span>최근 토큰 갱신 정보는 시연용입니다.</span></footer></div>;
}

function FeedbackPage({ feedback, allFeedback, filter, setFilter, query, setQuery, onStatusChange }: { feedback: Feedback[]; allFeedback: Feedback[]; filter: string; setFilter: (filter: string) => void; query: string; setQuery: (query: string) => void; onStatusChange: (id: string, status: FeedbackStatus) => void }) {
  const counts = { "전체": allFeedback.length, "미확인": allFeedback.filter((item) => item.status === "미확인").length, "반영 중": allFeedback.filter((item) => item.status === "반영 중").length, "반영 완료": allFeedback.filter((item) => item.status === "반영 완료").length };
  return <div className="page-content list-page"><div className="page-heading-row"><div><div className="date-eyebrow">VOICE OF MAPMORY</div><h1>서비스 의견</h1><p>사용자의 목소리를 모아 더 나은 경험을 만들어 보세요.</p></div><button className="secondary-button" onClick={() => window.print()}><Icon name="download" size={16}/> 목록 인쇄</button></div>
    <div className="feedback-overview"><div className="feedback-overview-copy"><span className="overview-icon"><Icon name="chat" size={19}/></span><div><strong>이번 주 새로운 의견 <b>12건</b></strong><p>의견은 서비스 개선을 위해 내부에서만 관리됩니다.</p></div></div><span className="overview-date"><Icon name="calendar" size={15}/> 10.02 – 10.08</span></div>
    <div className="panel table-panel feedback-table-panel"><div className="feedback-toolbar"><div className="feedback-tabs">{["전체", "미확인", "반영 중", "반영 완료"].map((item) => <button key={item} className={filter === item ? "feedback-tab active" : "feedback-tab"} onClick={() => setFilter(item)}>{item}<span>{counts[item as keyof typeof counts]}</span></button>)}</div><div className="table-actions"><div className="search-field"><Icon name="search" size={17}/><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="의견 내용 검색" aria-label="의견 내용 검색"/></div><button className="filter-button"><Icon name="filter" size={16}/> 상세 필터</button></div></div>
      <div className="table-scroll"><table className="feedback-table"><thead><tr><th>접수일</th><th>유형</th><th className="content-column">의견 내용</th><th>OS</th><th>처리 상태</th><th /></tr></thead><tbody>{feedback.map((item) => <tr key={item.id}><td><span className="feedback-date">2026.{item.createdAt}</span><small>{item.id}</small></td><td><span className={`category-tag ${item.category === "오류 제보" ? "bug-category" : item.category === "기능 제안" ? "feature-category" : "other-category"}`}>{item.category}</span></td><td className="feedback-content-cell">{item.content}</td><td><span className="os-label"><span className="os-symbol">{item.platform === "iOS" ? "●" : "◉"}</span>{item.platform}</span></td><td><select className={`status-select status-${item.status.replaceAll(" ", "")}`} value={item.status} onChange={(event) => onStatusChange(item.id, event.target.value as FeedbackStatus)} aria-label={`${item.id} 처리 상태`}>{feedbackStatuses.map((status) => <option key={status}>{status}</option>)}</select></td><td><button className="row-arrow" aria-label={`${item.id} 의견 상세`}><Icon name="chevron" size={15}/></button></td></tr>)}</tbody></table>{feedback.length === 0 && <div className="empty-state">해당 상태의 의견이 없습니다.</div>}</div>
      <div className="table-footer"><span>총 <strong>{feedback.length}</strong>건</span><div className="pagination"><button disabled>이전</button><button className="current-page">1</button><button>다음</button></div></div>
    </div><div className="feedback-privacy-note"><span>✳</span><p><strong>의견은 내부 개선 목적으로만 사용됩니다.</strong> 제출한 사용자에게 개별 답변은 제공하지 않습니다.</p></div><footer className="page-footer"><span>Mapmory Admin <b>·</b> 의견 관리</span><span>접수일은 서버 기준 시각으로 저장됩니다.</span></footer></div>;
}

function MemberDrawer({ member, onClose }: { member: Member; onClose: () => void }) {
  return <><button className="drawer-backdrop" aria-label="상세 닫기" onClick={onClose}/><aside className="member-drawer"><div className="drawer-header"><div><span className="date-eyebrow">MEMBER DETAIL</span><h2>회원 상세</h2></div><button className="icon-button" onClick={onClose} aria-label="닫기"><Icon name="close"/></button></div><div className="drawer-member"><div className={`avatar drawer-avatar ${member.provider === "GUEST" ? "guest-avatar" : "member-avatar"}`}>{member.provider === "GUEST" ? "G" : member.name.slice(0, 1)}</div><div><h3>{member.name}</h3><span>{member.id}</span></div></div><div className="detail-section"><h3>계정 정보</h3><DetailRow label="가입일" value={member.joinedAt}/><DetailRow label="로그인 방식" value={member.provider === "GUEST" ? "게스트" : "OAuth · Kakao"}/><DetailRow label="여행 기록" value={`${member.records}개`}/></div><div className="detail-section"><h3>로그인 상태</h3><div className="session-card"><div className="session-heading"><span className={member.refresh === "정상" ? "refresh-state" : "refresh-state failed"}><i/>{member.refresh}</span><span className="session-pill">최근 확인</span></div><p>{member.refresh === "정상" ? "최근 토큰 갱신이 정상적으로 완료됐어요." : "토큰 갱신에 실패했어요. 사용자가 다시 로그인해야 할 수 있습니다."}</p><small>최근 활동 · {member.lastActive}</small></div><DetailRow label="활성 세션" value={member.refresh === "정상" ? "1개" : "확인 필요"}/></div><div className="drawer-note"><span>ⓘ</span> 로그인 상태는 시연용 데이터입니다. 실제 화면에서는 서버의 최근 리프레시 결과를 표시합니다.</div><button className="secondary-button drawer-close" onClick={onClose}>닫기</button></aside></>;
}

function DetailRow({ label, value }: { label: string; value: string }) { return <div className="detail-row"><span>{label}</span><strong>{value}</strong></div>; }
function ProviderBadge({ provider }: { provider: Member["provider"] }) { return <span className={`provider-badge ${provider === "GUEST" ? "provider-guest" : "provider-kakao"}`}><i/>{provider === "GUEST" ? "게스트" : "Kakao"}</span>; }

export default App;
