package org.rsmod.content.quest.area.varrock.shieldofarrav

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import jakarta.inject.Inject
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.BLACKARM_CERTIFICATE
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.BLACKARM_SHIELD
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.BOOK
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.CERTIFICATE
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.CROSSBOW
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.INTEL_REPORT
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_CERTIFICATE
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_READ_BOOK
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_SHIELD
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_STARTED
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.WEAPON_STORE_KEY
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Everything in the quest that is read or inspected: the palace bookcase and the book it hides,
 * Jonny the Beard's report, the curator's certificate and its halves, the VTAM plaque and Benny's
 * Varrock Herald.
 */
class ArravReadables
@Inject
constructor(private val arrav: ShieldOfArravQuest, private val objRepo: ObjRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(BOOKCASE) { searchBookcase() }
        onOpHeld1(BOOK) { readBook() }
        onOpHeld1(INTEL_REPORT) { readReport() }
        onOpHeld1(CERTIFICATE) { readCertificate() }
        onOpHeld1(PHOENIX_CERTIFICATE) { inspectHalfCertificate() }
        onOpHeld1(BLACKARM_CERTIFICATE) { inspectHalfCertificate() }
        onOpHeldU(PHOENIX_CERTIFICATE, BLACKARM_CERTIFICATE) { combineCertificate() }
        onOpHeld1(PHOENIX_SHIELD) { inspectShield(PHOENIX_SHIELD) }
        onOpHeld1(BLACKARM_SHIELD) { inspectShield(BLACKARM_SHIELD) }
        onOpHeld4(WEAPON_STORE_KEY) {
            objbox(WEAPON_STORE_KEY, TRADEABLE_HINT.format("the key to the Phoenix Gang's weapon store"))
        }
        onOpHeld4(CROSSBOW) {
            objbox(CROSSBOW, TRADEABLE_HINT.format("a crossbow belonging to the Phoenix Gang"))
        }
        onOpLoc1(VTAM_PLAQUE) { readPlaque() }
        for (newspaper in NEWSPAPERS) {
            onOpHeld1(newspaper) { readNewspaper() }
        }
    }

    private suspend fun ProtectedAccess.searchBookcase() {
        arriveDelay()
        anim(SEARCH_SEQ)
        if (player.phoenixGang != PHOENIX_STARTED) {
            mes("A large collection of books.")
            return
        }
        if (arrav.owns(this, BOOK)) {
            mes("You already have the book about the Shield of Arrav from this bookcase.")
            return
        }
        if (!invAddOrDrop(objRepo, BOOK)) {
            return
        }
        objbox(BOOK, "You search the bookcase and find a book titled The Shield of Arrav.")
        startDialogue { chatPlayer(happy, "Aha! This is exactly the book I was after.") }
    }

    /**
     * The book's page arrows are pause buttons; the client stops sending presses until the book is
     * sent again, so every turn re-opens it at the new spread.
     */
    private suspend fun ProtectedAccess.readBook() {
        var spread = 0
        openSpread(spread)
        if (player.phoenixGang == PHOENIX_STARTED) {
            player.phoenixGang = PHOENIX_READ_BOOK
        }
        while (true) {
            val input = pauseButton()
            val turned =
                when (input.component) {
                    PAGE_LEFT -> spread - 1
                    PAGE_RIGHT -> spread + 1
                    else -> spread
                }
            if (turned in BOOK_SPREADS.indices) {
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
        ifSetText("component.book:title", BOOK_TITLE)
        ifSetEvents(PAGE_LEFT, -1..-1, IfEvent.PauseButton)
        ifSetEvents(PAGE_RIGHT, -1..-1, IfEvent.PauseButton)
        val (left, right) = BOOK_SPREADS[spread]
        for (line in 1..LINES_PER_PAGE) {
            ifSetText("component.book:page_left_text_$line", left.getOrElse(line - 1) { "" })
            ifSetText("component.book:page_right_text_$line", right.getOrElse(line - 1) { "" })
        }
        ifSetText("component.book:page_left_number", (spread * 2 + 1).toString())
        ifSetText("component.book:page_right_number", (spread * 2 + 2).toString())
        ifSetHide(PAGE_LEFT, spread == 0)
        ifSetHide(PAGE_RIGHT, spread == BOOK_SPREADS.lastIndex)
        soundSynth(PAGE_SOUND)
    }

    private fun ProtectedAccess.readReport() {
        ifOpenMainModal(SCROLL_INTERFACE)
        for (line in 1..SCROLL_LINES) {
            ifSetText("component.scroll:line$line", REPORT.getOrElse(line - 1) { "" })
        }
        soundSynth(PAPER_SOUND)
    }

    private fun ProtectedAccess.readCertificate() {
        ifOpenMainModal(CERTIFICATE_INTERFACE)
        ifSetText(CERTIFICATE_TEXT, CERTIFICATE_WORDING)
        soundSynth(PAPER_SOUND)
    }

    private suspend fun ProtectedAccess.inspectHalfCertificate() {
        if (inv.count(PHOENIX_CERTIFICATE) > 0 && inv.count(BLACKARM_CERTIFICATE) > 0) {
            val combine = choice2("Yes.", true, "No.", false, title = "Combine the two half-certificates?")
            if (combine) {
                combineCertificate()
            }
            return
        }
        objbox(
            PHOENIX_CERTIFICATE.takeIf { inv.count(it) > 0 } ?: BLACKARM_CERTIFICATE,
            TRADEABLE_HINT.format("half of a certificate for King Roald's reward"),
        )
    }

    private suspend fun ProtectedAccess.combineCertificate() {
        if (inv.count(PHOENIX_CERTIFICATE) == 0 || inv.count(BLACKARM_CERTIFICATE) == 0) {
            return
        }
        invDel(inv, PHOENIX_CERTIFICATE, 1, BLACKARM_CERTIFICATE, 1)
        invAdd(inv, CERTIFICATE)
        soundSynth(PAPER_SOUND)
        objbox(CERTIFICATE, "You fit the two halves together into a complete certificate.")
    }

    private suspend fun ProtectedAccess.inspectShield(half: String) {
        val side = if (half == PHOENIX_SHIELD) "left" else "right"
        objbox(
            half,
            "The $side half of the Shield of Arrav. The curator at the Varrock Museum should be " +
                "able to confirm it is genuine.",
        )
    }

    private suspend fun ProtectedAccess.readPlaque() {
        arriveDelay()
        ifOpenMainModal(PLAQUE_INTERFACE)
    }

    private fun ProtectedAccess.readNewspaper() {
        ifOpenMainModal(NEWSPAPER_INTERFACE)
        ifSetHide("component.newspaper:gazette_link", true)
        player.runClientScript(NEWSPAPER_CONTENT_SCRIPT, NEWSPAPER_LEFT, NEWSPAPER_RIGHT)
        soundSynth(PAPER_SOUND)
    }

    private companion object {
        const val BOOKCASE = "loc.questbookcase"
        const val VTAM_PLAQUE = "loc.qip_soa_vtam_corporation_sign"
        val NEWSPAPERS = listOf("obj.qip_soa_newspaper1", "obj.qip_soa_newspaper2")

        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val PAGE_SOUND = "synth.turn_book_page"
        const val PAPER_SOUND = "synth.paper_move"

        const val BOOK_INTERFACE = "interface.book"
        const val BOOK_INIT_SCRIPT = 2632
        const val BOOK_TITLE = "The Shield of Arrav"
        const val PAGE_LEFT = "component.book:page_left_button"
        const val PAGE_RIGHT = "component.book:page_right_button"
        const val LINES_PER_PAGE = 15
        const val WRAP_WIDTH = 26

        const val SCROLL_INTERFACE = "interface.scroll"
        const val SCROLL_LINES = 14

        const val CERTIFICATE_INTERFACE = "interface.qip_soa_arrav_shield_interface"
        const val CERTIFICATE_TEXT =
            "component.qip_soa_arrav_shield_interface:qip_soa_arrav_certificate"
        const val PLAQUE_INTERFACE = "interface.qip_soa_vtam_interface"

        const val NEWSPAPER_INTERFACE = "interface.newspaper"
        const val NEWSPAPER_CONTENT_SCRIPT = 3330

        const val TRADEABLE_HINT =
            "It's %s. If you need to give it to a player who can't trade, you can " +
                "<col=7f0000>use</col> it on them instead."

        val BOOK_CHAPTERS =
            listOf(
                "The Shield of Arrav" to
                    listOf(
                        "by A. R. Wright",
                        "Few heroes of the Fourth Age are as famous as Arrav, who stood with " +
                            "Avarrocka against the undead armies of the Mahjarrat Zemouregal.",
                        "His shield is one of the few relics of that age to survive, and for " +
                            "more than a century and a half it was the pride of the Varrock " +
                            "Museum.",
                    ),
                "The Theft" to
                    listOf(
                        "In the year 143 of the Fifth Age the Phoenix Gang raided the museum " +
                            "and carried the shield off.",
                        "King Roald II offered 1,200 gold for its return, a fortune at the " +
                            "time, hoping greed would turn one thief against the rest. None " +
                            "ever came forward.",
                    ),
                "The Split" to
                    listOf(
                        "Years later a quarrel split the Phoenix Gang, and the deserters " +
                            "founded a rival band, the Black Arm Gang.",
                        "Accounts differ as to which gang kept the shield, and nobody knows " +
                            "where it lies today. King Roald III has declared that his " +
                            "father's reward still stands.",
                    ),
            )

        val BOOK_SPREADS: List<Pair<List<String>, List<String>>> by lazy {
            val lines = mutableListOf<String>()
            for ((heading, paragraphs) in BOOK_CHAPTERS) {
                if (lines.size % LINES_PER_PAGE != 0) {
                    repeat(LINES_PER_PAGE - lines.size % LINES_PER_PAGE) { lines += "" }
                }
                lines += "<u>$heading</u>"
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

        val REPORT =
            listOf(
                "<u>Intelligence Report</u>",
                "",
                "A woman with a pick and shovel has been",
                "seen loitering by the statue outside the",
                "city walls. Is something buried beneath it?",
                "",
                "The museum is paying for a new channel and",
                "a barge down at the Digsite. What do they",
                "know that we don't?",
            )

        const val CERTIFICATE_WORDING =
            "The bearer of this certificate has returned both halves of the Shield of Arrav " +
                "to me, Haig Halen, Curator of the Varrock Museum. Having examined the shield I " +
                "am satisfied that it is genuine, and I ask His Majesty King Roald III to pay " +
                "the bearer the reward proclaimed by King Roald II in the year 143 of the Fifth " +
                "Age."

        const val NEWSPAPER_LEFT =
            "<u>A New Look for Varrock</u><br><br>Varrock has been given a thorough " +
                "makeover. King Roald says a tidier capital will bring in more visitors than " +
                "any royal decree, and urges everyone to see the refurbished museum."

        const val NEWSPAPER_RIGHT =
            "<u>Obituaries</u><br>Goblin<br>Giant rat<br>Unicorn<br>Varrock guard<br>" +
                "Varrock guard<br>Bear<br><br><u>Classifieds</u><br>Lowe's Archery Emporium: " +
                "the best ranged weapons in town.<br><br>Dressing up? The Fancy Dress Shop " +
                "has every outfit you need.<br><br>The Dancing Donkey: cold beer always on " +
                "tap."

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
