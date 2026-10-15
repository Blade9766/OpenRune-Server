package org.rsmod.content.quest.area.lighthouse.horrorfromthedeep

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.DIARY
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.JOURNAL
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.MANUAL
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The three books from Jossik's bookcase: the council's manual for the lighting mechanism, Jossik's
 * journal, and the diary of his uncle Silas, which hides the key to the strange wall.
 */
class LighthouseBooks : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(MANUAL) { read(MANUAL_TITLE, MANUAL_CHAPTERS) }
        onOpHeld1(JOURNAL) { read(JOURNAL_TITLE, JOURNAL_CHAPTERS) }
        onOpHeld1(DIARY) { read(DIARY_TITLE, DIARY_CHAPTERS) }
    }

    /**
     * The book's page arrows are pause buttons. The server queues the book to close when one is
     * pressed, and the client ignores further presses until the interface is sent again, so every
     * turn re-opens the book at the new spread.
     */
    private suspend fun ProtectedAccess.read(title: String, chapters: List<Pair<String, List<String>>>) {
        val spreads = layout(chapters)
        var spread = 0
        openSpread(title, spreads, spread)
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
            openSpread(title, spreads, spread)
        }
    }

    private fun ProtectedAccess.openSpread(title: String, spreads: List<Pair<List<String>, List<String>>>, spread: Int) {
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
        ifSetText("component.book:title", title)
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

    private companion object {
        const val BOOK_INTERFACE = "interface.book"
        const val BOOK_INIT_SCRIPT = 2632
        const val PAGE_LEFT = "component.book:page_left_button"
        const val PAGE_RIGHT = "component.book:page_right_button"
        const val PAGE_SOUND = "synth.turn_book_page"
        const val LINES_PER_PAGE = 15
        const val WRAP_WIDTH = 26

        const val MANUAL_TITLE = "Lightomatic Deluxe 500"
        const val JOURNAL_TITLE = "Jossik's Journal"
        const val DIARY_TITLE = "Diary"

        val MANUAL_CHAPTERS =
            listOf(
                "<u>Congratulations!</u>" to
                    listOf(
                        "The Council thanks you for keeping the coast safe. Your Lightomatic " +
                            "Deluxe 500 needs little care, but please read this guide before " +
                            "touching it.",
                    ),
                "<u>Daily Care</u>" to
                    listOf(
                        "Keep the wind vanes free of gulls. Oil the gears once a week.",
                        "The torch burns on a coat of swamp tar. When the flame dies, spread " +
                            "fresh tar over the torch and light it with a tinderbox.",
                    ),
                "<u>Troubleshooting</u>" to
                    listOf(
                        "Q: The light has gone dark.",
                        "A: Re-coat the torch in swamp tar and relight it.",
                        "Q: The beam is weak or scattered.",
                        "A: The lens is cracked. Pour molten glass into the lens frame to mend it.",
                        "Q: Something is scratching at the basement door.",
                        "A: The Council recommends that keepers do not use the basement.",
                    ),
            )

        val JOURNAL_CHAPTERS =
            listOf(
                "<u>A New Post</u>" to
                    listOf(
                        "The Council has made me keeper of the lighthouse, since nobody has seen " +
                            "my uncle Silas for months. Larrissa says I will be lonely out " +
                            "here. She visits often enough that I doubt it.",
                    ),
                "<u>Voices</u>" to
                    listOf(
                        "Some nights I hear something below the floor. At first I blamed the " +
                            "waves in the rocks. The waves do not whisper.",
                        "I have found my uncle's diary among his things. Most of it is nonsense, " +
                            "but he writes a great deal about a door in the basement.",
                    ),
                "<u>Last Entry</u>" to
                    listOf(
                        "I think I understand the strange wall at last. Tonight I will open it " +
                            "and see what my uncle was so afraid of. If Larrissa reads this, " +
                            "please do not worry.",
                    ),
            )

        val DIARY_CHAPTERS =
            listOf(
                "<u>Silas</u>" to
                    listOf(
                        "They come up out of the black water. Grey shells, too many teeth. The " +
                            "old fishermen called them dagannoth and I laughed at them. I do not " +
                            "laugh now.",
                    ),
                "<u>The Door</u>" to
                    listOf(
                        "I have sealed the cellar with a wall of iron that only opens for one " +
                            "who knows the key. The key is no key at all.",
                        "White of the wind, blue of the sea, brown of the earth and red of the " +
                            "flame. The yellow blade of the warrior and the green shaft of the " +
                            "hunter. Give it all six and it will open.",
                    ),
                "<u>The Mother</u>" to
                    listOf(
                        "The little ones are nothing. It is the great one I fear. She wears " +
                            "every colour in turn, and while she wears one, only that one can " +
                            "hurt her.",
                        "Wind, sea, earth, flame, blade, arrow. Wind, sea, earth, flame, blade, " +
                            "arrow. I cannot stop hearing it.",
                    ),
            )

        fun layout(chapters: List<Pair<String, List<String>>>): List<Pair<List<String>, List<String>>> {
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
