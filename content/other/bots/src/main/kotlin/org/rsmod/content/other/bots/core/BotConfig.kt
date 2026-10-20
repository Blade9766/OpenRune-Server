package org.rsmod.content.other.bots.core

enum class BotState(val label: String, val shortLabel: String) {
    Disabled("DISABLED", "Disabled"),
    Idle("IDLE", "Idle"),
    TravelToResource("TRAVEL_TO_RESOURCE", "To work"),
    Gathering("GATHERING", "Working"),
    TravelToBank("TRAVEL_TO_BANK", "To bank"),
    Banking("BANKING", "Banking"),
    PrepareSale("PREPARE_SALE", "Selling"),
    TravelToExchange("TRAVEL_TO_EXCHANGE", "To exchange"),
    Listing("LISTING", "Listing"),
    BuyingSupplies("BUYING_SUPPLIES", "Buying"),
    WaitingForSupplies("WAITING_FOR_SUPPLIES", "No supplies"),
    Error("ERROR", "Error"),
}

enum class BotRoleType(val key: String) {
    Woodcutting("woodcutting"),
    Mining("mining"),
    Runecrafting("runecrafting"),
    Fishing("fishing"),
    Thieving("thieving"),
    Combat("combat"),
    Slayer("slayer"),
    Smithing("smithing"),
    Smelting("smelting");

    companion object {
        fun of(key: String): BotRoleType? = entries.firstOrNull { it.key.equals(key, true) }
    }
}

enum class EssenceSource {
    /** Withdraw essence an administrator has stocked in the bot's bank. */
    Bank,

    /** Travel to the rune essence mine and mine it. */
    Mine,
}

data class SaleConfig(
    val enabled: Boolean = true,
    val item: String = "",
    val threshold: Int = 1000,
    val quantity: Int = 1000,
    val allowedItems: Set<String> = emptySet(),
)

data class PricingConfig(
    val referencePrice: Int? = null,
    val adjustmentPercent: Double = -1.0,
    val minPrice: Int = 1,
    val maxDeviationPercent: Double = 50.0,
    val windowMinutes: Int = 360,
    val minTrades: Int = 3,
    val minVolume: Long = 100,
    val cooldownMinutes: Int = 30,
    val listingTimeoutMinutes: Int = 60,
    val maxRetries: Int = 3,
    val retryStepPercent: Double = 2.0,
)

/** Buying supplies on the exchange, funded by the bot's products, when its bank runs out. */
data class RestockConfig(val enabled: Boolean = true, val quantity: Int = 500)

data class BotConfig(
    val role: BotRoleType,
    val site: String,
    val sale: SaleConfig = SaleConfig(),
    val pricing: PricingConfig = PricingConfig(),
    val essenceSource: EssenceSource = EssenceSource.Bank,
    val restock: RestockConfig = RestockConfig(),
    val actionIntervalTicks: Int = 1,
    /** Gathered items allowed per rolling hour; 0 means no limit. */
    val maxGatheredPerHour: Int = 0,
    /** Ticks a bot hesitates, picked at random in this range, before its next click. */
    val reactionMinTicks: Int = 1,
    val reactionMaxTicks: Int = 4,
) {
    fun validate(): List<String> = buildList {
        if (site.isBlank()) {
            add("site must be set")
        }
        if (sale.threshold !in SALE_THRESHOLD_RANGE) {
            add("sale threshold must be between ${SALE_THRESHOLD_RANGE.first} and ${SALE_THRESHOLD_RANGE.last}")
        }
        if (sale.quantity !in 1..sale.threshold) {
            add("sale quantity must be between 1 and the sale threshold")
        }
        if (sale.enabled && sale.item.isNotBlank() && sale.item !in sale.allowedItems) {
            add("sale item ${sale.item} is not in the allowed items")
        }
        if (pricing.adjustmentPercent !in -50.0..50.0) {
            add("price adjustment must be between -50% and 50%")
        }
        if (pricing.minPrice < 1) {
            add("minimum price must be at least 1")
        }
        if (pricing.referencePrice != null && pricing.referencePrice < 1) {
            add("reference price must be at least 1")
        }
        if (pricing.maxDeviationPercent <= 0) {
            add("max deviation must be positive")
        }
        if (pricing.maxRetries !in 0..20) {
            add("max retries must be between 0 and 20")
        }
        if (pricing.retryStepPercent !in 0.0..25.0) {
            add("retry step must be between 0% and 25%")
        }
        if (pricing.cooldownMinutes < 1 || pricing.listingTimeoutMinutes < 1) {
            add("cooldown and listing timeout must be at least one minute")
        }
        if (actionIntervalTicks !in 1..100) {
            add("action interval must be between 1 and 100 ticks")
        }
        if (reactionMinTicks !in REACTION_RANGE || reactionMaxTicks !in reactionMinTicks..REACTION_RANGE.last) {
            add("reaction must be a range within ${REACTION_RANGE.first}-${REACTION_RANGE.last} ticks")
        }
        if (restock.quantity !in RESTOCK_QUANTITY_RANGE) {
            add("restock quantity must be between ${RESTOCK_QUANTITY_RANGE.first} and ${RESTOCK_QUANTITY_RANGE.last}")
        }
        if (maxGatheredPerHour < 0) {
            add("hourly gathering limit cannot be negative")
        }
    }

    companion object {
        val SALE_THRESHOLD_RANGE: IntRange = 1000..2000
        val REACTION_RANGE: IntRange = 0..20
        val RESTOCK_QUANTITY_RANGE: IntRange = 1..100_000
    }
}

data class BotGlobalSettings(val masterEnabled: Boolean = false, val maxActiveBots: Int = 10)
