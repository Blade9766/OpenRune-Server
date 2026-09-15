package org.rsmod.content.quest.area.baxtorianfalls.waterfall

import dev.openrune.rscm.RSCM
import jakarta.inject.Inject
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.BOOK
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.STAGE_MET_HUDON
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.WaterfallQuest.Companion.STAGE_READ_BOOK
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The bookcase upstairs in the tourist centre and the book it hides. The book only turns up once
 * the player has met Hudon, and reading it is what sends them after Glarial's pebble.
 */
class BookOnBaxtorian
@Inject
constructor(private val waterfall: WaterfallQuest, private val objRepo: ObjRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(BOOKCASE) { searchBookcase() }
        onOpHeld1(BOOK) { readBook() }
    }

    private suspend fun ProtectedAccess.searchBookcase() {
        anim(SEARCH_SEQ)
        val stage = waterfall.stage(player)
        if (stage < STAGE_MET_HUDON) {
            mes("You search the books...")
            delay(1)
            mes(UNINTERESTING.random())
            return
        }
        if (player.inv.contains(BOOK)) {
            mes("You search the bookcase but find nothing of interest.")
            return
        }
        invAddOrDrop(objRepo, BOOK)
        objbox(BOOK, "Tucked away on the bookcase you find a volume titled 'Book on Baxtorian'.")
    }

    /**
     * The book's page arrows are pause buttons. The server queues the book to close when one is
     * pressed, and the client ignores further presses until the interface is sent again, so every
     * turn re-opens the book at the new spread.
     */
    private suspend fun ProtectedAccess.readBook() {
        var spread = 0
        openSpread(spread)
        if (waterfall.stage(player) == STAGE_MET_HUDON) {
            waterfall.advanceTo(this, STAGE_READ_BOOK)
        }
        while (true) {
            val input = pauseButton()
            val turned =
                when (input.component) {
                    PAGE_LEFT -> spread - 1
                    PAGE_RIGHT -> spread + 1
                    else -> spread
                }
            if (turned in SPREADS.indices) {
                spread = turned
            }
            openSpread(spread)
        }
    }

    private fun ProtectedAccess.openSpread(spread: Int) {
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
        showSpread(spread)
        soundSynth(PAGE_SOUND)
    }

    private fun ProtectedAccess.showSpread(spread: Int) {
        val (left, right) = SPREADS[spread]
        for (line in 1..LINES_PER_PAGE) {
            ifSetText("component.book:page_left_text_$line", left.getOrElse(line - 1) { "" })
            ifSetText("component.book:page_right_text_$line", right.getOrElse(line - 1) { "" })
        }
        ifSetText("component.book:page_left_number", (spread * 2 + 1).toString())
        ifSetText("component.book:page_right_number", (spread * 2 + 2).toString())
        ifSetHide(PAGE_LEFT, spread == 0)
        ifSetHide(PAGE_RIGHT, spread == SPREADS.lastIndex)
    }

    private companion object {
        const val BOOKCASE = "loc.bookcase_waterfall_quest"
        const val BOOK_INTERFACE = "interface.book"
        const val BOOK_INIT_SCRIPT = 2632
        const val TITLE = "Book on Baxtorian"
        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val PAGE_SOUND = "synth.turn_book_page"
        const val LINES_PER_PAGE = 15
        const val WRAP_WIDTH = 26

        const val PAGE_LEFT = "component.book:page_left_button"
        const val PAGE_RIGHT = "component.book:page_right_button"

        val UNINTERESTING =
            listOf(
                "Nothing here looks worth reading.",
                "You find nothing that interests you.",
                "None of them catch your eye.",
            )

        val CHAPTERS =
            listOf(
                "<u>The Lost Relics</u>" to
                    listOf(
                        "When the elves left these lands at the close of the Fourth Age, much of " +
                            "their history went with them. Most mourned of all are the hidden " +
                            "treasures of Baxtorian.",
                        "Some say the treasures still lie where he left them. Most scholars " +
                            "believe dwarven miners carried them off long ago.",
                        "Also lost is Glarial's pebble, the key that let her kin visit her tomb. " +
                            "A gnome family took it more than a century past, and their " +
                            "descendants may still keep it in their cave beneath the Tree Gnome " +
                            "Village.",
                    ),
                "<u>Baxtorian and Glarial</u>" to
                    listOf(
                        "For over a hundred years Baxtorian and Glarial lived together in peace, " +
                            "studying and teaching the ways of nature.",
                        "When war came to their western home, Baxtorian marched away to fight. " +
                            "He returned to find his people dead and his queen taken.",
                        "He searched for her for many years before retreating to the home he had " +
                            "made for her beneath the falls. He went inside and never came out.",
                        "Only the king and queen could pass into the waterfall, and none have " +
                            "entered since. Nature itself seems to guard his rest.",
                    ),
                "<u>Masters of Nature</u>" to
                    listOf(
                        "At their word forests grew, hills rose and rivers burst their banks.",
                        "Baxtorian's mastery of rune lore was unmatched. It was said that with " +
                            "the right stones he could bend water, earth and air to his will.",
                    ),
                "<u>A Verse by Baxtorian</u>" to
                    listOf(
                        "The book ends with a short poem in Baxtorian's own hand. In it he " +
                            "counts every treasure worthless beside his queen, and swears to " +
                            "hold her memory close even as his kingdom crumbles to dust.",
                    ),
            )

        /** Every page's lines, two pages to a spread. */
        val SPREADS: List<Pair<List<String>, List<String>>> by lazy {
            val lines = mutableListOf<String>()
            for ((heading, paragraphs) in CHAPTERS) {
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
            padded.chunked(2).map { it[0] to it[1] }
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
