# PantryCraft: Phase 2 Implementation Plan

* * *

## CameraX Dual-Mode Vision Pipeline (Live & Still) & Visual Ingestion

* * *

**Phase 2** | **Multimodal Vision** | **CameraX & ML Kit**

### 1. Phase Scope & Objectives

Phase 2 implements the visual ingestion engine for PantryCraft. It delivers a fluid, dual-mode camera interface capable of:

1. **Continuous Real-Time Viewfinder HUD:** Running an offline, low-latency (15 FPS) analysis loop using Google ML Kit Object Detection and Barcode Scanning to project interactive bounding boxes and quick-add chips over detected pantry items in real time.
2. **High-Resolution Shelf Snapshot Analysis:** Capturing still photos of entire refrigerator or pantry shelves and dispatching them to a Vision Model (on-device Gemini Nano via AICore or BYOKey multimodal API) to decompose multi-item scenes into structured inventory entities with quantities, locations, and expiry dates.

### 2. Architectural Components & Responsibilities

| Component | Package / Namespace | Responsibilities |
| :--- | :--- | :--- |
| **CameraManager** | `feature.camera.core` | Initializes CameraX `ProcessCameraProvider`, binds `Preview`, `ImageAnalysis`, and `ImageCapture` use cases to the Compose lifecycle. |
| **LiveVisionAnalyzer** | `feature.camera.analyzer` | Processes `YUV_420_888` frames at 15 FPS via chained ML Kit Barcode Scanning + Object Detection on a background executor, gated by an `AtomicBoolean` concurrency lock. |
| **SnapshotVisionService** | `feature.camera.vision` | Compresses high-res still frames (in-memory WebP), extracts bounding regions, builds multimodal LLM prompt payloads, and parses structured JSON output. |
| **OpenFoodFactsOfflineCache** | `core.database.foodfacts` | Embedded lightweight SQLite lookup table mapping common UPC/EAN barcodes to product names, brands, categories, and standard shelf lives without internet. |
| **CameraOverlayHUD** | `feature.camera.ui` | Jetpack Compose canvas rendering live bounding boxes, confidence tags, barcode tooltips, and shutter controls. |
| **BatchReviewBottomSheet** | `feature.camera.ui` | Review screen displaying parsed items after snapshot or live capture for rapid confirmation or batch editing before committing to `pantry_items`. |

### 3. Data Contracts & JSON Schemas

#### A. Vision Multimodal Output Schema (LLM Contract)

The vision prompt mandates strict JSON response conformity with normalized coordinates `[ymin, xmin, ymax, xmax]` (range 0.0 to 1.0):

```json
{
  "detected_items": [
    {
      "name": "Greek Yogurt",
      "category": "DAIRY",
      "storage_location": "FRIDGE",
      "quantity": 1.0,
      "unit": "COUNT",
      "confidence": 0.95,
      "estimated_shelf_life_days": 14,
      "is_aromatic_or_seasoning": false,
      "bounding_box": [0.32, 0.15, 0.68, 0.42],
      "condition_notes": "Unopened 32oz tub"
    },
    {
      "name": "Baby Spinach",
      "category": "PRODUCE",
      "storage_location": "FRIDGE",
      "quantity": 1.0,
      "unit": "COUNT",
      "confidence": 0.88,
      "estimated_shelf_life_days": 5,
      "is_aromatic_or_seasoning": false,
      "bounding_box": [0.20, 0.45, 0.55, 0.85],
      "condition_notes": "Plastic clamshell, crisper drawer"
    }
  ]
}
```

#### B. Live Frame Analysis Pipeline (Thread Pinning & Concurrency Guard)

```text
CameraX ImageAnalysis (YUV_420_888)  
        │  
        ▼ (Target: 15 FPS throttle + AtomicBoolean in-flight lock)  
[ LiveVisionAnalyzer Dispatcher ] (Pinned background executor)  
  ├── Pass 1: ML Kit BarcodeScanner (UPC-A, EAN-13, QR)  
  │     └── If detected -> Query offline OpenFoodFacts SQLite DB (Latency: <10ms)  
  │  
  └── Pass 2: ML Kit ObjectDetector (Prominent objects)  
        └── If tracking confidence > 0.70 -> Emit BoundingBoxModel to UI  
        │  
        ▼ (finally { imageProxy.close(); isAnalyzing.set(false) })  
[ CameraOverlayHUD Compose Canvas ] (Rendered at display refresh rate 60/120Hz)  
```

### 4. Privacy Invariants & Ephemeral Memory Management

* **Zero Gallery Storage:** Photos captured via `ImageCapture` are written to temporary private file handles in `context.cacheDir/vision_scratch/`.
* **Immediate Shredding:** Once vision parsing completes (or if the user cancels), the temporary image file is immediately deleted using secure overwriting before garbage collection.
* **Local-Only Barcode Resolution:** Barcode lookups are resolved against an embedded offline SQLite table. No external HTTP requests are issued without explicit user permission.

### 5. Acceptance & Verification Gates

* **Live Analysis Frame Time:** Live frame analysis processing time must remain under 40ms per frame to maintain smooth 15 FPS analysis without lagging camera preview.
* **Memory Footprint:** Heap allocation during high-resolution snapshot capture and vision model dispatch must not exceed 64 MB above baseline.
* **Barcode Hit Rate:** Sub-100ms detection and auto-population for common packaged items in the offline food facts test suite.
* **Zero Orphan Files:** Automated integration test verifying that `context.cacheDir/vision_scratch/` contains 0 files after 50 consecutive capture sessions.
