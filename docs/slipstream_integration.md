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

Network version remains 42: the updates do not change the multiplayer wire
format or simulation rules.

## Verification

- Both Android guest images and the native host build with NDK 28.2.
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
- Linux game units compile; the full Linux build stops at the local missing
  SDL3 headers. No Android device was connected for visual startup testing.
