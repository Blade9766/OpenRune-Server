package org.rsmod.content.quest.area.seers.elementalworkshop

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.draynor.porcineofinterest.cutsFlesh
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.BOOK
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.KEY
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.KNIFE
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.SLASHED_BOOK
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The bookcase in the Seers' Village house south of the anvil, the battered book it hides and the
 * key sewn into that book's spine. Reading the book starts the quest; its back pages carry the
 * workshop's maintenance notes, which are the quest's optional hints.
 *
 * The bookcase hands out another book whenever the player is left without a way in: no book at
 * all, or a slashed book with the key lost before the wall was ever unlocked.
 */
class ShieldBook
@Inject
constructor(private val ew: ElementalWorkshopQuest, private val objRepo: ObjRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(BOOKCASE) { searchBookcase() }
        onOpHeld1(BOOK) { read(battered = true) }
        onOpHeld1(SLASHED_BOOK) { read(battered = false) }
        onOpHeldU(BOOK, KNIFE) { slash() }
        onOpHeldU(BOOK) { slashWith(it.second) }
    }

    private suspend fun ProtectedAccess.searchBookcase() {
        anim(SEARCH_SEQ)
        soundSynth(BOOKCASE_SOUND)
        mes("You search the bookcase...")
        delay(1)
        if (!needsBook()) {
            mes("You find nothing of interest.")
            return
        }
        invAddOrDrop(objRepo, BOOK)
        objbox(BOOK, "You search the bookcase and find a battered book. You take it.")
    }

    private fun ProtectedAccess.needsBook(): Boolean {
        if (owns(BOOK)) {
            return false
        }
        if (!owns(SLASHED_BOOK)) {
            return true
        }
        return !owns(KEY) && !ew.isWorkshopUnlocked(player)
    }

    private suspend fun ProtectedAccess.slashWith(other: ItemServerType) {
        if (!cutsFlesh(other)) {
            mes("Nothing interesting happens.")
            return
        }
        slash()
    }

    private suspend fun ProtectedAccess.slash() {
        if (!ew.isStarted(player)) {
            mes("It seems a shame to cut up a book you haven't even read.")
            return
        }
        anim(SLASH_SEQ)
        invDel(inv, BOOK)
        if (!owns(SLASHED_BOOK)) {
            invAdd(inv, SLASHED_BOOK)
        }
        ew.markKeyFound(player)
        if (owns(KEY)) {
            mes("You cut the spine open, but the hollow inside is empty. You already have the key.")
            return
        }
        invAddOrDrop(objRepo, KEY)
        doubleobjbox(
            SLASHED_BOOK,
            KEY,
            "You make a small cut in the spine of the book. Inside you find a small, old, " +
                "battered key.",
        )
    }

    /**
     * The book's page arrows are pause buttons; each turn re-opens the book at the new spread,
     * as the client ignores further presses until the interface is sent again.
     */
    private suspend fun ProtectedAccess.read(battered: Boolean) {
        ew.start(this)
        val spreads = if (battered) BATTERED_SPREADS else SLASHED_SPREADS
        var spread = 0
        openSpread(spreads, spread)
        while (true) {
            val input = pauseButton()
            val turned =
                when (input.component) {
                    PAGE_LEFT -> spread - 1
                    PAGE_RIGHT -> spread + 1
                    else -> spread
                }
            if (turned in spreads.indices) {
                spread = turned
            }
            openSpread(spreads, spread)
        }
    }

    private fun ProtectedAccess.openSpread(spreads: List<Spread>, spread: Int) {
        ifOpenMainModal(BOOK_INTERFACE)
        player.runClientScript(
            BOOK_INIT_SCRIPT,
            RSCM.getRSCM("component.book:close_button"),
            RSCM.getRSCM("component.book:close_graphic"),
            RSCM.getRSCM(PAGE_LEFT),
            RSCM.getRSCM("component.book:page_left_graphic"),
            RSCM.getRSCM(PAGE_RIGHT),
            RSCM.getRSCM("component.book:page_right_graphic"),
        )
        ifSetText("component.book:title", TITLE)
        ifSetEvents(PAGE_LEFT, -1..-1, IfEvent.PauseButton)
        ifSetEvents(PAGE_RIGHT, -1..-1, IfEvent.PauseButton)
        val (left, right) = spreads[spread]
        for (line in 1..LINES_PER_PAGE) {
            ifSetText("component.book:page_left_text_$line", left.getOrElse(line - 1) { "" })
            ifSetText("component.book:page_right_text_$line", right.getOrElse(line - 1) { "" })
        }
        ifSetText("component.book:page_left_number", (spread * 2 + 1).toString())
        ifSetText("component.book:page_right_number", (spread * 2 + 2).toString())
        ifSetHide(PAGE_LEFT, spread == 0)
        ifSetHide(PAGE_RIGHT, spread == spreads.lastIndex)
        soundSynth(PAGE_SOUND)
    }

    private fun ProtectedAccess.owns(obj: String): Boolean =
        inv.count(obj) > 0 || bank.count(obj) > 0 || worn.count(obj) > 0

    internal companion object {
        const val BOOKCASE = "loc.elemental_workshop_bookcase"
        const val BOOK_INTERFACE = "interface.book"
        const val BOOK_INIT_SCRIPT = 2632
        const val TITLE = "The Elemental Shield"
        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val SLASH_SEQ = "seq.human_knife_slash"
        const val BOOKCASE_SOUND = "synth.bookcase_open_and_close"
        const val PAGE_SOUND = "synth.turn_book_page"
        const val LINES_PER_PAGE = 15
        const val WRAP_WIDTH = 26

        const val PAGE_LEFT = "component.book:page_left_button"
        const val PAGE_RIGHT = "component.book:page_right_button"

        val INTRODUCTION =
            "<u>A Forgotten Craft</u>" to
                listOf(
                    "Long before the town above was built, its smiths kept a workshop under " +
                        "the hill. There they harnessed water, air, earth and fire together, " +
                        "and from them forged metal that no ordinary furnace could make.",
                    "When the last of the smiths died, the workshop was sealed and the way " +
                        "into it hidden. Its machines have stood silent ever since.",
                    "I have written down all that I learnt there, lest the craft be lost " +
                        "entirely. As for the way in, I have kept it closer than any reader " +
                        "would think to look: it is bound up in this very book.",
                )

        val BATTERED_SPINE =
            "<u>A Note on the Binding</u>" to
                listOf(
                    "The binding of this book is lumpy and badly sewn, as if something was " +
                        "stitched into its spine after it was made.",
                )

        val INSTRUCTIONS =
            "<u>The Elemental Shield</u>" to
                listOf(
                    "First, take ore from the earth that lives. Ordinary picks and ordinary " +
                        "rocks will not serve.",
                    "Second, smelt one measure of that ore with four of coal. Only a furnace " +
                        "fed by the bellows, and the bellows driven by the wheel, burns hot " +
                        "enough to do it.",
                    "Third, take the metal to the workbench and work it with a hammer, " +
                        "following these instructions closely, and it will take the shape of " +
                        "a shield.",
                )

        val MAINTENANCE =
            "<u>Maintenance Notes</u>" to
                listOf(
                    "The wheel: the flow gates are particular. The gate nearer the dawn must " +
                        "be opened before the gate nearer the dusk. Gates opened out of turn " +
                        "are returned to rest by pulling the wheel lever.",
                    "The bellows: leather, thread and a needle will mend most of their hurts. " +
                        "Spare leather and sewing things were kept in the crates.",
                    "The furnace: it is kindled with lava from the southern trough, carried in " +
                        "one of the stone bowls kept in the central stores.",
                    "The ore: the rocks of the western cavern resent the pick. Be ready to " +
                        "defend yourself when you strike one.",
                )

        val BATTERED_SPREADS: List<Spread> by lazy {
            paginate(listOf(INTRODUCTION, BATTERED_SPINE, INSTRUCTIONS, MAINTENANCE))
        }

        val SLASHED_SPREADS: List<Spread> by lazy {
            paginate(listOf(INTRODUCTION, INSTRUCTIONS, MAINTENANCE))
        }

        fun paginate(chapters: List<Pair<String, List<String>>>): List<Spread> {
            val lines = mutableListOf<String>()
            for ((heading, paragraphs) in chapters) {
                if (lines.size % LINES_PER_PAGE != 0) {
                    repeat(LINES_PER_PAGE - lines.size % LINES_PER_PAGE) { lines += "" }
                }
                lines += heading
                lines += ""
                for (paragraph in paragraphs) {
                    lines += wrap(paragraph)
                    lines += ""
                }
            }
            val pages = lines.chunked(LINES_PER_PAGE)
            val padded = if (pages.size % 2 == 0) pages else pages + listOf(emptyList())
            return padded.chunked(2).map { it[0] to it[1] }
        }

        fun wrap(text: String): List<String> {
            val lines = mutableListOf<String>()
            var line = StringBuilder()
            for (word in text.split(' ')) {
                if (line.isNotEmpty() && line.length + 1 + word.length > WRAP_WIDTH) {
                    lines += line.toString()
                    line = StringBuilder()
                }
                if (line.isNotEmpty()) line.append(' ')
                line.append(word)
            }
            if (line.isNotEmpty()) lines += line.toString()
            return lines
        }
    }
}

internal typealias Spread = Pair<List<String>, List<String>>
