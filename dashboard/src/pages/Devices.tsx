import { useState, type FormEvent } from "react";
import { Link } from "react-router";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "../lib/api";
import { useDevices } from "../lib/queries";
import { deviceTypeLabel, timeAgo } from "../lib/format";
import type { Device, DeviceType, NewDeviceInput } from "../lib/types";
import { Badge, Button, Card, Empty, ErrorNote, Field, Input, Modal, PageHeader, Select, Spinner } from "../components/ui";

export function DevicesPage() {
  const devices = useDevices();
  const [adding, setAdding] = useState(false);
  const list = devices.data ?? [];
  const out = list.filter((d) => d.holderId).length;

  return (
    <>
      <PageHeader
        title="Devices"
        subtitle={`${out} of ${list.length} checked out. Couriers check devices in and out by scanning the QR sticker in the app.`}
        actions={
          <>
            <Link to="/devices/print">
              <Button>Print QR stickers</Button>
            </Link>
            <Button variant="primary" onClick={() => setAdding(true)}>
              + Add device
            </Button>
          </>
        }
      />
      <Card className="overflow-hidden">
        <ErrorNote error={devices.error} />
        {devices.isPending ? (
          <div className="flex justify-center p-10 text-muted">
            <Spinner />
          </div>
        ) : list.length === 0 ? (
          <Empty title="No devices" body="Add scanners, body cams, printers or vehicles." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-left text-sm">
              <thead className="bg-surface-2 text-xs uppercase tracking-wide text-muted">
                <tr>
                  <th className="px-4 py-3">Device</th>
                  <th className="px-4 py-3">Type</th>
                  <th className="px-4 py-3">Serial</th>
                  <th className="px-4 py-3">Battery</th>
                  <th className="px-4 py-3">Holder</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {list.map((d) => (
                  <DeviceRow key={d.id} device={d} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      {adding && <AddDeviceDialog onClose={() => setAdding(false)} />}
    </>
  );
}

function DeviceRow({ device: d }: { device: Device }) {
  const low = d.batteryPct < 20;
  return (
    <tr className="hover:bg-surface-2/50">
      <td className="px-4 py-3">
        <p className="font-semibold">{d.name}</p>
        <p className="font-mono text-xs text-muted">{d.id}</p>
      </td>
      <td className="px-4 py-3">{deviceTypeLabel[d.type]}</td>
      <td className="px-4 py-3 font-mono text-xs">{d.serial}</td>
      <td className="px-4 py-3">
        <div className="flex items-center gap-2">
          <div className="h-2 w-20 overflow-hidden rounded-full bg-surface-2">
            <div className={`h-full ${low ? "bg-danger" : "bg-ok"}`} style={{ width: `${Math.max(0, Math.min(100, d.batteryPct))}%` }} />
          </div>
          <span className={`text-xs ${low ? "font-semibold text-danger" : "text-muted"}`}>{d.batteryPct}%</span>
        </div>
      </td>
      <td className="px-4 py-3">
        {d.holderId ? (
          <div>
            <Badge tone="brand">{d.holderName ?? "Courier"}</Badge>
            {d.checkedOutAt && <p className="mt-1 text-xs text-muted">since {timeAgo(d.checkedOutAt)}</p>}
          </div>
        ) : (
          <Badge tone="info">Available</Badge>
        )}
      </td>
    </tr>
  );
}

const types: DeviceType[] = ["SCANNER", "BODY_CAM", "PRINTER", "VEHICLE"];

function AddDeviceDialog({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<NewDeviceInput>({ id: "", name: "", type: "SCANNER", serial: "", batteryPct: 100 });
  const create = useMutation({
    mutationFn: api.createDevice,
    onSuccess: (device) => {
      queryClient.setQueryData<Device[]>(["devices"], (list) => (list ? [...list, device].sort((a, b) => a.id.localeCompare(b.id)) : list));
      onClose();
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    create.mutate({ ...form, id: form.id.trim().toUpperCase(), name: form.name.trim(), serial: form.serial.trim() });
  }

  return (
    <Modal title="Add device" onClose={onClose}>
      <form onSubmit={submit} className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <Field label="Device id" hint="Printed in the QR code">
            <Input required pattern="[A-Za-z0-9\-]+" value={form.id} onChange={(e) => setForm({ ...form, id: e.target.value })} placeholder="DEV-007" />
          </Field>
          <Field label="Type">
            <Select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as DeviceType })}>
              {types.map((t) => (
                <option key={t} value={t}>
                  {deviceTypeLabel[t]}
                </option>
              ))}
            </Select>
          </Field>
        </div>
        <Field label="Name">
          <Input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Zebra TC22 Scanner" />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Serial">
            <Input required value={form.serial} onChange={(e) => setForm({ ...form, serial: e.target.value })} />
          </Field>
          <Field label="Battery %">
            <Input
              type="number"
              min={0}
              max={100}
              required
              value={form.batteryPct}
              onChange={(e) => setForm({ ...form, batteryPct: Number(e.target.value) })}
            />
          </Field>
        </div>
        <ErrorNote error={create.error} />
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" disabled={create.isPending}>
            {create.isPending && <Spinner />} Add device
          </Button>
        </div>
      </form>
    </Modal>
  );
}
