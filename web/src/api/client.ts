const API_BASE_URL = "http://localhost:8080";

export interface ApiFieldError {
  field: string;
  code: string;
}

export class ApiError extends Error {
  readonly status: number;
  /** Помилки по полях форми (400 від ValidationException). */
  readonly errors: ApiFieldError[];

  constructor(status: number, message: string, errors: ApiFieldError[] = []) {
    super(message);
    this.status = status;
    this.errors = errors;
  }
}

export type Realm = "tenant" | "platform";

// 401 = токена немає або він протермінований/відкликаний → відповідний
// контекст автентифікації розлогінює користувача (403 — це "бракує прав").
const unauthorizedHandlers: Partial<Record<Realm, () => void>> = {};

export function onUnauthorized(realm: Realm, handler: () => void) {
  unauthorizedHandlers[realm] = handler;
}

function realmOf(path: string): Realm {
  return path.startsWith("/platform-admin") ? "platform" : "tenant";
}

async function request<T>(path: string, options: RequestInit & { token?: string } = {}): Promise<T> {
  const { token, headers, ...rest } = options;
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...rest,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
  });

  if (!response.ok) {
    if (response.status === 401 && token) {
      unauthorizedHandlers[realmOf(path)]?.();
    }
    const body = await response.text();
    throw parseError(response.status, body, response.statusText);
  }

  // 204, або 200 з порожнім тілом (напр. ResponseEntity.ok().build()) —
  // response.json() на порожньому рядку кидає parse-помилку.
  const text = await response.text();
  if (!text) {
    return undefined as T;
  }
  return JSON.parse(text) as T;
}

function parseError(status: number, body: string, fallback: string): ApiError {
  let message = body;
  let errors: ApiFieldError[] = [];
  try {
    const json = JSON.parse(body);
    message = json.message ?? body;
    errors = Array.isArray(json.errors) ? json.errors : [];
  } catch {
    // тіло не JSON — лишаємо як є
  }
  return new ApiError(status, message || fallback, errors);
}

/**
 * Multipart-завантаження з прогресом: fetch не дає подій прогресу відправлення,
 * XMLHttpRequest — дає (upload.onprogress).
 */
export function uploadWithProgress<T>(
  path: string,
  form: FormData,
  token: string,
  onProgress: (fraction: number) => void,
): { promise: Promise<T>; abort: () => void } {
  const xhr = new XMLHttpRequest();
  const promise = new Promise<T>((resolve, reject) => {
    xhr.open("POST", `${API_BASE_URL}${path}`);
    xhr.setRequestHeader("Authorization", `Bearer ${token}`);
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(event.loaded / event.total);
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(JSON.parse(xhr.responseText) as T);
        return;
      }
      if (xhr.status === 401) unauthorizedHandlers[realmOf(path)]?.();
      reject(parseError(xhr.status, xhr.responseText, xhr.statusText));
    };
    xhr.onerror = () => reject(new ApiError(0, "network"));
    xhr.onabort = () => reject(new ApiError(0, "aborted"));
    xhr.send(form);
  });
  return { promise, abort: () => xhr.abort() };
}

export const api = {
  get: <T>(path: string, token: string) => request<T>(path, { method: "GET", token }),
  post: <T>(path: string, body: unknown, token?: string) =>
    request<T>(path, { method: "POST", body: JSON.stringify(body), token }),
  put: <T>(path: string, body: unknown, token: string) =>
    request<T>(path, { method: "PUT", body: JSON.stringify(body), token }),
  patch: <T>(path: string, body: unknown, token: string) =>
    request<T>(path, { method: "PATCH", body: JSON.stringify(body), token }),
  del: <T>(path: string, token: string) => request<T>(path, { method: "DELETE", token }),
};
