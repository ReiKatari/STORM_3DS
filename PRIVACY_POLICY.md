# Privacy Policy - STORM 3DS

Last updated: 2026-11-28

STORM 3DS is a free, open-source Nintendo 3DS emulator licensed under GNU GPLv3 with zero advertisements, zero in-app purchases, zero tracking SDKs, and zero telemetry collection by the STORM 3DS developers.

## 1. Data Collected & Local Storage
- **No developer telemetry or analytics:** STORM 3DS does not collect, sell, or transmit usage analytics, device fingerprints, crash telemetry, or advertising identifiers to STORM TEAM servers or third-party analytics providers.
- **Local settings and game files:** ROM paths, controller profiles, screen layouts, SRAM/SDMC saves, save states, shader caches, and user preferences are stored exclusively on your device.
- **Optional credentials you choose to enter:**
  - **RetroAchievements:** Your username and Connect API session token (`ra_token`) are encrypted at rest on Android using Android Keystore (`AES/GCM/NoPadding`) and transmitted solely to RetroAchievements (`https://retroachievements.org/dorequest.php`) when you sign in. Your password is never persisted to disk.
  - **Screen Translator (Android):** OCR uses on-device Google ML Kit models locally. Translation requests only touch external endpoints if you explicitly configure and trigger translation.
  - **Online / Multiplayer Features:** Artic Base and NetPlay/Pretendo connections only occur when you manually start or join a session and connect directly to the server address you configure.

## 2. External Network Endpoints & Server Jurisdictions
Network requests occur only for user-initiated features:
1. **Update Checks & Release Downloads:**
   - GitHub Releases (`github.com`, `api.github.com` - United States)
   - STORM GAMES WORLD (`stormgamesworld.ru` - Russian Federation)
2. **RetroAchievements Integration (optional, opt-in):**
   - Official RetroAchievements API (`retroachievements.org` - United States)
   - Sent with a unique client identifier (`STORM_3DS/<version> (Android <os>) azahar_core/<version>`).

## 3. Data Retention Periods
- **Local data on your device** (configuration, saves, caches, and RetroAchievements login token) remains stored locally until you sign out, clear app data, or uninstall STORM 3DS.
- **RetroAchievements account data** (achievements, leaderboards, rich presence) is governed by the [RetroAchievements Privacy Policy](https://retroachievements.org/terms).
- **Zero retention by STORM TEAM:** STORM TEAM operates no user accounts, cloud save databases, or telemetry logs for STORM 3DS; retention on developer servers is **0 days**.

## 4. Third-Party Services & Children's Privacy
- Because STORM 3DS collects no developer telemetry and shows no ads, no third-party ad networks or analytics processors receive your personal data.
- Users remain responsible for the privacy policies of optional external services they explicitly enable (RetroAchievements, Pretendo/Nimbus, or user-hosted NetPlay peers).

## 5. GDPR & User Rights
Under GDPR and applicable privacy laws, because all STORM 3DS state is stored locally on your device:
- **Access & Portability:** You have full direct file access to all configuration, saves, and logs inside your local STORM 3DS data directory.
- **Erasure ("Right to be Forgotten"):** Logging out of RetroAchievements immediately clears stored tokens from Preferences, and uninstalling or deleting the STORM 3DS data folder erases 100% of local data.
- For privacy inquiries, open an issue at https://github.com/ReiKatari/STORM_3DS/issues or contact STORM TEAM via https://stormgamesworld.ru.
