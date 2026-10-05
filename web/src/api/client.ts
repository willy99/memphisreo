const API_BASE_URL = "http://localhost:8080";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
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
    const body = await response.text();
    let message = body;
    try {
      message = JSON.parse(body).message ?? body;
    } catch {
      // тіло не JSON — лишаємо як є
    }
    throw new ApiError(response.status, message || response.statusText);
  }

  // 204, або 200 з порожнім тілом (напр. ResponseEntity.ok().build()) —
  // response.json() на порожньому рядку кидає parse-помилку.
  const text = await response.text();
  if (!text) {
    return undefined as T;
  }
  return JSON.parse(text) as T;
}

export const api = {
  get: <T>(path: string, token: string) => request<T>(path, { method: "GET", token }),
  post: <T>(path: string, body: unknown, token?: string) =>
    request<T>(path, { method: "POST", body: JSON.stringify(body), token }),
  patch: <T>(path: string, body: unknown, token: string) =>
    request<T>(path, { method: "PATCH", body: JSON.stringify(body), token }),
  del: <T>(path: string, token: string) => request<T>(path, { method: "DELETE", token }),
};
