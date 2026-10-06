import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../lib/api";
import { useCouriers, useEvidence } from "../lib/queries";
import { bytes, dateTime, timeAgo } from "../lib/format";
import type { EvidenceItem } from "../lib/types";
import { Badge, Button, Card, Empty, ErrorNote, Field, Hash, Input, Modal, PageHeader, Select, Spinner } from "../components/ui";

export function EvidencePage() {
  const couriers = useCouriers();
  const [courierId, setCourierId] = useState("");
  const [orderFilter, setOrderFilter] = useState("");
  const evidence = useEvidence(courierId || undefined);
  const [open, setOpen] = useState<EvidenceItem | null>(null);

  const visible = useMemo(() => {
    const q = orderFilter.trim().toLowerCase();
    const list = evidence.data ?? [];
    return q ? list.filter((e) => e.orderCode.toLowerCase().includes(q)) : list;
  }, [evidence.data, orderFilter]);

  return (
    <>
      <PageHeader
        title="Evidence"
        subtitle="Proof-of-delivery photos. Each record is hash-chained per courier; verification re-hashes every stored photo."
      />

      <div className="mb-4 grid items-start gap-4 lg:grid-cols-[1fr_360px]">
        <Card className="flex flex-wrap items-end gap-3 p-4">
          <Field label="Courier">
            <Select value={courierId} onChange={(e) => setCourierId(e.target.value)} className="min-w-48">
              <option value="">All couriers</option>
              {(couriers.data ?? []).map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Order code">
            <Input value={orderFilter} onChange={(e) => setOrderFilter(e.target.value)} placeholder="PD-1001" className="w-40" />
          </Field>
          <p className="ml-auto text-sm text-muted">{visible.length} record(s)</p>
        </Card>
        <VerifyPanel courierId={courierId} courierName={couriers.data?.find((c) => c.id === courierId)?.name} />
      </div>

      <ErrorNote error={evidence.error} />
      {evidence.isPending ? (
        <div className="flex justify-center p-10 text-muted">
          <Spinner />
        </div>
      ) : visible.length === 0 ? (
        <Card>
          <Empty title="No evidence yet" body="Records appear here as soon as a courier's phone uploads a sealed delivery photo." />
        </Card>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
          {visible.map((item) => (
            <EvidenceCard key={item.id} item={item} onOpen={() => setOpen(item)} />
          ))}
        </div>
      )}

      {open && <EvidenceDialog item={open} onClose={() => setOpen(null)} />}
    </>
  );
}

/** The stored photo, or a clear placeholder if it can't be decoded (the hash check is what proves integrity). */
function EvidencePhoto({ item, className, lazy }: { item: EvidenceItem; className: string; lazy?: boolean }) {
  const [broken, setBroken] = useState(false);
  if (broken) {
    return (
      <div className={`flex aspect-[4/3] items-center justify-center bg-surface-2 p-4 text-center text-xs text-muted ${className}`}>
        Photo can't be displayed ({bytes(item.sizeBytes)}). Use "Verify chain" to check its hash.
      </div>
    );
  }
  return (
    <img
      src={api.photoUrl(item.id)}
      alt={`Proof of delivery for ${item.orderCode}`}
      loading={lazy ? "lazy" : undefined}
      onError={() => setBroken(true)}
      className={className}
    />
  );
}

function Badges({ item }: { item: EvidenceItem }) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {item.latitude != null ? <Badge tone="info">GPS</Badge> : <Badge>No GPS</Badge>}
      {item.bleVerified ? <Badge tone="ok">Beacon verified</Badge> : <Badge>No beacon</Badge>}
      {!item.orderMatched && (
        <span title="The order was cancelled or reassigned when this proof arrived. It is kept in the courier's chain but did not complete the order.">
          <Badge tone="warn">Needs review</Badge>
        </span>
      )}
    </div>
  );
}

