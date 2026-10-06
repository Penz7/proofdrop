import { useEffect, useRef, useState, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from "react";
import type { OrderStatus } from "../lib/types";
import { orderStatusLabel, shortHash } from "../lib/format";

type Variant = "primary" | "secondary" | "ghost" | "danger";

const variants: Record<Variant, string> = {
  primary: "bg-brand text-brand-ink hover:brightness-95 font-semibold",
  secondary: "bg-surface-2 text-fg hover:bg-line border border-line",
  ghost: "text-fg hover:bg-surface-2",
  danger: "bg-danger text-white hover:brightness-110",
};

export function Button({
  variant = "secondary",
  className = "",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      {...props}
      className={`inline-flex items-center justify-center gap-2 rounded-lg px-3.5 py-2 text-sm transition disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand ${variants[variant]} ${className}`}
    />
  );
}

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <div className={`rounded-xl border border-line bg-surface ${className}`}>{children}</div>;
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <label className="block">
      <span className="mb-1 block text-xs font-medium text-muted">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-xs text-muted">{hint}</span>}
    </label>
  );
}

const inputClass =
  "w-full rounded-lg border border-line bg-bg px-3 py-2 text-sm text-fg placeholder:text-muted focus:border-brand focus:outline-none";

export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={`${inputClass} ${props.className ?? ""}`} />;
}

export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} className={`${inputClass} ${props.className ?? ""}`} />;
}

type Tone = "neutral" | "ok" | "warn" | "danger" | "info" | "brand";

const tones: Record<Tone, string> = {
  neutral: "bg-surface-2 text-muted",
  ok: "bg-ok/15 text-ok",
  warn: "bg-warn/15 text-warn",
  danger: "bg-danger/15 text-danger",
  info: "bg-info/15 text-info",
  brand: "bg-brand/20 text-brand-strong",
};

export function Badge({ tone = "neutral", children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span className={`inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold ${tones[tone]}`}>
      {children}
    </span>
  );
}

const statusTone: Record<OrderStatus, Tone> = {
  CREATED: "neutral",
  ASSIGNED: "info",
  PICKED_UP: "warn",
  DELIVERED: "ok",
  FAILED: "danger",
};

export function StatusBadge({ status }: { status: OrderStatus }) {
  return <Badge tone={statusTone[status]}>{orderStatusLabel[status]}</Badge>;
}

export function Hash({ value, n = 10 }: { value: string; n?: number }) {
  const [copied, setCopied] = useState(false);
  useEffect(() => {
    if (!copied) return;
    const t = setTimeout(() => setCopied(false), 1200);
    return () => clearTimeout(t);
  }, [copied]);
  return (
    <button
      type="button"
      title={copied ? "Copied" : `${value}\nClick to copy`}
      onClick={() => {
        void copyText(value).then((ok) => ok && setCopied(true));
      }}
      className="rounded px-1 font-mono text-xs text-muted hover:bg-surface-2 hover:text-fg"
    >
      {copied ? "copied ✓" : shortHash(value, n)}
    </button>
  );
}

/** Clipboard API needs a secure context (https or localhost); fall back for http://<LAN-IP>. */
async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
    const area = document.createElement("textarea");
    area.value = text;
    area.style.position = "fixed";
    area.style.opacity = "0";
    document.body.appendChild(area);
    area.select();
    const ok = document.execCommand("copy");
    area.remove();
    return ok;
  } catch {
    return false;
  }
}

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

export function Modal({ title, onClose, children, wide }: { title: string; onClose: () => void; children: ReactNode; wide?: boolean }) {
  const dialog = useRef<HTMLDivElement>(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;

  // Move focus into the dialog, keep Tab inside it, and give focus back to the opener on close.
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    const inForm = FOCUSABLE.split(", ").map((sel) => `form ${sel}`).join(", ");
    const first = dialog.current?.querySelector<HTMLElement>(inForm) ?? dialog.current;
    first?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") closeRef.current();
      if (e.key !== "Tab" || !dialog.current) return;
      const items = Array.from(dialog.current.querySelectorAll<HTMLElement>(FOCUSABLE));
      if (items.length === 0) return;
      const [head, tail] = [items[0], items[items.length - 1]];
      if (e.shiftKey && document.activeElement === head) {
        e.preventDefault();
        tail.focus();
      } else if (!e.shiftKey && document.activeElement === tail) {
        e.preventDefault();
        head.focus();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("keydown", onKey);
      opener?.focus?.();
    };
  }, []);
  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:p-8" onMouseDown={onClose}>
      <div
        ref={dialog}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={`w-full ${wide ? "max-w-4xl" : "max-w-lg"} rounded-2xl border border-line bg-surface shadow-2xl outline-none`}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-line px-5 py-3">
          <h2 className="font-semibold">{title}</h2>
          <button onClick={onClose} className="rounded-lg px-2 py-1 text-muted hover:bg-surface-2" aria-label="Close">
            ✕
          </button>
        </div>
        <div className="p-5">{children}</div>
      </div>
    </div>
  );
}

export function Empty({ title, body }: { title: string; body?: string }) {
  return (
    <div className="px-6 py-14 text-center">
      <p className="font-medium">{title}</p>
      {body && <p className="mt-1 text-sm text-muted">{body}</p>}
    </div>
  );
}

export function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null;
  return (
    <p className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
      {error instanceof Error ? error.message : String(error)}
    </p>
  );
}

export function Spinner() {
  return <span className="inline-block size-4 animate-spin rounded-full border-2 border-current border-r-transparent" aria-label="Loading" />;
}
