# Autopilot

Autopilot is a native Android auto-clicker with administrator-published modes, secure free access, and server-authorized account controls.

## Run & Operate

- Android sources live under `android-app/`.
- `cd android-app && ./gradlew :smartautoclicker:assemblePlayStoreDebug --no-daemon` — build the Play Store debug variant.
- `cd android-app && ./gradlew :smartautoclicker:testPlayStoreDebugUnitTest --no-daemon` — run unit tests.
- Apply `android-app/supabase/schema.sql` to the Supabase project before using remote auth, modes, rewards, or admin controls.
- The Play Store flavor includes Unity Ads; the F-Droid flavor intentionally excludes ad playback.

## Stack

- Android Gradle project, Kotlin, AndroidX, Room, and the existing Smart AutoClicker engine.
- Supabase Auth and PostgREST over HTTPS using the publishable client key.
- Unity Ads is isolated to the Play Store source set.

## Where things live

- `android-app/smartautoclicker/` — application entry point and Autopilot screens.
- `android-app/core/` and `android-app/feature/` — automation engine and reusable feature modules.
- `android-app/smartautoclicker/src/main/java/com/autopilot/driver/` — authentication, modes, profiles, admin controls, remote installation, and ad/session telemetry.
- `android-app/supabase/schema.sql` — source of truth for tables, RLS policies, triggers, and server-authorized RPCs.
- `attached_assets/` — product requirements and implementation history.

## Architecture decisions

- The Android client never contains a Supabase service-role key; privileged actions use security-definer RPCs with `auth.uid()` checks.
- Administrator-published modes are read-only to regular users and are validated before installation into the local automation engine.
- Reward access is granted only by the atomic server-side reward claim transaction after ad playback completion.
- Play Store and F-Droid builds share the product flow, while ad playback is implemented only in the Play Store source set.

## Product

Users authenticate, choose a published mode, start or stop automation, and earn one day of free access through verified reward ads. Administrators can publish modes, manage access, manage bans, and tune global free-access settings.

## User preferences

- Keep the product name as Autopilot.
- Keep the app 100% free: no billing, paid plans, or premium tiers.
- Push each completed implementation checkpoint to the connected GitHub repository.

## Gotchas

- `local.properties`, keystores, and Google service configuration are intentionally ignored and must be supplied by the Android build environment.
- Remote mode JSON is intentionally limited by `ScenarioValidator`; do not bypass validation in the installer.
- Profile, subscription, ban, ad, and click-session state must remain server-authoritative.

## Pointers

- See the `pnpm-workspace` skill for workspace structure, TypeScript setup, and package details
