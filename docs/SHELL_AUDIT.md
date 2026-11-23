# PG Ease Tenant shell audit

This repository is the Android shell for the PG Ease tenant web app at `https://tenant.pgease.com`. The web UI itself lives in [pg-tenant](https://github.com/pgease/pg-tenant) and is not changed here. The owner shell ([pg-owner-mobile-app](https://github.com/pgease/pg-owner-mobile-app)) started from the same Capacitor 6 wrapper; this branch keeps that shape (Capacitor WebView, live `server.url`, `PGEaseApp` user agent, `?app=1`) and adds the native behaviour the tenant site needs for Play.

Package name, launcher icon, app title, splash artwork, and signing config are unchanged. There was no release signing config to keep. `minSdk` moved from 22 to 24 because Capacitor 8 requires it (Android 7.0 and newer).

## How the shell worked

It is a Capacitor WebView, not a Trusted Web Activity, not a hand-written WebView activity, and not Flutter.

- `capacitor.config.json` sets `appId` to `in.pgease.tenant`, `appName` to `PG Ease Tenant`, and `server.url` to `https://tenant.pgease.com?app=1`.
- `appendUserAgent` is `PGEaseApp`. The web app treats that, `?app=1`, or a stored flag as app mode (`src/utils/appBridge.ts` in pg-tenant).
- `MainActivity` extends Capacitor `BridgeActivity`. The only custom code was a three-slide onboarding overlay stored in `pgease_tenant_mobile_prefs`.
- The layout file `activity_main.xml` contains a WebView, but Capacitor does not use it. The real view is Capacitor's `CapacitorWebView`.
- Bundled `dist/` is a fallback for `npm run mode:local`. Launch mode is the live site, so scripts injected only into `dist/index.html` never ran for real users.
- Session state is the web app's `localStorage` (`pgease_tenant_token`, `pgease_tenant_refresh_token`). The WebView profile persists that across restarts. Auth is not a cookie.

Capacitor 6.2 / AGP 8.2.1 / Gradle 8.2.1 / `compileSdk` 34 / `targetSdk` 34 / `minSdk` 22. No `colors.xml` even though `styles.xml` referenced `@color/colorPrimary`, so a resource-linked build was one missing file away from failing. The checked-in README still described the owner app (`in.pgease.owner`, `owner.pgease.in`).

## What was broken or missing

| Need | Before this branch |
|---|---|
| No browser URL bar | Already true. A WebView has no Chrome chrome. A TWA would show a URL bar until `assetlinks.json` verified, so the shell stays a WebView. |
| Android back | Capacitor's default handler only calls `goBack()` and never finishes the activity. It does not close the React sheets (rent, filters, complaints), which are `position: fixed` overlays, not history entries. The back listener in `scripts/sync-web-build.js` only exists in bundled mode and called `exitApp()` from `/dashboard`, a path this site does not use. |
| Pull to refresh | None. The tenant UI scrolls inside `<main class="overflow-y-auto">`, so a naive refresh layout would steal that scroll. |
| Camera and file upload | `<input type="file" accept="image/*">` is used for payment screenshots, cash slips, complaint photos, and the profile photo. None of them set `capture`. Capacitor only opens the camera when `capture` is set, and `CAMERA` was not in the manifest. Android 11+ also hides the camera app from `resolveActivity` without a `<queries>` entry. |
| `upi://` and other apps | Capacitor's `Bridge.launchIntent` sends every host other than `tenant.pgease.com` to an external browser. `upi://pay?...` (the Direct UPI button in `RentPaymentModal`), `tel:`, `mailto:`, and `https://wa.me/` were not given to the right app. |
| Easebuzz checkout | `handleInitiateGateway` sets `window.location` to `checkoutUrl` on `pay.easebuzz.in` (or the test host). The old client would open that in Chrome, so the return to `/payments/result` would not come back into the app. |
| PDF receipts and agreements | `window.open(receiptUrl)` and `<a target="_blank" href="...pdf">` do nothing in a WebView unless a new window is created and `DownloadListener` is set. There was no download path. |
| Offline screen | A failed main-frame load showed the WebView's `net::ERR_*` page. The site's own offline overlay only appears after the page has already loaded and `navigator.onLine` flips. |
| Brand bars and safe area | Status bar config was white (`#FFFFFF`) with the Capacitor `LIGHT` style. Splash artwork stays on `#005045`. The site brand is `#008080`. Android WebView often reports `env(safe-area-inset-*)` as 0, and targeting API 36 ignores `statusBarColor` unless the app draws the bars itself. |
| Session | localStorage already persisted. Cookies were not flushed on pause, which matters for the gateway host. |
| Push | The notifications screen is a list of in-app notices from the API. There is no service worker, no Web Push, and no `google-services.json`. WebView cannot deliver Web Push. `PGEaseBridge.requestPushPermission` is only a TypeScript hook. |
| Deep links | No `VIEW` intent filters. `https://tenant.pgease.com/wa/...` is a web route the site already understands, but Android opened it in a browser. |
| Play target | `targetSdk` 34. As of 1 November 2026, new Play submissions must target Android 16 (API 36). 16 KB page-size alignment is required for native code on 64-bit devices. |

## What this branch changes

Capacitor 8.6 (`targetSdk` / `compileSdk` 36, AGP 8.13.0, Gradle 8.14.3, Java 21). `minSdk` 24. JNI libs use `useLegacyPackaging = false` so AGP 8.5.1+ zip-aligns them to 16 KB. This app's release APK contains no `.so` files (Capacitor 8's Android core is Java/Kotlin), and `zipalign -c -P 16` passes.

