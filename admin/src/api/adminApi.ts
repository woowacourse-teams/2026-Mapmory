type ApiResponse<T> = {
  data?: T;
  detail?: string;
  title?: string;
  message?: string;
};

type CsrfToken = {
  headerName: string;
  token: string;
};

export type AdminAccount = {
  username: string;
};

export type AdminMember = {
  id: number;
  displayName: string;
  provider: "GUEST" | "KAKAO" | "GOOGLE" | null;
  joinedAt: string;
  travelRecordCount: number;
};

export type AdminMemberList = {
  members: AdminMember[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type AdminMemberDetail = {
  member: Omit<AdminMember, "travelRecordCount">;
  totalTravelRecords: number;
  totalPhotos: number;
  travelRecords: { id: number; createdAt: string; photoCount: number }[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type AdminFeedbackStatus = "UNCONFIRMED" | "IN_PROGRESS" | "COMPLETED" | "ON_HOLD" | "NOT_IMPLEMENTED";
export type AdminFeedbackCategory = "BUG_REPORT" | "FEATURE_REQUEST" | "USABILITY" | "OTHER";
export type AdminFeedbackPlatform = "IOS" | "ANDROID";

export type AdminFeedback = {
  feedbackId: number;
  category: AdminFeedbackCategory;
  content: string;
  platform: AdminFeedbackPlatform;
  status: AdminFeedbackStatus;
  createdAt: string;
};

export type AdminFeedbackList = {
  feedback: AdminFeedback[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

const apiBaseUrl = "https://api.map-mory.com/api/v1";
let csrfToken: CsrfToken | null = null;

async function readResponse<T>(response: Response): Promise<ApiResponse<T>> {
  if (response.status === 204) return {};

  try {
    return await response.json() as ApiResponse<T>;
  } catch {
    return {};
  }
}

function getErrorMessage(body: ApiResponse<unknown>, fallback: string) {
  return body.detail || body.message || body.title || fallback;
}

async function loadCsrfToken(): Promise<CsrfToken> {
  const response = await fetch(`${apiBaseUrl}/admin/auth/csrf`, {
    credentials: "include",
  });
  const body = await readResponse<CsrfToken>(response);

  if (!response.ok || !body.data) {
    throw new Error(getErrorMessage(body, "보안 토큰을 가져오지 못했습니다."));
  }

  csrfToken = body.data;
  return csrfToken;
}

export async function adminApiRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method || "GET").toUpperCase();
  const headers = new Headers(options.headers);

  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const token = csrfToken || await loadCsrfToken();
    headers.set(token.headerName, token.token);
  }

  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    method,
    headers,
    credentials: "include",
  });
  const body = await readResponse<T>(response);

  if (!response.ok) {
    throw new Error(getErrorMessage(body, "요청을 처리하지 못했습니다."));
  }

  if (response.status !== 204 && body.data === undefined) {
    throw new Error("서버 응답을 확인하지 못했습니다. 다시 시도해 주세요.");
  }
  return body.data as T;
}

export function getCurrentAdmin() {
  return adminApiRequest<AdminAccount>("/admin/auth/me");
}

export function getAdminMembers(keyword: string, provider: string, page: number, size = 20) {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (keyword.trim()) query.set("keyword", keyword.trim());
  if (provider) query.set("provider", provider);
  return adminApiRequest<AdminMemberList>(`/admin/members?${query.toString()}`);
}

export function getAdminMemberDetail(memberId: number, page = 0, size = 20) {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return adminApiRequest<AdminMemberDetail>(`/admin/members/${memberId}?${query.toString()}`);
}

export type AdminFeedbackFilters = {
  status?: AdminFeedbackStatus | "";
  category?: AdminFeedbackCategory | "";
  platform?: AdminFeedbackPlatform | "";
  from?: string;
  to?: string;
};

export function getAdminFeedback(filters: AdminFeedbackFilters, page: number, size = 20, signal?: AbortSignal) {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  for (const key of ["status", "category", "platform", "from", "to"] as const) {
    const value = filters[key];
    if (value) query.set(key, value);
  }
  return adminApiRequest<AdminFeedbackList>(`/admin/service-feedback?${query.toString()}`, { signal });
}

export function getAdminFeedbackDetail(feedbackId: number, signal?: AbortSignal) {
  return adminApiRequest<AdminFeedback>(`/admin/service-feedback/${feedbackId}`, { signal });
}

export function updateAdminFeedbackStatus(feedbackId: number, status: AdminFeedbackStatus) {
  return adminApiRequest<AdminFeedback>(`/admin/service-feedback/${feedbackId}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status }),
  });
}

export function loginAdmin(username: string, password: string) {
  return adminApiRequest<AdminAccount>("/admin/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
}

export async function logoutAdmin() {
  await adminApiRequest<void>("/admin/auth/logout", { method: "POST" });
  csrfToken = null;
}
