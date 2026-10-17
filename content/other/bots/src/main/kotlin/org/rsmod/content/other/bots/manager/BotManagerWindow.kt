package org.rsmod.content.other.bots.manager

import dev.openrune.definition.type.widget.IfEvent
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.WeakHashMap
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.content.other.bots.BotManager
import org.rsmod.content.other.bots.core.BotCoordinator
import org.rsmod.content.other.bots.core.BotPace
import org.rsmod.content.other.bots.core.BotState
import org.rsmod.content.other.bots.core.BotStatusView
import org.rsmod.content.other.bots.manager.Painter.ButtonState
import org.rsmod.content.other.bots.meister.BuyPriceMode
import org.rsmod.content.other.bots.meister.BuyRule
import org.rsmod.content.other.bots.meister.HistoryRange
import org.rsmod.content.other.bots.meister.HistorySide
import org.rsmod.content.other.bots.meister.HistorySort
import org.rsmod.content.other.bots.meister.MeisterFill
import org.rsmod.content.other.bots.meister.MeisterHistoryView
import org.rsmod.content.other.bots.meister.MeisterKnob
import org.rsmod.content.other.bots.meister.MeisterKnobs
import org.rsmod.content.other.bots.meister.MeisterRules
import org.rsmod.content.other.bots.meister.MeisterSettings
import org.rsmod.content.other.bots.meister.MeisterStatus
import org.rsmod.content.other.bots.meister.MeisterSwitch
import org.rsmod.content.other.bots.meister.MeisterTrader
import org.rsmod.content.other.bots.meister.SellPriceMode
import org.rsmod.content.other.bots.meister.SellRule
import org.rsmod.content.other.bots.meister.world.MasterMeister
import org.rsmod.content.other.bots.meister.world.MeisterCommands
import org.rsmod.game.entity.Player

/** The per-bot windows the Bot Manager opens; implemented by the bots script. */
interface BotWindows {
    fun openBotSettings(access: ProtectedAccess, botId: Long)

    fun openBotItems(access: ProtectedAccess, botId: Long)

    fun openMeisterItems(access: ProtectedAccess)

    suspend fun createBot(access: ProtectedAccess)

    fun sendHome(botId: Long): String
}

/**
 * The Bot Manager: Overview, Bot Roster, Master Meister (buy rules, sell rules, schedule, active
 * offers, trade history) and Settings in one window. Each administrator has a [ManagerState] that
 * keeps section, filters, pages and selections across refreshes and reopening. Edits to Master
 * Meister's rules and settings collect in a draft until Save Changes; switches marked as
 * applying at once (trading, buying, selling, bot master toggle) bypass the draft. Every change
 * goes through the trader or the bot coordinator, which validate it, and every handler re-checks
 * administrator rights.
 */
