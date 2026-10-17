---
name: litert-aicore-mobile-ai
description: Deploy and route between on-device SLMs (Gemini Nano, LiteRT-LM) and cloud BYOKey streaming endpoints with strict schema constraints and local tool-calling capabilities.
---

# LiteRT & AICore Mobile AI Router

Architecture for deploying local on-device SLMs, routing between local inference and BYOKey cloud providers, and executing local ReAct tool calls.

## When to Use
- Running 100% offline meal synthesis and entity extraction on Android.
- Abstracting between on-device models (Gemini Nano via AICore, LiteRT-LM Gemma 2) and cloud BYOKey APIs (OpenAI, Claude, Gemini, Ollama).
- Executing local tool-calling loops (querying inventory, checking allergies, simulating stock deltas).
- Enforcing device hardware feasibility checks to avoid out-of-memory crashes.

## Core Architectural Invariants
1. **Zero-Download AICore vs. On-Demand LiteRT-LM:** Zero-download, zero-network on-device SLM inference utilizes system-provided Android AICore (Gemini Nano) on supported hardware. For devices running LiteRT-LM (Gemma 2 2B / Qwen 2.5 3B, ~1.5GB), weights are downloaded on-demand through an explicit user-initiated flow via Play Feature Delivery to keep the base APK < 200MB.
2. **Local Function Calling Loop Support:** Expose local Room query tools (`search_inventory`, `simulate_depletion`) using structured JSON function definitions. Allow the model to execute multi-turn verification before returning the final recipe plan.
3. **Hardware RAM Pre-Check:** Before loading a 2B–3B parameter quantized model into RAM, query `ActivityManager.getMemoryInfo()`. The device must have at least 6 GB total RAM and 1.5 GB available heap; if insufficient, gracefully degrade to cloud BYOKey or rule-based heuristics.
4. **Context Window Compression:** On-device models have constrained context windows (2k–4k tokens). The agent must serialize the inventory database into a compact format (stripping IDs, internal timestamps, and formatting whitespace) prioritizing items nearing expiration.
5. **Grammar/JSON Output Enforcement:** Enforce JSON schema constraints using BNF grammars or strict regex decoders to prevent conversational filler from breaking recipe parsing.

## Model Router Interface with Tool-Calling
```kotlin
interface ModelEngine {
    suspend fun generateRecipeStream(
        inventoryContext: String,
        mode: RecipeMode,
        portions: Int
    ): kotlinx.coroutines.flow.Flow<String>

    suspend fun generateWithTools(
        systemPrompt: String,
        userPrompt: String,
        tools: List<CulinaryTool>
    ): ModelTurnResponse
}
```
