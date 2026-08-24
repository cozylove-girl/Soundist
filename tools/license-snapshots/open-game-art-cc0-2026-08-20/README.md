# OpenGameArt CC0 radio evidence

Reviewed and downloaded on 2026-08-20.

This folder records the source evidence for the ten CC0 tracks added to Soundist as replacements for the removed Open Music Archive recordings. Each entry in `manifest.json` binds together:

- the public source page and its saved HTML snapshot;
- the original downloaded attachment and SHA-256;
- the normalized Ogg Vorbis derivative bundled with Soundist and SHA-256;
- the credited creator shown on the source page;
- the applicable CC0 1.0 legal text.

The source pages explicitly display CC0. CC0 permits copying, modification and redistribution without a mandatory attribution condition. Soundist still retains creator and source information as provenance and courtesy credit.

Audio derivatives were produced with FFmpeg 9.0.1 using loudness normalization targeting `-18 LUFS`, true peak `-1.5 dBTP`, and `LRA 11`, then encoded as stereo 48 kHz Ogg Vorbis (`q5`). Measured output values are recorded per entry.

`originals/` and `pages/` are audit evidence and are not included in the APK. Runtime files live under `app/src/main/assets/radio/`.

