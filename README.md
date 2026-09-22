# Bifrost RP5 Edition

**Bifrost RP5 Edition** is a Retroid Pocket 5-focused enhancement/fork of Bifrost, built around the RP5's independent left/right LED hardware.

The project keeps the useful Bifrost features that already work on the RP5 while introducing a dedicated LED engine designed for low latency, smooth transitions, efficient background operation, calibration, and extensible effects.

> **Development status:** v2 architecture work is in progress. The existing `v0.1.0-alpha.1` release is a known-good RP5 baseline. The new RP5 LED engine is being introduced incrementally and will be validated on real RP5 hardware before release.

---

## 🎮 RP5 Edition Goals

The v2 direction is to make Bifrost feel like a purpose-built RP5 lighting system rather than a collection of independent animations.

### Core architecture

```
Screen / Audio / Battery / Temperature / App Profile
                         ↓
                   Effect Engine
                         ↓
                    Frame Mixer
                         ↓
              Color Processing Layer
                         ↓
                  LED Scheduler
                         ↓
                   RP5 LED HAL
                         ↓
              PServer / LED Driver
                         ↓
                  Left + Right LEDs
```

The scheduler will become the single authority for LED output. Effects generate desired frames; the scheduler handles timing, smoothing, brightness, gamma, duplicate-frame suppression, priorities, and hardware writes.

---

# ✨ Current Features

Bifrost already provides a substantial set of lighting features, including:

- **Ambient**
- **Audio Reactive**
- **Ambi Aurora**
- Static, Breath, Rainbow, Pulse, Strobe, Sparkle, Rave, Chase and other animations
- Independent left/right LED colors
- Per-app profiles
- Automatic profile switching
- Charging indicators
- CPU temperature effects
- Presets
- Preset artwork
- Preset export/import
- Auto-start support
- External API / live effect control
- MediaProjection screen capture
- Accessibility capture fallback
- Persistent foreground service controls

These existing capabilities are being retained while the RP5-specific engine is developed.

---

# 🧩 v2 RP5 LED Engine

The first v2 architectural layer now lives under:

`app/src/main/java/com/moonbench/bifrost/rp5/`

### LED frame model

A `LedFrame` represents the complete desired state of both physical LEDs:

- Left RGB
- Right RGB
- Frame timestamp

This gives every effect a common output format.

### LED driver abstraction

The new `LedDriver` interface separates the LED engine from the underlying RP5 hardware transport.

This allows the project to support:

- The real RP5 driver
- Future Retroid hardware
- A mock driver for automated tests

### Central scheduler

`LedScheduler` provides the foundation for:

- Configurable refresh rate
- Frame coalescing
- Duplicate-frame suppression
- Brightness processing
- Gamma processing
- Smoothing
- Clean shutdown

The scheduler is intentionally being introduced before replacing the existing service's hardware path so existing functionality remains the reference implementation during migration.

### Colour processing

The new colour layer provides:

- Brightness scaling
- Gamma correction
- Left/right frame blending
- Transition-friendly colour interpolation

---

# 🎨 RP5 Colour Sampling

One of the major v2 features is a dedicated **Colour Sampling** system designed around the physical RP5 analogue sticks.

Each LED can have its own sampling region:

```
┌─────────────────────────────────┐
│                                 │
│                                 │
│     ┌───────┐       ┌───────┐   │
│     │ LEFT  │       │ RIGHT │   │
│     │ SAMPLE│       │ SAMPLE│   │
│     └───────┘       └───────┘   │
│                                 │
└─────────────────────────────────┘
```

### Calibration

The regions are stored using **normalized coordinates** rather than screen pixels.

That means calibration can survive changes in:

- Capture resolution
- Display resolution
- Aspect ratio
- Orientation

Each region has independently adjustable:

- X position
- Y position
- Size

The default regions are positioned near the lower left and lower right analogue-stick areas, but users can move them to match their own preferences.

### Live calibration

The same sampling squares will be available over the **live captured screen**.

Users will be able to:

1. Start Live Screen preview.
2. See the current captured image.
3. Drag the LEFT and RIGHT sampling regions.
4. Resize each region.
5. See the sampled colour immediately.
6. Test the physical LEDs.
7. Save the calibration.

A **Gallery** mode will also allow an image to be selected as a calibration canvas before testing against the live screen.

### Sampling methods

The engine is designed to support:

- **Average**
- **Center weighted**
- **Dominant colour**

Additional algorithms can be added without changing the rest of the LED pipeline.

### Stability

The sampling system is also intended to support:

- Colour dead zones
- Temporal smoothing
- Flicker reduction
- Orientation-aware mapping
- Optional overlay visibility
- Per-profile calibration

---

# 🎮 Calibration Profiles

Hardware calibration will be kept separate from game/effect profiles.

For example:

**RP5 Hardware Calibration**

- Left sampling region
- Right sampling region
- Sampling algorithm
- Orientation
- Overlay preference

**Game Profile**

- Ambient / Audio / Static / Ambi Aurora
- Brightness
- Smoothing
- Sensitivity
- Effect settings

This means the user calibrates the physical device once and can then reuse that calibration across games.

---

# 🎛️ Effect System Direction

v2 is designed around layered responsibilities rather than making every animation responsible for hardware timing.

A future configuration can conceptually look like:

### Base
Screen Ambient

### Modulator
Audio Reactive

### System Override
Battery warning

### Output
RP5 LED Scheduler

This allows the left and right LEDs to behave independently while still being controlled by a common timing and hardware layer.

Examples:

- Left LED samples the left side of the screen.
- Right LED samples the right side.
- Audio changes brightness without replacing the sampled colour.
- A low-battery warning can temporarily override normal effects.
- App profiles can change the active effect automatically.

