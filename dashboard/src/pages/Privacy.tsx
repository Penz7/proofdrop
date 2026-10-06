import type { ReactNode } from "react";
import { Link } from "react-router";

/**
 * Public privacy policy for the ProofDrop courier app (linked from the Play Store listing and the
 * app). Keep in sync with docs/PRIVACY.md.
 */
export function PrivacyPage() {
  return (
    <div className="min-h-screen bg-bg px-4 py-10 text-fg sm:px-6">
      <article className="mx-auto max-w-3xl rounded-2xl border border-line bg-surface p-6 leading-relaxed sm:p-10">
        <header className="mb-8 border-b border-line pb-6">
          <p className="text-sm font-semibold text-brand-strong">ProofDrop</p>
          <h1 className="mt-1 text-3xl font-semibold">Privacy Policy</h1>
          <p className="mt-2 text-sm text-muted">Last updated: 6 October 2026</p>
        </header>

        <P>
          ProofDrop is a proof-of-delivery app for couriers. It is used by delivery companies ("your employer" or "the
          operator") to record that parcels were delivered. This policy explains what the ProofDrop Android app and the
          ProofDrop dispatch service collect, why, and what control you have.
        </P>
        <P>
          The data described here is sent only to <strong>the ProofDrop server operated by your employer</strong>. The
          app's developer does not receive it.
        </P>

        <H2>What we collect and why</H2>

        <H3>Photos (camera)</H3>
        <Ul>
          <li><B>What:</B> a photo you take when you deliver an order, plus a SHA-256 fingerprint of that photo.</li>
          <li><B>Why:</B> to prove the delivery happened and to detect if the photo is altered later.</li>
          <li>
            <B>How:</B> photos are stored in the app's private storage (not your gallery, not visible to other apps) and
            uploaded to your employer's server, where only dispatchers can view them. The camera is used only while the
            capture screen or the device QR scanner is open. QR codes are read on the device and never uploaded.
          </li>
        </Ul>

        <H3>Precise location</H3>
        <Ul>
          <li><B>What:</B> your GPS position.</li>
          <li>
            <B>When:</B> only (1) while you are <strong>on shift</strong>, which shows a permanent "On shift"
            notification (an Android foreground service), and (2) at the moment you capture a proof-of-delivery photo.
            Location is never collected when you are off shift and not capturing.
          </li>
          <li><B>Why:</B> to show dispatchers where couriers are, and to record where each delivery was made.</li>
          <li>
            <B>How:</B> your latest position is sent to your employer's server every few seconds during a shift. The
            server keeps only your last known position; past positions are not stored as a history. The position at
            capture time is stored with that delivery's proof.
          </li>
        </Ul>

        <H3>Bluetooth scanning</H3>
        <Ul>
          <li><B>What:</B> the names and signal strength of nearby Bluetooth Low Energy devices.</li>
          <li><B>When:</B> only while the capture screen is open for an order that has a drop-off beacon.</li>
          <li><B>Why:</B> to confirm you are physically at the drop-off point (a "beacon verified" delivery).</li>
          <li>
            <B>How:</B> the scan is declared <code>neverForLocation</code> and is not used to locate you. Only a yes/no
            "beacon verified" result is stored with the proof; the list of nearby devices is not saved or uploaded.
          </li>
        </Ul>

        <H3>Notifications</H3>
        <P>
          Used to tell you about newly assigned deliveries and to show that a shift is active. You can turn them off in
          Android settings; you will still see new orders in the app.
        </P>

        <H3>Account data</H3>
        <P>
          Your name, work email and role, created by your employer. Your password is stored only as a salted hash. Your
          sign-in token is kept on the phone, encrypted with a key held in the Android Keystore.
        </P>

        <H3>Delivery and device records</H3>
        <P>
          The orders assigned to you, their status changes, and which work devices (scanners, cameras, vehicles) you
          check out and return.
        </P>

        <H2>What we don't do</H2>
        <Ul>
          <li>No advertising, and no advertising identifiers.</li>
          <li>No selling or sharing of your data with third parties.</li>
          <li>No third-party analytics or crash-reporting SDKs.</li>
        </Ul>

        <H2>Third-party services</H2>
        <P>
          <B>Map tiles:</B> the fleet map loads map images from OpenStreetMap's tile servers (tile.openstreetmap.org).
          Like any website, those servers see your IP address and which map area is requested. See the{" "}
          <A href="https://osmfoundation.org/wiki/Privacy_Policy">OpenStreetMap Foundation privacy policy</A>.
        </P>

        <H2>Retention and deletion</H2>
        <Ul>
          <li>
            Proof-of-delivery records are kept for as long as your employer needs them as delivery evidence, according to
            their own retention policy.
          </li>
          <li>
            Signing out of the app deletes all ProofDrop data from the phone (the app warns you first if any proof hasn't
            been uploaded yet). Uninstalling the app also deletes it.
          </li>
          <li>
            To access or delete data held on the server, contact your employer, who operates the ProofDrop server and
            controls that data.
          </li>
        </Ul>

        <H2>Security</H2>
        <P>
          Data is sent over HTTPS in production deployments. Delivery records form a tamper-evident hash chain, and both
          the app and the server verify photos against their fingerprints.
        </P>

        <H2>Children</H2>
        <P>ProofDrop is a workplace tool and is not intended for children.</P>

        <H2>Changes and contact</H2>
        <P>We will update this page when the app's data practices change; the date at the top shows the latest version.</P>
        <P>
          Questions about the app: open an issue at{" "}
          <A href="https://github.com/Penz7/proofdrop/issues">github.com/Penz7/proofdrop/issues</A>. Questions about your
          data on your employer's server: contact your employer.
        </P>

        <footer className="mt-10 border-t border-line pt-6 text-sm">
          <Link to="/login" className="text-muted underline-offset-2 hover:text-fg hover:underline">
            ← Dispatcher sign-in
          </Link>
        </footer>
      </article>
    </div>
  );
}

const H2 = ({ children }: { children: ReactNode }) => <h2 className="mt-8 mb-3 text-xl font-semibold">{children}</h2>;
const H3 = ({ children }: { children: ReactNode }) => <h3 className="mt-5 mb-2 font-semibold">{children}</h3>;
const P = ({ children }: { children: ReactNode }) => <p className="mb-3 text-[15px]">{children}</p>;
const B = ({ children }: { children: ReactNode }) => <strong className="font-semibold">{children}</strong>;
const Ul = ({ children }: { children: ReactNode }) => (
  <ul className="mb-3 list-disc space-y-1.5 pl-6 text-[15px] marker:text-muted">{children}</ul>
);
const A = ({ href, children }: { href: string; children: ReactNode }) => (
  <a href={href} target="_blank" rel="noreferrer" className="font-medium text-info underline underline-offset-2">
    {children}
  </a>
);
