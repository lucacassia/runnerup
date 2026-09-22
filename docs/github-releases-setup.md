# GitHub Releases Setup

Two release channels are published from GitHub Actions on this fork:

- **Stable** (`.github/workflows/release.yml`): triggered by pushing a `v*` tag — builds a signed release APK (`org.runnerup`) and publishes it as a GitHub Release.
- **Nightly** (`.github/workflows/nightly.yml`): runs every day at 03:00 UTC (plus on manual `workflow_dispatch`) — builds a signed **F-Droid free** APK (`org.runnerup.free`, osmdroid, no play-services/wear/mapbox) and publishes it as a rolling `nightly` prerelease.

## One-time setup

### 1. Generate keystore

```bash
keytool -genkeypair -v \
  -keystore runnerup.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias runnerup \
  -storepass <YOUR_PASSWORD> \
  -keypass <YOUR_PASSWORD> \
  -dname "CN=RunnerUp, OU=Development, O=RunnerUp, L=Unknown, ST=Unknown, C=US"
```

### 2. Encode for GitHub Secrets

```bash
base64 -w 0 runnerup.jks > runnerup.jks.base64
cat runnerup.jks.base64
# Copy the output
```

### 3. Add GitHub Secrets

Go to repo Settings → Secrets and variables → Actions → New repository secret:

| Name | Value |
|------|-------|
| `ANDROID_KEYSTORE_BASE64` | Output from step 2 |
| `ANDROID_KEYSTORE_PASSWORD` | Your keystore password |
| `ANDROID_KEY_ALIAS` | `runnerup` |
| `ANDROID_KEY_PASSWORD` | Your key password |

Both workflows fail loudly if these secrets are missing, rather than publishing an unsigned APK. (`ANDROID_KEYSTORE_*` env vars are read by `app/build.gradle`; without them or a local `release.properties`, release builds are left unsigned as before.)

### 4. Clean up local files

```bash
rm runnerup.jks runnerup.jks.base64
```

**Never commit the keystore or its password.**

## Creating a stable release

```bash
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions will build the release APK and create a GitHub Release at:
`https://github.com/lucacassia/runnerup/releases/tag/v1.0.0`

## Nightlies

The `nightly` release is a rolling prerelease: each night the previous `nightly` release/tag is deleted and re-created at `https://github.com/lucacassia/runnerup/releases/tag/nightly`, so the link always points at the newest build. It runs every calendar night even when the code hasn't changed (serving as a nightly build-health check).

### What the nightly contains

- The F-Droid free flavor: `org.runnerup.free`, osmdroid maps, no Google Play services / Wear OS / mapbox dependencies — free software only, works on GMS-less phones.
- Versioning: `versionCode` is derived from the git commit count (monotonic, so each nightly installs over the previous one); `versionName` is `2.11.0.1-nightly-YYYYMMDD-<short-sha>`.
- Signed with the keystore from Secrets (the same key is used for every nightly).

### Installing a nightly

Any Android 9+ (minSdk 28) phone can sideload the APK from the release page:

1. Open `https://github.com/lucacassia/runnerup/releases/tag/nightly` in the phone's browser.
2. Download the APK and allow "install unknown apps" for the browser/downloader.
3. Tap the APK; Play Protect may warn because it is not Play-distributed — install anyway.

### Caveats

- The nightly is signed with the fork's own key, not the F-Droid build server's key. Anyone who already has the official F-Droid (or Play) build installed must uninstall it first; the two cannot coexist or upgrade each other.
- Nightlies are manual sideloads only — no automatic updates. Installing the next nightly upgrades in place (same key, higher `versionCode`).
- A device running a nightly (high `versionCode`) cannot go back to the official build without uninstalling.