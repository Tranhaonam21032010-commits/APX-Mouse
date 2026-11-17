# APX MOUSE

Native Android Game Space and input-mapping utility built with Kotlin, Android Views, and Shizuku UserService.

## Project status

This repository is an actively developed debug build, not a production-certified release. The UI and input pipeline have automated tests, but injected touch behavior still needs to be verified on each target Android device and in the intended game. OEM permission policy, input-device naming, screen geometry, and game touch handling can differ.

The original Canva reference contains five screens: Game Space, game selection, add apps, settings, and app details. Those screens have native Android implementations here. The original Canva artwork has not yet been exported and embedded, so some artwork remains represented by native gradients, labels, and installed-app icons rather than a pixel-identical recreation.

## Implemented

- Game Space with paginated installed-app cards, app details, launch and remove actions.
- Five-screen navigation with transition animations.
- Add-app list with pagination and local persistence.
- Per-game mapping points with save, move, remove, and key unbind actions.
- Shizuku connection and permission status.
- Input-device inventory and explicit selection of up to eight /dev/input/eventN devices.
- Input capture is opt-in and starts only while the selected game is foreground. It is stopped when the game is no longer foreground.
- getevent parser for keyboard EV_KEY and relative mouse EV_REL input. Touchscreen absolute coordinates EV_ABS and BTN_TOUCH are ignored.
- Keyboard-to-touch mapping from saved points, WASD joystick pointer, left-click point, and right-button drag point.
- Per-game X/Y mouse sensitivity settings.
- Multi-pointer touch-frame state machine and serialized Shizuku injection calls.
- Unit tests for pointer ordering, evdev parsing, keyboard mapping, joystick movement, mouse drag, and release behavior.

## Android requirements

- Minimum Android API: 24.
- Compile SDK: 35.
- Target SDK: 35.
- Java/Kotlin target: 17.
- Shizuku must be installed, started, and authorized for APX MOUSE.
- Overlay and Usage Access permissions are required for the Game Overlay session.
- A physical mouse or keyboard connected to the phone is required for hardware input mapping.

## First-run setup

1. Install and start Shizuku, then authorize APX MOUSE.
2. Open APX MOUSE settings and open Chẩn đoán chuột / bàn phím.
3. Scan input devices, select only the external mouse and/or keyboard, and save the selection.
4. Enable Bật đọc chuột / bàn phím trong phiên game.
5. Grant Overlay and Usage Access when Android requests them.
6. Add a launchable app to Game Space and open it from APX MOUSE.
7. Open the in-game APX overlay, choose Chỉnh sửa keymap, add or move a point, select it, then press a keyboard key or mouse button to bind it.
8. Save the mapping. The mapping is stored per game package.
9. Configure X/Y sensitivity from Mouse Tweak if needed.

The input event preview is a short diagnostic capture. Do not run it while entering passwords or other private information.

## Input behavior and limitations

- The InputManager injection method is a hidden Android API and is invoked from the Shizuku UserService. Finding the API by reflection is not proof that the device accepts injected events.
- ADB/Shell and Root are different execution identities. ADB permissions vary by Android version and OEM build; APX displays the current Shizuku UID where available.
- ADB shell commonly runs as UID 2000; Root runs as UID 0. The app does not gain Root privileges by itself.
- WASD uses a configurable joystick center/radius with current defaults. Its coordinates must be tuned for the game's on-screen joystick.
- The left mouse button remains anchored to its mapped point. The right mouse button follows relative movement and is used as the default drag point.
- Wheel events are parsed but are not yet mapped to a game action.
- System-wide pointer lock/cursor hiding, mouse hardware polling-rate changes, macros, and per-game advanced profiles are not implemented.
- The status overlay currently reports battery and general network ping. It does not measure the game's server RTT or actual FPS.
- Some OEM builds may deny access to selected /dev/input nodes or reject injected events. In that case, APX must show the failure instead of claiming that mapping is working.

## Privacy

Input capture is disabled by default. The user chooses device nodes and enables capture explicitly. Input events are handled in memory and are not written to a log file by the input reader. The parser discards touchscreen absolute events. Stop the capture switch when the feature is not needed.

## Build and tests

From the repository root with Android SDK and Java 17 installed, run:

    gradle testDebugUnitTest
    gradle assembleDebug

The Debug APK is created under:

    app/build/outputs/apk/debug/

GitHub Actions runs the same unit-test and debug-build tasks on pushes to main, codex/**, and pull requests. The workflow uploads the debug APK as the APX-MOUSE-APK artifact.

## Test coverage

- TouchFrameStateTest: DOWN/MOVE/UP/POINTER_DOWN/POINTER_UP/CANCEL, invalid data, pointer limits, transactional release.
- EvdevInputEventParserTest: keyboard, mouse buttons, relative axes, wheel, malformed input, ignored touchscreen events.
- InputEngineTest: mapped keyboard touch, WASD joystick, right-button drag, left-button anchored click, reset/release.

Automated unit tests and an APK build do not replace physical-device testing. Before calling a build release-ready, verify device-node access, injected DOWN/MOVE/UP acceptance, multi-touch ordering, orientation and screen-size mapping, game behavior, game switching, app background/foreground transitions, and battery/CPU use over a real play session.
