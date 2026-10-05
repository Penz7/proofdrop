import { useEffect, useState } from "react";
import { Link } from "react-router";
import QRCode from "qrcode";
import { useDevices } from "../lib/queries";
import { deviceTypeLabel } from "../lib/format";
import type { Device } from "../lib/types";
import { Button, PageHeader, Spinner } from "../components/ui";

/** Printable sheet of QR stickers. Each code contains only the device id, which the app scans. */
export function PrintQrPage() {
  const devices = useDevices();
  return (
    <>
      <div className="no-print">
        <PageHeader
          title="QR stickers"
          subtitle="Print and stick these on the devices. The courier app scans them to check a device in or out."
          actions={
            <>
              <Link to="/devices">
                <Button variant="ghost">← Back</Button>
              </Link>
              <Button variant="primary" onClick={() => window.print()}>
                Print
              </Button>
            </>
          }
        />
      </div>
      {devices.isPending ? (
        <Spinner />
      ) : (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 print:grid-cols-3 print:gap-3">
          {(devices.data ?? []).map((d) => (
            <Sticker key={d.id} device={d} />
          ))}
        </div>
      )}
    </>
  );
}

function Sticker({ device }: { device: Device }) {
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => {
    let alive = true;
    void QRCode.toDataURL(device.id, { margin: 1, width: 360, errorCorrectionLevel: "M" }).then((url) => alive && setSrc(url));
    return () => {
      alive = false;
    };
  }, [device.id]);

  return (
    <div className="break-inside-avoid rounded-xl border-2 border-dashed border-line bg-white p-4 text-center text-[#0E1A2B]">
      {src ? <img src={src} alt={`QR code ${device.id}`} className="mx-auto aspect-square w-full max-w-44" /> : <div className="aspect-square" />}
      <p className="mt-2 font-mono text-lg font-bold tracking-wider">{device.id}</p>
      <p className="text-xs text-[#5b6b7f]">
        {device.name} · {deviceTypeLabel[device.type]}
      </p>
      <p className="mt-1 text-[10px] font-semibold uppercase tracking-widest text-[#8a6400]">ProofDrop</p>
    </div>
  );
}
