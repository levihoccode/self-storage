type ApiRequestOptions = {
  method?: "GET" | "POST";
  body?: unknown;
  accessToken?: string;
  dispatchAuthExpired?: boolean;
  signal?: AbortSignal;
};

type ApiEnvelope<T> = {
  message: string;
  data: T;
};

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export const AUTH_SESSION_EXPIRED_EVENT = "auth:session-expired";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/+$/, "");

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const headers = new Headers({ Accept: "application/json" });
  if (options.body !== undefined) headers.set("Content-Type", "application/json");
  if (options.accessToken) headers.set("Authorization", `Bearer ${options.accessToken}`);

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: options.method ?? "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: options.signal,
  });
  const responseText = await response.text();
  let responseBody: unknown = null;

  if (responseText) {
    try {
      responseBody = JSON.parse(responseText);
    } catch {
      responseBody = null;
    }
  }

  if (!response.ok) {
    if (response.status === 401 && options.accessToken && options.dispatchAuthExpired !== false) {
      window.dispatchEvent(new Event(AUTH_SESSION_EXPIRED_EVENT));
    }

    const message =
      typeof responseBody === "object" &&
      responseBody !== null &&
      "message" in responseBody &&
      typeof responseBody.message === "string"
        ? responseBody.message
        : `Request failed with status ${response.status}`;
    throw new ApiError(response.status, message);
  }

  if (
    typeof responseBody === "object" &&
    responseBody !== null &&
    "message" in responseBody &&
    "data" in responseBody
  ) {
    return (responseBody as ApiEnvelope<T>).data;
  }

  return responseBody as T;
}
