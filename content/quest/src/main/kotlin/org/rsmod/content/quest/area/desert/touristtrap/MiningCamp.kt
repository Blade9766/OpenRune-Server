package org.rsmod.content.quest.area.desert.touristtrap

import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_IN_A_BARREL
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.CELL_BARS_VARBIT
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.CELL_DOOR_KEY
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.METAL_KEY
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.SOUND_BEND_BARS
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.SOUND_FALL_BACK
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.SOUND_LOCKED
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.SOUND_SQUEEZE_OUT
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_APPROACHED_CAPTAIN
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_ENTERED_CAMP
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_ENTERED_MINE
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_TRADED_CLOTHES
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The surface of the Desert Mining Camp: the trail of footprints leading to it, the locked
 * compound gate, the big wooden doors down to the mine, and the cell with its escape route of bent
 * bars, a rock and a cliff.
 *
 * While the player is inside, the guards take a look at them every so often; anyone they spot with
 * a weapon or armour, or anyone not dressed as a slave down the mine, is thrown in a cell.
 */
class MiningCamp
@Inject
constructor(
    private val quest: TouristTrapQuest,
    private val security: MiningCampSecurity,
    private val passages: GenericPassageScript,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (footprints in FOOTPRINTS) {
            onOpLoc1(footprints) { lookAtFootprints() }
            onOpLoc2(footprints) { searchFootprints() }
        }
        onOpLoc1(SCARF_CACTUS) {
            if (quest.isStarted(player)) {
                mesbox(
                    "You remember that Irena mentioned something about Ana wearing a red scarf " +
                        "before she left for the desert.",
                )
            } else {
                mesbox("A red silk scarf has snagged on the spines of this cactus.")
            }
        }

        for (gate in CAMP_GATES) {
            onOpLoc1(gate) { openCampGate(it.loc, it.type) }
            onOpLoc2(gate) { searchCampGate() }
            onOpLocU(gate, METAL_KEY) { openCampGate(it.loc, it.type) }
        }

        for (door in MINE_DOORS) {
            onOpLoc1(door) { goDownTheMine() }
            onOpLoc2(door) {
                mesbox(
                    "You watch the doors for some time. You notice that only slaves seem to go " +
                        "down there. You might be able to sneak down if you pass as a slave.",
                )
            }
        }

        onOpLoc1(CELL_DOOR) {
            soundSynth(SOUND_LOCKED)
            mes("The door seems to be pretty locked.")
        }
        onOpLocU(CELL_DOOR, CELL_DOOR_KEY) { unlockCellDoor(it.loc, it.type) }
        onOpLoc1(CELL_WINDOW) { cellWindow() }
        onOpLoc2(ESCAPE_ROCK) { climbRock() }
        onOpLoc1(CLIFF_UP) { climbCliff() }
        onOpLoc1(CLIFF_DOWN) { dropOffCliff() }

        onPlayerSoftTimer(GUARD_WATCH_TIMER) {
            if (!launcher.launch(player) { guardsLookAround() } && !insideCamp(player.coords)) {
                player.clearSoftTimer(GUARD_WATCH_TIMER)
            }
        }
        onPlayerLogin {
            if (insideCamp(player.coords) && !quest.isComplete(player)) {
                player.softTimer(GUARD_WATCH_TIMER, WATCH_MIN_CYCLES)
            }
        }
    }

    /* The trail from the Shantay Pass */

    private suspend fun ProtectedAccess.lookAtFootprints() {
        if (!quest.isStarted(player)) {
            mesbox("You see some footsteps in the sand.")
            return
        }
        mesbox("This looks like some disturbed sand. Footsteps seem to be heading off towards the South.")
    }

    private suspend fun ProtectedAccess.searchFootprints() {
        if (!quest.isStarted(player)) {
            mesbox("You just see some footsteps in the sand.")
            return
        }
        mesbox(
            "You search the footsteps more closely. You can see that there are five sets of " +
                "footprints. One set of footprints seems lighter than the others. The four other " +
                "footsteps were made by heavier people with boots.",
        )
    }

    /* The compound gate */

    private suspend fun ProtectedAccess.searchCampGate() {
        if (coords.x > TouristTrapCoords.CAMP_GATE_X) {
            mesbox(
                "You see a group of mercenaries hanging around outside the compound. They're " +
                    "probably up to no good.",
            )
            return
        }
        mesbox(
            "You see what looks like a mining compound. There seems to be people mining rocks. " +
                "They look as if they're chained to the rocks and they're being watched over by " +
                "the guards. It's not a very happy place.",
        )
        mesbox(
            "You notice that people are thoroughly searched as they enter and leave the compound " +
                "and people wielding weapons or wearing armour are treated quite severly.",
        )
    }

    /**
     * The gate is locked both ways and only the Mercenary Captain's key opens it. Nobody leaves
     * carrying a barrel, and a slave trying to slip out is stopped if a guard is watching.
     */
    private suspend fun ProtectedAccess.openCampGate(gate: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        val entering = coords.x <= TouristTrapCoords.CAMP_GATE_X
        if (!entering && !security.isExempt(player)) {
            if (ANA_IN_A_BARREL in inv) {
                caughtLeavingWithAna()
                return
            }
            val guard = security.guardNear(coords, GATE_WATCH)
            if (guard != null && player.wearingAnySlaveClothes()) {
                mes("A guard notices you as you try to slip past...")
                guard.say("Hey! Where d'ya think you're going?")
                delay(1)
                guard.say("Guards! Slave escaping!")
                with(security) { throwInCell(guard) }
                return
            }
        }
        if (METAL_KEY !in inv) {
            mes("This gate needs a key in order to be opened.")
            return
        }
        if (!entering) {
            with(passages) { walkThrough(gate, type) }
            return
        }
        mes("You use the metal key to unlock the gates.")
        mes("The guards search you thoroughly as you go through the gates.")
        if (quest.stage(player) in STAGE_APPROACHED_CAPTAIN until STAGE_ENTERED_CAMP) {
            quest.advanceTo(this, STAGE_ENTERED_CAMP)
        }
        val armed = player.wieldingWeapon() || player.wearingArmour()
        with(passages) { walkThrough(gate, type) }
        if (security.isExempt(player)) {
            return
        }
        if (armed) {
            delay(1)
            val guard = security.guardNear(coords, GATE_WATCH)
            for (line in ARMED_AT_GATE) {
                guard?.say(line)
                mes("Guard: $line")
            }
            delay(2)
            with(security) { throwInCell(guard) }
            return
        }
        softTimer(GUARD_WATCH_TIMER, random.of(WATCH_MIN_CYCLES, WATCH_MAX_CYCLES))
    }

    private suspend fun ProtectedAccess.caughtLeavingWithAna() {
        val guard = security.guardNear(coords, GATE_WATCH)
        for (line in BARREL_AT_GATE) {
            guard?.say(line)
            mes("Guard: $line")
            delay(2)
        }
        mes("The guards prise the lid off the barrel...")
        delay(2)
        guard?.say("Blimey - it's a jail break! Guards! Guards! Apprehend them!")
        mes("Guard: Blimey - it's a jail break! Guards! Guards! Apprehend them!")
        delay(2)
        startDialogue {
            chatNpcSpecific(
                "Ana",
                ANA_HEAD,
                angry,
                "I could have told you that we wouldn't get away with it! Now look at the mess " +
                    "you've caused.",
            )
            mesbox("The guards grab Ana and drag her away.")
            chatNpcSpecific("Ana", ANA_HEAD, angry, "Hey, watch it with the hands buster.")
            chatNpcSpecific("Ana", ANA_HEAD, angry, "These are the up market slaves clothes doncha know!")
        }
        with(security) { throwInCell(guard) }
    }

    /* The big wooden doors */

    private suspend fun ProtectedAccess.goDownTheMine() {
        arriveDelay()
        mes("You push the door.")
        delay(2)
        player.say("Ugh!")
        if (!player.wearingSlaveRobes() && !security.isExempt(player)) {
            val guard = security.guardNear(coords, DOOR_WATCH)
            with(security) { caughtInFancyClothes(guard) }
            return
        }
        mes("The doors open with some effort!")
        mes("The guards search you thoroughly as you go through the gates.")
        soundSynth(BIG_DOOR_SOUND)
        telejump(TouristTrapCoords.MINE_DOORS_UNDERGROUND, TeleportType.Exempt)
        if (!security.isExempt(player)) {
            softTimer(GUARD_WATCH_TIMER, random.of(WATCH_MIN_CYCLES, WATCH_MAX_CYCLES))
        }
        if (quest.stage(player) == STAGE_TRADED_CLOTHES) {
            quest.advanceTo(this, STAGE_ENTERED_MINE)
            mesbox(
                "The huge doors open into a dark, dank and smelly tunnel. The associated smells of " +
                    "a hundred sweaty miners greets your nostrils. And your ears ring with the " +
                    "'CLANG CLANG CLANG' as metal hits rock.",
            )
        }
    }

    /* The cell and the way out of it */

    private suspend fun ProtectedAccess.unlockCellDoor(door: BoundLocInfo, type: ObjectServerType) {
        with(passages) { walkThrough(door, type) }
    }

    private suspend fun ProtectedAccess.cellWindow() {
        arriveDelay()
        val inside = coords.x >= TouristTrapCoords.CELL_WINDOW_INSIDE.x
        if (player.vars[CELL_BARS_VARBIT] == 0) {
            anim(BEND_SEQ)
            soundSynth(SOUND_BEND_BARS)
            delay(3)
            setVarBit(player, CELL_BARS_VARBIT, 1)
            mes("You bend the bars back.")
            return
        }
        if (ANA_IN_A_BARREL in inv) {
            mesbox(
                "You'll never get Ana (in a barrel) through the window. The barrel is just too big.",
            )
            startDialogue {
                chatNpcSpecific(
                    "Ana (in a Barrel)",
                    TouristTrapQuest.ANA_BARREL_HEAD,
                    angry,
                    "Don't even think for one minute... you're gonna get me through that window!",
                )
            }
            return
        }
        mes("You prepare to squeeze through the bars.")
        soundSynth(SOUND_SQUEEZE_OUT)
        val dest =
            if (inside) TouristTrapCoords.CELL_WINDOW_OUTSIDE else TouristTrapCoords.CELL_WINDOW_INSIDE
        glideTo(dest, SQUEEZE_SEQ, SQUEEZE_TICKS)
        resetAnim()
        if (inside) {
            mes("You land near some rough rocks, which you may be able to climb.")
        }
    }

    private suspend fun ProtectedAccess.climbRock() {
        arriveDelay()
        val fromCell = coords.x >= TouristTrapCoords.ROCK_EAST.x
        val dest = if (fromCell) TouristTrapCoords.ROCK_WEST else TouristTrapCoords.ROCK_EAST
        mes("You start climbing the rocky elevation.")
        if (!statRandom("stat.agility", CLIMB_LOW, CLIMB_HIGH, 0)) {
            anim(SCRABBLE_FAIL_SEQ)
            soundSynth(SOUND_FALL_BACK)
            delay(2)
            hurt(ROCK_DAMAGE)
            mes("You scrape your hands and knees as you climb up.")
            anim(DUST_OFF_SEQ)
            return
        }
        glideTo(dest, SCRABBLE_SEQ, CLIMB_TICKS)
        resetAnim()
    }

    private suspend fun ProtectedAccess.climbCliff() {
        arriveDelay()
        val atFoot = coords.x >= TouristTrapCoords.CLIFF_FOOT.x
        if (!atFoot) {
            glideTo(TouristTrapCoords.CLIFF_FOOT, CLIFF_DROP_SEQ, CLIMB_TICKS)
            resetAnim()
            return
        }
        if (!statRandom("stat.agility", CLIMB_LOW, CLIMB_HIGH, 0)) {
            anim(CLIFF_FAIL_SEQ)
            soundSynth(SOUND_FALL_BACK)
            delay(2)
            hurt(CLIFF_DAMAGE)
            mes("You slip a little and tumble the rest of the way down the slope.")
            anim(DUST_OFF_SEQ)
            return
        }
        glideTo(TouristTrapCoords.CLIFF_TOP, CLIFF_CLIMB_SEQ, CLIMB_TICKS)
        resetAnim()
    }

    private suspend fun ProtectedAccess.dropOffCliff() {
        arriveDelay()
        val onTop = coords.x >= TouristTrapCoords.CLIFF_EDGE.x
        if (onTop) {
            glideTo(TouristTrapCoords.CLIFF_BOTTOM, CLIFF_DROP_SEQ, DROP_TICKS)
        } else {
            glideTo(TouristTrapCoords.CLIFF_EDGE, CLIFF_CLIMB_SEQ, DROP_TICKS)
        }
        resetAnim()
    }

    private fun ProtectedAccess.hurt(maxDamage: Int) {
        val damage = random.of(1, maxDamage).coerceAtMost(player.hitpoints - 1).coerceAtLeast(0)
        queueHit(player, delay = 0, type = HitType.Typeless, damage = damage)
    }

    /* The guards' rounds */

    private suspend fun ProtectedAccess.guardsLookAround() {
        if (security.isExempt(player) || !insideCamp(coords)) {
            clearSoftTimer(GUARD_WATCH_TIMER)
            return
        }
        softTimer(GUARD_WATCH_TIMER, random.of(WATCH_MIN_CYCLES, WATCH_MAX_CYCLES))
        if (TouristTrapCoords.inCell(coords)) {
            return
        }
        val guard = security.guardNear(coords, WATCH_RANGE) ?: return
        if (TouristTrapCoords.inMine(coords) && !player.wearingSlaveRobes()) {
            with(security) { caughtInFancyClothes(guard) }
            return
        }
        with(security) { caughtArmed(guard) }
    }

    private fun insideCamp(coords: CoordGrid): Boolean =
        TouristTrapCoords.inCamp(coords) || TouristTrapCoords.inMine(coords)

    private companion object {
        const val GUARD_WATCH_TIMER = "timer.touristtrap_guard_watch"
        const val WATCH_MIN_CYCLES = 25
        const val WATCH_MAX_CYCLES = 100
        const val WATCH_RANGE = 4
        const val GATE_WATCH = 6
        const val DOOR_WATCH = 10

        const val SCARF_CACTUS = "loc.tourtrap_qip_cactus_scarf"
        const val CELL_DOOR = "loc.capt_siad_cell_door"
        const val CELL_WINDOW = "loc.tourtrap_qip_cell_bars_multi"
        const val ESCAPE_ROCK = "loc.tourtrap_qip_climb_rock_1"
        const val CLIFF_UP = "loc.tourtrap_qip_clifftop_climbup"
        const val CLIFF_DOWN = "loc.tourtrap_qip_clifftop_climbdown"
        const val ANA_HEAD = "npc.ana"

        val CAMP_GATES = listOf("loc.miningcampgateclosedl", "loc.miningcampgateclosedr")
        val MINE_DOORS = listOf("loc.thttmineentrancel", "loc.thttmineentrancer")

        val FOOTPRINTS =
            listOf(
                "loc.tourtrap_qip_footprints_1",
                "loc.tourtrap_qip_footprints_2",
                "loc.tourtrap_qip_footprints_3",
                "loc.tourtrap_qip_footprints_4",
                "loc.tourtrap_qip_footprints_5",
                "loc.tourtrap_qip_footprints_diagonal_1",
                "loc.tourtrap_qip_footprints_diagonal_2",
                "loc.tourtrap_qip_footprints_diagonal_3",
                "loc.tourtrap_qip_footprints_diagonal_4",
                "loc.tourtrap_qip_footprints_diagonal_falloff",
                "loc.tourtrap_qip_footprints_diagonal_falloff_2",
                "loc.tourtrap_qip_footprints_diagonal_falloff_l",
                "loc.tourtrap_qip_footprints_diagonal_falloff_l_2",
            )

        val ARMED_AT_GATE =
            listOf("Hey - you with the weapon!", "You're not allowed in here!", "Quick, men, get them!!")

        val BARREL_AT_GATE =
            listOf(
                "Hey! Where d'ya think you're going with that barrel?",
                "You should know that they go out on the cart!",
                "I'd better check this out.",
            )

        const val BIG_DOOR_SOUND = "synth.bigdoor_open"
        const val BEND_SEQ = "seq.tourtrap_qip_bend_bars"
        const val SQUEEZE_SEQ = "seq.tourtrap_qip_escape_through_bars"
        const val SCRABBLE_SEQ = "seq.tourtrap_qip_scrabble_success"
        const val SCRABBLE_FAIL_SEQ = "seq.tourtrap_qip_scrabble_fail_part1"
        const val CLIFF_CLIMB_SEQ = "seq.tourtrap_qip_climb_cliff_pt1"
        const val CLIFF_FAIL_SEQ = "seq.tourtrap_qip_climb_cliff_fail_pt1"
        const val CLIFF_DROP_SEQ = "seq.tourtrap_qip_cliff_drop_pt1"
        const val DUST_OFF_SEQ = "seq.tourtrap_qip_get_up_dust_off"

        const val SQUEEZE_TICKS = 2
        const val CLIMB_TICKS = 3
        const val DROP_TICKS = 3
        const val CLIMB_LOW = 150
        const val CLIMB_HIGH = 250
        const val ROCK_DAMAGE = 3
        const val CLIFF_DAMAGE = 7
    }
}
