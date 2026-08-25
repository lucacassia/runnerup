# GitHub Releases Setup

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

### 4. Clean up local files

```bash
rm runnerup.jks runnerup.jks.base64
```

**Never commit the keystore or its password.**

## Creating a release

```bash
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions will build the release APK and create a GitHub Release at:
`https://github.com/lucacassia/runnerup/releases/tag/v1.0.0`
