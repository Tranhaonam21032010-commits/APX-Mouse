# APX Mouse — Autonomous Android Keymapping Engineering Prompt

> Use this prompt in a coding agent that has access to the connected GitHub repository and an Android/Gradle build environment.

## Mission

Act as the senior Android engineer responsible for taking the existing **APX Mouse** repository from its current state to a genuinely usable Android keyboard-and-mouse-to-touch mapping application, in the same product category as GG Mouse Pro 3 and Panda Mouse.

Work in the existing repository:
- Repository: `Tranhaonam21032010-commits/APX-Mouse`
- Default branch: `main`
- Existing package namespace: inspect Gradle/Manifest; do not guess or rename it.
- Existing architecture and code include `InputEngine.kt`, `TouchMappingEngine.kt`, `ShizukuInputBackend.kt`, `ShizukuInputCapabilityProbe.kt`, `GameOverlayService.kt`, `MappingEditor.kt`, and unit tests. Read the actual current versions before changing anything.

**Do not create a disconnected demo or rewrite the project from scratch.** Preserve working features and evolve the existing codebase. The goal is functional input mapping, not merely a convincing UI.

## Non-negotiable engineering rules

1. Start by inspecting the complete repository, Gradle configuration, manifest, current source, tests, and GitHub Actions workflow.
2. Produce a short architecture audit: what already works, what is incomplete, what is broken, and what Android permissions/API limitations apply.
3. Before implementation, write a phased plan and list every file expected to change.
4. Use test-driven development: add or update tests before changing behavior; run targeted tests after each phase.
5. Never claim a feature works unless it has passed an appropriate automated test or a clearly described physical-device test.
6. Never fake input injection, device compatibility, Shizuku authorization, connected-device status, or test results. Show unsupported states honestly in the UI.
7. Do not use root as a requirement. The target path is **no-root with Shizuku**, with explicit capability detection and user-granted authorization. If a capability cannot be achieved on a device/Android version, explain why and disable that feature safely.
8. Do not use Accessibility Service as a hidden bypass for input injection. If any accessibility feature is proposed, explain its exact purpose, user-visible disclosure, limitations, and obtain explicit user enablement.
9. Keep input processing lightweight, event-driven, and off the main thread where appropriate. Avoid polling loops, busy waits, excessive allocations, and unnecessary background services.
10. Preserve existing user data and mapping profiles. Use migrations when data formats change. Never delete or overwrite user files or unrelated Git history.
11. Do not copy proprietary code, assets, branding, or UI from GG Mouse Pro 3 or Panda Mouse. Build original APX Mouse implementations inspired only by publicly observable product capabilities.
12. Do not stop after generating code. Build, test, inspect failures, fix them, and repeat until all achievable checks pass.

## Product behavior

### A. Physical input capture
- Support external USB and Bluetooth keyboards and mice through Android input events.
- Distinguish keyboard key-down/key-up, mouse buttons, wheel, pointer movement, device attach/detach, and device identity.
- Handle key repeat, simultaneous key presses, modifier keys (Ctrl/Alt/Shift), Escape, and device reconnection.
- Provide a diagnostics screen showing detected devices, event types, key codes, button states, pointer coordinates, event timestamps, and capability status.
- Do not consume normal system input unless the user has explicitly enabled an active mapping session and the platform permits it.

### B. Visual mapping editor
- Provide an in-game overlay/editor with a transparent game-preview canvas.
- Let users add, move, resize, and remove mapping controls.
- Support at minimum:
  - Keyboard key → tap at a screen coordinate.
  - Keyboard key held → touch down while held, touch up on release.
  - WASD/arrow keys → virtual joystick with configurable center, radius, and dead zone.
  - Mouse movement → camera/look control where the selected injection backend can genuinely support continuous movement.
  - Mouse left/right/middle/side buttons → configurable touch/button actions.
  - Mouse wheel → configurable discrete actions.
- Allow saving, duplicating, renaming, importing/exporting, and selecting per-game profiles.
- Store coordinates in normalized display space (0..1), then transform correctly for current resolution, rotation, insets, cutouts, and aspect ratio.
- Provide a calibration flow and a visible test mode before launching a game.
- Make the editor usable with touch input as well as keyboard/mouse.

### C. Mapping runtime and touch state
- Maintain a deterministic mapping state machine.
- Correctly track simultaneous keys and pointers; one key release must not cancel another active mapping.
- Guarantee matching down/up/cancel lifecycle and release all active virtual touches on focus loss, app pause, device disconnect, session stop, backend failure, or service death.
- Handle duplicate events, repeated key-down, out-of-order events, cancellation, and rapid input without stuck touches.
- Use stable pointer IDs and a bounded pointer pool. Never silently exceed the platform's supported pointer count.
- Implement joystick diagonals with normalized vector magnitude, dead-zone handling, and configurable sensitivity.
- Keep mouse camera deltas smooth and bounded; provide sensitivity and acceleration options without introducing artificial input lag.
- Rate-limit only where necessary and document the reason. Do not discard key-up or touch-up events.

### D. Injection backend and Android compatibility — critical
- Audit `ShizukuInputBackend.kt` and all related service/AIDL code line by line.
- Verify the actual privileges and APIs available to a Shizuku user service on supported Android versions. **Shizuku is an authorization/IPC mechanism, not a guarantee that arbitrary multi-touch injection is permitted.**
- Separate input capture, mapping logic, touch command generation, and injection backend behind clear interfaces:
  `Physical Input → InputEngine → TouchMappingEngine → TouchCommand → CommandSink → Injection Backend`.
