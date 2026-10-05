import { useMemo, useRef } from "react";
import { useCouriers, useOrders } from "../lib/queries";
import { useRealtime } from "../lib/realtime";
import { courierStatusColor, courierStatusLabel, timeAgo } from "../lib/format";
import type { CourierPosition } from "../lib/types";
import { Badge, Card, PageHeader } from "../components/ui";
import { MapView, esc, type MapHandle, type MapMarker } from "../components/MapView";

export function LiveMapPage() {
  const { positions, fleet } = useRealtime();
  const couriers = useCouriers();
  const orders = useOrders();
  const mapRef = useRef<MapHandle>(null);

  // Live WebSocket positions win; fall back to the last position the REST API knows.
  const rows = useMemo(() => {
    const live = new Map(positions.map((p) => [p.courierId, p]));
    const list = (couriers.data ?? []).map((c) => ({
      id: c.id,
      name: c.name,
      activeOrders: c.activeOrders,
      position: live.get(c.id) ?? c.lastPosition,
    }));
    // Couriers on the socket that the REST list doesn't know yet.
    for (const p of positions) {
      if (!list.some((r) => r.id === p.courierId)) list.push({ id: p.courierId, name: p.name, activeOrders: 0, position: p });
    }
    return list.sort((a, b) => Number(isOnline(b.position)) - Number(isOnline(a.position)) || a.name.localeCompare(b.name));
  }, [positions, couriers.data]);

  const markers = useMemo<MapMarker[]>(() => {
    const out: MapMarker[] = [];
    for (const o of orders.data ?? []) {
      if (o.status === "DELIVERED" || o.status === "FAILED") continue;
      out.push({
        id: `order-${o.id}`,
        latitude: o.latitude,
        longitude: o.longitude,
        color: o.status === "CREATED" ? "#8a99ab" : "#FFC72C",
        shape: "square",
        popup: `<strong>${esc(o.code)}</strong><br/>${esc(o.customerName)}<br/><small>${esc(o.address)}</small><br/><small>${esc(o.courierName ?? "Unassigned")}</small>`,
      });
    }
    for (const r of rows) {
      if (!r.position) continue;
      const status = isOnline(r.position) ? r.position.status : "OFFLINE";
      out.push({
        id: `courier-${r.id}`,
        latitude: r.position.latitude,
        longitude: r.position.longitude,
        color: courierStatusColor[status],
        label: r.name,
        dimmed: status === "OFFLINE",
        popup: `<strong>${esc(r.name)}</strong><br/>${courierStatusLabel[status]} · ${timeAgo(r.position.updatedAt)}`,
      });
    }
    return out;
  }, [orders.data, rows]);

  const onlineCount = rows.filter((r) => isOnline(r.position)).length;

  return (
    <>
      <PageHeader
        title="Live map"
        subtitle={`${onlineCount} of ${rows.length} couriers online · positions streamed over WebSocket`}
        actions={
          <Badge tone={fleet === "open" ? "ok" : fleet === "connecting" ? "warn" : "danger"}>
            {fleet === "open" ? "● Live" : fleet === "connecting" ? "Connecting…" : "Disconnected"}
          </Badge>
        }
      />
      <div className="grid gap-4 lg:grid-cols-[1fr_300px]">
        <Card className="overflow-hidden p-1">
          <MapView ref={mapRef} markers={markers} className="h-[60vh] min-h-[420px] lg:h-[calc(100vh-180px)]" zoom={14} />
        </Card>
        <Card className="max-h-[calc(100vh-180px)] overflow-y-auto">
          <div className="border-b border-line px-4 py-3 text-xs font-semibold uppercase tracking-wide text-muted">Couriers</div>
          {rows.length === 0 && <p className="p-4 text-sm text-muted">No couriers yet.</p>}
          <ul className="divide-y divide-line">
            {rows.map((r) => {
              const status = r.position && isOnline(r.position) ? r.position.status : "OFFLINE";
              return (
                <li key={r.id}>
                  <button
                    className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-surface-2 disabled:cursor-default disabled:hover:bg-transparent"
                    disabled={!r.position}
                    onClick={() => r.position && mapRef.current?.flyTo(r.position.latitude, r.position.longitude)}
                  >
                    <span className="size-3 shrink-0 rounded-full" style={{ background: courierStatusColor[status] }} />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium">{r.name}</span>
                      <span className="block text-xs text-muted">
                        {courierStatusLabel[status]}
                        {r.position ? ` · ${timeAgo(r.position.updatedAt)}` : " · never seen"}
                      </span>
                    </span>
                    {r.activeOrders > 0 && <Badge tone="brand">{r.activeOrders}</Badge>}
                  </button>
                </li>
              );
            })}
          </ul>
          <div className="space-y-1 border-t border-line px-4 py-3 text-xs text-muted">
            <p className="flex items-center gap-2">
              <span className="size-2.5 rounded-sm bg-[#FFC72C]" /> Active order
            </p>
            <p className="flex items-center gap-2">
              <span className="size-2.5 rounded-sm bg-[#8a99ab]" /> Unassigned order
            </p>
          </div>
        </Card>
      </div>
    </>
  );
}

function isOnline(p: CourierPosition | null | undefined): p is CourierPosition {
  return !!p && p.status !== "OFFLINE" && Date.now() - p.updatedAt < 2 * 60_000;
}
