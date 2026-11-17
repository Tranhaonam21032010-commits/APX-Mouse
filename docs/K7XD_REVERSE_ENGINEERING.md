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

## Injection evidence
DEX strings include InputManager, InputManagerGlobal, injectInputEvent, and failure messages for resolving the InputManager or injection method. This supports an InputManager injection attempt; it does not prove that Android accepts it on every device.

## APX comparison and integration
The APX input-reliability branch already has Shizuku UserService access, selected-device discovery, getevent -lt streaming, relative/key/button parsing, touch mapping, serialized multi-pointer frames, and InputManager injection.

The key gap was raw capture: getevent reads selected nodes but does not implement explicit native exclusive grabbing. This branch now prefers a native EVIOCGRAB capture path and retains getevent as a non-exclusive fallback when the kernel or Shizuku identity refuses the grab.

## Verification boundaries
Unit tests cover numeric evdev decoding and relative-frame grouping. CI must verify Kotlin/JNI/CMake compilation and APK packaging. Physical-device tests are still required for Shizuku access, EVIOCGRAB permission, no stuck keys after stop/disconnect, touchscreen continuity, and game-specific touch injection acceptance.
