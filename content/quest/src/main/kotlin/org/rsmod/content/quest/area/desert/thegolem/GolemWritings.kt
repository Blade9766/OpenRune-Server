package org.rsmod.content.quest.area.desert.thegolem

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import jakarta.inject.Inject
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.LETTER
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.NOTES
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.SIDE_ASKED_ELISSA
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.SIDE_READ_LETTER
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.SIDE_READ_NOTES
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.STAGE_REPAIRED
import org.rsmod.content.quest.area.karamja.shilovillage.readScroll
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Elissa's letter to Varmen, found in the ruins of Uzer, and Varmen's expedition notes, shelved
 * in the south-east bookcase of the Exam Centre. Reading each moves the statuette trail on.
 */
class GolemWritings
@Inject
constructor(private val golem: TheGolemQuest, private val objRepo: ObjRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(LETTER) { readLetter() }
        onOpHeld1(NOTES) { readNotes() }
        onOpLoc1(BOOKCASE) { searchBookcase() }
    }

    private fun ProtectedAccess.readLetter() {
        readScroll("Letter", LETTER_TEXT)
        if (golem.stage(player) >= STAGE_REPAIRED && player.golemSide < SIDE_READ_LETTER) {
            player.golemSide = SIDE_READ_LETTER
        }
    }

    private suspend fun ProtectedAccess.searchBookcase() {
        arriveDelay()
        anim(SEARCH_SEQ)
        mes("You search the bookcase")
        if (golem.stage(player) < STAGE_REPAIRED || player.golemSide < SIDE_ASKED_ELISSA) {
            mes("You don't find anything of interest.")
            return
        }
        if (NOTES in inv) {
            mes("You don't find anything else of interest.")
            return
        }
        invAddOrDrop(objRepo, NOTES)
        objbox(NOTES, "You find Varmen's expedition notes.")
    }

    /**
     * The book's page arrows are pause buttons. The server queues the book to close when one is
     * pressed, and the client ignores further presses until the interface is sent again, so every
     * turn re-opens the book at the new spread.
     */
    private suspend fun ProtectedAccess.readNotes() {
        var spread = 0
        openSpread(spread)
        if (golem.stage(player) >= STAGE_REPAIRED && player.golemSide == SIDE_ASKED_ELISSA) {
            player.golemSide = SIDE_READ_NOTES
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
        ifSetText("component.book:title", NOTES_TITLE)
        ifSetEvents(PAGE_LEFT, -1..-1, IfEvent.PauseButton)
        ifSetEvents(PAGE_RIGHT, -1..-1, IfEvent.PauseButton)
        val (left, right) = SPREADS[spread]
        for (line in 1..LINES_PER_PAGE) {
            ifSetText("component.book:page_left_text_$line", left.getOrElse(line - 1) { "" })
            ifSetText("component.book:page_right_text_$line", right.getOrElse(line - 1) { "" })
        }
        ifSetText("component.book:page_left_number", (spread * 2 + 1).toString())
        ifSetText("component.book:page_right_number", (spread * 2 + 2).toString())
        ifSetHide(PAGE_LEFT, spread == 0)
        ifSetHide(PAGE_RIGHT, spread == SPREADS.lastIndex)
        soundSynth(PAGE_SOUND)
    }

    private companion object {
        const val BOOKCASE = "loc.golem_bookcase"
        const val SEARCH_SEQ = "seq.human_pickuptable"

        const val BOOK_INTERFACE = "interface.book"
        const val BOOK_INIT_SCRIPT = 2632
        const val PAGE_LEFT = "component.book:page_left_button"
        const val PAGE_RIGHT = "component.book:page_right_button"
        const val PAGE_SOUND = "synth.turn_book_page"
        const val LINES_PER_PAGE = 15
        const val WRAP_WIDTH = 26

        const val NOTES_TITLE = "The Ruins of Uzer"

        const val LETTER_TEXT =
            "My dearest Varmen,\n\n" +
                "I hope you are keeping well. The books you wanted are enclosed. We have had " +
                "exciting news here: a second city of the same age has turned up east of " +
                "Varrock, and a great dig is being organised. I doubt the museum can pay for " +
                "both, so I am afraid this journey of yours may have to be your last.\n\n" +
                "May Saradomin keep you safe on the road home.\n\n" +
                "All my love, Elissa."

        val ENTRIES =
            listOf(
                "<u>Septober 19</u>" to
                    listOf(
                        "The nomads spoke the truth. A whole city lies here, uncovered by the " +
                            "shifting sands after who knows how many ages. Even broken, its " +
                            "buildings are magnificent.",
                        "Odd, though: broken pottery lies everywhere, far more than a city " +
                            "this size could ever have used. We make camp tonight and survey " +
                            "properly in the morning.",
                    ),
                "<u>Septober 20</u>" to
                    listOf(
                        "The pottery explained itself. We dug a clay figure out of the sand and " +
                            "it got up and walked! A golem, dormant all this while. Its head is " +
                            "cracked and it will not speak to us.",
                        "The great kilns in several buildings suggest that, near the end, the " +
                            "whole city turned to making these creatures.",
                        "The large central building bears carvings of Saradomin, Zamorak and " +
                            "Armadyl, and one prominent symbol I do not recognise. I have written " +
                            "to Elissa for books on golems and on religious symbols.",
                    ),
                "<u>Septober 21</u>" to
                    listOf(
                        "The more we look, the plainer it is that the sand did not do this. " +
                            "These walls were pulled down by force, as if by enormous hands.",
                    ),
                "<u>Septober 22</u>" to
                    listOf(
                        "We have found stairs down into the temple's lower levels. Down there " +
                            "four fine statuettes stand in alcoves around a great door. I took " +
                            "one of them away with me. The door will not open.",
                        "The unknown symbol is everywhere below, and largest of all on the door.",
                    ),
                "<u>Septober 23</u>" to
                    listOf(
                        "The books arrived, and with them a letter from Elissa: the museum " +
                            "cannot pay for this dig as well as the new one near Varrock.",
                        "The symbol belongs to Thammaron, Zamorak's chief lieutenant in the " +
                            "godwars. This must be Uzer, the Saradominist city he attacked by " +
                            "opening a portal from his own realm into its heart. The city's " +
                            "army drove him back, and there the records stop.",
                        "That door looks made to seal just such a portal, and I am glad now " +
                            "that it stayed shut. I believe the golems were built to fight the " +
                            "demon once Uzer's soldiers were gone.",
                        "A golem is neither good nor evil. It does exactly what it is told, " +
                            "and suffers for as long as its task is left undone.",
                        "The surest way to command one was to place written words inside its " +
                            "skull: papyrus, inked with a natural dye, written with the tail " +
                            "feather of a phoenix. Such orders outrank anything spoken aloud.",
                    ),
            )

        val SPREADS: List<Pair<List<String>, List<String>>> by lazy {
            val lines = mutableListOf<String>()
            for ((heading, paragraphs) in ENTRIES) {
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
