package org.rsmod.content.quest.area.ardougne.regicide

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.BOOK
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.IORWERTH_MESSAGE
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.KINGS_MESSAGE
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_LETTER_UNSEALED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The quest's papers: the King's summons, the Big Book of Bangs and Lord Iorwerth's letter. The
 * letter stays sealed until Arianwyn opens it; after that it can be read again at any time.
 */
class RegicideReading @Inject constructor(private val regicide: RegicideQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(KINGS_MESSAGE) { readSummons() }
        onOpHeld1(BOOK) { readBook() }
        onOpHeld1(IORWERTH_MESSAGE) { readLetter() }
    }

    private suspend fun ProtectedAccess.readSummons() {
        mesbox(summonsText(player.displayName))
    }

    private suspend fun ProtectedAccess.readLetter() {
        if (regicide.stage(player) < STAGE_LETTER_UNSEALED && !regicide.isComplete(player)) {
            mes("The letter is sealed with Lord Iorwerth's seal. It is not addressed to you.")
            return
        }
        showLetter(this)
    }

    private suspend fun ProtectedAccess.readBook() {
        player.readBook = 1
        var spread = 0
        openSpread(spread)
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

    companion object {
        const val BOOK_INTERFACE = "interface.book"
        const val BOOK_INIT_SCRIPT = 2632
        const val TITLE = "The Big Book of Bangs"
        const val PAGE_SOUND = "synth.turn_book_page"
        const val LINES_PER_PAGE = 15
        const val WRAP_WIDTH = 26
        const val PAGE_LEFT = "component.book:page_left_button"
        const val PAGE_RIGHT = "component.book:page_right_button"

        fun summonsText(name: String): String =
            "Squire $name.<br><br>You are needed to serve the Kingdom of Kandarin. The Well of Voyage " +
                "has been reopened. We must discuss your next commission.<br><br>" +
                "<col=ff0000>His Majesty, King Lathas</col>"

        fun letterText(isFemale: Boolean): String =
            "King Lathas<br><br>Your ${if (isFemale) "woman" else "man"} did well. We are a step closer " +
                "to welcoming the Dark Lord into this realm. You will yet live to see Camelot " +
                "crushed under foot.<br><br>Lord Iorwerth"

        /** Shows the unsealed letter and remembers that it has been read. */
        suspend fun showLetter(access: ProtectedAccess) {
            access.player.readMessage = 1
            access.mesbox(letterText(access.player.appearance.bodyType != Constants.bodytype_a))
        }

        private val SECTIONS =
            listOf(
                "<u>Introduction</u>" to
                    "In my travels I have seen many explosive creations. One of particular note is " +
                    "fire oil, used in the far east, which sticks to anything it touches. These " +
                    "pages list what is needed to make it, and how it is put together.",
                "<u>Quicklime</u>" to
                    "Made by heating limestone in a furnace. Wear gloves when handling it, crush " +
                    "it before use, and keep it in a pot.",
                "<u>Sulphur</u>" to
                    "Yellow crystals found by poisoned waters. Like quicklime, it should be " +
                    "crushed before use.",
                "<u>Naphtha</u>" to "Drawn off coal tar in a fractionalising still.",
                "<u>Preparation</u>" to
                    "The quicklime, sulphur and naphtha can be mixed in any order. A large " +
                    "container, such as a barrel, is recommended for holding them. Once mixed, " +
                    "keep it well away from any flame, and give it a fuse of some sort.",
            )

        val SPREADS: List<Pair<List<String>, List<String>>> by lazy {
            val pages = SECTIONS.flatMap { (title, body) -> (listOf(title, "") + wrap(body)).chunked(LINES_PER_PAGE) }
            pages.chunked(2).map { it[0] to it.getOrElse(1) { emptyList() } }
        }

        private fun wrap(text: String): List<String> {
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
