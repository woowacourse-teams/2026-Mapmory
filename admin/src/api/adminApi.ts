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

  return body.data as T;
}

export function getCurrentAdmin() {
  return adminApiRequest<AdminAccount>("/admin/auth/me");
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
