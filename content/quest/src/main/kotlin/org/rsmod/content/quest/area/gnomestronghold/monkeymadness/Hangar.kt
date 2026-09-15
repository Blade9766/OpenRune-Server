package org.rsmod.content.quest.area.gnomestronghold.monkeymadness

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.random.Random
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.GLOUGH_FEE
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.HANGAR_GLOUGH
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.SPARE_CONTROLS
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_HANGAR
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The underground military glider hangar: the reinitialisation panel and its sliding puzzle, the
 * crate of spare controls, Glough's paid shortcut, and the gliders unfolding once the code is in.
 *
 * The puzzle runs on the cache's treasure trail puzzle interface in its Monkey Madness mode
 * (`varp.if1` = 7): the 24 `reinitialisation` pieces sit in `inv.reinitialisation_inv` with one
 * empty slot, the client draws them and reports a click on the pieces layer with the slot index.
 * Mode 8 with `inv.reinitialisation_inv_inactive` draws the same picture without any ops.
 * The piece order is kept in a quest attribute so a half-solved panel survives logging out.
 */
@Singleton
class Hangar
@Inject
constructor(
    private val monkeyMadness: MonkeyMadnessQuest,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    private val foldedGlider = ServerCacheManager.getObject(FOLDED_GLIDER.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $FOLDED_GLIDER")
    private val openGlider = ServerCacheManager.getObject(OPEN_GLIDER.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $OPEN_GLIDER")

    override fun ScriptContext.startup() {
        onOpLoc1(CONTROL_PANEL) { operatePanel() }
        onOpLoc1(SPARE_CONTROLS_CRATE) { searchCrate() }
        onOpNpc1(HANGAR_GLOUGH) { startDialogue(it.npc) { glough() } }
        onIfModalButton(PIECES) { slide(it.comsub) }
        onIfClose(PUZZLE_INTERFACE) { VarPlayerIntMapSetter.set(player, PUZZLE_MODE_VARP, 0) }
    }

    private suspend fun ProtectedAccess.operatePanel() {
        if (monkeyMadness.stage(player) < STAGE_HANGAR) {
            mes("The panel is covered in gnome writing you don't understand.")
            return
        }
        if (monkeyMadness.glidersReady.get(player)) {
            mes("The panel hums quietly. The gliders have already been reinitialised.")
            return
        }
        if (monkeyMadness.puzzle.get(player).isEmpty()) {
            mesbox("The panel shows a scrambled picture. Sliding the tiles into place should reinitialise the gliders.")
            monkeyMadness.puzzle.set(player, scrambled())
        }
        openPuzzle()
    }

    private fun ProtectedAccess.openPuzzle() {
        val inv = inv(PUZZLE_INV)
        invClear(inv)
        val order = monkeyMadness.puzzle.get(player)
        for ((slot, letter) in order.withIndex()) {
            if (letter != GAP) {
                invAdd(inv, PIECES_OBJS[letter - FIRST_PIECE], slot = slot)
            }
        }
        VarPlayerIntMapSetter.set(player, PUZZLE_MODE_VARP, PUZZLE_MODE_MM)
        invTransmit(inv)
        ifOpenMainModal(PUZZLE_INTERFACE)
        ifSetEvents(PIECES, 0 until PUZZLE_SIZE, IfEvent.Op1)
    }

    private suspend fun ProtectedAccess.slide(slot: Int) {
        val order = monkeyMadness.puzzle.get(player)
        if (order.length != PUZZLE_SIZE || slot !in 0 until PUZZLE_SIZE || monkeyMadness.glidersReady.get(player)) {
            return
        }
        val gap = order.indexOf(GAP)
        if (!adjacent(slot, gap)) {
            return
        }
        val chars = order.toCharArray()
        chars[gap] = chars[slot]
        chars[slot] = GAP
        val moved = String(chars)
        monkeyMadness.puzzle.set(player, moved)
        val inv = inv(PUZZLE_INV)
        invClear(inv)
        for ((i, letter) in moved.withIndex()) {
            if (letter != GAP) {
                invAdd(inv, PIECES_OBJS[letter - FIRST_PIECE], slot = i)
            }
        }
        invTransmit(inv)
        if (moved == SOLVED) {
            ifClose()
            reinitialise()
        }
    }

    private suspend fun ProtectedAccess.reinitialise() {
        mesbox("The panel lights up. Somewhere behind the walls, machinery begins to turn.")
        monkeyMadness.glidersReady.set(player, true)
        monkeyMadness.syncVars(player)
        unfoldGliders()
        mesbox("One by one the gliders unfold their wings. Waydar can fly now.")
    }

    /** Plays the unfold on every folded glider and leaves them open for a while. */
    private suspend fun ProtectedAccess.unfoldGliders() {
        soundSynth(MonkeyMadness.SOUND_WING_UNFOLD)
        for (tile in MonkeyMadness.HANGAR_GLIDERS) {
            val loc = locRepo.findExact(tile, foldedGlider) ?: continue
            locAnim(worldRepo, loc, MonkeyMadness.GLIDER_UNFOLD_SEQ)
        }
        delay(UNFOLD_TICKS)
        for (tile in MonkeyMadness.HANGAR_GLIDERS) {
            val loc = locRepo.findExact(tile, foldedGlider) ?: continue
            locRepo.change(loc, openGlider, OPEN_TICKS)
        }
    }

    private suspend fun ProtectedAccess.searchCrate() {
        anim(MonkeyMadness.SEARCH_SEQ)
        delay(1)
        if (monkeyMadness.stage(player) < STAGE_HANGAR || player.inv.contains(SPARE_CONTROLS) || monkeyMadness.glidersReady.get(player)) {
            mes("The crate is full of spare glider parts.")
            return
        }
        if (player.inv.freeSpace() < 1) {
            mes("You need a free inventory space to take anything from the crate.")
            return
        }
        invAdd(player.inv, SPARE_CONTROLS)
        objbox(SPARE_CONTROLS, "You find a set of spare controls with the finished picture printed on them.")
    }

    private suspend fun Dialogue.glough() {
        chatPlayer(shocked, "Glough! What are you doing down here?")
        chatNpc(angry, "Hiding from you, human, and from Narnode's toy soldiers. This is my hangar; I built it.")
        if (monkeyMadness.stage(player) != STAGE_HANGAR || monkeyMadness.glidersReady.get(player)) {
            chatNpc(angry, "Now get out of my sight.")
            return
        }
        chatNpc(laugh, "I see Waydar hasn't managed to get my gliders flying. He never could work the reinitialisation code. Nobody could, except me.")
        chatNpc(neutral, "I could enter it for you, of course. For a price. Shall we say two hundred thousand coins?")
        val choice =
            choice3(
                "Fine, here's your money.", 1,
                "I'd rather solve it myself.", 2,
                "You must think I'm a fool!", 3,
            )
        when (choice) {
            1 -> {
                chatPlayer(neutral, "Fine, here's your money.")
                if (player.inv.count(MonkeyMadnessQuest.COINS) < GLOUGH_FEE) {
                    chatNpc(laugh, "You don't have it! Come back when you do.")
                    return
                }
                access.invDel(player.inv, MonkeyMadnessQuest.COINS, GLOUGH_FEE)
                chatNpc(laugh, "A pleasure doing business. Stand back.")
                access.reinitialise()
            }
            2 -> {
                chatPlayer(neutral, "I'd rather solve it myself.")
                chatNpc(laugh, "Suit yourself. I'll be here when you give up.")
            }
            3 -> {
                chatPlayer(angry, "You must think I'm a fool!")
                chatNpc(angry, "I think you are a human. It comes to the same thing.")
            }
        }
    }

    private fun adjacent(a: Int, b: Int): Boolean {
        val ax = a % PUZZLE_WIDTH
        val ay = a / PUZZLE_WIDTH
        val bx = b % PUZZLE_WIDTH
        val by = b / PUZZLE_WIDTH
        return (ax == bx && kotlin.math.abs(ay - by) == 1) || (ay == by && kotlin.math.abs(ax - bx) == 1)
    }

    /** Slides random neighbours into the gap from the solved state, so the result is always solvable. */
    private fun scrambled(): String {
        val chars = SOLVED.toCharArray()
        var gap = chars.indexOf(GAP)
        repeat(SCRAMBLE_MOVES) {
            val neighbours = (0 until PUZZLE_SIZE).filter { adjacent(it, gap) }
            val pick = neighbours[Random.nextInt(neighbours.size)]
            chars[gap] = chars[pick]
            chars[pick] = GAP
            gap = pick
        }
        return String(chars)
    }

    companion object {
        const val CONTROL_PANEL = "loc.bunker_controlpanal"
        const val SPARE_CONTROLS_CRATE = "loc.mm_reinitialisation_crate"
        const val FOLDED_GLIDER = "loc.mm_glider"
        const val OPEN_GLIDER = "loc.mm_glider_open"
        const val PUZZLE_INTERFACE = "interface.trail_slidepuzzle"
        const val PIECES = "component.trail_slidepuzzle:pieces"
        const val PUZZLE_INV = "inv.reinitialisation_inv"
        const val PUZZLE_INV_INACTIVE = "inv.reinitialisation_inv_inactive"
        const val PUZZLE_MODE_VARP = "varp.if1"
        const val PUZZLE_MODE_MM = 7
        const val PUZZLE_MODE_INACTIVE = 8
        const val PUZZLE_WIDTH = 5
        const val PUZZLE_SIZE = 25
        const val GAP = '.'
        const val FIRST_PIECE = 'A'
        const val SOLVED = "ABCDEFGHIJKLMNOPQRSTUVWX."
        const val SCRAMBLE_MOVES = 255
        const val UNFOLD_TICKS = 6
        const val OPEN_TICKS = 500

        val PIECES_OBJS = (1..24).map { "obj.reinitialisation_" + it.toString().padStart(2, '0') }
        val INACTIVE_PIECES_OBJS = PIECES_OBJS.map { it + "_inactive" }
    }
}
