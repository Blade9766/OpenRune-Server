package org.rsmod.content.other.bots.core

/** A change to a bot's config; null when the setting is already at its limit. */
typealias ConfigChange = ((BotConfig) -> BotConfig)?

sealed interface SettingControl {
    data class Stepper(val down: ConfigChange, val up: ConfigChange) : SettingControl

    data class Toggle(val label: String, val change: ConfigChange) : SettingControl
}

/**
 * One line of the Bot Settings window. [unavailable] says why the setting cannot be changed for
 * this bot, in which case its controls are drawn greyed out and refuse clicks.
 */
data class SettingRow(
    val label: String,
    val value: String,
    val help: String,
    val control: SettingControl,
    val unavailable: String? = null,
)

/** What the settings window needs to know about a bot's site. */
data class SiteCapabilities(val canSell: Boolean, val usesSupplies: Boolean)

object BotSettingRows {
    const val HOURLY_STEP: Int = 250
    const val THRESHOLD_STEP: Int = 100
    const val MAX_INTERVAL: Int = 10

    fun of(config: BotConfig, site: SiteCapabilities): List<SettingRow> =
        listOf(difficulty(config), reaction(config), speed(config), hourly(config)) +
            selling(config, site) + listOf(threshold(config, site), restock(config, site))

    private fun difficulty(config: BotConfig): SettingRow {
        val pace = BotPace.of(config)
        val next = BotPace.next(config)
        return SettingRow(
            label = "Difficulty",
            value = pace?.label ?: "Custom",
            help = (pace?.description ?: BotPace.CUSTOM_DESCRIPTION) + " Next: ${next.label}.",
            control = SettingControl.Toggle("Next", next::applyTo),
        )
    }

    private fun reaction(config: BotConfig): SettingRow {
        val min = config.reactionMinTicks
        val max = config.reactionMaxTicks
        val range = BotConfig.REACTION_RANGE
        return SettingRow(
            label = "Reaction",
            value = if (min == max) "$min ticks" else "$min-$max ticks",
            help = "Random pause before each new click; 1 tick is 0.6 seconds. Lower reacts faster.",
            control =
                SettingControl.Stepper(
                    down = if (min > range.first) { c -> c.copy(reactionMinTicks = min - 1, reactionMaxTicks = max - 1) } else null,
                    up = if (max < range.last) { c -> c.copy(reactionMinTicks = min + 1, reactionMaxTicks = max + 1) } else null,
                ),
        )
    }

    private fun speed(config: BotConfig): SettingRow {
        val interval = config.actionIntervalTicks
        return SettingRow(
            label = "Speed",
            value = if (interval == 1) "every tick" else "every $interval ticks",
            help = "How often it decides what to do. Higher numbers slow everything it does, walking too.",
            control =
                SettingControl.Stepper(
                    down = if (interval > 1) { c -> c.copy(actionIntervalTicks = interval - 1) } else null,
                    up = if (interval < MAX_INTERVAL) { c -> c.copy(actionIntervalTicks = interval + 1) } else null,
                ),
        )
    }

    private fun hourly(config: BotConfig): SettingRow {
        val limit = config.maxGatheredPerHour
        return SettingRow(
            label = "Hourly limit",
            value = if (limit == 0) "none" else "$limit items",
            help = "Most items it gathers in an hour before it waits. None means no limit.",
            control =
                SettingControl.Stepper(
                    down = if (limit > 0) { c -> c.copy(maxGatheredPerHour = maxOf(0, limit - HOURLY_STEP)) } else null,
                    up = { c -> c.copy(maxGatheredPerHour = limit + HOURLY_STEP) },
                ),
        )
    }

    private fun selling(config: BotConfig, site: SiteCapabilities): SettingRow {
        val enabled = config.sale.enabled
        return SettingRow(
            label = "Selling",
            value = if (enabled) "on" else "off",
            help = "Whether it takes what it gathers to the Grand Exchange to sell.",
            control =
                SettingControl.Toggle(
                    if (enabled) "Turn off" else "Turn on",
                    { c -> c.copy(sale = c.sale.copy(enabled = !enabled)) },
                ),
            unavailable = if (site.canSell) null else "Its site has no route to the Grand Exchange.",
        )
    }

    private fun threshold(config: BotConfig, site: SiteCapabilities): SettingRow {
        val threshold = config.sale.threshold
        val range = BotConfig.SALE_THRESHOLD_RANGE
        return SettingRow(
            label = "Sell at",
            value = "$threshold banked",
            help = "How many of its sale item it banks before taking a batch to the exchange.",
            control =
                SettingControl.Stepper(
                    down = if (threshold > range.first) { c -> c.withThreshold(maxOf(range.first, threshold - THRESHOLD_STEP)) } else null,
                    up = if (threshold < range.last) { c -> c.withThreshold(minOf(range.last, threshold + THRESHOLD_STEP)) } else null,
                ),
            unavailable =
                when {
                    !site.canSell -> "Its site has no route to the Grand Exchange."
                    !config.sale.enabled -> "Turn selling on first."
                    else -> null
                },
        )
    }

    private fun restock(config: BotConfig, site: SiteCapabilities): SettingRow {
        val enabled = config.restock.enabled
        return SettingRow(
            label = "Restock",
            value = if (enabled) "on" else "off",
            help = "Buy supplies (feathers, food) on the exchange when its bank runs out, selling some catch to pay.",
            control =
                SettingControl.Toggle(
                    if (enabled) "Turn off" else "Turn on",
                    { c -> c.copy(restock = c.restock.copy(enabled = !enabled)) },
                ),
            unavailable =
                when {
                    !site.usesSupplies -> "This bot's work uses no supplies."
                    !site.canSell -> "Its site has no route to the Grand Exchange."
                    else -> null
                },
        )
    }

    /** A sale batch can never be bigger than the threshold that triggers it. */
    private fun BotConfig.withThreshold(threshold: Int): BotConfig =
        copy(sale = sale.copy(threshold = threshold, quantity = minOf(sale.quantity, threshold)))
}
