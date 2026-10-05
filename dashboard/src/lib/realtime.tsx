import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { withToken } from "./api";
import { useAuth } from "./auth";
import type { CourierPosition, EvidenceItem, Order } from "./types";

export type LinkState = "connecting" | "open" | "down";

interface RealtimeState {
  events: LinkState;
  fleet: LinkState;
  positions: CourierPosition[];
}

const RealtimeContext = createContext<RealtimeState>({ events: "down", fleet: "down", positions: [] });

const backoff = (attempt: number) => Math.min(30_000, 1_000 * 2 ** attempt);

/** Keeps one SSE stream (order/evidence changes) and one WebSocket (fleet positions) open while signed in. */
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const { token, logout } = useAuth();
  const queryClient = useQueryClient();
  const [events, setEvents] = useState<LinkState>("down");
  const [fleet, setFleet] = useState<LinkState>("down");
  const [positions, setPositions] = useState<CourierPosition[]>([]);

  // Server-Sent Events: keep TanStack Query caches in sync without polling.
  useEffect(() => {
    if (!token) return;
    let source: EventSource | null = null;
    let retry: ReturnType<typeof setTimeout> | undefined;
    let attempt = 0;
    let disposed = false;

    const connect = () => {
      setEvents("connecting");
      source = new EventSource(withToken("/api/dispatch/events"));
      source.onopen = () => {
        attempt = 0;
        setEvents("open");
      };
      source.addEventListener("order", (e) => {
        const order = JSON.parse((e as MessageEvent<string>).data) as Order;
        queryClient.setQueryData<Order[]>(["orders"], (list) => {
          if (!list) return list;
          const i = list.findIndex((o) => o.id === order.id);
          if (i === -1) return [order, ...list];
          const next = list.slice();
          next[i] = order;
          return next;
        });
        void queryClient.invalidateQueries({ queryKey: ["couriers"] });
      });
      source.addEventListener("evidence", (e) => {
        const item = JSON.parse((e as MessageEvent<string>).data) as EvidenceItem;
        void queryClient.invalidateQueries({ queryKey: ["evidence"] });
        void queryClient.invalidateQueries({ queryKey: ["verify", item.courierId] });
      });
      source.onerror = () => {
        // EventSource retries by itself while CONNECTING; take over once it gives up.
        if (source?.readyState === EventSource.CLOSED && !disposed) {
          setEvents("down");
          source.close();
          retry = setTimeout(connect, backoff(attempt++));
        } else {
          setEvents("connecting");
        }
      };
    };

    connect();
    return () => {
      disposed = true;
      clearTimeout(retry);
      source?.close();
      setEvents("down");
    };
  }, [token, queryClient]);

  // WebSocket fleet feed.
  useEffect(() => {
    if (!token) return;
    let socket: WebSocket | null = null;
    let retry: ReturnType<typeof setTimeout> | undefined;
    let attempt = 0;
    let disposed = false;

    const connect = () => {
      setFleet("connecting");
      const scheme = location.protocol === "https:" ? "wss" : "ws";
      socket = new WebSocket(`${scheme}://${location.host}${withToken("/fleet")}`);
      socket.onopen = () => {
        attempt = 0;
        setFleet("open");
      };
      socket.onmessage = (e) => {
        try {
          const msg = JSON.parse(e.data as string) as { event: string; data: unknown };
          if (msg.event === "fleet") setPositions(msg.data as CourierPosition[]);
        } catch {
          /* ignore malformed frames */
        }
      };
      socket.onclose = (e) => {
        if (disposed) return;
        setFleet("down");
        if (e.code === 4401) {
          logout();
          return;
        }
        retry = setTimeout(connect, backoff(attempt++));
      };
    };

    connect();
    return () => {
      disposed = true;
      clearTimeout(retry);
      socket?.close();
      setFleet("down");
    };
  }, [token, logout]);

  const value = useMemo(() => ({ events, fleet, positions }), [events, fleet, positions]);
  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>;
}

export function useRealtime() {
  return useContext(RealtimeContext);
}