- Implement only injection mechanisms that are technically valid on the target Android version and device. Verify command syntax, permissions, exit codes, process lifetime, and error output.
- Do not assume that shell `input tap/swipe` supports concurrent multi-touch or continuous high-frequency camera movement. Prove those behaviors experimentally before advertising them.
- If the current Shizuku route cannot support a required behavior, document the limitation and evaluate a legitimate alternative (for example, a user-authorized companion/ADB setup or a supported privileged integration). Do not silently substitute a weaker behavior.
- Add a capability matrix by Android API level and backend. Detect capabilities at runtime and show: Supported, Unsupported, Permission required, Shizuku unavailable, or Not tested.
- Ensure backend death/restart, permission revocation, and device reboot are handled without crashes or stuck input.
- Do not execute arbitrary shell strings assembled from user-controlled input. Use strict argument validation, safe process invocation, timeouts, and output/error handling.

### E. UX and reliability
- Build a polished, responsive Android UI consistent with APX Mouse branding and existing project conventions.
- Show clear setup steps for connecting a mouse/keyboard and authorizing Shizuku.
- Include a one-tap emergency stop that releases all active mappings and closes the overlay.
- Display truthful runtime status: input device connected, Shizuku authorized, backend ready, active profile, and supported injection capabilities.
- Persist settings safely and restore the last valid profile after process recreation.
- Respect battery and thermal state; do not keep high-frequency services running while mapping is inactive.
- Provide actionable error messages and a diagnostic export that excludes personal data.

## Architecture expectations

Keep modules/classes small and testable. Prefer interfaces for:
- `InputEventSource`
- `MappingRepository`
- `TouchCommandSink`
- `InjectionBackend`
- `CapabilityChecker`
- `MappingSessionController`

Use Kotlin and the project's existing Android/Gradle stack. Do not upgrade Gradle, Kotlin, Android Gradle Plugin, SDK levels, or dependencies without checking compatibility and explaining the reason. Avoid adding dependencies when the platform or current project already provides the required functionality.

## Required tests

Create meaningful unit tests for:
- Key-down/key-up and mouse-button down/up translation.
- Modifier keys, key repeat, duplicate events, and simultaneous mappings.
- Multiple active touch pointers and stable pointer IDs.
- One mapping release while another remains active.
- Focus loss, device disconnect, cancellation, backend failure, and emergency stop releasing every active pointer.
- Joystick dead zone, cardinal/diagonal directions, bounds, and sensitivity.
- Coordinate normalization and transformation across resolution, rotation, and insets.
- Profile serialization, validation, migration, and corrupted profile recovery.
- Capability detection and Shizuku permission/service lifecycle.
- Rate limiting without dropping release events.

Use fake input sources and fake injection backends for deterministic tests. Add Android instrumentation/UI tests for permission/setup flows and overlay lifecycle where feasible. Add a hardware test checklist for real USB/Bluetooth devices and physical Android devices.

## Build, test, and delivery

Run and report the actual results of:
1. `./gradlew test`
2. `./gradlew lint` (if configured; otherwise configure or state why unavailable)
3. `./gradlew assembleDebug`
4. Relevant instrumentation tests when an emulator/device is available.
5. GitHub Actions workflow validation.

Fix all compile errors, failing tests, lint issues that affect correctness, manifest/permission errors, and runtime crashes before calling the work complete. If a physical device or emulator is unavailable, explicitly label those checks **NOT RUN**; do not report them as passed.

Add/update:
- README with setup, Shizuku authorization, supported Android versions, limitations, build steps, and troubleshooting.
- Architecture documentation and a backend capability matrix.
- Tests and a reproducible hardware validation checklist.
- CI workflow that builds and runs unit tests on pushes and pull requests, without storing secrets in the repository.

Build a debug APK and make it available as a GitHub Actions artifact when CI supports it. Do not commit APK binaries unless the repository already has an explicit release policy for them.

## GitHub workflow

- Work in `Tranhaonam21032010-commits/APX-Mouse`; do not create a second repository.
- Inspect the current branch and working tree first. Preserve existing user changes.
- Create a feature branch named `feat/reliable-android-keymapping` unless that branch already exists; if it exists, inspect it and avoid overwriting unrelated work.
- Commit in small, logically grouped commits with descriptive messages.
- Push the branch and open a pull request to `main` if the connected GitHub tools allow it. Do not force-push, rewrite history, or merge into `main` without explicit approval.
- Include in the PR: summary, architecture changes, exact commands run, test results, APK artifact link (if generated), known limitations, and a device-validation checklist.
- If the environment cannot push, create the changes locally and provide exact Git commands; state clearly that nothing was pushed.

## Definition of done

The task is complete only when:
- The project builds successfully.
- Automated tests pass and their actual output is reported.
- The mapping editor can create and persist profiles.
- Physical keyboard/mouse events are translated correctly by the mapping engine.
- The injection backend has been verified on at least one real supported Android device for every feature advertised as working.
- Unsupported Android/device combinations are detected and explained instead of pretending to work.
- All active touches are released safely in every termination/error path.
- Documentation, CI, and GitHub PR are updated.

If any item cannot be completed, do not hide it. Deliver a precise status table with **PASS / FAIL / NOT RUN / BLOCKED**, evidence, and the remaining work. A compilable APK alone is not proof that keymapping works on a real device.

## First response expected from the agent

Before editing files, return:
1. Repository and branch confirmation.
2. Current architecture summary.
3. Existing features verified from source/tests.
4. Critical gaps and Android injection feasibility findings.
5. Proposed implementation phases and files to change.
6. Test/build commands available in the current environment.

Then proceed with implementation without waiting for approval unless a destructive change, permission/security decision, or genuinely ambiguous product requirement is encountered.
