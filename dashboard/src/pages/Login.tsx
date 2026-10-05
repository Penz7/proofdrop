import { useState, type FormEvent } from "react";
import { Navigate } from "react-router";
import { useAuth } from "../lib/auth";
import { Button, ErrorNote, Field, Input, Spinner } from "../components/ui";

export function LoginPage() {
  const { user, login } = useAuth();
  const [email, setEmail] = useState(import.meta.env.DEV ? "dispatcher@proofdrop.dev" : "");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to="/orders" replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email, password);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center bg-navy p-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex items-center justify-center gap-3 text-white">
          <svg viewBox="0 0 108 108" className="size-11" aria-hidden>
            <path fill="#FFC72C" d="M54,22 L80,32 L80,54 C80,70 68,82 54,86 C40,82 28,70 28,54 L28,32 Z" />
            <path fill="none" stroke="#0E1A2B" strokeWidth="7" strokeLinecap="round" strokeLinejoin="round" d="M42,55 L50,63 L67,45" />
          </svg>
          <div>
            <p className="text-xl font-semibold">ProofDrop</p>
            <p className="text-sm text-white/60">Dispatcher console</p>
          </div>
        </div>
        <form onSubmit={submit} className="space-y-4 rounded-2xl border border-line bg-surface p-6 shadow-xl">
          <Field label="Email">
            <Input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </Field>
          <Field label="Password">
            <Input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
          </Field>
          <ErrorNote error={error} />
          <Button type="submit" variant="primary" className="w-full" disabled={busy}>
            {busy && <Spinner />} Sign in
          </Button>
          {import.meta.env.DEV && (
            <p className="text-center text-xs text-muted">
              Local seed: dispatcher@proofdrop.dev / dispatch123
            </p>
          )}
        </form>
      </div>
    </div>
  );
}
