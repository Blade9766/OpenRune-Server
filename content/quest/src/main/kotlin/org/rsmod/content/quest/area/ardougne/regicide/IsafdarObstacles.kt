package org.rsmod.content.quest.area.ardougne.regicide

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.AGILITY_REQ
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_DENSE_FOREST
import org.rsmod.content.quest.area.ardougne.undergroundpass.acrossFrom
import org.rsmod.content.quest.area.ardougne.undergroundpass.clearWalkStyle
import org.rsmod.content.quest.area.ardougne.undergroundpass.climbOver
import org.rsmod.content.quest.area.ardougne.undergroundpass.lineTo
import org.rsmod.content.quest.area.ardougne.undergroundpass.setWalkStyle
import org.rsmod.content.quest.area.ardougne.undergroundpass.stepThrough
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The obstacles of Isafdar. Every trap is fenced in by server-side walls, so the forest can only
 * be crossed through them, by their ops:
 * - Leaves hide spiked pits; a jump clears the 3x3 patch, a failed one drops the player in for up
 *   to 18 damage and they climb out where they started.
 * - Tripwires set off the crossbows hidden in the twigs: 10 damage and a poison of 2 on a fail.
 * - Sticks are a trap the length of a narrow gap: 8 damage and a knock back on a fail.
 * - The log balances over the river want 45 Agility and the quest started, and never fail.
 * - Dense forest wants the tracker's lesson and 56 Agility, boostable, but only when heading away
 *   from the Underground Pass: the way back is always open, so nobody can be stranded.
 *
 * Success chances use the wiki's published curves: 180/255 for the leaves, 30/155 for the
 * tripwires and sticks.
 */
