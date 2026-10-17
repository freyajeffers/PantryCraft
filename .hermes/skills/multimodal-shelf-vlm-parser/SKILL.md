---
name: multimodal-shelf-vlm-parser
description: Decompose cluttered multi-item pantry/refrigerator shelf photos into structured inventory entities using VLMs, memory-pooled compression, and schema repair.
---

# Multimodal Shelf VLM Parser

Techniques for preparing, compressing, and extracting structured JSON inventory records from shelf photos using Vision LLMs.

## When to Use
- Processing high-resolution pantry or refrigerator shelf snapshots.
- Extracting multiple ingredients, quantities, packaging states, and storage locations in a single pass.
- Enforcing strict JSON schema output and repairing malformed model responses.

## Core Architectural Invariants
1. **In-Memory Image Downsampling:** Never send 48MP raw camera bitmaps to models. Downscale captured still frames to 1024x1024 or 1536x1536 WebP (quality 85) directly in volatile byte buffers. Peak bitmap memory allocation must remain under 32 MB.
2. **Zero Disk Residue:** Wipe in-memory byte buffers immediately following model inference. No photos may leak to `MediaStore` or public storage.
3. **Normalized Bounding Box Spatial Coordinates:** Prompt the model to return coordinates normalized from 0.0 to 1.0 (`[ymin, xmin, ymax, xmax]`), allowing Jetpack Compose to draw interactive bounding boxes over the photo during batch review.
4. **Resilient JSON Output Sanitizer:** Parses LLM responses through an automated repair filter that strips leading/trailing markdown backticks, trims unescaped control characters, and completes dangling closing brackets.

## JSON Extraction Prompt Contract
```text
You are an expert culinary vision parser. Identify all food ingredients in this image.
Return ONLY valid JSON matching this schema:
{
  "items": [
    {
      "name": "Item Name",
      "category": "PRODUCE|DAIRY|MEAT|PANTRY_STAPLE|FROZEN|OTHER",
      "storage_location": "PANTRY|FRIDGE|FREEZER|COUNTER",
      "quantity": 1.0,
      "unit": "COUNT|GRAMS|POUNDS|OUNCES",
      "is_aromatic_or_seasoning": false,
      "bounding_box": [ymin, xmin, ymax, xmax],
      "estimated_shelf_life_days": 7
    }
  ]
}
```
