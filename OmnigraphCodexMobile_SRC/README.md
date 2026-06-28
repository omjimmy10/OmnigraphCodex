# OmnigraphCodex Mobile

Native Android Alpha for OmniGraphCodex Method A / Channel Multiplexing.

## Alpha scope

- Import audio through the Android file picker.
- Normalize decoded audio to mono 44.1 kHz 16-bit PCM.
- Encode Method A to a square lossless PNG.
- Import an encoded PNG and decode it to WAV.
- Preview encoded and decoded images.
- Play normalized or decoded WAV output with play/pause, seek, progress, and duration.
- Save PNG files to Pictures/OmnigraphCodexMobile/.
- Save WAV files to Music/OmnigraphCodexMobile/.
- Build a debug APK with Codemagic.

## Method A compatibility

The codec core follows the PC reference behavior:

- Convert signed 16-bit PCM samples into 8-bit values.
- Split the 8-bit stream into sequential red, green, and blue channel segments.
- Store those segments row-by-row in a square RGB image.
- Fill only unused image pixels with zero values.
- Decode by flattening all red values, then all green values, then all blue values.
- Convert the reconstructed 8-bit stream back to signed 16-bit PCM WAV.

This Alpha uses Android's native media decoder for import normalization instead of bundling FFmpegKit, because FFmpegKit is retired and can make fresh CI builds brittle. The internal codec still uses the PC-compatible Method A channel layout.

## Codemagic setup

If this folder is uploaded inside the main repo as OmnigraphCodexMobile/, set the Codemagic project path to OmnigraphCodexMobile.

Run the android-debug workflow. The APK artifact will be collected from app/build/outputs/apk/debug/*.apk.

