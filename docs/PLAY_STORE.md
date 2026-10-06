# Publishing the Android app on Google Play

## 1. Signing

ProofDrop uses **Play App Signing**. You sign uploads with your *upload key*; Google re-signs with the app signing key it keeps.

- Upload keystore: `keystore/upload-keystore.jks` inside the project; the whole `keystore/` folder is git-ignored.
- `keystore.properties` at the repo root (git-ignored) points Gradle at it:

  ```properties
  storeFile=keystore/upload-keystore.jks
  storePassword=…
  keyAlias=upload
  keyPassword=…
  ```

- Without that file, release builds fall back to the debug key. CI does this, and such builds cannot be uploaded to Play.
- **Back up the keystore and its password** (password manager plus an offline copy). Losing the upload key means requesting a reset from Play support.

Create a new upload key if needed:

```bash
keytool -genkeypair -v -keystore upload-keystore.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

## 2. Build

```bash
# bump versionCode (+1 every upload) and versionName in app/build.gradle.kts, then:
./gradlew :app:bundleRelease        # → app/build/outputs/bundle/release/app-release.aab
```

Verify the signer before uploading:

```bash
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab | grep "CN="
```

## 3. Before the first release

- [ ] Deploy the backend over **HTTPS** ([DEPLOY.md](DEPLOY.md)). Release builds refuse cleartext HTTP except to local dev hosts.
- [ ] Publish the privacy policy at a public URL. The app links to `docs/PRIVACY.md` on GitHub (`PRIVACY_POLICY_URL` in `app/build.gradle.kts`); change it if you host it elsewhere, e.g. `https://<your-domain>/privacy`.
- [ ] Personal developer accounts created after Nov 2023 must run a **closed test with at least 12 testers for 14 consecutive days** before applying for production access.

## 4. Play Console answers

### App access

Reviewers can use the app without an account: on the login screen, tap **Try demo mode (no server)**. If you want them to see the live system, also give them a courier test account on your deployed server.

### Data safety

| Data type | Collected | Shared | Purpose | Optional? |
|---|---|---|---|---|
| Precise location | Yes | No | App functionality (proof of delivery GPS tag, live position during a shift) | Required for shifts |
| Photos | Yes | No | App functionality (proof-of-delivery photo) | Required to complete a delivery |
| Name, email | Yes | No | Account management (courier login) | Required |
| App interactions / device IDs / analytics | No | | | |

- Data is encrypted in transit: **Yes** (HTTPS in production).
- Users can request deletion: **Yes**. Their employer (the dispatch operator) deletes the account; contact details are in the privacy policy.
- No ads, no data sale, no third-party analytics SDKs.

### Permissions declarations

| Permission | Declaration |
|---|---|
| `FOREGROUND_SERVICE_LOCATION` | The courier explicitly starts a shift. A persistent notification shows while location is shared with their dispatcher, and it stops when they end the shift. Play asks for a short video: show *Start shift* → notification → dispatcher map → *End shift*. |
| `ACCESS_FINE_LOCATION` | GPS tag on proof of delivery; live position during a shift (foreground only, no background location permission requested). |
| `CAMERA` | Proof-of-delivery photo and QR scanning of company devices. |
| `BLUETOOTH_SCAN` (`neverForLocation`) | Detecting the BLE beacon at the drop-off point. |

### Content rating and audience

- Utility / business app. No user-generated public content, no violence.
- Target audience: 18+ (workforce app).

## 5. Store listing

**App name:** ProofDrop: Proof of Delivery

**Short description (EN, ≤80 chars):**
Tamper-evident proof of delivery with GPS, BLE beacons and live dispatch.

**Short description (VI):**
Bằng chứng giao hàng chống giả mạo: ảnh, GPS, beacon BLE và điều phối trực tiếp.

**Full description (EN):**

> ProofDrop gives delivery teams proof that holds up.
>
> • Photograph each drop-off. The photo is fingerprinted (SHA-256) and sealed into a hash chain, so any later edit, deletion or photo swap is detected.
> • GPS tag and optional BLE beacon check prove you were at the door.
> • Works offline: deliveries are queued and synced automatically when you're back online.
> • Start a shift to share your live location with dispatch and get new assignments as notifications.
> • Check company devices (scanners, body cams, scooters) in and out by scanning their QR code.
>
> ProofDrop works with your company's ProofDrop dispatch server. Try it without one in demo mode.

**Full description (VI):**

> ProofDrop giúp đội giao hàng có bằng chứng giao hàng đáng tin cậy.
>
> • Chụp ảnh mỗi lần giao. Ảnh được lấy "dấu vân tay" SHA-256 và niêm phong vào chuỗi hash, mọi chỉnh sửa, xoá hay tráo ảnh đều bị phát hiện.
> • Gắn GPS và kiểm tra beacon BLE (tuỳ chọn) để chứng minh bạn đã đến tận nơi.
> • Hoạt động offline: dữ liệu được xếp hàng và tự đồng bộ khi có mạng.
> • Bắt đầu ca để chia sẻ vị trí trực tiếp với điều phối và nhận đơn mới qua thông báo.
> • Mượn/trả thiết bị công ty (máy quét, body cam, xe) bằng cách quét mã QR.
>
> ProofDrop kết nối với máy chủ điều phối ProofDrop của công ty bạn. Có thể dùng thử không cần máy chủ ở chế độ demo.

**Graphics needed:** 512×512 icon (export the adaptive icon), 1024×500 feature graphic, and at least 2 phone screenshots (login, deliveries, capture result, ledger, fleet map).
