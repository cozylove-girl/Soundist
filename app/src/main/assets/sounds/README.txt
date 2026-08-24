Soundist release audio assets are staged into this directory by
scripts/stage-android-audio-assets.ps1 before building an installable APK.

The source of truth remains the repository public/sounds directory. Generated
audio files under app/src/main/assets/sounds are excluded from version control.

The 84 staged files are exact copies of Moodist upstream commit
983f7412e8cd054e76d156977c563da2028e4428. Moodist declares its third-party
sounds under the Pixabay Content License or CC0 1.0, but does not publish a
per-file mapping. See apps/android-native/AMBIENT_AUDIO_MANIFEST.tsv and
apps/android-native/THIRD_PARTY_AUDIO_NOTICES.md for hashes and evidence.