Native behaviour lives in `android/app/src/main/java/in/pgease/tenant/`:

- **Back.** Sheets close first (the header X, or `PGEaseBridge.onNativeBack` if the site sets it). Then WebView history. At `/`, `/login`, or `/home`, a second back within two seconds exits. From a deep link with no history, back returns to the tenant home instead of exiting. Easebuzz stays on the stack, so back returns to the page that opened checkout.
- **Pull to refresh.** Only on the tenant host, only when the touched scroller is already at the top and no sheet is open. Disabled on the gateway host so a reload cannot replay a checkout.
- **Photos.** Image file inputs get a chooser with camera and gallery. `CAMERA` is optional (`required=false`) so devices without a camera still install. Denying the camera still opens the gallery. The system picker is used, so the app does not request photo-library or `READ_MEDIA_IMAGES`.
- **External apps.** `upi://`, `tez://`, `phonepe://`, `paytmmp://`, `gpay://`, `bhim://`, `credpay://`, `intent://`, `tel:`, `mailto:`, `sms:`, WhatsApp (`wa.me`, `api.whatsapp.com`, `whatsapp://`), maps, and Play Store links leave the WebView. If no UPI app is installed the shell says so instead of showing a browser error.
- **Easebuzz.** Any other `https` URL, including `*.easebuzz.in` and the return to `https://tenant.pgease.com/payments/result`, stays in the WebView. `target="_blank"` (e-sign, receipt, WhatsApp share) is routed through the same rules.
- **PDFs.** URLs whose path ends in `.pdf`, and any response the WebView treats as a download, go to `DownloadManager` (public Downloads on Android 10+, the app downloads folder before that) with the site cookies attached. The system Downloads notification is used, so `POST_NOTIFICATIONS` is not requested.
- **Offline.** Main-frame network and TLS failures show a branded retry screen instead of the WebView error page. TLS errors are cancelled, not bypassed.
- **Bars and insets.** The shell opts into edge-to-edge, paints `#008080` behind transparent system bars, and pads the WebView by the status bar, navigation bar, cutout, and keyboard. Icons are white. Capacitor `SystemBars.insetsHandling` is `disable` so the framework does not add a second inset. The web app's own `pt-safe` / `pb-safe` still apply their small minimum padding because `env(safe-area-inset-*)` stays 0 once the native view is padded.
- **Session.** DOM storage stays on, third-party cookies are accepted (gateway pages), and cookies are flushed in `onPause`.
- **Push hook.** The shell defines `PGEaseBridge.requestPushPermission` and resolves `null`. That is readiness for the web app, not a notification implementation.
- **Links.** `https://tenant.pgease.com/...` is an auto-verified app link. `pgease://tenant/<path>` works without Digital Asset Links and is rewritten to the https URL with `app=1`.

