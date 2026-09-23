package org.rsmod.content.quest.area.karamja.legendsquest

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import jakarta.inject.Inject
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpObj3
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BOOK_OF_BINDING
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.ENCHANTED_VIAL
import org.rsmod.game.obj.Obj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Book of Binding, Eximus's treatise on demons, which the seven gems conjure in the cavern of
 * pools. Held open before the possessed Ungadulu it drives Nezikchened out of him, and its last
 * page is an enchantment that readies empty vials for holy water.
 */
class BookOfBinding @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpObj3(objType(BOOK_OF_BINDING)) { takeBook(it.obj) }
        onOpHeld1(BOOK_OF_BINDING) { readBook() }
        onOpHeld2(BOOK_OF_BINDING) { enchantVials() }
    }

    /** Taking the conjured book spends the gems, so another copy means placing all seven again. */
    private suspend fun ProtectedAccess.takeBook(book: Obj) {
        if (inv.isFull()) {
            mes("You don't have enough inventory space.")
            return
        }
        if (coords != book.coords) {
            delay(1)
        }
        anim(TAKE_SEQ)
        if (!objRepo.del(book)) {
            return
        }
        if (player.legendsGems == ALL_GEMS) {
            player.legendsGems = 0
        }
        invAdd(inv, BOOK_OF_BINDING, 1)
    }

    /**
     * The book's page arrows are pause buttons; every turn re-opens it at the new spread, as the
     * client ignores further presses until the interface is sent again.
     */
    private suspend fun ProtectedAccess.readBook() {
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

    private suspend fun ProtectedAccess.enchantVials() {
        objbox(BOOK_OF_BINDING, "You prepare an incantation from the page...")
        if (player.magicLvl < REQUIRED_LEVEL) {
            mesbox("You need a Magic level of at least 10 to cast this enchantment.")
            return
        }
        if (player.prayerLvl < REQUIRED_LEVEL) {
            mesbox("You need a Prayer level of at least 10 to cast this enchantment.")
            return
        }
        val vials = minOf(MAX_VIALS, inv.count(EMPTY_VIAL))
        if (vials == 0) {
            mesbox("However, you don't have the right components to cast this spell.")
            return
        }
        val wanted =
            when {
                vials == 1 -> 1
                vials == 2 -> choice2("Enchant 1 Vial", 1, "Enchant 2 Vials", 2)
                else -> {
                    val half = vials / 2 + 1
                    choice3("Enchant 1 Vial", 1, "Enchant $half Vials", half, "Enchant $vials Vials", vials)
                }
            }
        ifClose()
        val affordable = minOf((stat("stat.prayer") - 5) / 5, (stat("stat.magic") - 5) / 5)
        val count = minOf(wanted, affordable)
        if (count <= 0) {
            mesbox("You are too drained to cast this enchantment.")
            return
        }
        anim(CAST_SEQ)
        soundSynth(ENCHANT_SOUND)
        delay(1)
        statSub("stat.prayer", 5 * count, 0)
        statSub("stat.magic", 5 * count, 0)
        invDel(inv, EMPTY_VIAL, count)
        invAdd(inv, ENCHANTED_VIAL, count)
        objbox(ENCHANTED_VIAL, if (count == 1) "You enchant a vial!" else "You enchant some vials!")
    }

    private companion object {
        const val ALL_GEMS = 127
        const val REQUIRED_LEVEL = 10
        const val MAX_VIALS = 10
        const val EMPTY_VIAL = "obj.vial_empty"

        const val BOOK_INTERFACE = "interface.book"
        const val BOOK_INIT_SCRIPT = 2632
        const val TITLE = "Book of Binding"
        const val LINES_PER_PAGE = 15
        const val WRAP_WIDTH = 26
        const val PAGE_LEFT = "component.book:page_left_button"
        const val PAGE_RIGHT = "component.book:page_right_button"
        const val PAGE_SOUND = "synth.turn_book_page"
        const val ENCHANT_SOUND = "synth.godspell_charge"
        const val TAKE_SEQ = "seq.human_pickupfloor"
        const val CAST_SEQ = "seq.human_casting"

        val PAGES =
            listOf(
                listOf(
                    "<u>A treatise on Demons</u>",
                    "",
                    "-- Indexo --",
                    "",
                    "Arcana : I",
                    "Instructo : II",
                    "Defeati : III",
                    "Enchanto : IIII",
                ),
                wrapAll(
                    "<u>Arcana I</u>",
                    "Use holy water to determine possession. Slight appearance changes may be perceived when doused.",
                    "Legendary Silverlight will help to defeat any demon by weakening it.",
                    "<u>Arcana II</u>",
                    "Be wary of any demon, it may have special forms of attack.",
                    "Use an Octagram of Fire to confine unearthly creatures of the underworld - the perfect geometry confuses them.",
                    "Eximus",
                ),
                wrapAll(
                    "<u>Instructo</u>",
                    "Creation of holy water must be undertaken with determination and urgency.",
                    "Take to yourself empty vials free of all liquids.",
                    "Read warily the enchantment contained herewithin in order to magick the vial for the holding of holy or sacred water.",
                    "Take utmost care as you enchant them. With great care and precision place the sacred water into a magicked vial and stopper it.",
                    "Eximus",
                ),
                wrapAll(
                    "<u>Defeati</u>",
                    "The dreaded demon will be of unholy power and abilities.",
                    "Present thyself before the possessed with good intent and ready manner.",
                    "With least obstruction and utmost solemnity hold open the pages of this great tome in order that the goodlight falls upon the victim completely.",
                    "Be thee prepared in every capacity, for the demon's tricks and wiles will swiftly outwit the unready adventurer.",
                    "Attack with vigour and zest if thee hopes to see another day.",
                    "Eximus",
                ),
                wrapAll(
                    "<u>Enchanto</u>",
                    "Possessus valius emptious, projectus spellicus avoir valius magicus.",
                    "Castus enchanto avoir createur valius magicus holious avour defeati Demonicus Absolutus.",
                    "Extralias projectus Magicus Holarius",
                    "Attackanie Demonicus Absolutus distancie airus throwus armiues.",
                    "Eximus",
                ),
            )

        val SPREADS: List<Pair<List<String>, List<String>>> by lazy {
            val pages = PAGES.flatMap { it.chunked(LINES_PER_PAGE) }
            val padded = if (pages.size % 2 == 0) pages else pages + listOf(emptyList())
            padded.chunked(2).map { it[0] to it[1] }
        }

        fun wrapAll(vararg paragraphs: String): List<String> =
            paragraphs.flatMap { wrap(it) + "" }.dropLast(1)

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
