package org.rsmod.content.other.bots.manager

import org.rsmod.content.other.bots.core.BotRoleType
import org.rsmod.content.other.bots.meister.HistoryQuery
import org.rsmod.content.other.bots.meister.MeisterRules
import org.rsmod.content.other.bots.meister.MeisterSettings
import org.rsmod.content.other.bots.world.KitTarget

/**
 * A section of the Bot Manager; [nav] is its line in the navigation column. [bot] sections are the
 * tabs of one gathering bot's page, reached from the roster.
 */
enum class Section(val nav: Int, val meister: Boolean, val bot: Boolean = false) {
    Overview(0, false),
    Roster(1, false),
    BuyRules(3, true),
    SellRules(4, true),
    Schedule(5, true),
    Offers(6, true),
    History(7, true),
    Settings(8, false),
    BotBehaviour(1, false, bot = true),
    BotSelling(1, false, bot = true),
    BotTools(1, false, bot = true),
    BotActivity(1, false, bot = true),
    ;

    companion object {
        val TABS: List<Section> = listOf(BuyRules, SellRules, Schedule, Offers, History)
        val BOT_TABS: List<Section> = listOf(BotBehaviour, BotSelling, BotTools, BotActivity)

        fun ofNav(index: Int): Section? = if (index == 2) BuyRules else entries.firstOrNull { it.nav == index }
    }
}

/** Unsaved edits to Master Meister's rules and settings; Save applies them, Discard drops them. */
/** An administrator's unsaved edits, and the live rules and settings they started from. */
data class Draft(
    val rules: MeisterRules,
    val settings: MeisterSettings,
    val baseRules: MeisterRules = rules,
    val baseSettings: MeisterSettings = settings,
)

/** What the item picker chooses an item for. */
sealed interface PickerMode {
    data class Give(val botId: Long) : PickerMode

    data class AddRule(val buy: Boolean) : PickerMode

    data object Create : PickerMode
}

/**
 * The Bot Manager's picker: a list with icons and the chosen entry below it. Items are found with
 * a live search; creating a bot steps through a role, then one of its sites, then a name.
 */
data class Picker(
    val mode: PickerMode,
    val query: String = "",
    val page: Int = 0,
    val selected: String? = null,
    val amount: Int = 1,
    val target: KitTarget = KitTarget.Bank,
    val role: BotRoleType? = null,
    val name: String? = null,
)

enum class RosterStateFilter(val label: String) {
    All("All states"),
    InWorld("In world"),
    Queued("Queued"),
    Out("Out of world"),
    Error("Errors"),
}

/**
 * One administrator's view of the Bot Manager. It outlives the window, so reopening it comes back
 * to the same section, filters, pages and selections; only the draft is dropped on Discard.
 */
class ManagerState {
    var section: Section = Section.Overview
    var draft: Draft? = null
    var message: String? = null
    var help: String = ""

    var buySearch: String = ""
    var sellSearch: String = ""
    var buyPage: Int = 0
    var sellPage: Int = 0
    var buySelected: Int? = null
    var sellSelected: Int? = null

    val knobPages: MutableMap<Section, Int> = HashMap()

    var offerSlot: Int? = null

    /** The selected offer's creation time, so a cancel never hits a newer offer in the same slot. */
    var offerCreatedAt: Long? = null

    /** Offer slots in the order the rows were last drawn. */
    var offerRows: List<Int> = emptyList()

    var query: HistoryQuery = HistoryQuery()
    var historyPage: Int = 0
    var historySelected: String? = null

    var rosterSearch: String = ""
    var rosterState: RosterStateFilter = RosterStateFilter.All
    var rosterRole: String? = null
    var rosterPace: String? = null
    var rosterPage: Int = 0
    var rosterSelected: Long? = null

    /** Bot ids in the order the roster rows were last drawn. */
    var rosterRows: List<Long> = emptyList()

    var picker: Picker? = null
}
