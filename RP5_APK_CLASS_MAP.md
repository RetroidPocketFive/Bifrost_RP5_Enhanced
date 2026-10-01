# RP5 APK Class Map

Generated from the golden APK's DEX metadata. This is a recovery reference, not a replacement implementation.

## Calibration

### LedColorCalibration

Declared methods in the APK:

- `apply(Context, Stick, Int): Int`
- `applyDual(Context, Int, Int): Pair`
- `applyWithCommands(Int, CommandColor, CommandColor, CommandColor): Int`
- `getPrimaryCommand(Context, Stick, Primary): CommandColor`
- `reset(Context, Stick)`
- `setPrimaryCommand(Context, Stick, Primary, CommandColor)`
- internal `key(Stick, Primary, String): String`
- internal `prefix(Stick): String`
- internal `roundToInt(Float): Int`

The APK persists calibration under `bifrost_led_color_calibration`. Primary colours are RED, GREEN and BLUE. The implementation stores independent R/G/B command values for each stick and primary, then transforms output through the three calibrated command colours.

### Rp5CalibrationActivity

The APK activity is only about 180 Kotlin source lines according to its embedded Kotlin SMAP metadata. Its declared operations include:

- `load(): Rp5Calibration`
- `chooseLive()`
- `requestLivePermission()`
- `startLiveCapture(resultCode, data)`
- `stopCapture()`
- `resampleCurrentFrame()`
- `sampleBitmap(bitmap)`
- `save(leftRegion, rightRegion)`
- `testPhysicalLeds()`

It also has live-capture and still-image picker callbacks.

### Rp5CalibrationView

The APK view exposes:

- `leftRegion/rightRegion`
- `leftColor/rightColor`
- `setFrame(Bitmap)`
- `sample(ColorSampler): SampledColors`
- touch handling for the normalized regions
- drawing of the two regions

This confirms that calibration is region-based and interactive; it is not just a matrix editor.

### ColorSampler / SampledColors / SamplingMethod

APK sampling methods:

- AVERAGE
- CENTER_WEIGHTED
- DOMINANT

`ColorSampler.sample` accepts an ARGB pixel array, width/height, and two NormalizedRegion objects and returns `SampledColors(left, right)`.

## LED output pipeline

### LedOutputGovernor

Inputs:

- `batteryPercent: Int`
- `batteryTemperatureC: Float?`
- `pluggedIn: Boolean`
- `requestedBrightness: Int`

Decision:

- `brightness: Int`
- `scale: Float`
- `thermalScale: Float`
- `batteryScale: Float`

The APK has a distinct governor layer. Do not bypass this with a new ad-hoc brightness implementation.

### LedScheduler

Constructor contract includes:

- `LedDriver`
- `ScheduledExecutorService`
- integer timing parameter
- three float parameters

Operations:

- `start()`
- `stop(clear: Boolean)`
- `submit(LedFrame)`
- `tickForTest()`
- internal frame merge/tick logic

### Supporting RP5 classes

The APK also contains:

- `LedDriver`
- `LedFrame`
- `LedOutputGovernor`
- `LedScheduler`
- `MockLedDriver`
- `LedColorProcessor`
- `NormalizedRegion`

## Custom screen sampling

The APK contains a separate sampling subsystem:

- `SamplingEditorActivity`
- `SamplingCanvasView`
- `SamplingRegion`
- `SamplingRegionStore`
- `ScreenColors`
- `ScreenColorStabilityFilter`
- `ScreenAnalyserKt`

`SamplingEditorActivity` contains an actual editor, screenshot capture, still-image import, LED test, save/apply flow, and match-adjustment logic. Its embedded Kotlin source map is 570 lines.

## Implication for recovery

The earlier PR #6 calibration implementation was too broad in one class and did not reproduce this APK architecture. The recovery should restore these contracts as separate components and then reconnect them to `LEDService`, Ambient/Ambi Aurora, and the settings UI.

No Home-screen work is part of this subsystem recovery.
