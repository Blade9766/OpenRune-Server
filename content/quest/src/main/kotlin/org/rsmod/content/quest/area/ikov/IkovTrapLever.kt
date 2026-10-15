package org.rsmod.content.quest.area.ikov

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.rellekka.fremenniktrials.NavigatorsTrial
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The trapped lever in the alcove east of the Fire Warrior's corridor, and the two doors it and
 * he stand behind.
 *
 * Searching the lever is the quest's only use for Thieving: the trapdoor in the alcove floor is
 * wired to the handle, and anyone who yanks it without disarming the wire first ends up on the
 * spikes underneath. Either way the bolt on the western door comes off.
 */
class IkovTrapLever
@Inject
constructor(
    private val quest: TempleOfIkovQuest,
    private val passages: GenericPassageScript,
    private val locRepo: LocRepository,
    private val navigatorsTrial: NavigatorsTrial,
) : PluginScript() {

    private val openTrapdoor: ObjectServerType =
        ServerCacheManager.getObject(TRAPDOOR.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $TRAPDOOR")

    override fun ScriptContext.startup() {
        onOpLoc1(LEVER) { pullLever(it.loc) }
        onOpLoc2(LEVER) { searchLever() }
        onOpLoc1(TRAPDOOR) {
            // Swensen's house in Rellekka reuses this trapdoor over his maze.
            if (NavigatorsTrial.isSwensensHouse(it.loc.coords)) {
                with(navigatorsTrial) { trapdoor() }
            } else {
                examineTrapdoor()
            }
        }
        onOpLoc1(LEVER_DOOR) { leverDoor(it.loc, it.type) }
        onOpLoc1(FIRE_WARRIOR_DOOR) { fireWarriorDoor(it.loc, it.type) }
    }

    private suspend fun ProtectedAccess.searchLever() {
        arriveDelay()
        if (player.ikovTrapDisarmed) {
            mes("You have already pulled the wire out of this lever.")
            return
        }
        if (player.thievingLvl < TempleOfIkovQuest.THIEVING_REQ) {
            mes(
                "You need a Thieving level of ${TempleOfIkovQuest.THIEVING_REQ} to find out what " +
                    "is wrong with this lever.",
            )
            return
        }
        anim(SEARCH_SEQ)
        delay(2)
        mes("A wire runs from the handle down into the floor.")
        mes("You work it loose and the trapdoor beneath you settles.")
        player.ikovTrapDisarmed = true
    }

    private suspend fun ProtectedAccess.pullLever(lever: BoundLocInfo) {
        arriveDelay()
        anim(PULL_SEQ)
        soundSynth(LEVER_SOUND)
        delay(1)
        if (!player.ikovTrapDisarmed) {
            springTrap(lever)
        }
        if (player.ikovTrapLeverPulled) {
            mes("You pull the lever, but nothing else happens.")
            return
        }
        player.ikovTrapLeverPulled = true
        soundSynth(TempleOfIkovQuest.SOUND_LOCKED_DOOR)
        mes("Somewhere west of you a bolt slides out of a door.")
    }

    /**
     * The floor gives way onto the spikes. The trapdoor is ground decor rather than a hole, so the
     * player is left where they are and only shown falling into it.
     */
    private suspend fun ProtectedAccess.springTrap(lever: BoundLocInfo) {
        val trapdoor = locRepo.findExact(lever.coords, openTrapdoor)
        if (trapdoor != null) {
            locRepo.del(trapdoor, TRAPDOOR_OPEN_TICKS)
        }
        soundSynth(TRAPDOOR_SOUND)
        anim(FALL_SEQ)
        mes("The floor swings open and drops you onto a bed of spikes!")
        val damage = TempleOfIkovQuest.TRAP_DAMAGE.coerceAtMost(player.hitpoints - 1)
        if (damage > 0) {
            queueHit(player, delay = 0, type = HitType.Typeless, damage = damage)
        }
        delay(1)
        anim(CLIMB_SEQ)
        mes("You haul yourself back out of the pit.")
    }

    private suspend fun ProtectedAccess.examineTrapdoor() {
        arriveDelay()
        if (player.ikovTrapDisarmed) {
            mes("The trapdoor is wedged shut now that the wire is gone.")
            return
        }
        mes("It will not open by hand. Something else works it.")
    }

    /** The door west of the alcove, bolted until the lever has been pulled. */
    private suspend fun ProtectedAccess.leverDoor(door: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        val leaving = coords.z > door.coords.z
        if (leaving || quest.isComplete(player) || player.ikovTrapLeverPulled) {
            with(passages) { walkThrough(door, type) }
            return
        }
        soundSynth(TempleOfIkovQuest.SOUND_LOCKED_DOOR)
        mes("The door is bolted shut. There must be a lever for it somewhere.")
    }

    /**
     * The door the Fire Warrior guards. He stands in front of it and turns anyone back with a
     * bolt of fire; it only opens once he is dead.
     */
    private suspend fun ProtectedAccess.fireWarriorDoor(door: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        val leaving = coords.z > door.coords.z
        if (leaving || player.ikovFireWarriorSlain) {
            with(passages) { walkThrough(door, type) }
            return
        }
        soundSynth(TempleOfIkovQuest.SOUND_LOCKED_DOOR)
        mes("The Fire Warrior of Lesarkus stands between you and the door.")
    }

    private companion object {
        const val LEVER = "loc.ikov_traplever"
        const val TRAPDOOR = "loc.ikov_trapdoor"
        const val LEVER_DOOR = "loc.ikov_trapleverdoor"
        const val FIRE_WARRIOR_DOOR = "loc.ikov_firewarriordoor"

        const val TRAPDOOR_OPEN_TICKS = 3

        const val SEARCH_SEQ = "seq.human_pickupfloor"
        const val PULL_SEQ = "seq.human_leverdown"
        const val FALL_SEQ = "seq.human_pickupfloor"
        const val CLIMB_SEQ = "seq.human_climbing"
        const val LEVER_SOUND = "synth.lever"
        const val TRAPDOOR_SOUND = "synth.trapdoor_open"
    }
}
