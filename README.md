# PG Ease Tenant — Android shell

Capacitor shell around the tenant web app at `https://tenant.pgease.com`. The UI is built in [pg-tenant](https://github.com/pgease/pg-tenant). This repo is the native Android wrapper only.

`applicationId` is `in.pgease.tenant`. The app loads the live site (`?app=1`, user agent `PGEaseApp`) so web deploys show up without a Play update.

Play launch notes, deep links, and push setup are in [docs/SHELL_AUDIT.md](docs/SHELL_AUDIT.md).

## Run

Requirements: Node 22, JDK 21, Android SDK 36.

```bash
npm install
npm run sync:android
cd android && ./gradlew assembleDebug
```

`npm run mode:local` switches the shell to the bundled `dist/` assets. `npm run mode:live -- https://tenant.pgease.com` points it back at a URL. Production config is already live.

## Scripts

| Command | What it does |
|---|---|
| `npm run sync:android` | Refreshes the Android project and copies `dist/` |
| `npm run mode:live -- <url>` | Point the shell at a URL |
| `npm run mode:local` | Point the shell at bundled `dist/` |
| `npm run build:apk` | Debug APK |
| `npm run clean:android` | Gradle clean |

A release APK is unsigned until you add a Play upload key. Do not commit a keystore. `docs/SHELL_AUDIT.md` describes the debug-signed release APK used for testing.
