# Backtrack (Retroactive Audio Recorder)

**Backtrack** flips audio recording from predictive to retroactive. It continuously listens in a transient, rolling RAM buffer, acting as a personal audio cache. If nothing noteworthy occurs, the audio simply evaporates, overwritten in memory with **zero wear on phone flash storage**. If an epiphany, breakthrough discussion, off-hand instruction, or verbal agreement occurs, the user captures the audio that already happened (up to 10 minutes prior) with a single tap.

---

## Architecture Overview

```
[Hardware Mic] 
      │ (Raw 16-bit PCM Byte Stream via AudioRecord @ 16 kHz Mono)
      ▼
[BacktrackService] ── (PARTIAL_WAKE_LOCK & AudioFocus Listener)
      │
      ▼
[AudioRingBuffer (RAM)] ── Capacity: 10 mins (~19.2 MB)
      │                   Continuous FIFO Overwrite
      │
      ├── Actions: "Save Last 5 Min" / "Save Full Buffer (10 Min)"
      ▼
[WavExporter] ── Synthesize standard 44-byte RIFF/WAVE header
      │
      ▼
[Storage Engine] ── Write to app-specific Recordings directory (.wav)
      │
      ▼
[Compose UI & Lock Screen Notification] ── Live Waveform, In-Line Playback, Share
```

---

## Technical Specifications

### 1. In-Memory Audio Ring Buffer (`audio/AudioRingBuffer.kt`)
- **Audio Specs**: 16,000 Hz, 16-bit PCM, Mono channel.
- **Throughput**: $16{,}000 \text{ samples/sec} \times 2 \text{ bytes/sample} = 32{,}000 \text{ bytes/sec}$.
- **Buffer Capacity**: $32{,}000 \times 60 \times 10 = 19{,}200{,}000 \text{ bytes}$ (~18.31 MB).
- **5-Minute Slice**: $32{,}000 \times 60 \times 5 = 9{,}600{,}000 \text{ bytes}$ (~9.15 MB).
- **Concurrency**: Thread-safe synchronized writes and non-blocking ordered snapshot reads.
- **Graceful Truncation**: Gracefully saves whatever audio is available if recording duration is under 5 or 10 minutes.

### 2. Foreground Service & Hardware Handling (`service/BacktrackService.kt`)
- **Service Type**: `android:foregroundServiceType="microphone"`.
- **Power Management**: Holds `PowerManager.PARTIAL_WAKE_LOCK` while listening to prevent CPU sleep when screen locks.
- **Audio Focus Management**: Reacts to `AudioManager.OnAudioFocusChangeListener` (`AUDIOFOCUS_LOSS`, `AUDIOFOCUS_LOSS_TRANSIENT`) by pausing `AudioRecord` during cellular phone calls, camera recording, or assistant interactions, and automatically resumes on `AUDIOFOCUS_GAIN`.
- **Persistent Notification**: Ongoing, high-visibility notification with 3 direct lock-screen action buttons:
  - `Save Last 5 Min` (`ACTION_SAVE_5`)
  - `Save Last 10 Min` (`ACTION_SAVE_10`)
  - `Stop Listening` (`ACTION_STOP`)

### 3. WAV Export Engine (`audio/WavExporter.kt`)
- **Format**: Uncompressed 16-bit PCM RIFF `.wav`.
- **Header**: Standard 44-byte RIFF header (16 kHz, 1 channel, 16-bit sample, 32,000 byte rate).
- **Output Directory**: `context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)/Backtrack_YYYYMMDD_HHmmss.wav`.
- **Haptic Confirmation**: Vibrates via `VibrationHelper` upon successful export.

### 4. Modern Jetpack Compose UI (`ui/BacktrackScreen.kt`)
- **Status Dashboard**: Live multi-bar waveform visualizer pulsing with real-time mic amplitude.
- **Dynamic Readouts**: Active buffer duration (`07:42 / 10:00 filled`), RAM footprint meter, and audio format pill.
- **Controls**: Ergonomic Start/Stop Listening toggle and one-tap retroactive capture buttons.
- **Battery Optimization Banner**: Guidance card with 1-tap redirect to exempt app from OEM Doze restrictions.
- **Recordings History & Audio Player**:
  - Saved recordings list sorted newest first.
  - In-line audio player with Play/Pause, scrubber slider, and time indicators.
  - One-tap Android Share Sheet integration via `FileProvider`.
  - Delete with confirmation dialog.

---

## Project Structure

```
app/src/main/java/com/melonapp/an_rewind_recorder/
├── audio/
│   ├── AudioConstants.kt          # Spec constants (16kHz, buffer calculations)
│   ├── AudioRingBuffer.kt         # Thread-safe rolling RAM buffer
│   └── WavExporter.kt             # RIFF/WAV header synthesis and file writer
├── service/
│   ├── BacktrackService.kt        # Foreground mic service, wake lock & audio focus
│   └── BacktrackState.kt          # Reactive StateFlow communication bridge
├── util/
│   ├── AudioPlayerManager.kt      # MediaPlayer controller for inline playback & seeking
│   ├── BatteryOptimizationHelper.kt # Doze mode exemption handler
│   ├── NotificationHelper.kt      # Ongoing lock-screen notification & actions
│   ├── RecordingsRepository.kt    # Storage indexing, metadata, and FileProvider sharing
│   └── VibrationHelper.kt         # Haptic feedback confirmation
└── ui/
    ├── BacktrackScreen.kt         # Jetpack Compose UI (Dashboard, Player, Controls)
    └── BacktrackViewModel.kt      # ViewModel orchestrating service & player
```