@Singleton
class BotManagerWindow
@Inject
constructor(
    private val bots: BotManager,
    private val meister: MasterMeister,
) {
    var windows: BotWindows? = null
    private val meisterCommands = MeisterCommands(meister)
    private val states = WeakHashMap<Player, ManagerState>()
    private val painters = WeakHashMap<Player, Painter>()
    private val open = WeakHashMap<Player, Boolean>()

    fun isOpen(player: Player): Boolean = open[player] == true

    fun closed(player: Player) {
        open.remove(player)
        painters.remove(player)
    }

    private fun state(player: Player): ManagerState = states.getOrPut(player) { ManagerState() }

    private fun painter(player: Player): Painter = painters.getOrPut(player) { Painter(player) }

    fun isAdmin(access: ProtectedAccess): Boolean {
        if (access.player.modLevel.isAtLeast(Rights.ADMINISTRATOR)) return true
        access.ifClose()
        return false
    }

    /* Opening and navigation */

    fun open(access: ProtectedAccess, section: Section? = null) {
        val player = access.player
        val state = state(player)
        section?.let { state.section = it }
        access.ifOpenMainModal(Painter.INTERFACE)
        open[player] = true
        painter(player).reset()
        for ((name, events) in CLICKABLE) {
            access.ifSetEvents(Painter.com(name), -1..-1, *events)
        }
        render(access)
    }

    fun navigate(access: ProtectedAccess, section: Section) {
        state(access.player).section = section
        state(access.player).message = null
        render(access)
    }

    fun refresh(access: ProtectedAccess) {
        if (isOpen(access.player)) render(access)
    }

    fun render(access: ProtectedAccess) {
        val player = access.player
        val state = state(player)
        val paint = painter(player)
        for (i in 0 until 9) {
            paint.hide("nav${i}_bg", i != state.section.nav)
        }
        paint.npcHead("nav2_icon", PORTRAIT_NPC)
        paint.hide("mh", !state.section.meister)
        for ((section, layer) in SECTION_LAYERS) {
            paint.hide(layer, section != state.section)
        }
        paint.hide("kn", state.section != Section.Schedule && state.section != Section.Settings)
        paint.hide("ru", state.section != Section.BuyRules && state.section != Section.SellRules)
        if (state.section.meister) renderHeader(paint, state)
        when (state.section) {
            Section.Overview -> renderOverview(paint)
            Section.Roster -> renderRoster(paint, state)
            Section.BuyRules, Section.SellRules -> renderRules(paint, state)
            Section.Schedule, Section.Settings -> renderKnobs(paint, state)
            Section.Offers -> renderOffers(paint, state)
            Section.History -> renderHistory(paint, state)
        }
        renderFooter(paint, state)
    }

    /* Master Meister header */

    private fun renderHeader(paint: Painter, state: ManagerState) {
        paint.npcHead("mh_portrait", PORTRAIT_NPC)
        for ((i, tab) in Section.TABS.withIndex()) {
            paint.button("mh_tab$i", TAB_LABELS[i], if (tab == state.section) ButtonState.Selected else ButtonState.Normal)
        }
        val trader = meister.trader
        if (trader == null) {
            paint.dot("mh_dot", RED)
            paint.text("mh_status", "<col=ff0000>Not running")
            listOf("Cash", "Offers", "Realized P/L").forEachIndexed { i, label ->
                paint.text("mh_card${i}_label", label)
                paint.text("mh_card${i}_value", "-")
            }
            return
        }
        val settings = trader.settings
        val (colour, label) = tradingState(trader)
        paint.dot("mh_dot", colour)
        paint.text("mh_status", "$label<br><col=c8b48c>${if (settings.useRules) "Rule lists" else "Whole market"}</col>")
        paint.text("mh_card0_label", "Cash")
        paint.text("mh_card0_value", "${trader.cash.formatAmount} gp")
        paint.text("mh_card1_label", "Offers")
        paint.text("mh_card1_value", "${trader.offers().count { it.isOpen }} / ${settings.maxActiveOffers}")
        paint.text("mh_card2_label", "Realized P/L")
        paint.text("mh_card2_value", signed(trader.realizedProfit()) + " gp")
    }

    private fun tradingState(trader: MeisterTrader): Pair<Int, String> {
        val s = trader.settings
        return when {
            trader.status == MeisterStatus.NotSetUp -> RED to "Not set up"
            !s.tradingEnabled -> RED to "Trading Paused"
            s.buyingPaused && s.sellingPaused -> AMBER to "Buying and selling paused"
            s.buyingPaused -> AMBER to "Buying paused"
            s.sellingPaused -> AMBER to "Selling paused"
            else -> GREEN to "Trading Active"
        }
    }

    /* Draft handling */

    private fun draftOf(state: ManagerState, trader: MeisterTrader): Draft =
        state.draft ?: Draft(trader.rules, trader.settings).also { state.draft = it }

    private fun rulesOf(state: ManagerState, trader: MeisterTrader): MeisterRules = state.draft?.rules ?: trader.rules

    private fun settingsOf(state: ManagerState, trader: MeisterTrader): MeisterSettings = state.draft?.settings ?: trader.settings

    /** The draft's settings with the switches that apply at once taken from the live trader. */
    private fun merged(draft: MeisterSettings, live: MeisterSettings): MeisterSettings =
        draft.copy(tradingEnabled = live.tradingEnabled, buyingPaused = live.buyingPaused, sellingPaused = live.sellingPaused)

    private fun unsaved(state: ManagerState, trader: MeisterTrader?): Boolean {
        val draft = state.draft ?: return false
        trader ?: return false
        return draft.rules != trader.rules || merged(draft.settings, trader.settings) != trader.settings
    }

    fun save(access: ProtectedAccess) {
        val state = state(access.player)
        val trader = meister.trader ?: return
        val draft = state.draft ?: return message(access, "Nothing to save.")
        val admin = access.player.displayName
        val ruleError = if (draft.rules != trader.rules) trader.updateRules(draft.rules) else null
        if (ruleError != null) return message(access, "<col=ff0000>Not saved: $ruleError")
        val settingsError =
            if (merged(draft.settings, trader.settings) != trader.settings) {
                meisterCommands.change(admin, trader, "settings and schedule") { merged(draft.settings, it) }
            } else {
                null
            }
        if (settingsError != null) return message(access, "<col=ff0000>Rules saved, settings not: $settingsError")
        meister.saveNow()
        meisterCommands.audit(admin, "saved Master Meister's rules (${draft.rules.buy.size} buy, ${draft.rules.sell.size} sell) and settings")
        state.draft = null
        message(access, "<col=00ff00>Saved.</col> New offers follow the saved rules; open offers keep their terms.")
    }

    fun discard(access: ProtectedAccess) {
        val state = state(access.player)
        state.draft = null
        message(access, "Changes discarded.")
    }

    private fun message(access: ProtectedAccess, text: String) {
        state(access.player).message = text
        render(access)
    }

    /* Rules */

    private fun isBuy(state: ManagerState): Boolean = state.section == Section.BuyRules

    private fun shownRuleIds(state: ManagerState, trader: MeisterTrader): List<Int> {
        val rules = rulesOf(state, trader)
        val search = (if (isBuy(state)) state.buySearch else state.sellSearch).lowercase()
        val ids = if (isBuy(state)) rules.buy.map { it.itemId } else rules.sell.map { it.itemId }
        return ids.filter { search.isEmpty() || trader.itemName(it).lowercase().contains(search) }
    }

    private fun renderRules(paint: Painter, state: ManagerState) {
        val trader = meister.trader
        val buy = isBuy(state)
        val side = if (buy) "Buy" else "Sell"
        if (trader == null) {
            paint.text("ru_title", "$side Rules")
            for (r in 0 until RULE_ROWS) paint.hide("ru_r$r", true)
            paint.hide("ed", true)
            paint.text("ru_page", "Master Meister is not running.")
            return
        }
        val rules = rulesOf(state, trader)
        val ids = shownRuleIds(state, trader)
        val total = if (buy) rules.buy.size else rules.sell.size
        val search = if (buy) state.buySearch else state.sellSearch
        paint.text("ru_title", "$side Rules")
        paint.button("ru_search", if (search.isEmpty()) "Search items..." else "Search: $search")
        paint.button("ru_add", "+ Add item")
        paint.button("ru_bulk", "Bulk...", if (ids.isEmpty()) ButtonState.Disabled else ButtonState.Normal)
        val headers =
            if (buy) listOf("Item", "Max price", "Qty", "Max hold", "Cooldown", "On") else listOf("Item", "Min price", "Qty", "Keep", "Cooldown", "On")
        headers.forEachIndexed { i, h -> paint.text("ru_h$i", h) }
        val pages = pages(ids.size, RULE_ROWS)
        val page = clampPage(if (buy) state.buyPage else state.sellPage, pages).also { if (buy) state.buyPage = it else state.sellPage = it }
        val selected = if (buy) state.buySelected else state.sellSelected
        val shown = ids.drop(page * RULE_ROWS).take(RULE_ROWS)
        for (r in 0 until RULE_ROWS) {
            val row = "ru_r$r"
            val id = shown.getOrNull(r)
            paint.hide(row, id == null)
            if (id == null) continue
            paint.hide("${row}_bg", id != selected)
            paint.icon("${row}_icon", id)
            val name = trader.itemName(id)
            if (buy) {
                val rule = rules.buyRule(id)!!
                val on = rule.enabled
                paint.text("${row}_c0", if (on) name else "<col=9f9f9f>$name (off)</col>")
                paint.text("${row}_c1", "${rule.maxPrice.formatAmount} gp")
                paint.text("${row}_c2", rule.quantity.formatAmount)
                paint.text("${row}_c3", if (rule.maxHoldings == Int.MAX_VALUE) "No limit" else rule.maxHoldings.formatAmount)
                paint.text("${row}_c4", "${rule.cooldownMinutes} min")
                paint.check("${row}_check", on, name)
            } else {
                val rule = rules.sellRule(id)!!
                val on = rule.enabled
                paint.text("${row}_c0", if (on) name else "<col=9f9f9f>$name (off)</col>")
                paint.text("${row}_c1", "${rule.minPrice.formatAmount} gp")
                paint.text("${row}_c2", rule.quantity.formatAmount)
                paint.text("${row}_c3", rule.keepAtLeast.formatAmount)
                paint.text("${row}_c4", "${rule.cooldownMinutes} min")
                paint.check("${row}_check", on, name)
            }
        }
        val first = if (ids.isEmpty()) 0 else page * RULE_ROWS + 1
        val filterNote = if (search.isNotEmpty()) " (of $total)" else ""
        paint.text(
            "ru_page",
            when {
                total == 0 -> "No ${side.lowercase()} rules yet. ${if (settingsOf(state, trader).useRules) "He ${side.lowercase()}s nothing." else "Add one with + Add item."}"
                ids.isEmpty() -> "No rules match '$search'."
                else -> "Rules $first-${first + shown.size - 1} of ${ids.size}$filterNote"
            },
        )
        paint.button("ru_prev", "Prev", if (page > 0) ButtonState.Normal else ButtonState.Disabled)
        paint.button("ru_next", "Next", if (page < pages - 1) ButtonState.Normal else ButtonState.Disabled)
        renderEditor(paint, state, trader, selected?.takeIf { it in (if (buy) rules.buy.map { r -> r.itemId } else rules.sell.map { r -> r.itemId }) })
    }

    private fun renderEditor(paint: Painter, state: ManagerState, trader: MeisterTrader, itemId: Int?) {
        val buy = isBuy(state)
        val rules = rulesOf(state, trader)
        if (itemId == null) {
            paint.hide("ed_icon", true)
            paint.text("ed_title", "Select a rule to edit it")
            paint.hide("ed_check", true)
            paint.hide("ed_check_text", true)
            paint.hide("ed_remove", true)
            for (i in 0 until 6) {
                paint.hide("ed_l$i", true)
                paint.hide("ed_f$i", true)
            }
            paint.text(
                "ed_note",
                if (buy) "Buy prices are ceilings per item: the exchange fills at sellers' prices." else "Sell prices are floors per item: buyers who bid more pay their own price.",
            )
            return
        }
        paint.icon("ed_icon", itemId)
        paint.text("ed_title", "Selected: ${trader.itemName(itemId)}")
        paint.hide("ed_check_text", false)
        paint.hide("ed_remove", false)
        paint.button("ed_remove", "Remove")
        val warnings = ArrayList<String>()
        val labels: List<Pair<String, String>>
        val note: String
        if (buy) {
            val rule = rules.buyRule(itemId)!!
            paint.check("ed_check", rule.enabled, "Enable buy rule")
            labels =
                listOf(
                    "Max price" to "${rule.maxPrice.formatAmount}",
                    "Offer qty" to rule.quantity.formatAmount,
                    "Limit" to if (rule.maxHoldings == Int.MAX_VALUE) "No limit" else rule.maxHoldings.formatAmount,
                    "Cooldown" to "${rule.cooldownMinutes} min",
                    "Day spend" to if (rule.dailySpend == 0L) "Global" else rule.dailySpend.formatAmount,
                    "Mode" to rule.mode.label,
                )
            val guide = trader.guideOf(itemId)
            val cost = rule.maxPrice.toLong() * rule.quantity
            note = "Ceiling per item - largest offer ${cost.formatAmount} gp - holding ${trader.holding(itemId).formatAmount}"
            if (guide > 0 && rule.maxPrice > guide * (100 + settingsOf(state, trader).maxBuyPremiumPercent) / 100) {
                warnings += "Over guide ${guide.formatAmount}"
            }
        } else {
            val rule = rules.sellRule(itemId)!!
            paint.check("ed_check", rule.enabled, "Enable sell rule")
            labels =
                listOf(
                    "Min price" to "${rule.minPrice.formatAmount}",
                    "Offer qty" to rule.quantity.formatAmount,
                    "Keep" to rule.keepAtLeast.formatAmount,
                    "Cooldown" to "${rule.cooldownMinutes} min",
                    "" to "",
                    "Mode" to rule.mode.label,
                )
            val spare = (trader.holding(itemId) - rule.keepAtLeast).coerceAtLeast(0)
            val proceeds = rule.minPrice.toLong() * minOf(rule.quantity, spare)
            note = "Floor per item - ${spare.formatAmount} above the reserve - offer at least ${proceeds.formatAmount} gp before fees"
            if (spare == 0) warnings += "Nothing above the reserve"
        }
        if (itemId in rules.lossLoops()) {
            warnings += "Loss loop: sell floor <= buy ceiling"
        }
        if (settingsOf(state, trader).useRules.not()) {
            warnings += "Not in use: rule mode is off (Schedule)"
        }
        labels.forEachIndexed { i, (label, value) ->
            val blank = label.isEmpty()
            paint.hide("ed_l$i", blank)
            paint.hide("ed_f$i", blank)
            if (!blank) {
                paint.text("ed_l$i", label)
                paint.text("ed_f${i}_text", value)
            }
        }
        paint.text("ed_note", if (warnings.isEmpty()) note else "<col=ff981f>${warnings.joinToString(" - ")}</col>")
    }

    fun selectRule(access: ProtectedAccess, row: Int, history: Boolean) {
        val state = state(access.player)
        val trader = meister.trader ?: return
        val ids = shownRuleIds(state, trader)
        val page = if (isBuy(state)) state.buyPage else state.sellPage
        val id = ids.getOrNull(page * RULE_ROWS + row) ?: return
        if (history) {
            state.query = state.query.copy(itemId = id, search = "")
            state.historyPage = 0
            state.historySelected = null
            return navigate(access, Section.History)
        }
        if (isBuy(state)) state.buySelected = id else state.sellSelected = id
        render(access)
    }

    fun toggleRule(access: ProtectedAccess, row: Int?) {
        val state = state(access.player)
        val trader = meister.trader ?: return
        val id =
            if (row == null) {
                (if (isBuy(state)) state.buySelected else state.sellSelected) ?: return
            } else {
                val page = if (isBuy(state)) state.buyPage else state.sellPage
                shownRuleIds(state, trader).getOrNull(page * RULE_ROWS + row) ?: return
            }
        editRules(access, trader) { rules ->
            if (isBuy(state)) {
                rules.copy(buy = rules.buy.map { if (it.itemId == id) it.copy(enabled = !it.enabled) else it })
            } else {
                rules.copy(sell = rules.sell.map { if (it.itemId == id) it.copy(enabled = !it.enabled) else it })
            }
        }
    }

    private fun editRules(access: ProtectedAccess, trader: MeisterTrader, change: (MeisterRules) -> MeisterRules): Boolean {
        val state = state(access.player)
        val draft = draftOf(state, trader)
        val changed = change(draft.rules)
        val errors = changed.validate { trader.catalogItem(it) != null }
        if (errors.isNotEmpty()) {
            message(access, "<col=ff0000>${errors.first()}")
            return false
        }
        state.draft = draft.copy(rules = changed)
        state.message = null
        render(access)
        return true
    }

    suspend fun searchRules(access: ProtectedAccess) {
        val state = state(access.player)
        val text = access.stringDialog("Show rules whose item name contains (blank shows all):").trim()
        if (isBuy(state)) {
            state.buySearch = text
            state.buyPage = 0
        } else {
            state.sellSearch = text
            state.sellPage = 0
        }
        open(access)
    }

    suspend fun addRule(access: ProtectedAccess) {
        val state = state(access.player)
        val trader = meister.trader ?: return
        val buy = isBuy(state)
        val text = access.stringDialog("Item to ${if (buy) "buy" else "sell"} (part of its name):").trim()
        if (text.isEmpty()) return open(access)
        val found = trader.searchCatalog(text, ITEM_PICKS)
        if (found.isEmpty()) {
            state.message = "<col=ff0000>No tradeable item matches '$text'."
            return open(access)
        }
        val picked = pick(access, "Add a ${if (buy) "buy" else "sell"} rule for", found.map { "${it.name} (guide ${it.guidePrice.formatAmount})" } + "Cancel")
        val item = found.getOrNull(picked) ?: return open(access)
        val rules = rulesOf(state, trader)
        val exists = if (buy) rules.buyRule(item.id) != null else rules.sellRule(item.id) != null
        if (!exists) {
            val qty = defaultQuantity(item.guidePrice)
            editRules(access, trader) {
                if (buy) {
                    it.copy(buy = it.buy + BuyRule(item.id, maxPrice = item.guidePrice.coerceAtLeast(1), quantity = qty, maxHoldings = (trader.holding(item.id).toLong() + qty * 10L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
                } else {
                    it.copy(sell = it.sell + SellRule(item.id, minPrice = item.guidePrice.coerceAtLeast(1), quantity = qty))
                }
            }
            state.message = "Added ${item.name}; set its prices below, then Save Changes."
        } else {
            state.message = "${item.name} already has a ${if (buy) "buy" else "sell"} rule; it is selected."
        }
        if (buy) {
            state.buySelected = item.id
            state.buySearch = ""
            state.buyPage = rulesOf(state, trader).buy.indexOfFirst { it.itemId == item.id } / RULE_ROWS
        } else {
            state.sellSelected = item.id
            state.sellSearch = ""
            state.sellPage = rulesOf(state, trader).sell.indexOfFirst { it.itemId == item.id } / RULE_ROWS
        }
        open(access)
    }

    private fun defaultQuantity(guide: Int): Int =
        when {
            guide < 1_000 -> 100
            guide < 100_000 -> 10
            else -> 1
        }

    suspend fun bulkRules(access: ProtectedAccess) {
        val state = state(access.player)
        val trader = meister.trader ?: return
        val ids = shownRuleIds(state, trader).toSet()
        val side = if (isBuy(state)) "buy" else "sell"
        val choices = listOf("Enable the ${ids.size} shown $side rules", "Disable the ${ids.size} shown $side rules", "Remove the ${ids.size} shown $side rules...", "Clear every $side rule...", "Cancel")
        val picked = pick(access, "Bulk change", choices)
        val buy = isBuy(state)
        fun change(transformBuy: (BuyRule) -> BuyRule?, transformSell: (SellRule) -> SellRule?): (MeisterRules) -> MeisterRules = { rules ->
            if (buy) rules.copy(buy = rules.buy.mapNotNull { if (it.itemId in ids) transformBuy(it) else it })
            else rules.copy(sell = rules.sell.mapNotNull { if (it.itemId in ids) transformSell(it) else it })
        }
        when (picked) {
            0 -> editRules(access, trader, change({ it.copy(enabled = true) }, { it.copy(enabled = true) }))
            1 -> editRules(access, trader, change({ it.copy(enabled = false) }, { it.copy(enabled = false) }))
            2 ->
                if (confirm(access, "Remove ${ids.size} $side rule(s) from the draft?", "Yes, remove them")) {
                    editRules(access, trader, change({ null }, { null }))
                }
            3 ->
                if (confirm(access, "Clear every $side rule from the draft?", "Yes, clear the list")) {
                    editRules(access, trader) { if (buy) it.copy(buy = emptyList()) else it.copy(sell = emptyList()) }
                }
        }
        open(access)
    }

    suspend fun removeRule(access: ProtectedAccess) {
        val state = state(access.player)
        val trader = meister.trader ?: return
        val id = (if (isBuy(state)) state.buySelected else state.sellSelected) ?: return
        editRules(access, trader) { rules ->
            if (isBuy(state)) rules.copy(buy = rules.buy.filterNot { it.itemId == id }) else rules.copy(sell = rules.sell.filterNot { it.itemId == id })
        }
        if (isBuy(state)) state.buySelected = null else state.sellSelected = null
        message(access, "Removed ${trader.itemName(id)} from the draft. Its open offer, if any, is untouched.")
    }

    suspend fun editField(access: ProtectedAccess, field: Int) {
        val state = state(access.player)
        val trader = meister.trader ?: return
        val buy = isBuy(state)
        val id = (if (buy) state.buySelected else state.sellSelected) ?: return
        if (field == 5) {
            if (buy) {
                val mode = BuyPriceMode.entries.getOrNull(pick(access, "Buy price mode", BuyPriceMode.entries.map { "${it.label}: ${it.help}" } + "Cancel")) ?: return open(access)
                editRules(access, trader) { r -> r.copy(buy = r.buy.map { if (it.itemId == id) it.copy(mode = mode) else it }) }
            } else {
                val mode = SellPriceMode.entries.getOrNull(pick(access, "Sell price mode", SellPriceMode.entries.map { "${it.label}: ${it.help}" } + "Cancel")) ?: return open(access)
                editRules(access, trader) { r -> r.copy(sell = r.sell.map { if (it.itemId == id) it.copy(mode = mode) else it }) }
            }
            return open(access)
        }
        val prompt =
            when (field) {
                0 -> if (buy) "Maximum buy price per item (gp):" else "Minimum sell price per item (gp):"
                1 -> "Items per offer:"
                2 -> if (buy) "Most he may hold (0 = no limit):" else "Always keep at least:"
                3 -> "Minutes to wait after an offer for it ends:"
                4 -> "Coins he may spend on it per day (0 = global limits only):"
                else -> return
            }
        val value = access.countDialog(prompt).toLong()
        if (value < 0) return open(access)
        val ok =
            editRules(access, trader) { rules ->
                if (buy) {
                    rules.copy(
                        buy =
                            rules.buy.map {
                                if (it.itemId != id) it
                                else
                                    when (field) {
                                        0 -> it.copy(maxPrice = value.toInt())
                                        1 -> it.copy(quantity = value.toInt())
                                        2 -> it.copy(maxHoldings = if (value == 0L) Int.MAX_VALUE else value.toInt())
                                        3 -> it.copy(cooldownMinutes = value.toInt())
                                        else -> it.copy(dailySpend = value)
                                    }
                            }
                    )
                } else {
                    rules.copy(
                        sell =
                            rules.sell.map {
                                if (it.itemId != id) it
                                else
                                    when (field) {
                                        0 -> it.copy(minPrice = value.toInt())
                                        1 -> it.copy(quantity = value.toInt())
                                        2 -> it.copy(keepAtLeast = value.toInt())
                                        else -> it.copy(cooldownMinutes = value.toInt())
                                    }
                            }
                    )
                }
            }
        if (ok) state.message = "Changed in the draft; Save Changes to apply."
        open(access)
    }

    fun rulePage(access: ProtectedAccess, delta: Int) {
        val state = state(access.player)
        if (isBuy(state)) state.buyPage = (state.buyPage + delta).coerceAtLeast(0) else state.sellPage = (state.sellPage + delta).coerceAtLeast(0)
        render(access)
    }

    /* Schedule and settings */

    private sealed interface Control {
        class Stepper(val down: (() -> String?)?, val up: (() -> String?)?) : Control

        class Toggle(val label: String, val action: suspend (ProtectedAccess) -> String?) : Control
    }

    private class KnobRow(val label: String, val value: String, val help: String, val control: Control, val info: String = "")

    private fun knobRows(state: ManagerState): List<KnobRow> {
        val trader = meister.trader
        val rows = ArrayList<KnobRow>()
        if (state.section == Section.Settings) {
            val coordinator = bots.coordinator
            if (coordinator != null) {
                val s = coordinator.settings
                rows += KnobRow("Bots master switch *", if (s.masterEnabled) "on" else "off", "Lets enabled bots into the world. Applies at once.", Control.Toggle(if (s.masterEnabled) "Turn off" else "Turn on") { _ ->
                    coordinator.setMaster(!s.masterEnabled)
                    null
                })
                rows += KnobRow("Bots in world *", s.maxActiveBots.toString(), "Most bots in the world at once. Applies at once.", Control.Stepper(
                    down = if (s.maxActiveBots > 0) { -> coordinator.setMaxActive(s.maxActiveBots - 1) } else null,
                    up = if (s.maxActiveBots < BotCoordinator.MAX_ACTIVE_LIMIT) { -> coordinator.setMaxActive(s.maxActiveBots + 1) } else null,
                ))
                rows += KnobRow("All bots' difficulty *", "${coordinator.all().size} bots", "Set every bot to Easy, Normal or Hard. Applies at once.", Control.Toggle("Set...") { a -> allDifficulty(a, coordinator) })
            }
        }
        if (trader == null) return rows
        val settings = settingsOf(state, trader)
        if (state.section == Section.Schedule) {
            val live = trader.settings
            rows += KnobRow("Trading *", if (live.tradingEnabled) "on" else "off", "Master switch for Master Meister. Off: no new offers; open offers stay until they fill, time out or you cancel them. Applies at once.", Control.Toggle(if (live.tradingEnabled) "Turn off" else "Turn on") { a ->
                meisterCommands.change(a.player.displayName, trader, "trading") { it.copy(tradingEnabled = !live.tradingEnabled) }
            })
            rows += KnobRow("Buying *", if (live.buyingPaused) "paused" else "active", "Pausing stops new buy offers only; cancel open ones under Active Offers. Applies at once.", Control.Toggle(if (live.buyingPaused) "Resume" else "Pause") { a ->
                meisterCommands.change(a.player.displayName, trader, "buying") { it.copy(buyingPaused = !live.buyingPaused) }
            })
            rows += KnobRow("Selling *", if (live.sellingPaused) "paused" else "active", "Pausing stops new sell offers only; cancel open ones under Active Offers. Applies at once.", Control.Toggle(if (live.sellingPaused) "Resume" else "Pause") { a ->
                meisterCommands.change(a.player.displayName, trader, "selling") { it.copy(sellingPaused = !live.sellingPaused) }
            })
            rows += switchRow(state, trader, MeisterKnobs.switch("rules")!!)
            for (key in SCHEDULE_KNOBS) {
                val knob = MeisterKnobs.knob(key)!!
                val info =
                    when (key) {
                        "interval" -> "next ${countdown(trader.nextBuyAt)}"
                        "sellinterval" -> "next ${countdown(trader.nextSellAt)}"
                        else -> ""
                    }
                rows += knobRow(state, trader, knob, settings, info)
            }
        } else {
            for (key in SETTINGS_SWITCHES) rows += switchRow(state, trader, MeisterKnobs.switch(key)!!)
            for (key in SETTINGS_KNOBS) rows += knobRow(state, trader, MeisterKnobs.knob(key)!!, settings, "")
        }
        return rows
    }

    private fun switchRow(state: ManagerState, trader: MeisterTrader, switch: MeisterSwitch): KnobRow {
        val on = switch.get(settingsOf(state, trader))
        return KnobRow(switch.label, if (on) "on" else "off", switch.help, Control.Toggle(if (on) "Turn off" else "Turn on") { _ -> editSettings(state, trader) { switch.set(it, !on) } })
    }

    private fun knobRow(state: ManagerState, trader: MeisterTrader, knob: MeisterKnob, settings: MeisterSettings, info: String): KnobRow {
        val value = knob.get(settings)
        return KnobRow(
            knob.label,
            knob.format(value),
            knob.help,
            Control.Stepper(
                down = if (value > knob.range.first) { -> editSettings(state, trader) { knob.set(it, maxOf(knob.range.first, value - knob.step)) } } else null,
                up = if (value < knob.range.last) { -> editSettings(state, trader) { knob.set(it, minOf(knob.range.last, value + knob.step)) } } else null,
            ),
            info,
        )
    }

    private fun editSettings(state: ManagerState, trader: MeisterTrader, change: (MeisterSettings) -> MeisterSettings): String? {
        val draft = draftOf(state, trader)
        val changed = change(draft.settings)
        val errors = changed.validate()
        if (errors.isNotEmpty()) return errors.first()
        state.draft = draft.copy(settings = changed)
        return null
    }

    private suspend fun allDifficulty(access: ProtectedAccess, coordinator: BotCoordinator): String? {
        val bots = coordinator.all()
        val pace = BotPace.entries.getOrNull(pick(access, "Difficulty for all ${bots.size} bots", BotPace.entries.map { "${it.label}: ${it.description}" } + "Cancel")) ?: return null
        val failed = bots.count { coordinator.updateConfig(it.id, pace::applyTo) != null }
        return if (failed > 0) "$failed bot(s) refused the change" else null
    }

    private fun knobRowsPerPage(section: Section): Int = if (section == Section.Schedule) SCHEDULE_ROWS else KNOB_ROWS

    private fun renderKnobs(paint: Painter, state: ManagerState) {
        val schedule = state.section == Section.Schedule
        val perPage = knobRowsPerPage(state.section)
        paint.position("kn", 0, if (schedule) SCHEDULE_OFFSET else 0)
        paint.position("kn_help", 4, KNOB_TOP + perPage * KNOB_PITCH)
        paint.hide("kn_title", schedule)
        paint.hide("kn_sub", schedule)
        paint.text("kn_title", if (schedule) "Trading Schedule" else "Settings")
        paint.text(
            "kn_sub",
            if (schedule) "Timing decides when he places offers; fills depend on the exchange's matching." else "Safeguards for whole-market trading, and the gathering bots' global switches.",
        )
        val rows = knobRows(state)
        val pages = pages(rows.size, perPage)
        val page = clampPage(state.knobPages[state.section] ?: 0, pages).also { state.knobPages[state.section] = it }
        val pagerY = KNOB_TOP + perPage * KNOB_PITCH + 1
        paint.position("kn_prev", 300, pagerY)
        paint.position("kn_next", 346, pagerY)
        paint.button("kn_prev", "Prev", if (page > 0) ButtonState.Normal else ButtonState.Disabled)
        paint.button("kn_next", "Next", if (page < pages - 1) ButtonState.Normal else ButtonState.Disabled)
        val shown = rows.drop(page * perPage).take(perPage)
        for (r in 0 until KNOB_ROWS) {
            val row = shown.getOrNull(r)
            val name = "kn_r$r"
            paint.hide(name, row == null)
            if (row == null) continue
            paint.text("${name}_label", row.label)
            paint.text("${name}_value", row.value)
            paint.text("${name}_info", row.info)
            when (val control = row.control) {
                is Control.Stepper -> {
                    paint.hide("${name}_toggle", true)
                    paint.button("${name}_dec", "-", if (control.down != null) ButtonState.Normal else ButtonState.Disabled)
                    paint.button("${name}_inc", "+", if (control.up != null) ButtonState.Normal else ButtonState.Disabled)
                }
                is Control.Toggle -> {
                    paint.hide("${name}_dec", true)
                    paint.hide("${name}_inc", true)
                    paint.button("${name}_toggle", control.label)
                }
            }
        }
        val star = "Settings marked * apply at once; the rest wait for Save Changes."
        paint.text("kn_help", state.help.ifEmpty { "Click a setting's name for what it does. $star" })
    }

    fun knobAction(access: ProtectedAccess, index: Int, action: String) {
        val state = state(access.player)
        val rows = knobRows(state)
        val page = state.knobPages[state.section] ?: 0
        val row = rows.getOrNull(page * knobRowsPerPage(state.section) + index) ?: return
        when (action) {
            "label" -> {
                state.help = "<col=ff981f>${row.label.removeSuffix(" *")}:</col> ${row.help}"
                render(access)
            }
            "dec", "inc" -> {
                val control = row.control as? Control.Stepper ?: return
                val change = (if (action == "dec") control.down else control.up) ?: return message(access, "${row.label} is already at its limit.")
                val error = change()
                message(access, error?.let { "<col=ff0000>Not changed: $it" } ?: "${row.label.removeSuffix(" *")} changed${if (row.label.endsWith("*")) "." else " in the draft; Save Changes to apply."}")
            }
        }
    }

    suspend fun knobToggle(access: ProtectedAccess, index: Int) {
        val state = state(access.player)
        val rows = knobRows(state)
        val page = state.knobPages[state.section] ?: 0
        val row = rows.getOrNull(page * knobRowsPerPage(state.section) + index) ?: return
        val control = row.control as? Control.Toggle ?: return
        val error = control.action(access)
        state.message = error?.let { "<col=ff0000>Not changed: $it" } ?: "${row.label.removeSuffix(" *")} changed${if (row.label.endsWith("*")) "." else " in the draft; Save Changes to apply."}"
        open(access)
    }

    fun knobPage(access: ProtectedAccess, delta: Int) {
        val state = state(access.player)
        state.knobPages[state.section] = ((state.knobPages[state.section] ?: 0) + delta).coerceAtLeast(0)
        state.help = ""
        render(access)
    }

    /* Active offers */

    private fun renderOffers(paint: Painter, state: ManagerState) {
        val trader = meister.trader
        paint.text("of_title", "Active Offers")
        val offers = trader?.offers().orEmpty().sortedBy { it.slot }
        listOf("Item", "Side", "Price", "Filled", "Status").forEachIndexed { i, h -> paint.text("of_h$i", h) }
        paint.button("of_cancelall", "Cancel all...", if (offers.any { it.isOpen }) ButtonState.Normal else ButtonState.Disabled)
        for (r in 0 until OFFER_ROWS) {
            val row = "of_r$r"
            val offer = offers.getOrNull(r)
            paint.hide(row, offer == null)
            if (offer == null || trader == null) continue
            paint.hide("${row}_bg", offer.slot != state.offerSlot)
            paint.icon("${row}_icon", offer.itemId)
            paint.text("${row}_c0", trader.itemName(offer.itemId))
            paint.text("${row}_c1", if (offer.sell) "<col=00ff00>Sell</col>" else "<col=5fa8ff>Buy</col>")
            paint.text("${row}_c2", "${offer.price.formatAmount} gp")
            paint.text("${row}_c3", "${offer.completed.formatAmount} / ${offer.total.formatAmount}")
            val status =
                when {
                    offer.isOpen -> "Open, ${offer.remaining.formatAmount} left"
                    offer.completed >= offer.total -> "Completed"
                    else -> "Cancelled"
                }
            paint.text("${row}_c4", status + if (offer.hasCollectable) " (collecting)" else "")
        }
        val selected = offers.firstOrNull { it.slot == state.offerSlot }
        if (offers.isEmpty()) {
            paint.text("of_sel", if (trader == null) "Master Meister is not running." else "No offers on the exchange right now.")
        } else if (selected == null) {
            paint.text("of_sel", "Select an offer to cancel it.")
        } else {
            paint.text("of_sel", "Slot ${selected.slot}: ${if (selected.sell) "sell" else "buy"} ${trader!!.itemName(selected.itemId)}")
        }
        val usable = selected?.isOpen == true
        paint.button("of_cancel", "Cancel", if (usable) ButtonState.Normal else ButtonState.Disabled)
        paint.button("of_replace", "Cancel and replace", if (usable) ButtonState.Normal else ButtonState.Disabled)
        paint.text("of_help", "Edits apply to new offers. Cancel keeps fills and returns the rest; replace re-offers.")
    }

    fun selectOffer(access: ProtectedAccess, row: Int) {
        val state = state(access.player)
        val offer = meister.trader?.offers()?.sortedBy { it.slot }?.getOrNull(row) ?: return
        state.offerSlot = offer.slot
        render(access)
    }

    fun cancelOffer(access: ProtectedAccess, replace: Boolean) {
        val state = state(access.player)
        val trader = meister.trader ?: return
        val slot = state.offerSlot ?: return
        val error = trader.cancelOffer(slot, replace, "cancelled by ${access.player.displayName} in the Bot Manager")
        meister.saveNow()
        if (error == null) meisterCommands.audit(access.player.displayName, "cancelled Master Meister's offer in slot $slot${if (replace) " to replace it" else ""}")
        message(
            access,
            error?.let { "<col=ff0000>Not cancelled: $it" }
                ?: if (replace) "Cancelled; once its box is collected he places a fresh offer on the saved rule." else "Cancelled; any fills stay his and the rest comes back to his ledger.",
        )
    }

    suspend fun cancelAll(access: ProtectedAccess) {
        val trader = meister.trader ?: return
        val open = trader.offers().count { it.isOpen }
        if (!confirm(access, "Cancel all $open of Master Meister's open offers?", "Yes, cancel them")) return open(access)
        var result = ""
        meisterCommands.run(access.player.displayName, listOf("cancel")) { result = it }
        state(access.player).message = result
        open(access)
    }

    /* Trade history */

    private fun historyRows(state: ManagerState, trader: MeisterTrader): List<MeisterFill> =
        MeisterHistoryView.filter(trader.historyFills(), state.query, System.currentTimeMillis()) { trader.itemName(it) }

    private fun renderHistory(paint: Painter, state: ManagerState) {
        val trader = meister.trader
        paint.text("hi_title", "Trade History")
        if (trader == null) {
            paint.text("hi_scope", "Master Meister is not running.")
            for (r in 0 until HISTORY_ROWS) paint.hide("hi_r$r", true)
            return
        }
        val rows = historyRows(state, trader)
        val summary = MeisterHistoryView.summarize(rows)
        val pages = pages(rows.size, HISTORY_ROWS)
        val page = clampPage(state.historyPage, pages).also { state.historyPage = it }
        val filtered = state.query.search.isNotEmpty() || state.query.itemId != null || state.query.side != HistorySide.All || state.query.range != HistoryRange.All
        paint.text("hi_scope", "${rows.size} fills - ${if (filtered) "totals cover the filtered fills" else "totals cover all history"} - page ${page + 1}/$pages")
        val cards =
            listOf(
                "Spent" to "${summary.spent.formatAmount} gp",
                "Net sales" to "${summary.netProceeds.formatAmount} gp",
                "Net cash flow" to "${signed(summary.netCashFlow)} gp",
                "Realized P/L" to "${signed(summary.realized)} gp",
            )
        cards.forEachIndexed { i, (label, value) ->
            paint.text("hi_card${i}_label", label)
            paint.text("hi_card${i}_value", value)
        }
        val itemFilter = state.query.itemId?.let { trader.itemName(it) }
        val search = itemFilter ?: state.query.search
        paint.button("hi_search", if (search.isEmpty()) "Search items..." else "Item: $search")
        paint.button("hi_side", state.query.side.label)
        paint.button("hi_range", state.query.range.label)
        paint.button("hi_sort", "${state.query.sort.label} ${if (state.query.descending) "v" else "^"}")
        paint.button("hi_export", "Export CSV", if (rows.isEmpty()) ButtonState.Disabled else ButtonState.Normal)
        listOf("Time", "Item", "Type", "Qty", "Unit", "Fees", "Net total", "P/L").forEachIndexed { i, h -> paint.text("hi_h$i", h) }
        val shown = rows.drop(page * HISTORY_ROWS).take(HISTORY_ROWS)
        for (r in 0 until HISTORY_ROWS) {
            val row = "hi_r$r"
            val fill = shown.getOrNull(r)
            paint.hide(row, fill == null)
            if (fill == null) continue
            paint.hide("${row}_bg", fill.id != state.historySelected)
            paint.icon("${row}_icon", fill.itemId)
            paint.text("${row}_c0", time(fill.time))
            paint.text("${row}_c1", shorten(trader.itemName(fill.itemId), HISTORY_NAME_CHARS))
            paint.text("${row}_c2", when { fill.reversal -> "<col=ff981f>FIX</col>"; fill.buy -> "<col=5fa8ff>BUY</col>"; else -> "<col=00ff00>SELL</col>" })
            paint.text("${row}_c3", fill.quantity.formatAmount)
            paint.text("${row}_c4", fill.unitPrice.formatAmount)
            paint.text("${row}_c5", fill.fee.formatAmount)
            paint.text("${row}_c6", signed(fill.net))
            paint.text(
                "${row}_c7",
                when {
                    fill.buy || fill.reversal -> "-"
                    fill.realized == null -> "<col=c8b48c>Unknown</col>"
                    else -> signed(fill.realized)
                },
            )
        }
        if (rows.isEmpty()) {
            paint.hide("hi_r0", false)
            paint.hide("hi_r0_bg", true)
            paint.hide("hi_r0_icon", true)
            for (c in 0 until 8) paint.text("hi_r0_c$c", "")
            paint.text("hi_r0_c1", if (trader.historyFills().isEmpty()) "No fills yet." else "No fills match.")
        }
        renderFillDetails(paint, state, trader, rows.firstOrNull { it.id == state.historySelected })
    }

    private fun renderFillDetails(paint: Painter, state: ManagerState, trader: MeisterTrader, fill: MeisterFill?) {
        for (i in 0 until 4) paint.hide("hd_v$i", fill == null)
        paint.hide("hd_text", fill != null)
        if (fill == null) {
            paint.text("hd_title", "Select a fill for its details")
            paint.text("hd_note", "Tracking since ${date(trader.historyTrackingSince)}")
            paint.text("hd_text", "Profit = net proceeds minus weighted-average cost; starting stock is Unknown, not free.")
            return
        }
        val name = trader.itemName(fill.itemId)
        paint.text("hd_note", "Offer #${fill.offerId}, slot ${fill.slot}: filled to ${fill.filledAfter}/${fill.offerTotal} at ${fill.offerPrice.formatAmount}")
        if (fill.buy || fill.reversal) {
            paint.text("hd_title", "${if (fill.reversal) "Correction" else "Purchase"}: $name")
            paint.text("hd_v0", "Paid ${fill.gross.formatAmount} gp")
            paint.text("hd_v1", "Unit ${fill.unitPrice.formatAmount} gp")
            paint.text("hd_v2", "GE fees 0 gp")
            paint.text("hd_v3", if (fill.reversal) "Exchange lost this fill" else "Adds to cost basis")
            return
        }
        paint.text("hd_title", "Sale: $name")
        paint.text("hd_v0", "Gross ${fill.gross.formatAmount} gp")
        paint.text("hd_v1", "GE fees ${fill.fee.formatAmount} gp")
        paint.text("hd_v2", fill.costBasis?.let { "Basis ${it.formatAmount} gp" } ?: "Basis: Unknown")
        paint.text(
            "hd_v3",
            when {
                fill.realized == null -> "Profit: Unknown (${fill.unknownQuantity} starting stock)"
                fill.unknownQuantity > 0 -> "P/L ${signed(fill.realized)} (${fill.unknownQuantity} excluded)"
                else -> "Realized ${signed(fill.realized)} gp"
            },
        )
    }

    fun selectFill(access: ProtectedAccess, row: Int) {
        val state = state(access.player)
        val trader = meister.trader ?: return
        val fill = historyRows(state, trader).drop(state.historyPage * HISTORY_ROWS).getOrNull(row) ?: return
        state.historySelected = fill.id
        render(access)
    }

    suspend fun historyFilter(access: ProtectedAccess, which: String) {
        val state = state(access.player)
        val q = state.query
        state.query =
            when (which) {
                "search" -> {
                    val text = access.stringDialog("Show fills whose item name contains (blank shows all):").trim()
                    q.copy(search = text, itemId = null)
                }
                "side" -> q.copy(side = HistorySide.entries[(q.side.ordinal + 1) % HistorySide.entries.size])
                "range" -> q.copy(range = HistoryRange.entries[(q.range.ordinal + 1) % HistoryRange.entries.size])
                "sort" -> {
                    val choices = HistorySort.entries.flatMap { listOf("${it.label}, highest first", "${it.label}, lowest first") } + "Cancel"
                    val picked = pick(access, "Sort fills by", choices)
                    val sort = HistorySort.entries.getOrNull(picked / 2) ?: return open(access)
                    q.copy(sort = sort, descending = picked % 2 == 0)
                }
                else -> q
            }
        state.historyPage = 0
        open(access)
    }

    fun exportHistory(access: ProtectedAccess) {
        val state = state(access.player)
        val trader = meister.trader ?: return
        val rows = historyRows(state, trader)
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault()).format(Instant.now())
        val path = Path.of(EXPORT_DIR, "master-meister-trades-$stamp.csv")
        val text =
            try {
                Files.createDirectories(path.parent)
                Files.writeString(path, MeisterHistoryView.csv(rows) { trader.itemName(it) })
                "Exported ${rows.size} fills (current filters) to $path"
            } catch (e: Exception) {
                "<col=ff0000>Export failed: ${e.message}"
            }
        access.mes(text)
        meisterCommands.audit(access.player.displayName, "exported ${rows.size} Master Meister fills to $path")
        message(access, text)
    }

    fun historyPage(access: ProtectedAccess, delta: Int) {
        val state = state(access.player)
        state.historyPage = (state.historyPage + delta).coerceAtLeast(0)
        render(access)
    }

    /* Overview */

    private fun renderOverview(paint: Painter) {
        val coordinator = bots.coordinator
        val all = coordinator?.all().orEmpty()
        val live = all.count { coordinator?.isLive(it.id) == true }
        val queued = all.count { it.enabled && coordinator?.isLive(it.id) != true && it.state != BotState.Error }
        val errors = all.count { it.state == BotState.Error }
        val max = coordinator?.settings?.maxActiveBots ?: 0
        val cards = listOf("Bots" to all.size.toString(), "In world" to "$live / $max", "Queued" to queued.toString(), "Errors" to if (errors > 0) "<col=ff0000>$errors" else "0")
        cards.forEachIndexed { i, (label, value) ->
            paint.text("ov_card${i}_label", label)
            paint.text("ov_card${i}_value", value)
        }
        paint.npcHead("ov_m_portrait", PORTRAIT_NPC)
        val trader = meister.trader
        val master = coordinator?.settings?.masterEnabled == true
        paint.button("ov_b0", "Bots master: ${if (master) "<col=00ff00>ON" else "<col=ff0000>OFF"}", if (coordinator == null) ButtonState.Disabled else ButtonState.Normal)
        paint.button("ov_b1", "Open bot roster")
        if (trader == null) {
            paint.text("ov_m0", "<col=ff0000>Not running")
            paint.text("ov_m1", "")
            paint.text("ov_m2", "")
            for (i in 2..5) paint.button("ov_b$i", BUTTON_OV[i], ButtonState.Disabled)
        } else {
            val s = trader.settings
            paint.text("ov_m0", "${tradingState(trader).second} - ${if (s.useRules) "rule lists (${trader.rules.buy.count { it.enabled }} buy, ${trader.rules.sell.count { it.enabled }} sell)" else "whole market"}")
            paint.text("ov_m1", "Cash ${trader.cash.formatAmount} gp - ${trader.holdingsView().size} item kinds - ${plural(trader.offers().count { it.isOpen }, "open offer")}")
            paint.text("ov_m2", "Realized P/L ${signed(trader.realizedProfit())} gp over ${trader.historyFills().size} recorded fills")
            paint.button("ov_b2", "Trading: ${if (s.tradingEnabled) "<col=00ff00>ON" else "<col=ff0000>OFF"}")
            paint.button("ov_b3", if (s.buyingPaused) "Resume buying" else "Pause buying")
            paint.button("ov_b4", if (s.sellingPaused) "Resume selling" else "Pause selling")
            paint.button("ov_b5", "Master Meister's items")
        }
        paint.text(
            "ov_note",
            "Gathering bots work their sites and sell on the Grand Exchange; manage them under Bot Roster. Master Meister trades on the same exchange under Master Meister: buy and sell rules, schedule, open offers and his trade history. Pausing stops new offers; cancelling is separate.",
        )
    }

    suspend fun overviewAction(access: ProtectedAccess, index: Int) {
        val coordinator = bots.coordinator
        val trader = meister.trader
        val admin = access.player.displayName
        when (index) {
            0 -> coordinator?.let { it.setMaster(!it.settings.masterEnabled) }
            1 -> return navigate(access, Section.Roster)
            2 -> trader?.let { t -> meisterCommands.change(admin, t, "trading") { it.copy(tradingEnabled = !t.settings.tradingEnabled) } }
            3 -> trader?.let { t -> meisterCommands.change(admin, t, "buying") { it.copy(buyingPaused = !t.settings.buyingPaused) } }
            4 -> trader?.let { t -> meisterCommands.change(admin, t, "selling") { it.copy(sellingPaused = !t.settings.sellingPaused) } }
            5 -> return windows?.openMeisterItems(access) ?: Unit
        }
        render(access)
    }

    /* Bot roster */

    private fun rosterRows(state: ManagerState): List<BotStatusView> {
        val coordinator = bots.coordinator ?: return emptyList()
        val search = state.rosterSearch.lowercase()
        return coordinator.status().filter { view ->
            val record = view.record
            val live = view.active
            (search.isEmpty() || record.name.lowercase().contains(search)) &&
                (state.rosterRole == null || record.config.role.key == state.rosterRole) &&
                (state.rosterPace == null || (BotPace.of(record.config)?.label ?: "Custom") == state.rosterPace) &&
                when (state.rosterState) {
                    RosterStateFilter.All -> true
                    RosterStateFilter.InWorld -> live
                    RosterStateFilter.Queued -> record.enabled && !live && record.state != BotState.Error
                    RosterStateFilter.Out -> !record.enabled
                    RosterStateFilter.Error -> record.state == BotState.Error
                }
        }
    }

    private fun renderRoster(paint: Painter, state: ManagerState) {
        val coordinator = bots.coordinator
        val rows = rosterRows(state)
        paint.button("ro_search", if (state.rosterSearch.isEmpty()) "Search names..." else "Name: ${state.rosterSearch}")
        paint.button("ro_fstate", state.rosterState.label)
        paint.button("ro_frole", state.rosterRole?.replaceFirstChar(Char::uppercaseChar) ?: "All roles")
        paint.button("ro_fdiff", state.rosterPace ?: "All levels")
        listOf("Name", "Role", "State", "Level", "Inv", "GE", "Coins").forEachIndexed { i, h -> paint.text("ro_h$i", h) }
        val pages = pages(rows.size, ROSTER_ROWS)
        val page = clampPage(state.rosterPage, pages).also { state.rosterPage = it }
        val shown = rows.drop(page * ROSTER_ROWS).take(ROSTER_ROWS)
        for (r in 0 until ROSTER_ROWS) {
            val row = "ro_r$r"
            val view = shown.getOrNull(r)
            paint.hide(row, view == null)
            if (view == null) continue
            val record = view.record
            paint.hide("${row}_bg", record.id != state.rosterSelected)
            val waiting = record.enabled && !view.active && record.state != BotState.Error
            val stateText =
                when {
                    record.state == BotState.Error -> "<col=ff0000>Error"
                    waiting -> "<col=ff981f>Queued"
                    !record.enabled -> "<col=9f9f9f>Disabled"
                    record.activity.startsWith("Resting") -> "Resting"
                    else -> record.state.shortLabel
                }
            paint.text("${row}_c0", if (record.enabled) record.name else "<col=9f9f9f>${record.name}")
            paint.text("${row}_c1", record.config.role.key.replaceFirstChar(Char::uppercaseChar))
            paint.text("${row}_c2", stateText)
            paint.text("${row}_c3", BotPace.of(record.config)?.label ?: "Custom")
            paint.text("${row}_c4", "${view.inventoryUsed}/${view.inventoryCapacity}")
            paint.text("${row}_c5", view.activeListings.toString())
            paint.text("${row}_c6", view.coins.formatAmount)
        }
        val total = coordinator?.all()?.size ?: 0
        paint.text("ro_page", when { coordinator == null -> "Bots are not loaded yet."; total == 0 -> "No bots yet - press Create."; rows.isEmpty() -> "No bots match the filters."; else -> "${rows.size} of $total bots, page ${page + 1}/$pages" })
        paint.button("ro_create", "Create")
        paint.button("ro_bulk", "Bulk...", if (rows.isEmpty()) ButtonState.Disabled else ButtonState.Normal)
        paint.button("ro_prev", "Prev", if (page > 0) ButtonState.Normal else ButtonState.Disabled)
        paint.button("ro_next", "Next", if (page < pages - 1) ButtonState.Normal else ButtonState.Disabled)
        val selected = coordinator?.find(state.rosterSelected?.toString() ?: "")?.takeIf { state.rosterSelected != null }
        if (selected == null) {
            paint.text("rd_title", "No bot selected")
            paint.text("rd_info", "Select a bot to see what it is doing and to manage it. Configure opens its full settings.")
            for (i in 0 until 6) paint.hide("rd_a$i", true)
            paint.text("rd_hint", "Filters and search narrow the list; Bulk acts on every bot shown.")
            return
        }
        val live = coordinator.isLive(selected.id)
        paint.text("rd_title", "#${selected.id} ${selected.name}")
        val error = selected.lastError?.let { "<br><col=ff0000>Error: $it</col>" } ?: ""
        paint.text("rd_info", "${selected.config.role.key.replaceFirstChar(Char::uppercaseChar)} at ${selected.config.site}<br>${if (live) "<col=00ff00>In the world</col>" else "Out of the world"} - ${selected.state.shortLabel}: ${selected.activity}$error")
        paint.button("rd_a0", "Configure")
        paint.button("rd_a1", if (selected.enabled) "Disable" else "Enable")
        paint.button("rd_a2", "Send home", if (live) ButtonState.Normal else ButtonState.Disabled)
        paint.button("rd_a3", "Go to bot", if (live) ButtonState.Normal else ButtonState.Disabled)
        paint.button("rd_a4", "Items")
        paint.button("rd_a5", "Remove", if (live) ButtonState.Disabled else ButtonState.Normal)
        paint.text("rd_hint", if (live) "Disable it before removing it." else "")
    }

    fun selectBot(access: ProtectedAccess, row: Int) {
        val state = state(access.player)
        val view = rosterRows(state).drop(state.rosterPage * ROSTER_ROWS).getOrNull(row) ?: return
        state.rosterSelected = view.record.id
        render(access)
    }

    suspend fun rosterFilter(access: ProtectedAccess, which: String) {
        val state = state(access.player)
        when (which) {
            "search" -> state.rosterSearch = access.stringDialog("Show bots whose name contains (blank shows all):").trim()
            "state" -> state.rosterState = RosterStateFilter.entries[(state.rosterState.ordinal + 1) % RosterStateFilter.entries.size]
            "role" -> {
                val roles = listOf<String?>(null) + (bots.coordinator?.all().orEmpty().map { it.config.role.key }.distinct().sorted())
                state.rosterRole = roles[(roles.indexOf(state.rosterRole) + 1) % roles.size]
            }
            "pace" -> {
                val paces = listOf<String?>(null) + BotPace.entries.map { it.label } + "Custom"
                state.rosterPace = paces[(paces.indexOf(state.rosterPace) + 1) % paces.size]
            }
        }
        state.rosterPage = 0
        open(access)
    }

    fun rosterPage(access: ProtectedAccess, delta: Int) {
        val state = state(access.player)
        state.rosterPage = (state.rosterPage + delta).coerceAtLeast(0)
        render(access)
    }

    suspend fun rosterBulk(access: ProtectedAccess) {
        val state = state(access.player)
        val coordinator = bots.coordinator ?: return
        val shown = rosterRows(state).map { it.record }
        val picked = pick(access, "Bulk change for ${shown.size} shown bot(s)", listOf("Enable them", "Disable them", "Set their difficulty...", "Cancel"))
        val result =
            when (picked) {
                0, 1 -> {
                    val enable = picked == 0
                    if (!confirm(access, "${if (enable) "Enable" else "Disable"} ${shown.size} bot(s)?", "Yes")) return open(access)
                    val failed = shown.count { (if (enable) coordinator.enable(it.id) else coordinator.disable(it.id)) != null }
                    "${if (enable) "Enabled" else "Disabled"} ${shown.size - failed} bot(s)${if (failed > 0) ", $failed refused" else ""}."
                }
                2 -> {
                    val pace = BotPace.entries.getOrNull(pick(access, "Difficulty for ${shown.size} bots", BotPace.entries.map { "${it.label}: ${it.description}" } + "Cancel")) ?: return open(access)
                    val failed = shown.count { coordinator.updateConfig(it.id, pace::applyTo) != null }
                    "Set ${shown.size - failed} bot(s) to ${pace.label}."
                }
                else -> null
            }
        state.message = result
        open(access)
    }

    suspend fun botAction(access: ProtectedAccess, index: Int) {
        val state = state(access.player)
        val coordinator = bots.coordinator ?: return
        val id = state.rosterSelected ?: return
        val record = coordinator.find(id.toString()) ?: return
        val windows = windows ?: return
        when (index) {
            0 -> return windows.openBotSettings(access, id)
            1 -> {
                val error = if (record.enabled) coordinator.disable(id) else coordinator.enable(id)
                state.message = error?.let { "<col=ff0000>${record.name}: $it" } ?: "${record.name} is now ${if (record.enabled) "disabled" else "enabled"}."
            }
            2 -> state.message = windows.sendHome(id)
            3 -> {
                val bot = bots.playerOf(id) ?: return
                access.ifClose()
                access.telejump(bot.coords)
                return
            }
            4 -> return windows.openBotItems(access, id)
            5 -> {
                if (!confirm(access, "Remove ${record.name} for good?", "Yes, remove it and its items")) return open(access)
                val error = coordinator.remove(id, force = false)
                state.message = error?.let { "<col=ff0000>Not removed: $it" } ?: "Removed ${record.name}."
                if (error == null) state.rosterSelected = null
            }
        }
        open(access)
    }

    suspend fun createBot(access: ProtectedAccess) {
        windows?.createBot(access)
        open(access)
    }

    /* Footer */

    private fun renderFooter(paint: Painter, state: ManagerState) {
        val trader = meister.trader
        val section = state.section
        val message = state.message
        when {
            section == Section.History -> {
                paint.dot("ft_dot0", GREEN)
                paint.text("ft_t0", "History synced")
                paint.dot("ft_dot1", TRANSPARENT)
                paint.text("ft_t1", message ?: "Cost basis: weighted average")
                paint.button("ft_b0", "Refresh")
                paint.button("ft_b1", "Prev page", if (state.historyPage > 0) ButtonState.Normal else ButtonState.Disabled)
                val rows = trader?.let { historyRows(state, it).size } ?: 0
                paint.button("ft_b2", "Next page", if ((state.historyPage + 1) * HISTORY_ROWS < rows) ButtonState.Normal else ButtonState.Disabled)
            }
            section == Section.Overview || section == Section.Roster -> {
                val coordinator = bots.coordinator
                val master = coordinator?.settings?.masterEnabled == true
                val live = coordinator?.all()?.count { coordinator.isLive(it.id) } ?: 0
                val max = coordinator?.settings?.maxActiveBots ?: 0
                paint.dot("ft_dot0", if (master) GREEN else RED)
                paint.text("ft_t0", "Bots master: ${if (master) "on" else "off"}")
                paint.dot("ft_dot1", if (live < max) GREEN else AMBER)
                paint.text("ft_t1", message ?: "In world $live / $max")
                paint.button("ft_b0", if (master) "Master off" else "Master on")
                paint.button("ft_b1", "Max -", if (max > 0) ButtonState.Normal else ButtonState.Disabled)
                paint.button("ft_b2", "Max +", if (max < BotCoordinator.MAX_ACTIVE_LIMIT) ButtonState.Normal else ButtonState.Disabled)
            }
            else -> {
                val dirty = unsaved(state, trader)
                if (trader == null) {
                    paint.dot("ft_dot0", RED)
                    paint.text("ft_t0", "Master Meister is not running")
                } else {
                    val s = trader.settings
                    paint.dot("ft_dot0", if (!s.tradingEnabled) RED else if (s.buyingPaused) AMBER else GREEN)
                    paint.text("ft_t0", "Buying: ${if (!s.tradingEnabled) "off" else if (s.buyingPaused) "paused" else "active, next ${countdown(trader.nextBuyAt)}"}")
                }
                val selling =
                    trader?.settings?.let { s -> "Selling: ${if (!s.tradingEnabled) "off" else if (s.sellingPaused) "paused" else "active, next ${countdown(trader.nextSellAt)}"}" }
                val sellingOn = trader?.settings?.let { it.tradingEnabled && !it.sellingPaused } == true
                paint.dot("ft_dot1", if (dirty || message != null) AMBER else if (sellingOn) GREEN else RED)
                paint.text("ft_t1", message ?: if (dirty) "<col=ff981f>Unsaved changes</col>" else selling ?: "")
                if (section == Section.Settings) {
                    paint.button("ft_b0", "Reset", if (trader == null) ButtonState.Disabled else ButtonState.Normal)
                } else {
                    paint.button("ft_b0", if (trader?.settings?.buyingPaused == true) "Resume buying" else "Pause buying", if (trader == null) ButtonState.Disabled else ButtonState.Normal)
                }
                paint.button("ft_b1", "Discard", if (dirty) ButtonState.Normal else ButtonState.Disabled)
                paint.button("ft_b2", "Save Changes", if (dirty) ButtonState.Selected else ButtonState.Disabled)
            }
        }
    }

    suspend fun footerAction(access: ProtectedAccess, index: Int) {
        val state = state(access.player)
        val trader = meister.trader
        val coordinator = bots.coordinator
        when (state.section) {
            Section.History ->
                when (index) {
                    0 -> message(access, "Refreshed.")
                    1 -> historyPage(access, -1)
                    2 -> historyPage(access, 1)
                }
            Section.Overview, Section.Roster -> {
                coordinator ?: return
                val error =
                    when (index) {
                        0 -> coordinator.setMaster(!coordinator.settings.masterEnabled).let { null }
                        1 -> coordinator.setMaxActive(coordinator.settings.maxActiveBots - 1)
                        else -> coordinator.setMaxActive(coordinator.settings.maxActiveBots + 1)
                    }
                message(access, error?.let { "<col=ff0000>$it" } ?: "Saved.")
            }
            else ->
                when (index) {
                    0 ->
                        if (state.section == Section.Settings) {
                            trader ?: return
                            if (!confirm(access, "Reset Master Meister's settings to the defaults (in the draft)?", "Yes, reset them")) return open(access)
                            val draft = draftOf(state, trader)
                            state.draft = draft.copy(settings = MeisterSettings(useRules = draft.settings.useRules))
                            state.message = "Defaults loaded into the draft; Save Changes to apply."
                            open(access)
                        } else {
                            trader ?: return
                            val result = meisterCommands.change(access.player.displayName, trader, "buying") { it.copy(buyingPaused = !trader.settings.buyingPaused) }
                            message(access, result?.let { "<col=ff0000>$it" } ?: if (trader.settings.buyingPaused) "Buying paused: no new buy offers. Open ones stay." else "Buying resumed.")
                        }
                    1 -> discard(access)
                    2 -> save(access)
                }
        }
    }

    /* Helpers */

    private suspend fun pick(access: ProtectedAccess, title: String, choices: List<String>): Int {
        val picked = access.menu(title, hotkeys = false, choices = choices.take(MAX_MENU))
        access.ifCloseSub(MENU_INTERFACE)
        return picked
    }

    private suspend fun confirm(access: ProtectedAccess, question: String, yes: String): Boolean = pick(access, question, listOf(yes, "No")) == 0

    private fun pages(count: Int, perPage: Int): Int = maxOf(1, (count + perPage - 1) / perPage)

    private fun clampPage(page: Int, pages: Int): Int = page.coerceIn(0, pages - 1)

    private fun signed(value: Long): String =
        when {
            value > 0 -> "<col=00ff00>+${value.formatAmount}</col>"
            value < 0 -> "<col=ff3030>-${(-value).formatAmount}</col>"
            else -> "0"
        }

    private fun shorten(text: String, chars: Int): String = if (text.length <= chars) text else text.take(chars - 1).trimEnd() + "."

    private fun plural(count: Int, noun: String): String = "$count $noun${if (count == 1) "" else "s"}"

    private fun countdown(at: Long): String {
        val seconds = ((at - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    private fun time(at: Long): String {
        val formatter = if (System.currentTimeMillis() - at < DAY_MS) TIME else DAY
        return formatter.format(Instant.ofEpochMilli(at))
    }

    private fun date(at: Long): String = DATE.format(Instant.ofEpochMilli(at))

    companion object {
        const val RULE_ROWS: Int = 4
        const val KNOB_ROWS: Int = 9
        private const val SCHEDULE_ROWS = 7
        private const val KNOB_TOP = 34
        private const val KNOB_PITCH = 21
        private const val SCHEDULE_OFFSET = 61 - KNOB_TOP
        const val OFFER_ROWS: Int = 8
        const val HISTORY_ROWS: Int = 5
        private const val HISTORY_NAME_CHARS = 13
        const val ROSTER_ROWS: Int = 8
        private const val ITEM_PICKS = 12
        private const val MAX_MENU = 120
        private const val DAY_MS = 24 * 60 * 60_000L
        private const val PORTRAIT_NPC = "npc.ge_boss"
        private const val MENU_INTERFACE = "interface.menu"
        private const val EXPORT_DIR = ".data/exports"
        private const val GREEN = 0x00c000
        private const val AMBER = 0xffb000
        private const val RED = 0xd00000
        private const val TRANSPARENT = 0x2b2218
        private val TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        private val DAY = DateTimeFormatter.ofPattern("dd/MM").withZone(ZoneId.systemDefault())
        private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault())
        private val TAB_LABELS = listOf("Buy Rules", "Sell Rules", "Timing", "Offers", "History")
        private val BUTTON_OV = listOf("", "", "Trading", "Pause buying", "Pause selling", "Master Meister's items")
        private val SCHEDULE_KNOBS = listOf("interval", "sellinterval", "jitter", "buychance", "sellchance", "maxoffers", "maxbuys", "maxsells", "cooldown", "flip", "buytimeout", "selltimeout")
        private val SETTINGS_SWITCHES = listOf("buysensitive", "sellsensitive", "replenishcash", "replenishitems")
        private val SETTINGS_KNOBS = listOf("premium", "biddiscount", "markup", "minsell", "deviation", "offerspend", "itemspend", "dayspend", "buyqty", "sellqty", "sellshare")

        private val SECTION_LAYERS = listOf(Section.Overview to "ov", Section.Roster to "ro", Section.Offers to "of", Section.History to "hi")

        private val OP1 = arrayOf(IfEvent.Op1)
        private val OP12 = arrayOf(IfEvent.Op1, IfEvent.Op2)

        /** Every component that takes clicks, with the ops it answers. */
        val CLICKABLE: List<Pair<String, Array<IfEvent>>> = buildList {
            for (i in 0 until 9) add("nav$i" to OP1)
            for (i in 0 until 5) add("mh_tab$i" to OP1)
            listOf("ru_search", "ru_add", "ru_bulk", "ru_prev", "ru_next", "ed_check", "ed_remove").forEach { add(it to OP1) }
            for (r in 0 until RULE_ROWS) {
                add("ru_r$r" to OP12)
                add("ru_r${r}_check" to OP1)
            }
            for (i in 0 until 6) add("ed_f$i" to OP1)
            listOf("kn_prev", "kn_next").forEach { add(it to OP1) }
            for (r in 0 until KNOB_ROWS) listOf("label", "dec", "inc", "toggle").forEach { add("kn_r${r}_$it" to OP1) }
            listOf("of_cancelall", "of_cancel", "of_replace").forEach { add(it to OP1) }
            for (r in 0 until OFFER_ROWS) add("of_r$r" to OP1)
            listOf("hi_search", "hi_side", "hi_range", "hi_sort", "hi_export").forEach { add(it to OP1) }
            for (r in 0 until HISTORY_ROWS) add("hi_r$r" to OP1)
            for (i in 0 until 6) add("ov_b$i" to OP1)
            listOf("ro_search", "ro_fstate", "ro_frole", "ro_fdiff", "ro_create", "ro_bulk", "ro_prev", "ro_next").forEach { add(it to OP1) }
            for (r in 0 until ROSTER_ROWS) add("ro_r$r" to OP1)
            for (i in 0 until 6) add("rd_a$i" to OP1)
            for (i in 0 until 3) add("ft_b$i" to OP1)
        }
    }
}
