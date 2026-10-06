import { useMemo, useState, type FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "../lib/api";
import { useCouriers, useOrders } from "../lib/queries";
import { orderStatusLabel, timeAgo } from "../lib/format";
import { HUB, type NewOrderInput, type Order, type OrderStatus } from "../lib/types";
import { Badge, Button, Card, Empty, ErrorNote, Field, Input, Modal, PageHeader, Select, Spinner, StatusBadge } from "../components/ui";
import { MapView } from "../components/MapView";

const filters: (OrderStatus | "ALL" | "ACTIVE")[] = ["ACTIVE", "ALL", "CREATED", "ASSIGNED", "PICKED_UP", "DELIVERED", "FAILED", "CANCELLED"];
const isActive = (s: OrderStatus) => s === "CREATED" || s === "ASSIGNED" || s === "PICKED_UP";

export function OrdersPage() {
  const orders = useOrders();
  const couriers = useCouriers();
  const [filter, setFilter] = useState<(typeof filters)[number]>("ACTIVE");
  const [creating, setCreating] = useState(false);

  const visible = useMemo(() => {
    const list = orders.data ?? [];
    if (filter === "ALL") return list;
    if (filter === "ACTIVE") return list.filter((o) => isActive(o.status));
    return list.filter((o) => o.status === filter);
  }, [orders.data, filter]);

  const counts = useMemo(() => {
    const c: Record<string, number> = { ALL: 0, ACTIVE: 0 };
    for (const o of orders.data ?? []) {
      c.ALL++;
      if (isActive(o.status)) c.ACTIVE++;
      c[o.status] = (c[o.status] ?? 0) + 1;
    }
    return c;
  }, [orders.data]);

  return (
    <>
      <PageHeader
        title="Orders"
        subtitle="Create deliveries and assign them to couriers. Changes reach the courier's phone instantly over SSE."
        actions={
          <Button variant="primary" onClick={() => setCreating(true)}>
            + New order
          </Button>
        }
      />

      <div className="mb-4 flex flex-wrap gap-2">
        {filters.map((f) => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={`rounded-full border px-3 py-1 text-sm transition ${
              filter === f ? "border-brand bg-brand/20 font-semibold text-fg" : "border-line text-muted hover:text-fg"
            }`}
          >
            {f === "ALL" ? "All" : f === "ACTIVE" ? "Active" : orderStatusLabel[f]}
            <span className="ml-1.5 text-xs opacity-70">{counts[f] ?? 0}</span>
          </button>
        ))}
      </div>

      <Card className="overflow-hidden">
        <ErrorNote error={orders.error} />
        {orders.isPending ? (
          <div className="flex justify-center p-10 text-muted">
            <Spinner />
          </div>
        ) : visible.length === 0 ? (
          <Empty title="No orders here" body="Create an order or pick another filter." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead className="bg-surface-2 text-xs uppercase tracking-wide text-muted">
                <tr>
                  <th className="px-4 py-3">Order</th>
                  <th className="px-4 py-3">Customer</th>
                  <th className="px-4 py-3">Items</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Courier</th>
                  <th className="px-4 py-3">Time</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {visible.map((order) => (
                  <OrderRow key={order.id} order={order} couriers={couriers.data ?? []} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {creating && <NewOrderDialog onClose={() => setCreating(false)} />}
    </>
  );
}

function OrderRow({ order, couriers }: { order: Order; couriers: { id: string; name: string; online: boolean }[] }) {
  const queryClient = useQueryClient();
  const assign = useMutation({
    mutationFn: (courierId: string | null) => api.assignOrder(order.id, courierId),
    onSuccess: (updated) => {
      queryClient.setQueryData<Order[]>(["orders"], (list) => list?.map((o) => (o.id === updated.id ? updated : o)));
      void queryClient.invalidateQueries({ queryKey: ["couriers"] });
    },
  });
  const cancel = useMutation({
    mutationFn: () => api.cancelOrder(order.id),
    onSuccess: (updated) => {
      queryClient.setQueryData<Order[]>(["orders"], (list) => list?.map((o) => (o.id === updated.id ? updated : o)));
      void queryClient.invalidateQueries({ queryKey: ["couriers"] });
      setConfirming(false);
    },
  });
  const [confirming, setConfirming] = useState(false);
  const locked = order.status === "DELIVERED" || order.status === "CANCELLED";

  return (
    <tr className="align-top hover:bg-surface-2/50">
      <td className="px-4 py-3">
        <p className="font-semibold">{order.code}</p>
        {order.beaconId && (
          <p className="mt-1">
            <Badge tone="info">⌁ {order.beaconId}</Badge>
          </p>
        )}
      </td>
      <td className="px-4 py-3">
        <p className="font-medium">{order.customerName}</p>
        <p className="text-xs text-muted">{order.address}</p>
        {order.customerPhone && <p className="text-xs text-muted">{order.customerPhone}</p>}
      </td>
      <td className="max-w-56 px-4 py-3 text-muted">{order.items}</td>
      <td className="px-4 py-3">
        <StatusBadge status={order.status} />
        {!locked && (
          <p className="mt-1.5">
            <button
              type="button"
              onClick={() => setConfirming(true)}
              className="rounded text-xs text-muted underline-offset-2 hover:text-danger hover:underline focus-visible:outline-2 focus-visible:outline-brand"
            >
              Cancel order
            </button>
          </p>
        )}
        {confirming && (
          <Modal title={`Cancel ${order.code}?`} onClose={() => setConfirming(false)}>
            <p className="text-sm text-muted">
              {order.courierName
                ? `${order.courierName} will see it disappear from their list right away. `
                : ""}
              A cancelled order can't be reassigned or delivered. Proof that still arrives for it is kept and flagged for review.
            </p>
            <ErrorNote error={cancel.error} />
            <div className="mt-4 flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setConfirming(false)}>
                Keep order
              </Button>
              <Button variant="danger" disabled={cancel.isPending} onClick={() => cancel.mutate()}>
                {cancel.isPending && <Spinner />} Cancel order
              </Button>
            </div>
          </Modal>
        )}
      </td>
      <td className="px-4 py-3">
        <div className="flex items-center gap-2">
          <Select
            aria-label={`Courier for ${order.code}`}
            className="min-w-40 py-1.5"
            disabled={locked || assign.isPending}
            value={order.courierId ?? ""}
            onChange={(e) => assign.mutate(e.target.value || null)}
          >
            <option value="">Unassigned</option>
            {couriers.map((c) => (
              <option key={c.id} value={c.id}>
                {c.online ? "● " : "○ "}
                {c.name}
              </option>
            ))}
            {order.courierId && !couriers.some((c) => c.id === order.courierId) && (
              <option value={order.courierId}>{order.courierName ?? "Unknown courier"}</option>
            )}
          </Select>
          {assign.isPending && <Spinner />}
        </div>
        {assign.error && <p className="mt-1 text-xs text-danger">{(assign.error as Error).message}</p>}
      </td>
      <td className="whitespace-nowrap px-4 py-3 text-xs text-muted">
        {order.deliveredAt ? `Delivered ${timeAgo(order.deliveredAt)}` : `${order.courierId ? "Assigned" : "Created"} ${timeAgo(order.assignedAt)}`}
      </td>
    </tr>
  );
}

function NewOrderDialog({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const couriers = useCouriers();
  const [form, setForm] = useState({
    customerName: "",
    customerPhone: "",
    address: "",
    items: "",
    beaconId: "",
    courierId: "",
    latitude: HUB.latitude,
    longitude: HUB.longitude,
  });
  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }));

  const create = useMutation({
    mutationFn: (input: NewOrderInput) => api.createOrder(input),
    onSuccess: (order) => {
      queryClient.setQueryData<Order[]>(["orders"], (list) => (list ? [order, ...list.filter((o) => o.id !== order.id)] : list));
      void queryClient.invalidateQueries({ queryKey: ["couriers"] });
      onClose();
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    create.mutate({
      customerName: form.customerName.trim(),
      customerPhone: form.customerPhone.trim() || undefined,
      address: form.address.trim(),
      items: form.items.trim(),
      beaconId: form.beaconId.trim() || undefined,
      courierId: form.courierId || undefined,
      latitude: Number(form.latitude.toFixed(6)),
      longitude: Number(form.longitude.toFixed(6)),
    });
  }

  return (
    <Modal title="New order" onClose={onClose} wide>
      <form onSubmit={submit} className="grid gap-5 md:grid-cols-2">
        <div className="space-y-3">
          <Field label="Customer name">
            <Input required value={form.customerName} onChange={set("customerName")} placeholder="Nguyễn An" />
          </Field>
          <Field label="Phone (optional)">
            <Input value={form.customerPhone} onChange={set("customerPhone")} placeholder="0901 234 567" />
          </Field>
          <Field label="Address">
            <Input required value={form.address} onChange={set("address")} placeholder="12 Nguyễn Huệ, Q.1, TP.HCM" />
          </Field>
          <Field label="Items">
            <Input required value={form.items} onChange={set("items")} placeholder="2x Cà phê sữa đá" />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Drop-off beacon" hint="BLE name, e.g. PD-BEACON-01">
              <Input value={form.beaconId} onChange={set("beaconId")} placeholder="Optional" />
            </Field>
            <Field label="Assign to">
              <Select value={form.courierId} onChange={set("courierId")}>
                <option value="">Leave unassigned</option>
                {(couriers.data ?? []).map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                    {c.online ? " (online)" : ""}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
        </div>
        <div className="flex flex-col">
          <p className="mb-1 text-xs font-medium text-muted">Drop-off location (click the map)</p>
          <MapView
            className="h-64 flex-1 border border-line md:h-auto"
            zoom={14}
            onPick={(latitude, longitude) => setForm((f) => ({ ...f, latitude, longitude }))}
            markers={[{ id: "pick", latitude: form.latitude, longitude: form.longitude, color: "#FFC72C", shape: "pin" }]}
          />
          <p className="mt-1 font-mono text-xs text-muted">
            {form.latitude.toFixed(6)}, {form.longitude.toFixed(6)}
          </p>
        </div>
        <div className="md:col-span-2">
          <ErrorNote error={create.error} />
          <div className="mt-3 flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" disabled={create.isPending}>
              {create.isPending && <Spinner />} Create order
            </Button>
          </div>
        </div>
      </form>
    </Modal>
  );
}
