export type UserRole = "customer";

export type AuthUser = {
  id: string;
  name: string;
  email: string;
  role: UserRole;
};

export type AuthSession = {
  user: AuthUser;
  issuedAt: string;
};

export type LoginResult = { ok: true; session: AuthSession } | { ok: false; message: string };

export interface AuthGateway {
  getSession(): AuthSession | null;
  login(email: string, password: string): Promise<LoginResult>;
  logout(): void;
  subscribe(listener: (session: AuthSession | null) => void): () => void;
}

const SESSION_STORAGE_KEY = "kho-moc-demo-session";
const DEMO_EMAIL = "customer@kho-moc.demo";
const DEMO_PASSWORD = "demo123";

const demoUser: AuthUser = {
  id: "demo-customer-001",
  name: "Nguyễn Minh Anh",
  email: DEMO_EMAIL,
  role: "customer",
};

function readSession(): AuthSession | null {
  try {
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
    } else {
      window.localStorage.removeItem(SESSION_STORAGE_KEY);
    }
  } catch {
    // The demo still works for the current tab when storage is unavailable.
  }
}

const listeners = new Set<(session: AuthSession | null) => void>();

/**
 * DEMO ONLY — REMOVE THIS WHOLE MOCK GATEWAY WHEN THE BACKEND LOGIN EXISTS.
 * The credential is intentionally visible in the frontend bundle.
 * Replace this object with a gateway that calls POST /api/auth/login.
 */
export const authGateway: AuthGateway = {
  getSession: readSession,
  async login(email, password) {
    await new Promise((resolve) => window.setTimeout(resolve, 450));

    if (email.trim().toLowerCase() !== DEMO_EMAIL || password !== DEMO_PASSWORD) {
      return { ok: false, message: "Email hoặc mật khẩu demo chưa đúng." };
    }

    const session: AuthSession = {
      user: demoUser,
      issuedAt: new Date().toISOString(),
    };
    writeSession(session);
    listeners.forEach((listener) => listener(session));
    return { ok: true, session };
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

export { DEMO_EMAIL, DEMO_PASSWORD };
