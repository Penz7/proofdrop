import { NavLink, Outlet } from "react-router";
import { useAuth } from "../lib/auth";
import { useRealtime, type LinkState } from "../lib/realtime";

const nav = [
  { to: "/orders", label: "Orders", icon: "M3 7h18M3 12h18M3 17h12" },
  { to: "/map", label: "Live map", icon: "M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2Zm0 0v14m6-12v14" },
  { to: "/evidence", label: "Evidence", icon: "M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6l-8-3Zm-3 9 2 2 4-4" },
  { to: "/devices", label: "Devices", icon: "M7 3h10v18H7zM11 18h2" },
];

function Logo() {
  return (
    <svg viewBox="0 0 108 108" className="size-8 shrink-0" aria-hidden>
      <rect width="108" height="108" rx="24" fill="#0E1A2B" stroke="#263a55" />
      <path fill="#FFC72C" d="M54,22 L80,32 L80,54 C80,70 68,82 54,86 C40,82 28,70 28,54 L28,32 Z" />
      <path fill="none" stroke="#0E1A2B" strokeWidth="7" strokeLinecap="round" strokeLinejoin="round" d="M42,55 L50,63 L67,45" />
    </svg>
  );
}

function Dot({ state }: { state: LinkState }) {
  const color = state === "open" ? "bg-ok" : state === "connecting" ? "bg-warn animate-pulse" : "bg-danger";
  return <span className={`inline-block size-2 rounded-full ${color}`} />;
}

export function Layout() {
  const { user, logout } = useAuth();
  const { events, fleet } = useRealtime();
  const live = events === "open" && fleet === "open";

  return (
    <div className="flex min-h-full flex-col lg:flex-row">
      <aside className="no-print flex shrink-0 flex-col bg-navy text-white lg:sticky lg:top-0 lg:h-screen lg:w-60">
        <div className="flex items-center gap-3 px-5 py-4">
          <Logo />
          <div>
            <p className="font-semibold leading-tight">ProofDrop</p>
            <p className="text-xs text-white/60">Dispatch</p>
          </div>
        </div>
        <nav className="flex gap-1 overflow-x-auto px-3 pb-3 lg:flex-col lg:overflow-visible lg:pb-0">
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `flex items-center gap-3 whitespace-nowrap rounded-lg px-3 py-2 text-sm ${
                  isActive ? "bg-brand font-semibold text-brand-ink" : "text-white/80 hover:bg-white/10"
                }`
              }
            >
              <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                <path d={item.icon} />
              </svg>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="mt-auto hidden space-y-3 border-t border-white/10 px-5 py-4 text-xs lg:block">
          <div className="space-y-1.5 text-white/70" title="Server-Sent Events / WebSocket">
            <p className="flex items-center gap-2">
              <Dot state={events} /> Order events (SSE)
            </p>
            <p className="flex items-center gap-2">
              <Dot state={fleet} /> Fleet feed (WebSocket)
            </p>
          </div>
          <div className="flex items-center justify-between gap-2">
            <div className="min-w-0">
              <p className="truncate font-medium text-white">{user?.name}</p>
              <p className="truncate text-white/50">{user?.email}</p>
            </div>
            <button onClick={logout} className="shrink-0 whitespace-nowrap rounded-md px-2 py-1 text-white/70 hover:bg-white/10 hover:text-white">
              Sign out
            </button>
          </div>
        </div>
        {/* Compact status + sign out on small screens */}
        <div className="flex items-center justify-between border-t border-white/10 px-5 py-2 text-xs lg:hidden">
          <span className="flex items-center gap-2 text-white/70">
            <Dot state={live ? "open" : events === "down" && fleet === "down" ? "down" : "connecting"} />
            {live ? "Live" : "Reconnecting…"}
          </span>
          <button onClick={logout} className="text-white/70 hover:text-white">
            Sign out
          </button>
        </div>
      </aside>
      <main className="min-w-0 flex-1 p-4 sm:p-6 lg:p-8">
        <Outlet />
      </main>
    </div>
  );
}
