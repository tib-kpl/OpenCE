# SlipstreamCE updates, November 2026

The French fork includes the functional updates after SlipstreamCE's
`7607fe9ff` through [`19cd0e7ac`](https://github.com/thelinkin3000/SlipstreamCE/commit/19cd0e7ac).
Imported commits keep their authors and their `cherry-pick -x` references.

The update adds shared dynamic lighting and CPU light occlusion for Vulkan
and deko3d, cheaper lighting and decal work, ASTC conversion for Vulkan
devices without BC sampling, Custom Edition HUD corrections, replay camera
and co-op keyframe fixes, ping sorting, custom-map deletion and optional
touch controls during cinematics. Added lighting remains off by default.

The Android package, fork updater, French menus, controller remapping,
independent X/Y sensitivity and Android settings panel are retained. The
upstream vertical-sensitivity setting is redundant with this fork's Stick
Aim screen and is not imported. Upstream release commits, package migration,
branding and release packaging are not applied to this fork.

Android's launcher and game now share a task. The game keeps its separate
process and heap, but uses `singleTop` with `CLEAR_TOP` when the launcher
hands off to it, so an existing game is reused. The handoff has no activity
animation or second starting preview. Import, driver preparation and
failed-start reporting still run before entering the game.

Network version is corrected to 41, shared by OpenCE and SlipstreamCE. The
earlier integration had unnecessarily isolated this fork as version 42:
its recorded wire format is the same. New clients accept both 41 and the
historical 42 in discovery, sorting and joining, while advertising 41.
Other protocols and hosts using the old lockstep netcode are still refused.
An old version-42 client must update to gain this compatibility itself.
Existing replay files marked 42 remain readable; that exception applies to
recordings only, not to live multiplayer joins.

The server browser now uses the d-pad and left stick for normal row/button
navigation, with the right stick for sorting. Its bottom button bar restores
focus on return, as the Mods and Theater bars do, in Internet, LAN and
Direct Link modes.

## Verification

- Both Android guest images and the native host build in debug and optimized
  release configurations with NDK 28.2.
- The release APK builds with the French assets and the fork's updater identity.
- The broad engine/Android/host suite initially passed 1,971 tests, with
  10 skips. Six old Vulkan visibility tests were then adapted to the atomic
  result/test-number format; all 21 visibility tests pass.
- The remaining three broad-suite failures reproduce on the starting `main`
  under clang 18: elevator prediction's `riding-up` and polygon clipping's
  two `nan-crossing` negative controls.
- The 67 build-graph, layout, touch-menu and repository-identity tests pass.
- Java touch-layout, gyroscope, controller-chord and shortcut checks pass.
- French menu generation reports no untranslated text.
- The multiplayer correction passes 59 targeted checks: controller focus in
  Internet/LAN/Direct Link, version-41/legacy-42 joins and incompatible-host refusals,
  the recorded wire format, tactile menus and replay compatibility.
- Linux game units compile; the full Linux build stops at the local missing
  SDL3 headers. No Android device was connected for visual startup testing.
