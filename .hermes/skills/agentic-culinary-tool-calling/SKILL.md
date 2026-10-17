---
name: agentic-culinary-tool-calling
description: Implement local on-device ReAct function calling loops for Gemini Nano and LiteRT-LM, executing Room SQLite tools for constraint verification, inventory search, and meal drafting.
---

# Agentic Culinary Tool-Calling & ReAct Loops

Specialized engineering guidance for implementing local, multi-turn tool-calling loops on Android using on-device models (Android AICore Gemini Nano / LiteRT-LM) and BYOKey providers.

## When to Use
- Allowing the culinary reasoning engine to autonomously inspect stock, test constraints, and draft meal plans.
- Executing ReAct loops (*Reason -> Call Tool -> Observe -> Plan*) on mobile hardware without cloud roundtrips.
- Enforcing strict parameter validation and sandboxing on model-invoked Room SQLite queries.

## Core Architectural Invariants
1. **Local Tool Sandbox Boundaries:** Tools exposed to models (`search_inventory`, `simulate_depletion`, `add_grocery_draft`, `schedule_prep_timer`) must run as read-only queries or staged draft transactions. They must never directly mutate canonical inventory without user approval gates.
2. **Schema-Constrained Function Definitions:** Define tool signatures using JSON Schema / OpenAPI specs with strict parameter types (e.g. enum storage locations, non-negative quantities).
3. **Turn Ceiling & Token Budgeting:** On-device ReAct loops must enforce a hard execution ceiling of maximum 3 tool iterations per reasoning task to conserve battery and stay within the 2k–4k token context budget.
4. **Resilient JSON Tool Parsing:** Fall back to regex/BNF parameter extraction if a quantized model emits slight markdown formatting around the tool call JSON.

## Implementation Pattern (Kotlin / Android AICore & LiteRT)
```kotlin
sealed class CulinaryTool(val name: String, val description: String) {
    data class SearchInventory(val zone: String?, val category: String?, val maxExpiryDays: Int?) : CulinaryTool("search_inventory", "Query active stock by zone or category")
    data class SimulateDepletion(val recipeId: String, val portions: Int) : CulinaryTool("simulate_depletion", "Verify if recipe leaves invalid negative balances")
    data class AddGroceryDraft(val items: List<String>) : CulinaryTool("add_grocery_draft", "Queue missing bridge items into grocery checklist")
}

class AgenticReActCoordinator(
    private val modelEngine: ModelEngine,
    private val inventoryRepository: InventoryRepository
) {
    suspend fun executeReasoningLoop(goal: String): Flow<RecipePlan> = flow {
        var iterations = 0
        var currentContext = "User Goal: $goal\n"
        
        while (iterations < 3) {
            val response = modelEngine.generateTurn(currentContext)
            if (response.hasToolCall()) {
                val toolCall = response.extractToolCall()
                val toolResult = executeLocalTool(toolCall)
                currentContext += "Tool Call: ${toolCall.name}\nResult: $toolResult\n"
                iterations++
            } else {
                emit(response.parseFinalPlan())
                break
            }
        }
    }

    private suspend fun executeLocalTool(call: ToolCall): String {
        return when (call.name) {
            "search_inventory" -> inventoryRepository.queryStock(call.params).toCompactSemanticString()
            "simulate_depletion" -> inventoryRepository.simulateDeduction(call.params).toJson()
            else -> "Error: Unknown tool"
        }
    }
}
```
