package org.rsmod.content.quest.area.mortton.shades

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import jakarta.inject.Inject
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.APOTHECARY_XP
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.ASHES
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.ASHES_VIAL
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.DIARY
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.SERUM_207
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_MADE_SERUM
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_READ_DIARY
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.TARROMIN
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.TARROMIN_UNF
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.VIAL_EMPTY
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.VIAL_WATER
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Herbi Flax's diary and the house he left it in: the shelf that holds the diary, the smashed
 * table with his last herbs, the sink next door, and the serum his notes describe. The Varrock
 * Apothecary pays once in Herblore experience for a look at the diary.
 */
class HerbiFlaxDiary @Inject constructor(private val shades: ShadesOfMorttonQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(SHELF) { searchShelf() }
        onOpLoc5(SMASHED_TABLE) { searchTable() }
        onOpLoc1(SIGNPOST) { mes("The signpost reads: 'Mort'ton. Beware the Shades!'") }
        onOpLocU(SINK, VIAL_EMPTY) { fillVials() }
        onOpHeld1(DIARY) { readDiary() }
        onOpHeldU(TARROMIN_UNF, ASHES) { makeSerum(TARROMIN_UNF, ASHES) }
        onOpHeldU(ASHES_VIAL, TARROMIN) { makeSerum(ASHES_VIAL, TARROMIN) }
        onOpHeldU(VIAL_WATER, ASHES) { mixAshes() }
        onOpNpcU(APOTHECARY, DIARY) { showApothecary(it.npc) }
    }

    private suspend fun ProtectedAccess.searchShelf() {
        anim(SEARCH_SEQ)
        delay(1)
        if (DIARY in inv) {
            mes("You find nothing useful here.")
            return
        }
        if (inv.isFull()) {
            mes("You find an interesting looking book, but you have no room to take it.")
            return
        }
        invAdd(inv, DIARY)
        objbox(DIARY, "You find an interesting looking book on the shelf.")
    }

    private suspend fun ProtectedAccess.searchTable() {
        anim(SEARCH_SEQ)
        delay(1)
        if (shades.searchedTable.get(player) || inv.freeSpace() < 2) {
            mes("You search the table but find nothing.")
            return
        }
        shades.searchedTable.set(player, true)
        invAdd(inv, GRIMY_TARROMIN, 2)
        invAdd(inv, GRIMY_ROGUES_PURSE)
        doubleobjbox(GRIMY_TARROMIN, GRIMY_ROGUES_PURSE, "You find a selection of herbs.")
    }

    private suspend fun ProtectedAccess.fillVials() {
        while (VIAL_EMPTY in inv) {
            anim(FILL_SEQ)
            soundSynth(FILL_SOUND)
            invReplace(inv, VIAL_EMPTY, 1, VIAL_WATER)
            mes("You fill the vial with water.")
            delay(2)
        }
    }

    private suspend fun ProtectedAccess.readDiary() {
        if (shades.stage(player) == 0) {
            if (!shades.meetsRequirements(player)) {
                mes("You need to have completed Priest in Peril to start this quest.")
                return
            }
            var start = false
            startDialogue { start = startQuestPrompt(shades.quest) }
            if (!start) {
                return
            }
            shades.advanceTo(this, STAGE_READ_DIARY)
            ifClose()
        }
        read()
    }

    private suspend fun ProtectedAccess.mixAshes() {
        if (!knowsRecipe()) {
            return
        }
        anim(MIX_SEQ)
        soundSynth(MIX_SOUND)
        invDel(inv, VIAL_WATER)
        invDel(inv, ASHES)
        invAdd(inv, ASHES_VIAL)
        mes("You add the ashes to the vial of water.")
    }

    private suspend fun ProtectedAccess.makeSerum(base: String, ingredient: String) {
        if (!knowsRecipe()) {
            return
        }
        if (statBase("stat.herblore") < SERUM_LEVEL) {
            mesbox("You need a Herblore level of $SERUM_LEVEL to make this potion.")
            return
        }
        while (base in inv && ingredient in inv) {
            anim(MIX_SEQ)
            soundSynth(MIX_SOUND)
            invDel(inv, base)
            invDel(inv, ingredient)
            invAdd(inv, SERUM_207[2])
            statAdvance("stat.herblore", SERUM_XP)
            mes("You make serum 207.")
            shades.advanceTo(this, STAGE_MADE_SERUM)
            delay(2)
        }
    }

    private fun ProtectedAccess.knowsRecipe(): Boolean {
        if (shades.stage(player) >= STAGE_READ_DIARY || shades.isComplete(player)) {
            return true
        }
        mes("Nothing interesting happens.")
        return false
    }

    private suspend fun ProtectedAccess.showApothecary(apothecary: Npc) {
        startDialogue(apothecary) {
            chatPlayer(quiz, "Could you take a look at this diary?")
            if (shades.apothecaryBonus.get(player)) {
                chatNpc(neutral, "I've already read it, thank you. Fascinating work.")
                return@startDialogue
            }
            chatNpc(
                happy,
                "Herbi Flax! I remember him, a fine scholar. Let me see... Senithiline process, " +
                    "fyreneght... Ah, I see what he was trying to do.",
            )
            chatNpc(
                happy,
                "Let me show you a thing or two about these solutions, it may help you in your " +
                    "own brewing.",
            )
            shades.apothecaryBonus.set(player, true)
            access.statAdvance("stat.herblore", APOTHECARY_XP)
            mesbox("The Apothecary explains some of Herbi Flax's techniques to you.")
        }
    }

    /**
     * The book's page arrows are pause buttons; the client ignores further presses until the
     * interface is sent again, so every turn re-opens the book at the new spread.
     */
    private suspend fun ProtectedAccess.read() {
        val spreads = layout(PAGES)
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

    private fun ProtectedAccess.openSpread(spreads: List<Pair<List<String>, List<String>>>, spread: Int) {
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
        ifSetText("component.book:page_left_number", (FIRST_PAGE + spread * 2).toString())
        ifSetText("component.book:page_right_number", (FIRST_PAGE + spread * 2 + 1).toString())
        ifSetHide(PAGE_LEFT, spread == 0)
        ifSetHide(PAGE_RIGHT, spread == spreads.lastIndex)
        soundSynth(PAGE_SOUND)
    }

    private companion object {
        const val SHELF = "loc.shades_experimentshelf"
        const val SMASHED_TABLE = "loc.shades_experimenttable"
        const val SIGNPOST = "loc.mortton_signpost"
        const val SINK = "loc.mortton_sink"
        const val APOTHECARY = "npc.apothecary"

        const val GRIMY_TARROMIN = "obj.unidentified_tarromin"
        const val GRIMY_ROGUES_PURSE = "obj.unidentified_rogues_purse"

        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val FILL_SEQ = "seq.human_pickuptable"
        const val FILL_SOUND = "synth.vial_mix"
        const val MIX_SEQ = "seq.human_herbing_vial"
        const val MIX_SOUND = "synth.vial_mix"

        const val SERUM_LEVEL = 15
        const val SERUM_XP = 50.0

        const val BOOK_INTERFACE = "interface.book"
        const val BOOK_INIT_SCRIPT = 2632
        const val PAGE_LEFT = "component.book:page_left_button"
        const val PAGE_RIGHT = "component.book:page_right_button"
        const val PAGE_SOUND = "synth.turn_book_page"
        const val LINES_PER_PAGE = 15
        const val WRAP_WIDTH = 26
        const val FIRST_PAGE = 11
        const val TITLE = "Diary of Herbi Flax"

        /** Herbi Flax's surviving pages, 11 to 25; each entry is one page of the diary. */
        val PAGES =
            listOf(
                listOf(
                    "Some local townsfolk are worried about the mists coming from the North East. " +
                        "Ordered more fire logs from Razmire's shop, must find a more reliable " +
                        "source of fyreneght. That money grabbing Razmire didn't know what I was " +
                        "talking about, had to explain they were ashes!",
                    "Herbs like harralander, marrentil and tarromin in short supply. I'm hoping " +
                        "the latter will make solution 194 the best yet.",
                ),
                listOf(
                    "Not much happening today, though the stench from the east increases.",
                    "The people of Mort'ton seem very depressed. Noticed several townsfolk just " +
                        "staring blankly into the air.",
                    "Temple is most likely early pagan and devoted to elemental power.",
                ),
                listOf(
                    "The townsfolk are quite short in temper and fight readily amongst themselves.",
                    "Temple was almost surely made of limestone bricks and shows signs of heat " +
                        "damage in the centre.",
                    "Samples from Mort Myre appear inconclusive, more testing needed.",
                ),
                listOf(
                    "Horror of horrors! The local tradesman Razmire has started charging higher " +
                        "prices for everyday consumables.",
                    "First test vials with Guam not successful. Senithiline process with water " +
                        "and tarromin proving successful.",
                ),
                listOf(
                    "Temple to the North, is known to the locals as 'Flamtaer' holds an " +
                        "interesting story.",
                    "Local superstition talks about pagan rites and a sacred flame which could " +
                        "'hold for permanence'.",
                    "Also have reports of shadowy creatures being seen! Such utter nonsense!",
                ),
                listOf(
                    "The mists from Mort Myre are getting worse. Victims of the effect become " +
                        "vacant and unintelligible. No known cure at this stage.",
                    "Heard some further superstitious nonsense regarding a 'sacred-flame' where " +
                        "Flamtaer stood.",
                ),
                listOf(
                    "A small breakthrough!",
                    "A patient only slightly afflicted had a short recovery with solution 194, " +
                        "but this lasted only a few minutes.",
                    "This new solution might last longer with fyreneght, but I must pack and " +
                        "leave soon before the affliction gets to me.",
                ),
                listOf(
                    "It's too late! I already show the signs of the affliction.",
                    "Ulsquire makes me laugh, he believes the pagan flame may still have power.",
                ),
                listOf(
                    "Must write this quickly as it's hard to concentrate. Solution 198 was a " +
                        "change to 194 with added Snake-weed. Solution 194 was still the strongest.",
                ),
                listOf(
                    "Possible last entry... solution 194 revised to 207 which gives longer term " +
                        "but still not permanent relief from the affliction.",
                    "Senithelene works best with added fyrneght... agitation and warmth should " +
                        "seal this..",
                    "Laughing at Ulsquire before but his suggestions may have merit.",
                ),
                listOf("Ffssiies urgsl sikflv dfdsf....", "urgll splats fysi rsil"),
                listOf("raditz ufus spokes me too", "wets lands in deis tinker"),
                emptyList(),
                emptyList(),
                listOf(
                    "<u>Index of Solution content value (ScV)</u>",
                    "Water ScV(100)",
                    "Tarromin ScV(94)",
                    "Fyrneght ScV(13)",
                    "Water+Tarromin ScV(194)",
                    "Fyrneght+Water ScV(113)",
                ),
            )

        /** Each diary entry starts a fresh page; an entry longer than a page runs onto the next. */
        fun layout(pages: List<List<String>>): List<Pair<List<String>, List<String>>> {
            val rendered =
                pages.flatMap { paragraphs ->
                    val lines = paragraphs.flatMap { wrap(it) + "" }.dropLastWhile { it.isEmpty() }
                    if (lines.isEmpty()) listOf(emptyList()) else lines.chunked(LINES_PER_PAGE)
                }
            val padded = if (rendered.size % 2 == 0) rendered else rendered + listOf(emptyList())
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
