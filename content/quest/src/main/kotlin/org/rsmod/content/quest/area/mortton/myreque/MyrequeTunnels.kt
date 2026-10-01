package org.rsmod.content.quest.area.mortton.myreque

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.REQUIRED_AGILITY
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_ROUTE_REVEALED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_SHORTCUT_OPENED
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The tunnels between Curpile Fyod's door, the Myreque hideout and the Canifis tavern cellar.
 *
 * The cache has the "Squeeze-past" stalagmite but the map never places it, so it is raised at
 * startup in the mouth of the dead-end pocket that holds the hideout's cave entrance; squeezing
 * past needs [REQUIRED_AGILITY] Agility. The other cave entrances in these tunnels lead nowhere.
 *
 * The false wall between the tunnels and the tavern cellar only gives once Veliaf has told the
 * player about it; the first time it does, the Canifis trapdoor shortcut is unlocked for good.
 */
class MyrequeTunnels
@Inject
constructor(
    private val myq: InSearchOfTheMyrequeQuest,
    private val locRepo: LocRepository,
    private val betrayal: Betrayal,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onGameStartup { raiseStalagmite() }
        onOpLoc1(STALAGMITE) { mes("Tooth-shaped rocks crowd the gap. Someone nimble might squeeze past.") }
        onOpLoc2(STALAGMITE) { squeeze() }
        onOpLoc1(CAVE_ENTRANCE) { enterCave(it.loc) }
        onOpLoc1(FALSE_WALL) { searchWall() }
        onOpLoc1(BASEMENT_LADDER) { climbToCanifis() }
        onOpLoc1(CANIFIS_TRAPDOOR) { openTrapdoor() }
    }

    private fun raiseStalagmite() {
        if (!locRepo.findLoc(MyrequeCoords.STALAGMITE, STALAGMITE)) {
            locRepo.add(MyrequeCoords.STALAGMITE, STALAGMITE, Int.MAX_VALUE, LocAngle[STALAGMITE_ANGLE], LocShape.CentrepieceStraight)
        }
    }

    private suspend fun ProtectedAccess.squeeze() {
        arriveDelay()
        if (stat(AGILITY) < REQUIRED_AGILITY) {
            mes("You need an Agility level of $REQUIRED_AGILITY to squeeze past the stalagmites.")
            return
        }
        val inward = coords.z < MyrequeCoords.STALAGMITE.z
        anim(SQUEEZE_SEQ)
        soundSynth(SQUEEZE_SOUND)
        delay(SQUEEZE_TICKS)
        telejump(if (inward) MyrequeCoords.POCKET_INSIDE else MyrequeCoords.POCKET_OUTSIDE, TeleportType.Exempt)
        mes("You squeeze through the gap between the stalagmites.")
    }

    private suspend fun ProtectedAccess.enterCave(loc: BoundLocInfo) {
        arriveDelay()
        when (loc.coords) {
            MyrequeCoords.POCKET_CAVE -> {
                anim(CRAWL_SEQ)
                delay(CRAWL_TICKS)
                telejump(MyrequeCoords.HIDEOUT_ARRIVAL, TeleportType.Exempt)
                mes("You crawl through the narrow tunnel into a lamp-lit cave.")
                with(betrayal) { enteredHideout() }
            }
            MyrequeCoords.HIDEOUT_CAVE -> {
                with(betrayal) { leaveHideout() }
                anim(CRAWL_SEQ)
                delay(CRAWL_TICKS)
                telejump(MyrequeCoords.POCKET_INSIDE, TeleportType.Exempt)
            }
            else -> mes("The tunnel beyond twists away into the dark. It's far too narrow to follow.")
        }
    }

    private suspend fun ProtectedAccess.searchWall() {
        arriveDelay()
        if (myq.stage(player) < STAGE_ROUTE_REVEALED) {
            mes("You search the wall but find nothing unusual.")
            return
        }
        soundSynth(WALL_SOUND)
        val north = coords.z > MyrequeCoords.FALSE_WALL.z
        telejump(if (north) MyrequeCoords.FALSE_WALL_SOUTH else MyrequeCoords.FALSE_WALL_NORTH, TeleportType.Exempt)
        mes("You find a hidden catch, and a section of the wall swings aside to let you through.")
        if (myq.stage(player) < STAGE_SHORTCUT_OPENED) {
            myq.advanceTo(this, STAGE_SHORTCUT_OPENED)
            mes("<col=800000>You have unlocked a shortcut between Canifis and the Myreque's tunnels.</col>")
        }
    }

    private suspend fun ProtectedAccess.climbToCanifis() {
        arriveDelay()
        anim(CLIMB_UP_SEQ)
        delay(CLIMB_TICKS)
        telejump(MyrequeCoords.CANIFIS_EXIT, TeleportType.Exempt)
        mes("You climb the ladder and push up through a trapdoor behind the Canifis tavern.")
    }

    private suspend fun ProtectedAccess.openTrapdoor() {
        arriveDelay()
        if (!myq.isShortcutUnlocked(player)) {
            mes("The trapdoor won't budge. It seems to be bolted from below.")
            return
        }
        soundSynth(TRAPDOOR_SOUND)
        anim(CLIMB_DOWN_SEQ)
        delay(CLIMB_TICKS)
        telejump(MyrequeCoords.BASEMENT_FOOT, TeleportType.Exempt)
        mes("You climb down into a musty cellar.")
    }

    internal companion object {
        const val STALAGMITE = "loc.route_stalagmite_cave_entrace"
        const val CAVE_ENTRANCE = "loc.route_cavewalltunnel"
        const val FALSE_WALL = "loc.thrttavernbasementfalsewall"
        const val BASEMENT_LADDER = "loc.thrttavernbasementladder"
        const val CANIFIS_TRAPDOOR = "loc.thrt_tavern_trap_door"
        const val AGILITY = "stat.agility"
        const val STALAGMITE_ANGLE = 1

        const val SQUEEZE_SEQ = "seq.human_squeeze"
        const val CRAWL_SEQ = "seq.human_pickupfloor"
        const val CLIMB_UP_SEQ = "seq.human_reachforladdertop"
        const val CLIMB_DOWN_SEQ = "seq.human_reachforladder"
        const val SQUEEZE_SOUND = "synth.squeeze_thru_crack"
        const val WALL_SOUND = "synth.climb_under"
        const val TRAPDOOR_SOUND = "synth.trapdoor_open"

        const val SQUEEZE_TICKS = 3
        const val CRAWL_TICKS = 2
        const val CLIMB_TICKS = 2
    }
}
