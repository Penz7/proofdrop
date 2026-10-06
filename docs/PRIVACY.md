# ProofDrop Privacy Policy

_Last updated: 6 October 2026_

ProofDrop is a proof-of-delivery app for couriers. It is used by delivery companies ("your employer" or "the operator") to record that parcels were delivered. This policy explains what the ProofDrop Android app and the ProofDrop dispatch service collect, why, and what control you have.

The data described here is sent only to **the ProofDrop server operated by your employer**. The app's developer does not receive it.

## What we collect and why

### Photos (camera)
- **What:** a photo you take when you deliver an order, plus a SHA-256 fingerprint of that photo.
- **Why:** to prove the delivery happened and to detect if the photo is altered later.
- **How:** photos are stored in the app's private storage (not your gallery, not visible to other apps) and uploaded to your employer's server, where only dispatchers can view them. The camera is used only while the capture screen or the device QR scanner is open. QR codes are read on the device and never uploaded.

### Precise location
- **What:** your GPS position.
- **When:** only (1) while you are **on shift**, which shows a permanent "On shift" notification (an Android foreground service), and (2) at the moment you capture a proof-of-delivery photo. Location is never collected when you are off shift and not capturing.
- **Why:** to show dispatchers where couriers are, and to record where each delivery was made.
- **How:** your latest position is sent to your employer's server every few seconds during a shift. The server keeps only your last known position; past positions are not stored as a history. The position at capture time is stored with that delivery's proof.

### Bluetooth scanning
- **What:** the names and signal strength of nearby Bluetooth Low Energy devices.
- **When:** only while the capture screen is open for an order that has a drop-off beacon.
- **Why:** to confirm you are physically at the drop-off point (a "beacon verified" delivery).
- **How:** the scan is declared `neverForLocation` and is not used to locate you. Only a yes/no "beacon verified" result is stored with the proof; the list of nearby devices is not saved or uploaded.

### Notifications
Used to tell you about newly assigned deliveries and to show that a shift is active. You can turn them off in Android settings; you will still see new orders in the app.

### Account data
Your name, work email and role, created by your employer. Your password is stored only as a salted hash. Your sign-in token is kept on the phone, encrypted with a key held in the Android Keystore.

### Delivery and device records
The orders assigned to you, their status changes, and which work devices (scanners, cameras, vehicles) you check out and return.

## What we don't do
- No advertising, and no advertising identifiers.
- No selling or sharing of your data with third parties.
- No third-party analytics or crash-reporting SDKs.

## Third-party services
- **Map tiles:** the fleet map loads map images from OpenStreetMap's tile servers (tile.openstreetmap.org). Like any website, those servers see your IP address and which map area is requested. See the [OpenStreetMap Foundation privacy policy](https://osmfoundation.org/wiki/Privacy_Policy).

## Retention and deletion
- Proof-of-delivery records are kept for as long as your employer needs them as delivery evidence, according to their own retention policy.
- Signing out of the app deletes all ProofDrop data from the phone (the app warns you first if any proof hasn't been uploaded yet). Uninstalling the app also deletes it.
- To access or delete data held on the server, contact your employer, who operates the ProofDrop server and controls that data.

## Security
Data is sent over HTTPS in production deployments. Delivery records form a tamper-evident hash chain, and both the app and the server verify photos against their fingerprints.

## Children
ProofDrop is a workplace tool and is not intended for children.

## Changes and contact
We will update this page when the app's data practices change; the date at the top shows the latest version.

Questions about the app: open an issue at <https://github.com/Penz7/proofdrop/issues>. Questions about your data on your employer's server: contact your employer.