function EvidenceCard({ item, onOpen }: { item: EvidenceItem; onOpen: () => void }) {
  return (
    <Card className="overflow-hidden">
      <button onClick={onOpen} className="block aspect-[4/3] w-full overflow-hidden bg-surface-2" title="Open">
        <EvidencePhoto item={item} lazy className="size-full object-cover transition hover:scale-[1.02]" />
      </button>
      <div className="space-y-2 p-4">
        <div className="flex items-start justify-between gap-2">
          <div>
            <p className="font-semibold">
              {item.orderCode} <span className="font-normal text-muted">· #{item.sequence}</span>
            </p>
            <p className="text-xs text-muted">
              {item.courierName} · {timeAgo(item.capturedAt)}
            </p>
          </div>
        </div>
        <Badges item={item} />
        <div className="text-xs text-muted">
          photo <Hash value={item.fileSha256} /> · record <Hash value={item.recordHash} />
        </div>
      </div>
    </Card>
  );
}

function EvidenceDialog({ item, onClose }: { item: EvidenceItem; onClose: () => void }) {
  const rows: [string, React.ReactNode][] = [
    ["Order", item.orderCode],
    ["Courier", item.courierName],
    ["Sequence", `#${item.sequence}`],
    ["Captured", dateTime(item.capturedAt)],
    ["Received", dateTime(item.receivedAt)],
    ["Size", bytes(item.sizeBytes)],
    [
      "Location",
      item.latitude != null && item.longitude != null ? (
        <a
          className="text-info underline"
          target="_blank"
          rel="noreferrer"
          href={`https://www.openstreetmap.org/?mlat=${item.latitude}&mlon=${item.longitude}#map=18/${item.latitude}/${item.longitude}`}
        >
          {item.latitude.toFixed(5)}, {item.longitude.toFixed(5)}
        </a>
      ) : (
        "—"
      ),
    ],
    ["Photo SHA-256", <Hash value={item.fileSha256} n={24} />],
    ["Previous hash", <Hash value={item.previousHash} n={24} />],
    ["Record hash", <Hash value={item.recordHash} n={24} />],
  ];
  return (
    <Modal title={`Evidence #${item.sequence} · ${item.orderCode}`} onClose={onClose} wide>
      <div className="grid gap-5 md:grid-cols-[3fr_2fr]">
        <EvidencePhoto item={item} className="w-full rounded-xl border border-line bg-surface-2 object-contain" />
        <div className="space-y-3">
          <Badges item={item} />
          <dl className="space-y-2 text-sm">
            {rows.map(([k, v]) => (
              <div key={k} className="grid grid-cols-[110px_1fr] gap-2">
                <dt className="text-muted">{k}</dt>
                <dd className="min-w-0 break-words">{v}</dd>
              </div>
            ))}
          </dl>
        </div>
      </div>
    </Modal>
  );
}

function VerifyPanel({ courierId, courierName }: { courierId: string; courierName?: string }) {
  const verify = useQuery({
    queryKey: ["verify", courierId],
    queryFn: () => api.verifyChain(courierId),
    enabled: false, // only on demand: it re-downloads every photo
    retry: false,
  });

  return (
    <Card className="p-4">
      <p className="text-sm font-semibold">Chain verification</p>
      {!courierId ? (
        <p className="mt-1 text-sm text-muted">Pick a courier to verify their evidence chain.</p>
      ) : (
        <>
          <p className="mt-1 text-sm text-muted">Re-hash every photo and link for {courierName ?? "this courier"}.</p>
          <Button className="mt-3" variant="primary" disabled={verify.isFetching} onClick={() => void verify.refetch()}>
            {verify.isFetching && <Spinner />} Verify chain
          </Button>
          {verify.error && (
            <div className="mt-3">
              <ErrorNote error={verify.error} />
            </div>
          )}
          {verify.data && !verify.isFetching && (
            <div
              className={`mt-3 rounded-lg px-3 py-2 text-sm ${verify.data.valid ? "bg-ok/15 text-ok" : "bg-danger/15 text-danger"}`}
            >
              {verify.data.valid ? (
                <>
                  <strong>Chain intact</strong> · {verify.data.count} record(s), every photo and link matches.
                </>
              ) : (
                <>
                  <strong>Tampering detected at #{verify.data.atSequence}</strong> · {verify.data.reason}
                </>
              )}
            </div>
          )}
        </>
      )}
    </Card>
  );
}
