import { ApiError, apiRequest } from "./api";

export type UserRole = "ADMIN" | "BOM" | "FM" | "FS" | "CUSTOMER";

export type AuthUser = {
  id: string;
  name: string;
  email: string;
  role: UserRole;
};

export type AuthSession = {
  user: AuthUser;
  issuedAt: string;
  token: string;
};

export type LoginResult = { ok: true; session: AuthSession } | { ok: false; message: string };

export interface AuthGateway {
  getSession(): AuthSession | null;
  login(email: string, password: string): Promise<LoginResult>;
  restoreSession(): Promise<AuthSession | null>;
  logout(): void;
  subscribe(listener: (session: AuthSession | null) => void): () => void;
}

const SESSION_STORAGE_KEY = "kho-moc-auth-session";
const ACCESS_TOKEN_STORAGE_KEY = "kho-moc-access-token";
const LEGACY_DEMO_SESSION_STORAGE_KEY = "kho-moc-demo-session";

type LoginResponse = {
  accountId: number;
  email: string;
  token: string;
};

type CurrentUserResponse = {
  email: string;
  role: UserRole;
};

function readSession(): AuthSession | null {
  try {
    window.localStorage.removeItem(LEGACY_DEMO_SESSION_STORAGE_KEY);
    const stored = window.localStorage.getItem(SESSION_STORAGE_KEY);
    return stored ? (JSON.parse(stored) as AuthSession) : null;
  } catch {
    return null;
  }
}

function writeSession(session: AuthSession | null) {
  try {
    if (session) {
      window.localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
      writeAccessToken(session.token);
    } else {
      window.localStorage.removeItem(SESSION_STORAGE_KEY);
      writeAccessToken(null);
    }
  } catch {
    // The current tab can still use the in-memory session when storage is unavailable.
  }
}

function writeAccessToken(token: string | null) {
  try {
    if (token) {
      window.localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, token);
    } else {
      window.localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
    }
  } catch {
    // The current tab can still use the in-memory session.
  }
}

function readAccessToken() {
  try {
    return window.localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);
  } catch {
    return null;
  }
}

const listeners = new Set<(session: AuthSession | null) => void>();

export const authGateway: AuthGateway = {
  getSession: readSession,
  async login(email, password) {
    try {
      const loginResponse = await apiRequest<LoginResponse>("/api/auth/login", {
        method: "POST",
        body: { email, password },
      });
      writeSession(null);
      listeners.forEach((listener) => listener(null));
      writeAccessToken(loginResponse.token);
      const currentUser = await apiRequest<CurrentUserResponse>("/api/auth/me", {
        accessToken: loginResponse.token,
      });
      const session: AuthSession = {
        user: {
          id: String(loginResponse.accountId),
          name: currentUser.email,
          email: currentUser.email,
          role: currentUser.role,
        },
        issuedAt: new Date().toISOString(),
        token: loginResponse.token,
      };

      writeSession(session);
      listeners.forEach((listener) => listener(session));
      return { ok: true, session };
    } catch (error) {
      return {
        ok: false,
        message:
          error instanceof Error && "status" in error
            ? error.message
            : "Không thể kết nối tới máy chủ.",
      };
    }
  },
  async restoreSession() {
    const cachedSession = readSession();
    const accessToken = readAccessToken() ?? cachedSession?.token;
    if (!accessToken) {
      if (cachedSession) writeSession(null);
      return null;
    }

    try {
      const currentUser = await apiRequest<CurrentUserResponse>("/api/auth/me", {
        accessToken,
      });
      const session: AuthSession = {
        user: {
          id: cachedSession?.user.id ?? currentUser.email,
          name: currentUser.email,
          email: currentUser.email,
          role: currentUser.role,
        },
        issuedAt: cachedSession?.issuedAt ?? new Date().toISOString(),
        token: accessToken,
      };

      writeSession(session);
      listeners.forEach((listener) => listener(session));
      return session;
    } catch (error) {
      if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
        writeSession(null);
        listeners.forEach((listener) => listener(null));
        return null;
      }
      throw error;
    }
  },
  logout() {
    writeSession(null);
    listeners.forEach((listener) => listener(null));
  },
  subscribe(listener) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};
