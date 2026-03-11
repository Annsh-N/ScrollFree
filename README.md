# ScrollFree

ScrollFree is an Android app that enables **hands-free scrolling** using blink gestures.

It uses the front camera + ML Kit face detection to recognize blink patterns, then sends scroll gestures through an accessibility service so users can move through content without touching the screen.

## Why This Project

This project explores a practical assistive interaction model:
- Reduce dependency on touch gestures for short reading sessions
- Offer a lightweight path toward accessibility-focused controls
- Demonstrate end-to-end Android systems work (camera, foreground service, overlay UI, accessibility gestures)

## MVP Features

- Blink detection running in a foreground service
- Gesture mapping:
  - Single blink -> Scroll down
  - Double blink -> Scroll up
- Debounce/cooldown logic to reduce accidental repeated triggers
- Adjustable blink sensitivity in-app
- Floating widget with:
  - Active/inactive state indicator
  - Real-time swipe feedback (`Scroll ↑` / `Scroll ↓`)
- Onboarding/checklist UI for required permissions:
  - Camera
  - Draw over other apps (overlay)
  - Accessibility service enablement

## Tech Stack

- **Language:** Kotlin
- **UI:** XML Views (Activity + floating overlay)
- **Camera Pipeline:** CameraX (`ImageAnalysis`)
- **Vision:** Google ML Kit Face Detection
- **System Integration:**
  - Foreground `Service`
  - `AccessibilityService` with gesture dispatch
  - Overlay window via `WindowManager`
- **State/Settings:** SharedPreferences + Kotlin `StateFlow`

## Architecture Overview

- `MainActivity`
  - Setup flow, permission actions, runtime controls, sensitivity tuning
- `BlinkDetectionService`
  - Foreground execution, camera stream setup, overlay rendering, action dispatch
- `FaceAnalyzer`
  - ML Kit-based eye state analysis + blink pattern recognition
- `ScrollAccessibilityService`
  - Executes swipe gestures at OS level for cross-app scrolling
- Shared state:
  - `AppSettingsRepository` for persisted user preferences
  - `AppRuntimeState` for live service/overlay state

## Getting Started

### 1. Prerequisites

- Android Studio (recent stable)
- Android device/emulator (API 24+)
- Front camera available for testing detection quality

### 2. Build and Run

1. Open project in Android Studio.
2. Let Gradle sync.
3. Run the `app` configuration on a device.

From terminal:

```bash
./gradlew :app:assembleDebug
```

### 3. First-Time Setup in App

1. Grant camera permission.
2. Grant overlay permission (if you want floating widget feedback).
3. Open accessibility settings and enable **ScrollFree** service.
4. Turn on **Enable Blink Detection**.

## How It Works (Gesture Logic)

- Eye openness probabilities are read from ML Kit per frame.
- A blink is recognized when both eyes close below a sensitivity-derived threshold and reopen within a blink duration window.
- Pattern recognition:
  - First blink starts a short timer.
  - If a second blink occurs inside the configured window -> double blink action (`Scroll up`).
  - If not -> single blink action (`Scroll down`).
- A cooldown interval prevents rapid repeated actions.

## Permissions Used

- `CAMERA` - read front camera frames for blink detection
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CAMERA` - keep detection running reliably
- `SYSTEM_ALERT_WINDOW` - draw floating feedback widget
- Accessibility binding (`BIND_ACCESSIBILITY_SERVICE`) - execute scroll gestures

## Current Limitations

- Detection quality varies by lighting, camera angle, and user blink style
- Gesture mapping is intentionally simple in MVP (single/double blink)
- No analytics/cloud sync; all settings stay local on-device

## Roadmap Ideas

- Per-user calibration wizard
- Additional gestures (long blink, wink, gaze direction)
- Haptic/audio feedback modes
- Session-level usage metrics and adaptive thresholding
- Expanded test coverage and device compatibility matrix

## Repository Notes

This branch includes phased commits that track MVP development from core state management through UI/overlay polish and docs.
