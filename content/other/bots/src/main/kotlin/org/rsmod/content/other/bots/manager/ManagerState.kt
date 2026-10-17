package org.rsmod.content.other.bots.manager

import org.rsmod.content.other.bots.meister.HistoryQuery
import org.rsmod.content.other.bots.meister.MeisterRules
import org.rsmod.content.other.bots.meister.MeisterSettings

/** A section of the Bot Manager; [nav] is its line in the navigation column. */
enum class Section(val nav: Int, val meister: Boolean) {
    Overview(0, false),
    Roster(1, false),
    BuyRules(3, true),
    SellRules(4, true),
    Schedule(5, true),
    Offers(6, true),
    History(7, true),
    Settings(8, false),
    ;

    companion object {
        val TABS: List<Section> = listOf(BuyRules, SellRules, Schedule, Offers, History)

        fun ofNav(index: Int): Section? = if (index == 2) BuyRules else entries.firstOrNull { it.nav == index }
    }
}

/** Unsaved edits to Master Meister's rules and settings; Save applies them, Discard drops them. */
data class Draft(val rules: MeisterRules, val settings: MeisterSettings)

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

    var query: HistoryQuery = HistoryQuery()
    var historyPage: Int = 0
    var historySelected: String? = null

    var rosterSearch: String = ""
    var rosterState: RosterStateFilter = RosterStateFilter.All
    var rosterRole: String? = null
    var rosterPace: String? = null
    var rosterPage: Int = 0
    var rosterSelected: Long? = null
}
