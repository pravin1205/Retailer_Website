/**
 * Axios HTTP client configured for the Marketly backend.
 *
 * - All requests go to /api/v1/* (proxied to http://localhost:8080 in dev by vite).
 * - Attaches Authorization: Bearer <accessToken> automatically.
 * - On 401, attempts a silent token refresh once, then logs out.
 * - Unwraps the standard { success, data } envelope from every response.
 */
import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from "axios";

// ── Constants ─────────────────────────────────────────────────────────────────

export const BASE_URL = "/api/v1";

// ── Token storage (localStorage for persistence across tabs) ──────────────────

export const tokenStorage = {
  getAccess:    ()    => localStorage.getItem("marketly_access_token"),
  getRefresh:   ()    => localStorage.getItem("marketly_refresh_token"),
  setAccess:    (t: string) => localStorage.setItem("marketly_access_token", t),
  setRefresh:   (t: string) => localStorage.setItem("marketly_refresh_token", t),
  clearAll:     ()    => {
    localStorage.removeItem("marketly_access_token");
    localStorage.removeItem("marketly_refresh_token");
  },
};

// ── Axios instance ─────────────────────────────────────────────────────────────

export const http: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 15_000,
  headers: { "Content-Type": "application/json" },
});

// ── Request interceptor — attach Bearer token ─────────────────────────────────

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStorage.getAccess();
  if (token) {
    config.headers = config.headers ?? {};
    config.headers["Authorization"] = `Bearer ${token}`;
  }
  return config;
});

// ── Response interceptor — unwrap envelope + handle 401 refresh ───────────────

let isRefreshing = false;
let failedQueue: Array<{ resolve: (v: unknown) => void; reject: (e: unknown) => void }> = [];

function processQueue(error: unknown, token: string | null) {
  failedQueue.forEach(({ resolve, reject }) => (error ? reject(error) : resolve(token)));
  failedQueue = [];
}

http.interceptors.response.use(
  (response) => {
    // Unwrap { success: true, data: ... } envelope
    if (response.data && response.data.success !== undefined) {
      return response.data.data ?? response.data;
    }
    return response.data;
  },
  async (error) => {
    const original: AxiosRequestConfig & { _retry?: boolean } = error.config;

    if (error.response?.status === 401 && !original._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then((token) => {
          original.headers = { ...original.headers, Authorization: `Bearer ${token}` };
          return http(original);
        });
      }

      original._retry = true;
      isRefreshing = true;

      const refreshToken = tokenStorage.getRefresh();
      if (!refreshToken) {
        isRefreshing = false;
        tokenStorage.clearAll();
        window.location.href = "/auth/login";
        return Promise.reject(error);
      }

      try {
        const { data } = await axios.post(`${BASE_URL}/auth/refresh`, { refreshToken });
        const newAccess = data.data?.accessToken ?? data.accessToken;
        const newRefresh = data.data?.refreshToken ?? data.refreshToken;
        tokenStorage.setAccess(newAccess);
        tokenStorage.setRefresh(newRefresh);
        processQueue(null, newAccess);
        original.headers = { ...original.headers, Authorization: `Bearer ${newAccess}` };
        return http(original);
      } catch (refreshError) {
        processQueue(refreshError, null);
        tokenStorage.clearAll();
        window.location.href = "/auth/login";
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    // Surface the backend error message when available
    const backendError = error.response?.data?.error;
    if (backendError?.message) {
      error.message = backendError.message;
      error.code    = backendError.code;
    }

    return Promise.reject(error);
  },
);

// ── Tenant-scoped helper ───────────────────────────────────────────────────────

/**
 * Create a per-request config that injects X-Tenant-ID.
 * Used by all product / category / order calls.
 */
export function withTenant(tenantId: string, extra?: AxiosRequestConfig): AxiosRequestConfig {
  return {
    ...extra,
    headers: {
      ...(extra?.headers ?? {}),
      "X-Tenant-ID": tenantId,
    },
  };
}
