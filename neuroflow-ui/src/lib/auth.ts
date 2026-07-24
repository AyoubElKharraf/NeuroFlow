const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

const SESSION_KEY = "neuroflow_auth_session";
export const AUTH_UNAUTHORIZED_EVENT = "neuroflow:unauthorized";

export type AuthSession = {
  accessToken: string;
  tokenType: string;
  expiresInMs: number;
  username: string;
  roles: string[];
  /** Epoch ms when the access token should be treated as expired. */
  expiresAt: number;
};

type LoginApiResponse = {
  accessToken: string;
  tokenType: string;
  expiresInMs: number;
  username: string;
  roles: string[];
};

function canUseStorage(): boolean {
  return typeof window !== "undefined";
}

export function getStoredSession(): AuthSession | null {
  if (!canUseStorage()) return null;
  try {
    const raw = window.localStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as AuthSession;
    if (!session?.accessToken || !session.expiresAt) return null;
    if (Date.now() >= session.expiresAt) {
      clearStoredSession();
      return null;
    }
    return session;
  } catch {
    clearStoredSession();
    return null;
  }
}

export function getStoredToken(): string | null {
  return getStoredSession()?.accessToken ?? null;
}

export function storeSession(session: AuthSession): void {
  if (!canUseStorage()) return;
  window.localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function clearStoredSession(): void {
  if (!canUseStorage()) return;
  window.localStorage.removeItem(SESSION_KEY);
  // legacy key from earlier MVP auto-login
  window.localStorage.removeItem("neuroflow_jwt");
}

export function notifyUnauthorized(): void {
  clearStoredSession();
  if (!canUseStorage()) return;
  window.dispatchEvent(new CustomEvent(AUTH_UNAUTHORIZED_EVENT));
}

export async function loginRequest(
  username: string,
  password: string,
): Promise<AuthSession> {
  const response = await fetch(`${API_URL}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
    cache: "no-store",
  });

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error("Identifiants invalides");
    }
    throw new Error(`Connexion impossible (HTTP ${response.status})`);
  }

  const body = (await response.json()) as LoginApiResponse;
  const session: AuthSession = {
    accessToken: body.accessToken,
    tokenType: body.tokenType || "Bearer",
    expiresInMs: body.expiresInMs,
    username: body.username,
    roles: body.roles ?? [],
    expiresAt: Date.now() + (body.expiresInMs || 86_400_000),
  };
  storeSession(session);
  return session;
}

export function logoutLocal(): void {
  clearStoredSession();
}

export function buildSseUrl(token?: string | null): string {
  const base = `${API_URL}/api/v1/alerts/stream`;
  const value = token ?? getStoredToken();
  if (!value) return base;
  return `${base}?token=${encodeURIComponent(value)}`;
}

export function hasRole(session: AuthSession | null, role: string): boolean {
  if (!session) return false;
  const normalized = role.startsWith("ROLE_") ? role : `ROLE_${role}`;
  return session.roles.includes(normalized) || session.roles.includes(role);
}
