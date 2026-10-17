---
name: sherpa-onnx-stt
description: Build 100% offline streaming speech-to-text using Sherpa-ONNX C++ JNI bindings, memory ring buffers, barge-in interruptibility, and voice activity detection.
---

# Sherpa-ONNX Offline Speech-to-Text

Integration guide for real-time, 100% offline speech recognition on Android using Sherpa-ONNX with ambient sous-chef copilot support.

## When to Use
- Adding hands-free voice dictation for messy kitchen hands.
- Operating an ambient hands-free voice companion during cooking with barge-in interruptibility.
- Streaming real-time transcription tokens without internet access.

## Core Architectural Invariants
1. **NDK ABI Filtering:** Mandate ABI filtering in `build.gradle` (`ndk { abiFilters.addAll(listOf("arm64-v8a", "x86_64")) }`) to prevent bloated 200MB universal APKs and `UnsatisfiedLinkError` crashes.
2. **Ephemeral Memory Discipline:** Microphone PCM chunks must stream into circular ring buffers in volatile memory (maximum 10 seconds capacity). Strictly prohibit writing `.wav` or temporary audio files to disk to maintain zero-trace privacy.
3. **Barge-In Interruptibility:** Audio capture must integrate with the TTS narration system. When incoming user speech is detected during step readout, immediately pause TTS playback to listen to the user's question or timer intent.
4. **Low-Power VAD for Cooking Mode:** Use energy-efficient Voice Activity Detection (VAD) during cooking companion mode to process audio frames only when human speech is detected, maintaining battery consumption $<4\%$ over 30 minutes.
5. **Thread Isolation & JNI Dispatchers:** Run native C++ inference on a dedicated `Dispatchers.Default` background thread pool pinned away from the main UI thread to ensure continuous speech recognition never drops frames in Jetpack Compose.
