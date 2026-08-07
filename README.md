# BrightMarket

An app marketplace for the Light Phone III. Browse sideloaded apps, install them,
and keep them updated — all free, all from GitHub releases.

## Install via BrightMarket

<p align="center">
  <img src="https://gi-os.github.io/brightmarket-index/assets/qr/BrightMarket.png" alt="Scan to open BrightMarket in BrightMarket" width="180" />
</p>

BrightMarket is the marketplace itself — the client above browses, installs and
silently updates every other Bright app over ADB, with no PC and no Play Store
account required. If you already have it installed, the code above opens its
own listing (handy for confirming you're on the signed release build).
Otherwise, grab the APK directly and browse every app currently listed at
**[gi-os.github.io/brightmarket-index/browse.html](https://gi-os.github.io/brightmarket-index/browse.html)**.

**Package `com.gios.brightmarket`. minSdk 30** (the rest of the portfolio targets
29; wireless debugging, which the future silent-install path needs, is Android 11+).

## How it works

There is no server and no database. [`gi-os/brightmarket-index`](https://github.com/gi-os/brightmarket-index)
holds a curated `apps.yml`; an hourly GitHub Action turns it into a single
`index-v1.json` (about 1KB gzipped) served from GitHub Pages. The app fetches that
one file.

Sorting needs no analytics: **Popular** sums GitHub's own `download_count` across
every release, **Updated** uses the release timestamp, **New** uses the date the
index first saw the app. BrightMarket never sees a user.

## Installing

v1 uses `PackageInstaller` with the system's own confirmation dialog. It works on
any device with no setup. Every download is checked against the `sha256` in the
index before it is handed to the installer — the index is a static file on GitHub
Pages, and that hash is the only thing making it trustworthy.

A silent path is planned (an embedded ADB client pairing the phone to its own
`adbd` over loopback, which gets shell uid and therefore `INSTALL_PACKAGES`).
The dialog path stays as a real fallback regardless: an ADB maintainer has
proposed binding `adbd` to `wlan0` only, which would end that technique outright.

## Coming from Obtainium

**Import from Obtainium** at the bottom of the list takes an Obtainium export and
matches it against the index. It reads both the current `{"apps":[{"app":{…}}]}`
shape and the older bare-array exports, and it matches on **applicationId first**,
falling back to repo URL — so an export made before the Light→Bright rename still
resolves, because applicationIds never changed. Anything it can't place is
reported by count rather than silently dropped.

## Adding your app

Use the [portal](https://gi-os.github.io/brightmarket-index/submit.html) (GitHub
sign-in, `read:user` only, lists just the repos you own) or open a submission
issue. Checks and the trust model are documented in the index repo.

## Building

```
./gradlew :app:assembleRelease
```

The keystore is committed at `keystore/brightmarket.jks` so every build carries the
same certificate and upgrades install over the top. CI pins that certificate's
SHA-256 in `signing-fingerprint.txt` and fails if it drifts, because a changed cert
surfaces to users only as an opaque `Failure: Invalid`. Exactly one APK is attached
per release.