class IsafdarObstacles
@Inject
constructor(private val regicide: RegicideQuest, private val guard: TyrasGuardEncounter) : PluginScript() {

    override fun ScriptContext.startup() {
        for (leaf in LEAVES) {
            onOpLoc1(leaf) { jumpLeaves(it.loc) }
        }
        onOpLoc1(TRIPWIRE) { stepOverTripwire(it.loc.coords) }
        onOpLoc1(TRIPWIRE_ROCK) { stepOverTripwire(it.loc.coords) }
        onOpLoc1(STICKS) { passSticks(it.loc) }
        for (log in LOG_STARTS) {
            onOpLoc1(log) { crossLog(it.loc) }
        }
        for (forest in DENSE_FORESTS) {
            onOpLoc1(forest) { enterForest(it.loc) }
        }
    }

    private suspend fun ProtectedAccess.jumpLeaves(leaf: BoundLocInfo) {
        arriveDelay()
        val patch = LeafPatch.entries.firstOrNull { it.centre.chebyshevDistance(leaf.coords) <= 1 } ?: return
        val dest = patch.across(coords)
        faceSquare(patch.centre)
        if (!statRandom("stat.agility", LEAVES_LOW, LEAVES_HIGH, 0)) {
            anim(FALL_SEQ)
            mes("You fall into a pit of spikes hidden under the leaves!")
            delay(1)
            takeInstantHit(HitType.Typeless, random.of(1, LEAVES_MAX_DAMAGE))
            delay(1)
            resetAnim()
            mes("You climb back out of the pit.")
            return
        }
        climbOver(dest, JUMP_SEQ, ticks = 2)
        mes("You jump over the leaves.")
    }

    private suspend fun ProtectedAccess.stepOverTripwire(clicked: CoordGrid) {
        arriveDelay()
        val wire = Tripwire.entries.minByOrNull { it.coords.chebyshevDistance(clicked) } ?: return
        if (wire.coords.chebyshevDistance(clicked) > 2) {
            return
        }
        val dest = wire.across(coords)
        faceSquare(dest)
        if (!statRandom("stat.agility", TRAP_LOW, TRAP_HIGH, 0)) {
            soundSynth(TRIPWIRE_SOUND)
            mes("You trip the wire and are hit by crossbow bolts from the twigs!")
            takeInstantHit(HitType.Typeless, TRIPWIRE_DAMAGE)
            CombatEffects.poison(player, TRIPWIRE_POISON)
            return
        }
        climbOver(dest, STEP_OVER_SEQ, ticks = 2)
        mes("You carefully step over the tripwire.")
    }

    private suspend fun ProtectedAccess.passSticks(sticks: BoundLocInfo) {
        arriveDelay()
        val start = coords
        val dest = alongTrap(sticks)
        faceSquare(dest)
        if (!statRandom("stat.agility", TRAP_LOW, TRAP_HIGH, 0)) {
            soundSynth(STICKS_SOUND)
            anim(KNOCKED_BACK_SEQ)
            mes("The sticks spring back and hit you!")
            takeInstantHit(HitType.Typeless, STICKS_DAMAGE)
            delay(1)
            resetAnim()
            if (coords != start) {
                telejump(start)
            }
            return
        }
        climbOver(dest, SQUEEZE_SEQ, ticks = 3)
        mes("You carefully pass through the sticks.")
    }

    private suspend fun ProtectedAccess.crossLog(start: BoundLocInfo) {
        arriveDelay()
        val log = LogBalance.entries.firstOrNull { start.coords == it.first || start.coords == it.second } ?: return
        if (!regicide.isStarted(player) && !regicide.isComplete(player)) {
            mes("You can't see a safe way across.")
            return
        }
        if (stat("stat.agility") < LOG_AGILITY_REQ) {
            mesbox("You need an Agility level of $LOG_AGILITY_REQ to cross this log.")
            return
        }
        val dest = log.across(coords)
        faceSquare(dest)
        mes("You walk carefully across the slippery log...")
        setWalkStyle(BALANCE_SEQ)
        try {
            stepThrough(lineTo(dest))
        } finally {
            clearWalkStyle()
        }
        mes("...and make it safely to the other side.")
    }

    /**
     * Dense forest is crossed through its thin side. Heading away from the Underground Pass needs
     * the tracker's lesson and 56 Agility; heading back towards it needs nothing, and a forest
     * between two others needs only the lesson, so a player whose boost wears off mid-band can
     * always walk back out.
     */
    private suspend fun ProtectedAccess.enterForest(forest: BoundLocInfo) {
        arriveDelay()
        val dest = acrossFrom(forest)
        val direction = DenseForest.directionOf(forest, dest)
        if (direction != DenseForest.Direction.TOWARDS_PASS) {
            if (!regicide.knowsDenseForest(player)) {
                mes("The forest is too dense to get through here.")
                return
            }
            if (direction == DenseForest.Direction.AWAY_FROM_PASS && stat("stat.agility") < AGILITY_REQ) {
                mesbox("You need an Agility level of $AGILITY_REQ to find a way through this forest.")
                return
            }
        }
        faceSquare(dest)
        climbOver(dest, SQUEEZE_SEQ, ticks = 3)
        if (forest.coords == DenseForest.GUARD_TRIGGER && dest.x < forest.coords.x && regicide.stage(player) == STAGE_DENSE_FOREST) {
            guard.summon(this)
        }
    }

    /** The tile at the far end of a trap that is crossed along its length. */
    private fun ProtectedAccess.alongTrap(trap: BoundLocInfo): CoordGrid {
        val minX = trap.coords.x
        val maxX = minX + trap.adjustedWidth - 1
        val minZ = trap.coords.z
        val maxZ = minZ + trap.adjustedLength - 1
        return if (trap.adjustedWidth >= trap.adjustedLength) {
            CoordGrid(if (coords.x <= minX) maxX + 1 else minX - 1, minZ, trap.coords.level)
        } else {
            CoordGrid(minX, if (coords.z <= minZ) maxZ + 1 else minZ - 1, trap.coords.level)
        }
    }

    /** The four leaf-covered pits, by their middle tile and the axis they are jumped along. */
    enum class LeafPatch(val centre: CoordGrid, private val northSouth: Boolean) {
        WEST(CoordGrid(2209, 3203, 0), northSouth = true),
        ARRIVAL(CoordGrid(2267, 3203, 0), northSouth = true),
        SOUTH(CoordGrid(2274, 3174, 0), northSouth = true),
        NORTH(CoordGrid(2277, 3262, 0), northSouth = false),
        ;

        fun across(from: CoordGrid): CoordGrid =
            if (northSouth) {
                CoordGrid(centre.x, if (from.z < centre.z) centre.z + 2 else centre.z - 2, centre.level)
            } else {
                CoordGrid(if (from.x < centre.x) centre.x + 2 else centre.x - 2, centre.z, centre.level)
            }
    }

    /** The tripwires, by their south-west tile, length and the axis they are stepped over along. */
    enum class Tripwire(val coords: CoordGrid, private val northSouth: Boolean) {
        CAMP_EAST(CoordGrid(2220, 3153, 0), northSouth = true),
        CAMP_WEST(CoordGrid(2215, 3154, 0), northSouth = true),
        OLD_CAMP(CoordGrid(2251, 3168, 0), northSouth = false),
        EAST(CoordGrid(2285, 3188, 0), northSouth = false),
        NORTH_EAST(CoordGrid(2294, 3243, 0), northSouth = true),
        ;

        fun across(from: CoordGrid): CoordGrid =
            if (northSouth) {
                CoordGrid(coords.x, if (from.z < coords.z) coords.z + 2 else coords.z - 1, coords.level)
            } else {
                CoordGrid(if (from.x < coords.x) coords.x + 2 else coords.x - 1, coords.z, coords.level)
            }
    }

    /** The three log balances over the river, by the start loc at each end. */
    enum class LogBalance(val first: CoordGrid, val second: CoordGrid) {
        WEST(CoordGrid(2197, 3237, 0), CoordGrid(2201, 3237, 0)),
        CENTRE(CoordGrid(2259, 3250, 0), CoordGrid(2263, 3250, 0)),
        EAST(CoordGrid(2290, 3233, 0), CoordGrid(2290, 3238, 0)),
        ;

        fun across(from: CoordGrid): CoordGrid =
            if (first.z == second.z) {
                if (from.x <= first.x) second.translate(1, 0) else first.translate(-1, 0)
            } else {
                if (from.z <= first.z) second.translate(0, 1) else first.translate(0, -1)
            }
    }

    companion object {
        val LEAVES = listOf("loc.regicide_pitfall_corner", "loc.regicide_pitfall_mid", "loc.regicide_pitfall_side")
        const val TRIPWIRE = "loc.regicide_trap_tripwire"
        const val TRIPWIRE_ROCK = "loc.regicide_rock1_trap"
        const val STICKS = "loc.regicide_trap_woodspring"
        val LOG_STARTS = listOf("loc.regicide_logbalance1_start", "loc.regicide_logbalance2_start", "loc.regicide_logbalance3_start")
        val DENSE_FORESTS =
            listOf(
                "loc.regicide_cross_over1",
                "loc.regicide_cross_over2",
                "loc.regicide_cross_over3",
                "loc.regicide_cross_over1_tyras_camp",
                "loc.regicide_cross_over2_tyras_camp",
            )

        const val LEAVES_LOW = 180
        const val LEAVES_HIGH = 255
        const val LEAVES_MAX_DAMAGE = 18
        const val TRAP_LOW = 30
        const val TRAP_HIGH = 155
        const val TRIPWIRE_DAMAGE = 10
        const val TRIPWIRE_POISON = 2
        const val STICKS_DAMAGE = 8
        const val LOG_AGILITY_REQ = 45

        const val JUMP_SEQ = "seq.human_longjump"
        const val FALL_SEQ = "seq.human_stumble_back"
        const val STEP_OVER_SEQ = "seq.regicide_stepover"
        const val SQUEEZE_SEQ = "seq.regicide_tightfit"
        const val KNOCKED_BACK_SEQ = "seq.human_stumble_back"
        const val BALANCE_SEQ = "seq.human_walk_logbalance"
        const val TRIPWIRE_SOUND = "synth.upass_tripwire"
        const val STICKS_SOUND = "synth.upass_springtrap"
    }
}
