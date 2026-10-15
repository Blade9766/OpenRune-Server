package org.rsmod.content.quest.area.ardougne.clocktower

import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.game.entity.NpcList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The rat cage in the north-west of the Clock Tower dungeon, between the player and the white cog.
 *
 * Two levers on the corridor wall work the cage's two gates: the western lever starts down with
 * the outer gate shut, the eastern one up with the inner gate open, and both gates stand open only
 * while both levers are up. The levers and gates are shared by everyone in the dungeon and fall
 * back to their starting positions after [LEVER_RESET_TICKS]. Rat poison in the cage's food trough
 * kills the rats, whose death throes shake the far gate loose for that player.
 */
class RatCage
@Inject
constructor(
    private val clockTower: ClockTowerQuest,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val npcList: NpcList,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (lever in Lever.entries) {
            onOpLoc1(lever.startLoc) { pull(it.loc, lever, toStart = false) }
            onOpLoc1(lever.pulledLoc) { pull(it.loc, lever, toStart = true) }
            onOpLoc1(lever.gate.closedLoc) { mes("This door doesn't seem to open from here...") }
        }
        onOpLoc1(WHITE_COG_GATE) { throughWhiteCogGate(it.loc) }
        onOpLocU(TROUGH, RAT_POISON) { poisonTrough() }
    }

    private suspend fun ProtectedAccess.pull(lever: BoundLocInfo, which: Lever, toStart: Boolean) {
        arriveDelay()
        val up = if (toStart) which.startsUp else !which.startsUp
        anim(if (up) PULL_UP_SEQ else PULL_DOWN_SEQ)
        soundSynth(LEVER_SOUND)
        mes("You pull the lever ${if (up) "up" else "down"}.")
        val duration = if (toStart) Int.MAX_VALUE else LEVER_RESET_TICKS
        setGate(which.gate, open = up, duration)
        locRepo.change(lever, if (toStart) which.startLoc else which.pulledLoc, duration)
    }

    private fun setGate(gate: Gate, open: Boolean, duration: Int) {
        if (open) {
            locRepo.add(gate.coords, OPEN_GATE, duration, LocAngle.South, LocShape.WallStraight)
        } else {
            locRepo.add(gate.coords, gate.closedLoc, duration, LocAngle.East, LocShape.WallStraight)
        }
    }

    private suspend fun ProtectedAccess.throughWhiteCogGate(gate: BoundLocInfo) {
        arriveDelay()
        if (!clockTower.ratsPoisoned(player)) {
            mes("This door does not seem to be openable.")
            return
        }
        val westward = coords.x >= gate.coords.x
        if (westward) {
            mesbox(
                "The death throes of the rats seem to have shaken the door loose of its hinges. " +
                    "You pick it up and go through.",
            )
        }
        val dest = if (westward) gate.coords.translateX(-1) else gate.coords
        anim(WALK_SEQ)
        exactMove(
            start = coords,
            end = dest,
            delay1 = 0,
            delay2 = GATE_WALK_CYCLES,
            dir = if (westward) Constants.em_face_west else Constants.em_face_east,
            teleportType = TeleportType.Exempt,
        )
    }

    private suspend fun ProtectedAccess.poisonTrough() {
        arriveDelay()
        if (!clockTower.isActive(player)) {
            mes(Constants.dm_default)
            return
        }
        if (invDel(inv, RAT_POISON).failure) {
            return
        }
        clockTower.poisonRats(player)
        anim(POUR_SEQ)
        mes("The rats swarm towards the poisoned food...")
        delay(2)
        mes("... and devour it hungrily.")
        delay(2)
        mes("You see them smashing against the gates in a panic.")
        delay(2)
        mes("They seem to be dying.")
        killRats()
    }

    private suspend fun ProtectedAccess.killRats() {
        val rats = npcList.filterNotNull().filter { npc ->
            npc.isVisible && RATS.any { npc.isType(it) } && inCage(npc.coords)
        }
        for (rat in rats) {
            rat.anim(RAT_DEATH_SEQ)
        }
        delay(RAT_DEATH_TICKS)
        for (rat in rats) {
            if (rat.isVisible) {
                npcRepo.despawn(rat, RAT_RESPAWN_TICKS)
            }
        }
    }

    enum class Gate(val coords: CoordGrid, val closedLoc: String) {
        OUTER(CoordGrid(2595, 9657, 0), "loc.ctratgatea"),
        INNER(CoordGrid(2593, 9657, 0), "loc.ctratgateb"),
    }

    enum class Lever(
        val coords: CoordGrid,
        val startLoc: String,
        val pulledLoc: String,
        val startsUp: Boolean,
        val gate: Gate,
    ) {
        WEST(CoordGrid(2591, 9661, 0), "loc.ctlevera", "loc.ctlevera2", startsUp = false, Gate.OUTER),
        EAST(CoordGrid(2593, 9661, 0), "loc.ctleverb", "loc.ctleverb2", startsUp = true, Gate.INNER),
    }

    companion object {
        const val WHITE_COG_GATE = "loc.ctratgatec"
        const val TROUGH = "loc.ctfoodtrough"
        const val RAT_POISON = "obj.rat_poison"
        const val OPEN_GATE = "loc.inactiveprisondoor"

        val RATS = listOf("npc.clocktower_rat", "npc.clocktower_rat2", "npc.clocktower_rat3")

        const val LEVER_RESET_TICKS = 100
        const val RAT_DEATH_TICKS = 3
        const val RAT_RESPAWN_TICKS = 100

        fun inCage(coords: CoordGrid): Boolean =
            coords.level == 0 && coords.x in 2579..2592 && coords.z in 9653..9660

        private const val PULL_UP_SEQ = "seq.macro_lever_switch_up"
        private const val PULL_DOWN_SEQ = "seq.macro_lever_switch_down"
        private const val POUR_SEQ = "seq.human_pickuptable"
        private const val WALK_SEQ = "seq.human_walk_f"
        private const val RAT_DEATH_SEQ = "seq.giant_rat_update_death"
        private const val LEVER_SOUND = "synth.lever"

        private const val GATE_WALK_CYCLES = 30
    }
}
