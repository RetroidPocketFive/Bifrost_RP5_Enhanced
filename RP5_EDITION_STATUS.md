# Bifrost RP5 Edition — Development Status

This repository is the starting point for the Bifrost RP5 Edition project.

## Current baseline

- Retroid Pocket 5 / SN3112L + SN3112R LED support
- Screen/ambient capture pipeline
- Audio-reactive pipeline
- AmbiAurora mode
- Presets and per-app profiles
- Scheduling and boot handling
- External API
- Plugin infrastructure
- Gradle Wrapper 8.13
- Android Gradle Plugin 8.13.2

## Next engineering targets

1. Central RP5 LED frame scheduler
2. Duplicate-frame suppression and adaptive refresh
3. Unified left/right LED frame submission
4. Screen sampling and ambient latency optimization
5. Audio analysis optimization
6. RP5 lifecycle, sleep/wake and battery behavior
7. RP5-focused UI and profiles
8. Hardware testing on a physical Retroid Pocket 5

This file intentionally documents the baseline and roadmap; implementation changes should be made incrementally and tested on hardware.
