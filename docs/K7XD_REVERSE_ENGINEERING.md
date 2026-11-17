# K7XD Lite v1.0.0 — static reverse-engineering notes

## Scope and confidence
Based on static inspection of the supplied APK: DEX string/type/method tables, ELF imports, and native-library strings. The APK was not executed. Names/imports are direct evidence; runtime ordering, exact ioctl flags, timing, and device compatibility remain inferences pending physical-device tests.

## Native library evidence
The APK packages libvdmaper_input.so for arm64-v8a, armeabi-v7a, x86, and x86_64.

The ARM64 ELF imports ioctl, open, read, poll, eventfd, pthread_create, pthread_join, inotify_init1, inotify_add_watch, and synchronization functions.

Native strings include /dev/input, /dev/input/event%d, messages saying devices are captured/grabbed, all input devices are ungrabbed and released, touchscreen is monitored without grab, typing mode releases the keyboard while the mouse remains grabbed, and the input reader thread has started.

Together these are strong evidence for a native evdev reader with explicit grab/release, a background polling thread, device-directory monitoring, and a touchscreen monitor that does not grab the touchscreen. The ioctl import plus grab/release messages strongly suggest EVIOCGRAB semantics; the exact ioctl argument was not independently decoded from machine instructions in this pass.

## DEX API evidence
NativeInputGrabber declares startGrab(): Int, stopGrab(): Unit, nativeSetPollingRate(Int): Unit, and onNativeInputEvent(Int, Int, Int, Int).

The IVDMapperService AIDL surface includes takeRawMouseDelta(), takeRawMouseClick(), injectFpsCamera(Float, Float), moveVirtualMouse(Float, Float), updateJoystick(Float, Float), updateScreenSize(Int, Int), setMouseSensitivity(Int), and setPollingRate(Int).

The DEX also contains MultiTouchInjector, MotionEvent.PointerProperties, MotionEvent.PointerCoords, pointer down/up/cancel strings, and a touch-cap diagnostic. This supports multi-pointer handling but does not prove every device/game combination works.

## FPS camera and touch injection evidence

The DEX data-class string for FpsCameraConfig exposes these fields: enabled, centerX, centerY, radius, sensitivity, sensitivityX, sensitivityY, invertX, invertY, triggerKey, resistanceRatio, edgeResetMode, and mousePollingRate.

The service method injectFpsCamera(Float, Float) reads the FPS-camera profile and calls the touch helper pm.p three times. The last argument at those three call sites is 0, 2, and 1, matching Android MotionEvent ACTION_DOWN, ACTION_MOVE, and ACTION_UP. The helper pm.e constructs MotionEvent via MotionEvent.obtain(...) and invokes a reflected Method; the helper pm.j contains MultiTouchInjector logic and a diagnostic that caps live pointers at 12.

The native callback handler a.o(Int, Int, Int, Int) also reads the FPS-camera profile, accesses sensitivityX/sensitivityY, invertX/invertY, and resistanceRatio, and calls the same touch helper. This strongly supports the model that raw mouse deltas are converted into short touchscreen gestures centered on the configured FPS-camera point. The exact scaling, resistance, edge-reset, and trigger-key formula still requires full instruction-level reconstruction or runtime tracing.

DEX strings also include InputManager, InputManagerGlobal, injectInputEvent, and failure messages for resolving the InputManager or injection method. This supports an InputManager injection attempt; it does not prove that Android accepts it on every device.

## APX comparison and integration
The APX input-reliability branch already has Shizuku UserService access, selected-device discovery, getevent -lt streaming, relative/key/button parsing, touch mapping, serialized multi-pointer frames, and InputManager injection.

The first confirmed gap was raw capture: getevent reads selected nodes but does not implement explicit native exclusive grabbing. This branch now prefers a native EVIOCGRAB capture path and retains getevent as a non-exclusive fallback when the kernel or Shizuku identity refuses the grab. A second gap remains: APX currently ignores InputEngine.TouchCommand.AimDelta in ShizukuInputBackend, so K7XD's FPS-camera swipe path has not yet been ported. It is intentionally not approximated with an unverified sensitivity/resistance formula.

## Verification boundaries
Unit tests cover numeric evdev decoding and relative-frame grouping. CI must verify Kotlin/JNI/CMake compilation and APK packaging. Physical-device tests are still required for Shizuku access, EVIOCGRAB permission, no stuck keys after stop/disconnect, touchscreen continuity, and game-specific touch injection acceptance. APX's native reader currently does not reproduce K7XD's inotify hotplug watcher or its ungrabbed touchscreen monitor; it stops and releases grabbed nodes on a disconnect.