---

# ⚡ Performance and Battery

RP5 Edition v2 will treat performance as part of the design rather than an afterthought.

Planned controls include:

### Battery Saver

- Lower capture rate
- Lower LED update rate
- Reduced processing

### Balanced

- Normal capture and LED refresh

### Performance

- Higher update rate
- Lowest practical latency

The system will also reduce unnecessary work when:

- The display is off
- The service is stopping
- No frame has changed
- An effect does not require continuous updates
- Battery or thermal policy requires throttling

Screen analysis will use reduced-resolution processing where possible, while calibration/live preview can temporarily use a higher-quality capture.

---

# 🛠️ Diagnostics and Developer Mode

v2 will include a development/diagnostics layer so hardware problems can be separated from capture and effect problems.

The planned diagnostics view includes:

```
BIFROST RP5 DIAGNOSTICS

Capture
  Resolution: 1920 × 1080
  FPS: 30

Sampler
  Left:  #4287F5
  Right: #E95671

LED
  Output FPS: 24
  Duplicate frames: 41%

Service
  Running: YES
```

A hardware test mode will provide direct testing of:

- Left LED
- Right LED
- Both LEDs
- Red
- Green
- Blue
- White
- Off
- Fade
- Pulse

A mock LED driver will allow the frame engine and colour-processing logic to be tested without physical hardware.

---

# 🔧 v2 Development Priorities

## Tier 1 — Engine foundation

- [x] LED frame model
- [x] LED driver abstraction
- [x] Central scheduler foundation
- [x] Brightness processing
- [x] Gamma processing
- [x] Frame blending foundation
- [x] Duplicate-frame suppression
- [x] Normalized sampling regions
- [x] Colour sampler foundation
- [ ] Integrate the scheduler with the existing RP5 hardware driver
- [ ] Hardware abstraction backed by the current LED controller
- [ ] Mock LED driver tests
- [ ] Service lifecycle integration
- [ ] Sleep/wake recovery
- [ ] Battery/thermal governor
- [ ] End-to-end latency diagnostics

## Tier 2 — RP5 sampling

- [x] Independent left/right regions
- [x] Adjustable normalized position
- [x] Adjustable region size
- [x] Average sampling
- [x] Center-weighted sampling
- [x] Dominant-colour foundation
- [ ] Live screen calibration UI
- [ ] Gallery calibration UI
- [ ] Drag/resize overlays
- [ ] Sampled-colour indicators
- [ ] Physical LED test from calibration screen
- [ ] Calibration persistence
- [ ] Orientation-aware calibration
- [ ] Overlay visibility toggle

## Tier 3 — Product polish

- [ ] RP5-focused settings/navigation
- [ ] Diagnostics screen
- [ ] Developer mode
- [ ] Hardware test screen
- [ ] Profile/calibration separation
- [ ] Expanded profile import/export
- [ ] Presets for common RP5 layouts
- [ ] Performance governor
- [ ] Automatic recovery/failsafe LED off

## Tier 4 — Advanced effects

- [ ] Layered effects
- [ ] Audio + ambient blending
- [ ] Advanced colour interpolation
- [ ] Region-of-interest capture optimization
- [ ] Additional sampling algorithms
- [ ] Shareable community profiles

---

# 🧪 Testing Strategy

Every major v2 change should be tested at two levels.

### Automated

- Colour sampler tests
- Normalized coordinate tests
- Colour-processing tests
- Scheduler tests
- Profile/calibration migration tests
- Service lifecycle tests

### Real RP5

The physical device remains the final authority for:

- LED latency
- Left/right synchronization
- Brightness behaviour
- Sleep/wake recovery
- Battery impact
- Thermal behaviour
- MediaProjection behaviour
- Live sampling accuracy

Important test scenarios include:

- Game → Home → Game
- Game → Sleep → Wake → Game
- Reboot → Auto Start
- Screen rotation
- Changing app profiles
- Starting/stopping capture
- Battery saver changes
- Charging/unplugging
- Service restart
- Rapid colour changes
- Rapid audio changes

---

# 📦 Installation

The latest tested APKs are published through the project's GitHub Releases.

For the current RP5 baseline, use the **v0.1.0-alpha.1** release.

Future v2 builds will be clearly labelled as development builds until the new engine has been validated on real RP5 hardware.

---

# 🔒 Permissions

Ambient, Audio Reactive, Ambi Aurora and live screen sampling may require Android screen-capture permission.

Screen data is processed locally for lighting purposes. Bifrost is not intended to save or transmit captured screen contents.

Additional Android permissions may be required for:

- Foreground service operation
- Notifications
- Accessibility fallback capture
- Automatic startup
- App-profile detection

---

# 🎮 Hardware Focus

Bifrost RP5 Edition is primarily developed and tested for:

- **Retroid Pocket 5**

The upstream Bifrost project supports other handheld hardware. RP5-specific behaviour is being isolated where practical so future hardware support does not require rewriting the effect system.

---

# 📌 Project Status

The project is actively being developed as **Bifrost RP5 Edition**.

The current release is a stable baseline for testing the existing Bifrost functionality on the RP5.

The v2 branch is introducing the new architecture incrementally. Features marked as planned are design targets and should not be considered available until their implementation and RP5 testing are complete.

---

# ❤️ Credits

Bifrost RP5 Edition builds on the work of the original Bifrost project and its contributors.

Huge thanks to the upstream Bifrost contributors for the existing LED, capture, profile, animation, service and integration work that makes the RP5 Edition possible.

---

# 📜 License

This project is licensed under **GPLv3**.

You are free to use, study, modify, and redistribute the app under the terms of the GPLv3 license.

