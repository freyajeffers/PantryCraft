# PantryCraft: Phase 4 Implementation Plan

* * *

## Real-Time Multimodal Voice & Semantic NLP Ingestion

* * *

**Phase 4** | **Offline Voice STT** | **Ambient Cooking Companion**

### 1. Phase Scope & Objectives

Phase 4 expands PantryCraft beyond visual and manual ingestion by introducing a 100% offline, hands-free voice ingestion pipeline and real-time semantic natural language parser. Building on top of the established inventory model and meal engine (Phases 1–3), Phase 4 enables users to log groceries verbally while unpacking and operate an ambient hands-free voice companion while cooking.

* **Offline Streaming Speech-to-Text:** Integrates Sherpa-ONNX with INT8 quantized Zipformer (~25–35MB) or Whisper-tiny streaming acoustic models (packaged with `arm64-v8a` ABI filtering), with fallback to Android's built-in offline `SpeechRecognizer` service.
* **Semantic Entity Extractor:** Translates freeform colloquial kitchen speech (e.g. *"Put two cartons of almond milk in the fridge and a bag of basmati rice in the pantry"*) into typed inventory records (`pantry_items`) with quantities, units, locations, shelf-life estimates, and seasoning tags.
* **Ambient Hands-Free Cooking Companion:** Supports voice step navigation (*"Next step"*, *"How much garlic did I need?"*) and mid-cooking stock depletion without touching the screen.

### 2. Architectural Components & Responsibilities

| Component | Package / Namespace | Responsibilities |
| :--- | :--- | :--- |
| **AudioRecordController** | `feature.voice.audio` | Captures 16kHz 16-bit mono PCM audio from the microphone; streams non-blocking byte chunks via a Kotlin `SharedFlow` into ephemeral ring buffers. |
| **SherpaOnnxSpeechEngine** | `feature.voice.stt` | Feeds raw PCM audio chunks into the Sherpa-ONNX online recognizer native JNI engine; emits partial and finalized text tokens at sub-100ms latency. |
| **AndroidSpeechFallbackEngine** | `feature.voice.stt` | Wraps Android `SpeechRecognizer` with `EXTRA_PREFER_OFFLINE` enabled for low-end hardware. |
| **SemanticIngredientParser** | `feature.voice.nlp` | Uses on-device SLM or rule-based heuristics to decompose raw transcripts into structured `pantry_items` records with fuzzy category mapping. |
| **AmbientCookingVoiceAssistant** | `feature.voice.companion` | Manages low-power Voice Activity Detection (VAD) during active recipe execution to answer ingredient queries and advance recipe steps. |
| **VoiceInputHUD** | `feature.voice.ui` | Jetpack Compose push-to-talk button, real-time waveform visualizer, live transcript stream, and staging tray for parsed items. |

### 3. Data Contracts & NLP Transformation Pipeline

#### A. Speech Ingestion & Ambient Companion Flow

```text
[ Microphone (16kHz PCM) ]  
        │  
        ▼ (50ms chunks)  
[ AudioRecordController (Ring Buffer) ]  
        │  
        ▼ (Kotlin SharedFlow<ByteArray>)  
[ SherpaOnnxSpeechEngine ] (C++ JNI via sherpa-onnx)  
        │  
        ▼ (Streaming Tokens)  
  ├── Mode A (Grocery Ingestion):  
  │     └── [ SemanticIngredientParser ] -> Structured PantryItem JSON -> Room DB  
  │  
  └── Mode B (Ambient Cooking Companion):  
        └── [ Intent Recognizer ] -> "Next step" / "Read timer" / "Deduct 1 tbsp butter"  
```

#### B. NLP Extraction Structured JSON Contract

```json
{  
 "raw_transcript": "two pounds of roma tomatoes on the counter, three ribeye steaks in the freezer, and half a gallon of whole milk in the fridge",  
 "parsed_items": [  
   {  
     "name": "Roma Tomatoes",  
     "storageLocation": "COUNTER",  
     "category": "PRODUCE",  
     "quantity": 2.0,  
     "unit": "POUNDS",  
     "isOpened": false,  
     "isAromaticOrSeasoning": false,  
     "estimated_shelf_life_days": 7  
   },  
   {  
     "name": "Ribeye Steaks",  
     "storageLocation": "FREEZER",  
     "category": "MEAT",  
     "quantity": 3.0,  
     "unit": "COUNT",  
     "isOpened": false,  
     "isAromaticOrSeasoning": false,  
     "estimated_shelf_life_days": 180  
   },  
   {  
     "name": "Whole Milk",  
     "storageLocation": "FRIDGE",  
     "category": "DAIRY",  
     "quantity": 0.5,  
     "unit": "GALLONS",  
     "isOpened": true,  
     "isAromaticOrSeasoning": false,  
     "estimated_shelf_life_days": 7  
   }  
 ]  
}  
```

### 4. Audio Privacy, Sandboxing & Memory Hygiene

* **Zero Disk Footprint:** Audio buffers exist exclusively in volatile memory ring buffers (max 10 seconds capacity). Zero `.wav` or temporary audio files are written to disk.
* **Complete Local Processing:** In offline voice mode, all acoustic and language weights run on-device via ONNX Runtime Mobile. Audio frames never leave the handset.

### 5. Acceptance & Verification Gates

* **Real-Time Factor (RTF):** Sherpa-ONNX streaming transcription maintains RTF < 0.35 on a standard ARM64 CPU.
* **Entity Extraction Precision:** Automated testing against 100 conversational speech transcripts achieves >90% accuracy in entity and attribute mapping.
* **Ambient Cooking Battery SLA:** 30 minutes of active cooking companion VAD listening consumes < 4% battery capacity.