Splash images, splash background `#005045`, launcher icons, `applicationId`, `versionCode` 1, and `versionName` 1.0 are unchanged. `versionCode` must be increased before a Play upload if version 1 was already used.

Onboarding slides are unchanged. Hardware back moves to the previous slide, and from the first slide it dismisses onboarding the same way Skip does.

## Push notifications

Not implemented, because the web app does not subscribe to push.

What exists today:

- `/notifications` loads notices from the API. It is an in-app inbox.
- There is no service worker and no `PushManager` usage.
- Android WebView does not support the Web Push API, so a browser push certificate would not deliver inside this shell.

What the founder has to add before the shell can show OS notifications:

1. Create a Firebase project for `in.pgease.tenant` and download `google-services.json` into `android/app/`. The Gradle file already applies the Google Services plugin when that file is present.
2. Add `@capacitor/push-notifications` (Capacitor 8) and a small native call that the web app can reach. The shell already installs `window.PGEaseBridge.requestPushPermission`; today it returns `null`.
3. On Android 13+ request `POST_NOTIFICATIONS` at the moment the user opts in, not at first launch. Play rejects apps that request it without a shown use.
4. Have pg-tenant send the FCM token to the existing API, and send data messages from the backend. Tapping a notification should open `pgease://tenant/<path>` or `https://tenant.pgease.com/<path>`.
5. Do not rely on a website service worker for this shell.

## Deep links the founder must host

Verified https links open the app only after this file is served at:

`https://tenant.pgease.com/.well-known/assetlinks.json`

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "in.pgease.tenant",
    "sha256_cert_fingerprints": ["PLAY_APP_SIGNING_SHA256"]
  }
}]
```

`Content-Type` must be `application/json`. The current `vercel.json` in pg-tenant rewrites every path, including `/.well-known/*`, to `index.html`. That rewrite has to skip `/.well-known/(.*)` or verification will fail and https links will keep opening in Chrome. The app itself still works.

The fingerprint is the **Play App Signing** certificate (Play Console → App integrity → App signing), not the upload key and not the debug key used for the test APK in this branch. The debug-signed test APK fingerprint is `00:F5:5A:BF:85:81:0D:7B:58:47:41:BB:3B:D9:98:32:A0:1E:BE:6A:A9:9F:B3:D0:8B:AB:92:10:36:39:D5:1D` and must not be shipped in production `assetlinks.json`.

Until that file is live, use the custom scheme, which needs no site change:

`pgease://tenant/payments`
`pgease://tenant/payments/result?txnid=...`
`pgease://tenant/documents`

Easebuzz return URLs should be `https://tenant.pgease.com/payments/result?...` so checkout stays inside the WebView and comes back to the result page. A return URL on another domain would load that domain inside the app.

WhatsApp templates that already point at `https://tenant.pgease.com/wa/...` will open the app once app links verify. The site then routes `/wa/*` itself.

## Play listing notes

- `targetSdk` 36, `minSdk` 24, 16 KB compatible (no native libraries; zip alignment checked).
- No notification permission is declared, because notifications are not sent yet.
- Camera is declared and not required.
- Cleartext is still allowed (`server.cleartext: true`) so `npm run mode:live -- http://192.168.x.x:5173` keeps working. Production traffic is https.
- There is still no upload keystore in the repo. Create the Play app, let Google manage the app signing key, and do not commit a keystore.
- Bump `versionCode` before the first upload if 1 is already taken.
- The test APK attached to the PR is a release build signed with the local debug key. It is for install testing, not for Play.

## Verification

- `./gradlew testDebugUnitTest` — navigation policy tests (UPI, Easebuzz stays in app, PDF download, deep links, root back).
- `./gradlew assembleDebug assembleRelease` — both succeed. Release APK `zipalign -c -P 16` passes.
- Release APK installed on an Android 15 emulator against the live site, read-only: onboarding, login screen, guest explore (local `sessionStorage` only), back from a subpage, deep link `pgease://tenant/search`, offline retry. No OTP request, no payment, no create/update/delete.
