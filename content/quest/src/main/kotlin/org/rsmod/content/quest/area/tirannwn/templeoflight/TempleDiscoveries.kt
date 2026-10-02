package org.rsmod.content.quest.area.tirannwn.templeoflight

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import jakarta.inject.Inject
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.content.quest.area.tirannwn.mourningsend.swap
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.CHARGED_CRYSTAL
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.CHISEL
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.COLOUR_WHEEL
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.JOURNAL
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.NEW_CRYSTAL
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.NOTES
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.SAMPLE
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.STAGE_CRYSTAL
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.STAGE_FOUND
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.STAGE_KEY
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.STAGE_RESTORED
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * What the player finds at the temple: the dig team lying dead at its entrance (the first sight of
 * them moves the quest on), Edern's journal on the guard outside, the colour wheel and notes on
 * the guard by the crystal dispenser, the black crystal on the top floor, and the Death Altar,
 * which charges the new crystal.
 *
 * Restoring the safeguards is one commit: the charged crystal leaves the pack, the player's own
 * `varbit.mourning_light_temple_safe_guards` turns their view of the black crystal into the
 * repowered one, and the stage moves on, all in the same tick. Nobody else's crystal changes.
 */
class TempleDiscoveries
@Inject
constructor(private val quest: MourningsEndPart2Quest, private val launcher: ProtectedAccessLauncher) : PluginScript() {

    override fun ScriptContext.startup() {
        onPlayerCoordsChanged {
            if (atDigSite(player.coords) && quest.stage(player) == STAGE_KEY) {
                launcher.launch(player) { findDigTeam() }
            }
        }
        onOpLoc1(EDERN) { searchEdern() }
        onOpLoc1(DISPENSER_GUARD) { searchDispenserGuard() }
        for (body in EMPTY_BODIES) {
            onOpLoc1(body) { mes("The man died with nothing.") }
        }
        onOpHeld1(JOURNAL) { readJournal() }
        onOpHeld1(NOTES) { mesbox(NOTES_TEXT) }
        onOpLoc1(BLACK_CRYSTAL) { mes("There are many blackened crystal shards here, firmly attached. You'll need something to chip one off with.") }
        onOpLocU(BLACK_CRYSTAL, CHISEL) { chipSample() }
        onOpLocU(BLACK_CRYSTAL, CHARGED_CRYSTAL) { restore() }
        onOpLocU(BLACK_CRYSTAL, NEW_CRYSTAL) { mes("The new crystal has no power in it yet.") }
        onOpLocU(DEATH_ALTAR, NEW_CRYSTAL) { charge() }
    }

    internal suspend fun ProtectedAccess.findDigTeam() {
        if (quest.stage(player) != STAGE_KEY) {
            return
        }
        quest.advanceTo(this, STAGE_FOUND)
        startDialogue {
            chatPlayer(worried, "This must be the missing dig team, and it looks as though they found the temple. I'd better tell Arianwyn before the mourners find out.")
        }
    }

    private suspend fun ProtectedAccess.searchEdern() {
        arriveDelay()
        if (ownsAnywhere(JOURNAL)) {
            mes("The man died with nothing else.")
            return
        }
        if (inv.freeSpace() < 1 || invAdd(inv, JOURNAL).failure) {
            mes("You find a journal, but you don't have enough room to take it.")
            return
        }
        objbox(JOURNAL, "You find a journal.")
    }

    private suspend fun ProtectedAccess.searchDispenserGuard() {
        arriveDelay()
        val wants = listOf(COLOUR_WHEEL, NOTES).filter { !ownsAnywhere(it) }
        if (wants.isEmpty()) {
            mes("The man died with nothing else.")
            return
        }
        if (inv.freeSpace() < wants.size) {
            mes("You find a colour wheel and some notes but you don't have enough room to take them.")
            return
        }
        for (obj in wants) invAdd(inv, obj)
        doubleobjbox(COLOUR_WHEEL, NOTES, "You find a colour wheel and some notes.")
    }

    private suspend fun ProtectedAccess.chipSample() {
        arriveDelay()
        val stage = quest.stage(player)
        if (stage < STAGE_KEY || stage >= STAGE_CRYSTAL || quest.guarded(player)) {
            mes("You have no reason to take any of the crystal.")
            return
        }
        if (ownsAnywhere(SAMPLE)) {
            mes("You already have a sample of the black crystal.")
            return
        }
        if (inv.freeSpace() < 1) {
            mes("You don't have enough room to take a shard.")
            return
        }
        anim(CHISEL_SEQ)
        delay(2)
        if (invAdd(inv, SAMPLE).failure) {
            return
        }
        objbox(SAMPLE, "You chip off one of the many blackened shards.")
    }

    private suspend fun ProtectedAccess.restore() {
        arriveDelay()
        if (quest.stage(player) != STAGE_CRYSTAL || player.safeguardsRestored == 1) {
            mes("Nothing interesting happens.")
            return
        }
        if (invDel(inv, CHARGED_CRYSTAL, 1).failure) {
            return
        }
        player.safeguardsRestored = 1
        quest.advanceTo(this, STAGE_RESTORED)
        anim(PLACE_SEQ)
        objbox(CHARGED_CRYSTAL, "You place the powered crystal amongst the other shards.")
    }

    private suspend fun ProtectedAccess.charge() {
        arriveDelay()
        if (quest.stage(player) != STAGE_CRYSTAL) {
            mes("Nothing interesting happens.")
            return
        }
        if (!swap(listOf(NEW_CRYSTAL to 1), listOf(CHARGED_CRYSTAL to 1))) {
            return
        }
        anim(PLACE_SEQ)
        objbox(CHARGED_CRYSTAL, "You place the crystal on the altar. It starts to glow with a strange light.")
    }

    /** The journal on the book interface, a two-page spread at a time. */
    private suspend fun ProtectedAccess.readJournal() {
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
            if (turned in SPREADS.indices) spread = turned
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
        ifSetText("component.book:title", JOURNAL_TITLE)
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
        const val EDERN = "loc.mourning_dead_guard4"
        const val DISPENSER_GUARD = "loc.mourning_dead_guard1"
        val EMPTY_BODIES =
            listOf("loc.mourning_dead_guard2", "loc.mourning_dead_guard3", "loc.mourning_dead_slave1", "loc.mourning_dead_slave2")
        const val BLACK_CRYSTAL = "loc.mourning_temple_obsidian_crystal"
        const val DEATH_ALTAR = "loc.death_altar"
        const val CHISEL_SEQ = "seq.human_crafting"
        const val PLACE_SEQ = "seq.human_pickuptable"

        /** The bodies at the temple's entrance, on the mines side of its doorway. */
        fun atDigSite(coords: CoordGrid): Boolean = coords.level == 0 && coords.x in 1918..1934 && coords.z in 4628..4646

        const val NOTES_TEXT =
            "We've found where the shadows come from: an old temple, perhaps the one we were sent " +
                "to find. Nissyen wants a look around before we report back. Its doors are made of " +
                "coloured light, and each colour seems to need light of another colour to open it, " +
                "so I've drawn up a rough colour wheel to work out which. There's a device on the " +
                "wall nearby that hands out mirrors and crystals, and puts them all back if we " +
                "need to start again."

        const val BOOK_INTERFACE = "interface.book"
        const val BOOK_INIT_SCRIPT = 2632
        const val JOURNAL_TITLE = "Journal of Nissyen Edern"
        const val PAGE_SOUND = "synth.turn_book_page"
        const val LINES_PER_PAGE = 15
        const val WRAP_WIDTH = 26
        const val PAGE_LEFT = "component.book:page_left_button"
        const val PAGE_RIGHT = "component.book:page_right_button"

        /**
         * A summary of the journal's nine entries in this server's words: Edern joins the Death
         * Guard, comes to West Ardougne with the mourners, keeps up the plague and follows the
         * tunnel down to the temple.
         */
        private val ENTRIES =
            listOf(
                "Day one" to "My brother has found me a place in the Death Guard, an old and honourable order. I leave for the east tomorrow.",
                "Day two" to "We crossed the mountains by night and came to a human city under a plague. We are to wear the masks of its healers.",
                "Day three" to "The plague is not what the humans think. We are told to keep it alive, and to say nothing.",
                "Day four" to "Those who 'fall ill' are taken below to dig. I do not like this work, but the orders come from Lord Iorwerth himself.",
                "Day five" to "The tunnels run deep under the city. There are beasts in the dark; we lost two diggers today.",
                "Day six" to "We have broken into a great cavern to the west. The air is cold and something moves in it.",
                "Day seven" to "Shadows. They take the light from the lamps and the life from those who stray too far from the group.",
                "Day eight" to "We have found doors of light set into a wall of white stone. This must be the temple we were sent to find.",
                "Day nine" to "Nissyen has ordered us to look around before we report. The shadows are closing in. I hope this journal reaches someone.",
            )

        val SPREADS: List<Pair<List<String>, List<String>>> by lazy {
            val pages = ENTRIES.flatMap { (title, body) -> (listOf("<u>$title</u>", "") + wrap(body)).chunked(LINES_PER_PAGE) }
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
