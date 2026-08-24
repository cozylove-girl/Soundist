# Soundist for Android

Soundist is a local-first Android sound workspace for ambient mixing, curated
open music, generative soundscapes, focus sessions, sleep timers, records and
notes. This repository contains the native Android application only.

## Requirements

- JDK 17
- Android SDK 36
- Android NDK 27
- CMake 3.22.1 or compatible Android SDK installation
- Git 2.40 or newer

The distributable audio library is stored in ordinary Git. After cloning, build
the app directly:

```powershell
.\gradlew.bat :app:assembleDebug
```

Local release signing files are intentionally excluded. See
`RELEASE_CHECKLIST.md` for release and device-verification requirements.

## Audio and licensing

Soundist contains ambient sounds derived from Moodist, open music recordings,
CC0 VSCO 2 CE instrument samples and project-owned interface audio. The source,
license and SHA-256 records are maintained in:

- `THIRD_PARTY_AUDIO_NOTICES.md`
- `RADIO_RELEASE_ATTRIBUTION.md`
- `VSCO_SAMPLE_LICENSE_MANIFEST.tsv`
- `tools/license-snapshots/`

Third-party audio remains governed by its own license. See `NOTICE.md` for the
project origin and complete notice entry points.

## Parent repository

The wider Soundist workspace can include this repository at
`apps/android-native` as a Git submodule. Android development and releases are
managed here independently from the desktop app and design prototypes.
