package org.rsmod.content.other.bots.meister.world

import com.github.michaelbull.logging.InlineLogger
import org.rsmod.content.other.bots.meister.MeisterKnobs
import org.rsmod.content.other.bots.meister.MeisterSettings
import org.rsmod.content.other.bots.meister.MeisterTrader
import org.rsmod.content.other.bots.meister.formatCoins

/**
 * `::meister` - Master Meister's admin commands. The caller checks administrator rights; every
 * change goes through [MeisterTrader.updateSettings], which validates it, and is saved at once.
 */
class MeisterCommands(private val meister: MasterMeister) {
    private val adminLog = InlineLogger("bots.admin")

    fun run(admin: String, args: List<String>, out: (String) -> Unit) {
        val trader = meister.trader
        if (trader == null) {
            out("<col=ff0000>Master Meister is not running (disabled in his config, or his ledger could not be read).")
            return
        }
        when (args.firstOrNull()) {
            null, "status" -> statusLines(trader).forEach(out)
            "offers" -> offerLines(trader).forEach(out)
            "trace" -> trader.trace.takeLast(10).forEach { out("${ago(it.time)} ${it.text}") }
            "items" -> itemLines(trader, args.getOrNull(1)?.toIntOrNull() ?: 10).forEach(out)
            "keys" -> {
                out("Numbers: " + MeisterKnobs.ALL.joinToString(", ") { it.key })
                out("Switches (on/off): " + MeisterKnobs.SWITCHES.joinToString(", ") { it.key })
            }
            "set" -> {
                val key = args.getOrNull(1)
                val value = args.getOrNull(2)
                if (key == null || value == null) return out(USAGE)
                out(set(admin, trader, key, value))
            }
            "trading", "buying", "selling" -> {
                val value = args.getOrNull(1) ?: return out(USAGE)
                out(set(admin, trader, args[0], value))
            }
            "cancel" -> {
                val count = trader.cancelAll("cancelled by $admin")
                meister.saveNow()
                adminLog.info { "$admin cancelled $count of Master Meister's offers" }
                out("Cancelled $count offer(s); their contents are collected over the next ticks.")
            }
            "home" -> out(if (meister.sendHome()) "Master Meister is back at his spot." else "He is not in the world.")
            "reset" -> {
                val keep = trader.settings
                val error = trader.updateSettings { MeisterSettings(tradingEnabled = keep.tradingEnabled) }
                meister.saveNow()
                adminLog.info { "$admin reset Master Meister's settings" }
                out(error ?: "Settings reset to the defaults (trading stays ${if (keep.tradingEnabled) "on" else "off"}).")
            }
            else -> out(USAGE)
        }
    }

    fun audit(admin: String, what: String) {
        adminLog.info { "$admin $what" }
    }

    /** Applies a settings change from the Bot Manager window; null when it was saved. */
    fun change(admin: String, trader: MeisterTrader, what: String, change: (MeisterSettings) -> MeisterSettings): String? {
        val error = trader.updateSettings(change)
        if (error == null) {
            meister.saveNow()
            adminLog.info { "$admin changed Master Meister's $what: ${trader.settings}" }
        }
        return error
    }

    fun set(admin: String, trader: MeisterTrader, key: String, value: String): String {
        val switch = MeisterKnobs.switch(key)
        val knob = MeisterKnobs.knob(key)
        val error =
            when {
                switch != null -> {
                    val on = when (value) {
                        "on", "true", "yes" -> true
                        "off", "false", "no" -> false
                        else -> return "<col=ff0000>$key takes on or off."
                    }
                    trader.updateSettings { switch.set(it, on) }
                }
                knob != null -> {
                    val number = parseAmount(value) ?: return "<col=ff0000>$value is not a number."
                    trader.updateSettings { knob.set(it, number) }
                }
                else -> return "<col=ff0000>Unknown setting '$key'. ::meister keys lists them."
            }
        if (error != null) {
            return "<col=ff0000>Not changed: $error"
        }
        meister.saveNow()
        adminLog.info { "$admin set Master Meister's $key to $value" }
        return "Master Meister's $key is now $value."
    }

    companion object {
        const val USAGE: String =
            "::meister [status|offers|trace|items [n]|keys|set <key> <value>|trading|buying|selling on/off|cancel|home|reset]"

        fun statusLines(trader: MeisterTrader): List<String> {
            val s = trader.settings
            val stats = trader.stats
            val open = trader.offers().count { it.isOpen }
            return listOf(
                "Master Meister: ${trader.status.label}. Cash ${formatCoins(trader.cash)}, ${trader.holdingsView().size} item kinds.",
                "Trading ${onOff(s.tradingEnabled)}, buying ${onOff(!s.buyingPaused)}, selling ${onOff(!s.sellingPaused)}. Offers $open/${s.maxActiveOffers}.",
                "Spent today ${formatCoins(trader.spentTodayCoins)} of ${formatCoins(s.maxSpendPerDay)}; ${formatCoins(trader.committedCoins())} tied up in buy offers.",
                "Lifetime: bought ${stats.itemsBought} items for ${formatCoins(stats.coinsSpent)}, sold ${stats.itemsSold} for ${formatCoins(stats.coinsEarned)} (tax ${formatCoins(stats.taxPaid)}); ${stats.offersPlaced} offers, ${stats.offersCancelled} cancelled.",
            )
        }

        fun offerLines(trader: MeisterTrader): List<String> {
            val offers = trader.offers()
            if (offers.isEmpty()) {
                return listOf("Master Meister has no exchange offers.")
            }
            return offers.map { offer ->
                val side = if (offer.sell) "Sell" else "Buy"
                val state = if (offer.finished) "done" else "${ago(offer.createdAt)} old"
                "Slot ${offer.slot}: $side ${offer.completed}/${offer.total} x ${trader.itemName(offer.itemId)} at ${offer.price} ($state" +
                    (if (offer.hasCollectable) ", to collect ${offer.collectItems} items ${offer.collectCoins} coins" else "") + ")"
            }
        }

        fun itemLines(trader: MeisterTrader, count: Int): List<String> {
            val top = trader.holdingsView().entries.sortedByDescending { it.value.toLong() * trader.guideOf(it.key) }.take(count.coerceIn(1, 50))
            return listOf("Most valuable holdings (of ${trader.holdingsView().size} kinds):") +
                top.map { "${it.value} x ${trader.itemName(it.key)} (${formatCoins(it.value.toLong() * trader.guideOf(it.key))})" }
        }

        fun parseAmount(text: String): Long? {
            val lower = text.lowercase().replace(",", "")
            val multiplier =
                when (lower.lastOrNull()) {
                    'k' -> 1_000L
                    'm' -> 1_000_000L
                    'b' -> 1_000_000_000L
                    else -> 1L
                }
            val digits = if (multiplier == 1L) lower else lower.dropLast(1)
            return digits.toLongOrNull()?.let { runCatching { Math.multiplyExact(it, multiplier) }.getOrNull() }
        }

        private fun onOff(on: Boolean): String = if (on) "<col=00ff00>on</col>" else "<col=ff0000>off</col>"

        fun ago(time: Long): String {
            val minutes = (System.currentTimeMillis() - time) / 60_000
            return if (minutes < 60) "${minutes}m" else "${minutes / 60}h${minutes % 60}m"
        }
    }
}
