# Bifrost RP5 APK Recovery Baseline

This branch is a recovery baseline. It is intentionally based on the accepted Home-screen test branch, not on PR #5.

## Golden APK

Authoritative reference:

- File: `Bifrost-RP5-Edition-version 1.7.0-alpha.3-debug.apk`
- SHA-256: `cfc9e5753ded3d4202e62b8e5d70d48bab6ccd5ced56a509884397c774a741e9`
- Size: 32,887,578 bytes

The APK is the behavioral/reference artifact. Existing source code is not assumed to be complete merely because it builds.

## Rules for recovery

1. Do not redesign the Home screen unless explicitly requested.
2. Do not replace APK behavior with a simplified reimplementation when the APK contains the original implementation.
3. Before adding a missing feature, identify its APK class/resources, its callers, persistence keys, and related UI strings.
4. Recover feature groups as coherent units and build/test after each unit.
5. Preserve every known-good APK with its SHA-256 and the source commit that produced it.
6. PR #5 is not a baseline.
7. `main` is a source baseline only; it is not the authoritative feature baseline.

## APK/source gap discovered so far

The APK contains implementation classes that are absent from `main`. Important examples include:

### RP5 calibration and sampling

- `com.moonbench.bifrost.rp5.LedColorCalibration`
- `com.moonbench.bifrost.rp5.Rp5CalibrationActivity`
- `com.moonbench.bifrost.rp5.Rp5CalibrationView`
- `com.moonbench.bifrost.rp5.SampledColors`
- `com.moonbench.bifrost.rp5.SamplingMethod`
- `com.moonbench.bifrost.rp5.LedOutputGovernor`
- `com.moonbench.bifrost.SamplingEditorActivity`
- `com.moonbench.bifrost.tools.SamplingRegion`
- `com.moonbench.bifrost.tools.SamplingRegionStore`
- `com.moonbench.bifrost.tools.ScreenColorStabilityFilter`
- `com.moonbench.bifrost.tools.ScreenColors`
- `com.moonbench.bifrost.tools.ScreenAnalyserKt`
- `com.moonbench.bifrost.ui.SamplingCanvasView`

### Preset/UI support

- `com.moonbench.bifrost.IconLabelSpinnerAdapter`
- `com.moonbench.bifrost.ui.DeletePresetDialog`
- `com.moonbench.bifrost.tools.Crossfade`
- `com.moonbench.bifrost.PresetVisualSpec`

### Scheduling

The APK contains additional scheduling model classes not currently present as source:

- `com.moonbench.bifrost.schedule.DateWindow`
- `com.moonbench.bifrost.schedule.ScheduleAction`
- `ScheduleAction.PlayPreset`
- `ScheduleAction.TurnOff`

### Evidence in APK resources/strings

The APK contains strings/resources for:

- RP5 LED calibration
- live-screen and still-image calibration
- left/right stick selection
- draggable/sampling UI
- reset/save/test calibration flows
- clear-cache confirmation and status
- preset artwork and visual-spec handling
- scheduling
- screen-colour stability/sampling

## Current experimental work

PR #6 contains a first-pass calibration implementation. That code is **not yet considered an exact recovery of the APK**. The next recovery work must compare the APK implementation and the current source before extending that implementation.

## Recovery objective

Restore the missing implementation from the APK reference while retaining the currently accepted Home layout:

- portrait Home: accepted
- landscape Home: accepted
- Heimdall label/control: accepted as currently implemented
- Clear Cache: accepted with current compact layout
- settings close/exit control: accepted

No Home redesign is part of this recovery task.
