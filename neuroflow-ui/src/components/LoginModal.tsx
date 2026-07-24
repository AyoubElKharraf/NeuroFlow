"use client";

import { type FormEvent, useState } from "react";
import { LockKeyhole, Shield } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";

export function LoginModal() {
  const { login } = useAuth();
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("admin123");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setPending(true);
    try {
      await login(username.trim(), password);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Échec de connexion");
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#04080f]/75 px-4 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-2xl border border-line bg-panel/95 p-6 shadow-glow">
        <div className="mb-6 flex items-start gap-3">
          <div className="rounded-lg border border-accent/30 bg-accent/10 p-2 text-accent">
            <Shield className="h-5 w-5" aria-hidden />
          </div>
          <div>
            <h1 className="font-display text-2xl tracking-tight text-ink">
              NeuroFlow Sign-in
            </h1>
            <p className="mt-1 text-sm text-mute">
              JWT required to reach the observability API and live SSE stream.
            </p>
          </div>
        </div>

        <form className="space-y-4" onSubmit={onSubmit}>
          <label className="block space-y-1.5 text-xs uppercase tracking-[0.14em] text-mute">
            Username
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              className="w-full rounded-md border border-line bg-elevated px-3 py-2.5 font-mono text-sm normal-case tracking-normal text-ink outline-none transition focus:border-accent"
              required
            />
          </label>

          <label className="block space-y-1.5 text-xs uppercase tracking-[0.14em] text-mute">
            Password
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              className="w-full rounded-md border border-line bg-elevated px-3 py-2.5 font-mono text-sm normal-case tracking-normal text-ink outline-none transition focus:border-accent"
              required
            />
          </label>

          {error && (
            <p className="rounded-md border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={pending}
            className="inline-flex w-full cursor-pointer items-center justify-center gap-2 rounded-md border border-accent/40 bg-accent/15 px-4 py-2.5 text-sm font-medium text-accent transition hover:bg-accent/25 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <LockKeyhole className="h-4 w-4" aria-hidden />
            {pending ? "Signing in…" : "Sign in"}
          </button>
        </form>

        <p className="mt-4 font-mono text-[11px] text-mute">
          Demo: <span className="text-ink">admin / admin123</span> (ADMIN) ·{" "}
          <span className="text-ink">user / user123</span> (USER)
        </p>
      </div>
    </div>
  );
}
