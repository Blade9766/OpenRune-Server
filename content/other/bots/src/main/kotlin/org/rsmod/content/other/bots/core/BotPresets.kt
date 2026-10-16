package org.rsmod.content.other.bots.core

/**
 * How hard a bot plays: how long it hesitates before each click and how often it makes a
 * decision. A bot whose settings match no preset is "Custom".
 */
enum class BotPace(
    val label: String,
    val description: String,
    val reactionMinTicks: Int,
    val reactionMaxTicks: Int,
    val intervalTicks: Int,
) {
    Easy("Easy", "Relaxed: long pauses between clicks, like a casual player.", 4, 10, 2),
    Normal("Normal", "Human pace: a short random pause before each click.", 1, 4, 1),
    Hard("Hard", "Focused: reacts almost at once, like a player grinding hard.", 0, 2, 1);

    fun applyTo(config: BotConfig): BotConfig =
        config.copy(
            reactionMinTicks = reactionMinTicks,
            reactionMaxTicks = reactionMaxTicks,
            actionIntervalTicks = intervalTicks,
        )

    companion object {
        const val CUSTOM_DESCRIPTION: String = "Custom: reaction or speed changed by hand."

        fun of(config: BotConfig): BotPace? =
            entries.firstOrNull {
                it.reactionMinTicks == config.reactionMinTicks &&
                    it.reactionMaxTicks == config.reactionMaxTicks &&
                    it.intervalTicks == config.actionIntervalTicks
            }

        /** The preset after [config]'s one; Custom moves on to Easy. */
        fun next(config: BotConfig): BotPace = of(config)?.let { entries[(it.ordinal + 1) % entries.size] } ?: Easy
    }
}

/**
 * [config] with every behaviour and pricing setting back at its default. What the bot sells, and
 * where a runecrafter gets its essence, stay as they were: those follow from how it was set up.
 */
fun BotConfig.withDefaults(): BotConfig =
    BotConfig(
        role = role,
        site = site,
        sale = SaleConfig(enabled = sale.enabled, item = sale.item, allowedItems = sale.allowedItems),
        essenceSource = essenceSource,
    )

/** [other] with this config's behaviour settings; what [other] sells is left alone. */
fun BotConfig.copyBehaviourTo(other: BotConfig): BotConfig =
    other.copy(
        reactionMinTicks = reactionMinTicks,
        reactionMaxTicks = reactionMaxTicks,
        actionIntervalTicks = actionIntervalTicks,
        maxGatheredPerHour = maxGatheredPerHour,
        restock = restock,
    )
