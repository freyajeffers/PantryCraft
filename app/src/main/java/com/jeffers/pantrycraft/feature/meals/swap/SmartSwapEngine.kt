package com.jeffers.pantrycraft.feature.meals.swap

import com.jeffers.pantrycraft.data.PantryItem

data class SmartSwapSuggestion(
    val missingItem: String,
    val substituteName: String,
    val ratioExplanation: String,
    val requiredStockItems: List<String>,
    val isFullyAvailableOnHand: Boolean,
)

data class SwapRule(
    val targetKeyword: String,
    val substituteName: String,
    val ratioExplanation: String,
    val requiredKeywords: List<String>,
)

object SmartSwapEngine {

    private val swapCatalog = listOf(
        SwapRule(
            targetKeyword = "buttermilk",
            substituteName = "Milk + Acid (Lemon/Vinegar)",
            ratioExplanation = "1 cup milk + 1 tbsp lemon juice or vinegar (let sit 5 mins)",
            requiredKeywords = listOf("milk"),
        ),
        SwapRule(
            targetKeyword = "sour cream",
            substituteName = "Greek Yogurt",
            ratioExplanation = "1:1 substitution with equal texture and tartness",
            requiredKeywords = listOf("yogurt"),
        ),
        SwapRule(
            targetKeyword = "heavy cream",
            substituteName = "Milk + Butter",
            ratioExplanation = "3/4 cup milk + 1/4 cup melted butter",
            requiredKeywords = listOf("milk", "butter"),
        ),
        SwapRule(
            targetKeyword = "egg",
            substituteName = "Applesauce or Yogurt Binder",
            ratioExplanation = "1/4 cup unsweetened applesauce or yogurt per egg for baking",
            requiredKeywords = listOf("applesauce", "yogurt"),
        ),
        SwapRule(
            targetKeyword = "tomato sauce",
            substituteName = "Tomato Paste + Water",
            ratioExplanation = "1 part tomato paste mixed with 1 part water",
            requiredKeywords = listOf("paste"),
        ),
        SwapRule(
            targetKeyword = "breadcrumbs",
            substituteName = "Crushed Crackers or Oats",
            ratioExplanation = "1:1 swap using crushed crackers, panko, or rolled oats",
            requiredKeywords = listOf("oats", "cracker"),
        ),
        SwapRule(
            targetKeyword = "cornstarch",
            substituteName = "All-Purpose Flour (2x)",
            ratioExplanation = "Use 2 tbsp all-purpose flour for every 1 tbsp cornstarch",
            requiredKeywords = listOf("flour"),
        ),
        SwapRule(
            targetKeyword = "brown sugar",
            substituteName = "White Sugar + Molasses or Honey",
            ratioExplanation = "1 cup white sugar + 1 tbsp molasses or honey",
            requiredKeywords = listOf("sugar"),
        ),
        SwapRule(
            targetKeyword = "mayonnaise",
            substituteName = "Greek Yogurt or Avocado",
            ratioExplanation = "1:1 swap with plain Greek yogurt or mashed ripe avocado",
            requiredKeywords = listOf("yogurt", "avocado"),
        ),
        SwapRule(
            targetKeyword = "ricotta",
            substituteName = "Cottage Cheese",
            ratioExplanation = "1:1 swap using drained, blended cottage cheese",
            requiredKeywords = listOf("cottage"),
        ),
    )

    fun findSwapsForMissingItem(
        missingItem: String,
        onHandItems: List<PantryItem>,
    ): List<SmartSwapSuggestion> {
        val lowerMissing = missingItem.trim().lowercase()
        val onHandNames = onHandItems
            .filter { !it.isDeleted && it.quantity > 0f }
            .map { it.name.lowercase() }

        val matches = swapCatalog.filter { rule ->
            lowerMissing.contains(rule.targetKeyword)
        }

        return matches.map { rule ->
            val hasStock = rule.requiredKeywords.any { req ->
                onHandNames.any { stockName -> stockName.contains(req) }
            }
            SmartSwapSuggestion(
                missingItem = missingItem,
                substituteName = rule.substituteName,
                ratioExplanation = rule.ratioExplanation,
                requiredStockItems = rule.requiredKeywords,
                isFullyAvailableOnHand = hasStock,
            )
        }
    }

    fun enrichMissingItemsWithSwaps(
        missingItems: List<String>,
        onHandItems: List<PantryItem>,
    ): Map<String, List<SmartSwapSuggestion>> =
        missingItems.associateWith { item ->
            findSwapsForMissingItem(item, onHandItems)
        }
}
