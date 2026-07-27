# Publishing Skyline Glider to Google Play

Everything here is ordered. Steps marked **you only** need your hands or your
money — I can't do them.

---

## 0. The timeline you should know about first

You don't have a Play developer account yet. That matters more than anything
technical in this document:

- Registration is a **one-time $25 fee**, and identity verification can take a
  few days.
- If you register a **personal** account, Google requires a **closed test with
  at least 12 testers opted in for 14 consecutive days** before you may apply
  for production access. Twelve real people, each accepting the invite and
  installing the app under the matching Google account. Invited-but-not-installed
  doesn't count, and the 14 days restart if you drop below twelve.
- **Organisation** accounts are exempt from that requirement but need a D-U-N-S
  number.

Realistic path to a public listing on a personal account: **three to four
weeks**. Plan for it rather than being surprised by it.

---

## 1. Build config — already done

| Item | Value |
| --- | --- |
| `applicationId` | `com.skyline.glider` |
| `versionCode` / `versionName` | `1` / `1.0.0` |
| `minSdk` | 24 (Android 7.0) |
| `compileSdk` / `targetSdk` | 36 (Android 16) |
| Release build | R8 minification + resource shrinking, `debuggable false` |
| Debug build | suffixed `.debug` so both can sit on one phone |
| Output | Android App Bundle (`.aab`) |

Play requires **targetSdk 36 for new apps from 31 August 2026**, so the project
is already on it. That bump required AGP 8.9.1 and Gradle 8.11.1, which I also
updated.

> **If Gradle sync now fails:** you need SDK Platform 36 installed
> (`Tools → SDK Manager → SDK Platforms → Android 16`). If it still fails, run
> `Tools → AGP Upgrade Assistant`, which picks a compatible combination for your
> Studio version. As a last resort you can drop `compileSdk`/`targetSdk` back to
> 35 and AGP to `8.5.2` — still accepted by Play until 31 August 2026.

---

## 2. Create your signing keystore — **you only**

This is the single most important file in the project. If you lose it, you can
never ship an update to the same listing; you'd have to publish a new app and
abandon your users. If it leaks, someone else can sign builds as you.

```bash
cd ~/claude/mobile-apps-for-publish/skyline-glider

keytool -genkey -v \
  -keystore skyline-glider-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias skyline
```

It prompts for a password and your name/organisation. Then:

```bash
cp keystore.properties.template keystore.properties
```

Edit `keystore.properties` and put your real passwords in. Both the `.jks` and
`keystore.properties` are gitignored — verify with `git status` that neither
appears before you commit anything.

**Back up the `.jks` file and its passwords today.** Password manager, encrypted
drive, somewhere that survives losing this laptop.

---

## 3. Build the release bundle

In Android Studio: `Build → Generate Signed App Bundle / APK → Android App
Bundle`. Or from the terminal once the Gradle wrapper exists:

```bash
./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

**Test the release build before uploading.** R8 is now on, which can break
things that worked in debug:

```bash
./gradlew installRelease
```

Play the game for a few minutes. If it crashes or renders wrong but the debug
build is fine, R8 stripped something it shouldn't have — set
`isMinifyEnabled = false` in `app/build.gradle.kts` and re-check.

> The Gradle wrapper (`gradlew`) still isn't in the repo. Generate it from
> Android Studio's Gradle panel: **Gradle tool window → skyline-glider → Tasks →
> build setup → wrapper**. Commit the result so CI and fresh clones work.

---

## 4. Capture screenshots — **you only**

Play needs at least two; four is better. They must be genuine captures of the
running app. Suggested shots:

1. Mid-glide, wingsuit open, coin arc overhead
2. Sliding under a scaffolding bar
3. Drone encounter with the shield aura lit
4. Main menu with the title and your selected glider

Emulator: camera icon in the toolbar. Galaxy: Volume Down + Power.

---

## 5. Publish the privacy policy — **you only**

A privacy policy URL is mandatory for every app on Play, even one that collects
no data at all. `docs/privacy-policy.html` is written and ready. To host it free
from the repo you already have:

1. Push the repo (`git push -u origin main`)
2. GitHub → repo → **Settings → Pages**
3. Source: *Deploy from a branch*, Branch: `main`, Folder: `/docs`
4. Save, wait a minute, then confirm the page loads at:
   `https://deepakatl1981.github.io/skyline-glider/privacy-policy.html`

Paste that URL into Play Console. It must be publicly reachable when you submit
or the review fails.

---

## 6. Play Console setup — **you only**

1. Register at [play.google.com/console](https://play.google.com/console) — $25, one time
2. Complete identity verification
3. **Create app** → name *Skyline Glider*, English, Game, Free
4. Fill in the store listing from `play-assets/STORE_LISTING.md`
5. Upload `play-icon-512.png`, `play-feature-graphic-1024x500.png`, screenshots
6. Complete these declarations (answers are all in `STORE_LISTING.md`):
   - Data safety — *no data collected*
   - Content rating questionnaire — expect Everyone / PEGI 3
   - Target audience and content
   - Ads — *contains no ads*
   - App access — all functionality available without login
   - Government / financial / health apps — no to all
7. Upload the `.aab` to a **closed testing** track
8. Recruit your 12 testers, keep the test running 14 consecutive days
9. Apply for production access

---

## 7. Version bumps for future releases

Every upload needs a higher `versionCode` than the last. In
`app/build.gradle.kts`:

```kotlin
versionCode = 2
versionName = "1.0.1"
```

Play rejects a duplicate `versionCode` outright, and it can never go backwards.

---

## What's genuinely not done yet

Being straight with you about the gaps:

- **The game has never been played by anyone.** It compiles, but no human has
  confirmed the difficulty curve is fun, the swipe threshold feels right, or
  that it holds 60fps on real hardware. Ship to yourself first.
- **No release build has been tested.** R8 minification is newly enabled and
  untested — see step 3.
- **Screenshots don't exist**, and can't until you play it.
- **No crash reporting.** If a tester hits a crash you'll have no telemetry.
  Play Console's built-in Android vitals will catch some of it after launch.
- **The Gradle wrapper is still missing**, so CI isn't possible yet.
