"use client";

import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { authApi } from "@/lib/api";

type Mode = "login" | "register";

const TOKEN_KEY = "minichat_access_token";

export default function LoginPage() {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const token = localStorage.getItem(TOKEN_KEY);
    if (token) {
      router.replace("/chat");
    }
  }, [router]);

  const submitLabel = useMemo(() => (mode === "login" ? "Sign In" : "Create Account"), [mode]);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(null);

    try {
      const normalizedEmail = email.trim().toLowerCase();
      const response =
        mode === "login"
          ? await authApi.login(normalizedEmail, password)
          : await authApi.register(normalizedEmail, password);

      localStorage.setItem(TOKEN_KEY, response.accessToken);
      router.push("/chat");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Authentication failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-card">
        <p className="eyebrow">MiniChat</p>
        <h1>{mode === "login" ? "Welcome Back" : "Create Your Account"}</h1>
        <p className="auth-subtitle">Use your email and password to continue.</p>

        <form onSubmit={onSubmit} className="auth-form">
          <label>
            Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="name@example.com"
              required
            />
          </label>

          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="At least 8 characters"
              minLength={8}
              required
            />
          </label>

          {error && <p className="error-text">{error}</p>}

          <button type="submit" disabled={loading}>
            {loading ? "Please wait..." : submitLabel}
          </button>
        </form>

        <div className="auth-switch">
          <span>{mode === "login" ? "No account yet?" : "Already registered?"}</span>
          <button
            type="button"
            className="link-button"
            onClick={() => setMode(mode === "login" ? "register" : "login")}
          >
            {mode === "login" ? "Create one" : "Sign in"}
          </button>
        </div>
      </section>
    </main>
  );
}
