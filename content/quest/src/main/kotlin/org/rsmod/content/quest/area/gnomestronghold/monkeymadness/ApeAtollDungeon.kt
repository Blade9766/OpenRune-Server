package org.rsmod.content.quest.area.gnomestronghold.monkeymadness

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.clearInteractionRoute
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The tunnel under Ape Atoll that the sappers dug towards Zooknock: floor spikes that bite a
 * share of whatever health the player has left, two rock traps that drop a boulder on anyone who
 * treads on the trigger stone without first laying a plank over it, and a roof that sheds rocks
 * every so often on anyone not standing under an overhang.
 */
class ApeAtollDungeon
@Inject
constructor(
    private val launcher: ProtectedAccessLauncher,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    private val spikesType = ServerCacheManager.getObject(FLOOR_SPIKES.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $FLOOR_SPIKES")

    override fun ScriptContext.startup() {
        onPlayerCoordsChanged {
            if (player.coords != lastKnownCoords) {
                stepped(player)
            }
        }
        onPlayerSoftTimer(ROCKFALL_TIMER) { launcher.launch(player) { roofFall() } }
        onOpLoc1(ROCK_TRIGGER) { examineTrigger() }
        onOpLoc1(ROCK_TRAP) { examineTrigger() }
        onOpLocU(ROCK_TRIGGER, PLANK) { layPlank(it.loc) }
    }

    private fun stepped(player: Player) {
        val coords = player.coords
        if (!coords.inDungeon()) {
            return
        }
        if (ROCKFALL_TIMER !in player.softTimerMap) {
            player.softTimer(ROCKFALL_TIMER, ROCKFALL_INTERVAL)
        }
        when (coords) {
            in SPIKE_TILES -> {
                player.clearInteractionRoute()
                launcher.launch(player) { spiked() }
            }
            in ROCK_TRIGGERS -> {
                player.clearInteractionRoute()
                launcher.launch(player) { boulder() }
            }
        }
    }

    private suspend fun ProtectedAccess.spiked() {
        val loc = locRepo.findExact(player.coords, spikesType)
        if (loc != null) {
            locAnim(worldRepo, loc, SPIKES_SEQ)
        }
        queueHit(player, delay = 0, type = HitType.Typeless, damage = share(SPIKE_PERCENT))
        mes("Spikes shoot up out of the floor and stab through your boots!")
    }

    private suspend fun ProtectedAccess.boulder() {
        if (locRepo.findExact(player.coords, ROCK_TRIGGER.asRSCM(RSCMType.LOC).let { ServerCacheManager.getObject(it)!! }) == null) {
            return
        }
        spotanim(MonkeyMadness.ROCKFALL_SPOTANIM)
        soundSynth(MonkeyMadness.SOUND_RUMBLING)
        delay(1)
        queueHit(player, delay = 0, type = HitType.Typeless, damage = share(BOULDER_PERCENT))
        anim(MonkeyMadness.ROCKFALL_SEQ)
        mes("You tread on a loose stone and a boulder crashes down on you!")
    }

    private suspend fun ProtectedAccess.roofFall() {
        if (!player.coords.inDungeon()) {
            clearSoftTimer(ROCKFALL_TIMER)
            return
        }
        if (random.of(OVERHANG_ODDS) != 0) {
            return
        }
        spotanim(MonkeyMadness.ROCKFALL_SPOTANIM)
        soundSynth(MonkeyMadness.SOUND_RUMBLING_FADE)
        delay(1)
        queueHit(player, delay = 0, type = HitType.Typeless, damage = share(ROOF_PERCENT))
        mes("Rocks fall from the roof of the tunnel and land on your head.")
    }

    private suspend fun ProtectedAccess.examineTrigger() {
        mes("A flat stone, worn smooth. It sits a little lower than the floor around it.")
    }

    private suspend fun ProtectedAccess.layPlank(trigger: BoundLocInfo) {
        invDel(player.inv, PLANK)
        locRepo.del(trigger, PLANK_TICKS)
        mes("You lay the plank over the trigger stone. It should hold for a while.")
    }

    private fun ProtectedAccess.share(percent: Int): Int = (player.hitpoints * percent / 100).coerceAtLeast(1)

    private fun CoordGrid.inDungeon(): Boolean = level == 0 && x in DUNGEON_X && z in DUNGEON_Z

    companion object {
        const val ROCKFALL_TIMER = "timer.mm_rockfall"
        const val FLOOR_SPIKES = "loc.mm_floorspikes"
        const val ROCK_TRIGGER = "loc.mm_double_springtrap_trigger"
        const val ROCK_TRAP = "loc.mm_rocktrap"
        const val PLANK = "obj.woodplank"
        const val SPIKES_SEQ = "seq.mm_floorspikes_activate"
        const val SPIKE_PERCENT = 8
        const val BOULDER_PERCENT = 20
        const val ROOF_PERCENT = 4
        const val ROCKFALL_INTERVAL = 25
        const val OVERHANG_ODDS = 3
        const val PLANK_TICKS = 200
        val DUNGEON_X = 2688..2815
        val DUNGEON_Z = 9088..9151

        val ROCK_TRIGGERS = setOf(CoordGrid(2693, 9109, 0), CoordGrid(2743, 9146, 0))

        val SPIKE_TILES =
            setOf(
                CoordGrid(2691, 9115, 0), CoordGrid(2701, 9142, 0), CoordGrid(2703, 9106, 0), CoordGrid(2703, 9108, 0), CoordGrid(2704, 9113, 0), CoordGrid(2705, 9102, 0),
                CoordGrid(2706, 9106, 0), CoordGrid(2706, 9109, 0), CoordGrid(2707, 9103, 0), CoordGrid(2707, 9113, 0), CoordGrid(2708, 9132, 0), CoordGrid(2708, 9136, 0),
                CoordGrid(2709, 9110, 0), CoordGrid(2709, 9134, 0), CoordGrid(2710, 9106, 0), CoordGrid(2710, 9114, 0), CoordGrid(2711, 9104, 0), CoordGrid(2711, 9111, 0),
                CoordGrid(2711, 9131, 0), CoordGrid(2712, 9133, 0), CoordGrid(2712, 9136, 0), CoordGrid(2713, 9107, 0), CoordGrid(2713, 9109, 0), CoordGrid(2713, 9130, 0),
                CoordGrid(2715, 9119, 0), CoordGrid(2715, 9134, 0), CoordGrid(2715, 9137, 0), CoordGrid(2716, 9131, 0), CoordGrid(2717, 9133, 0), CoordGrid(2719, 9130, 0),
                CoordGrid(2725, 9097, 0), CoordGrid(2733, 9105, 0), CoordGrid(2735, 9122, 0), CoordGrid(2736, 9120, 0), CoordGrid(2737, 9123, 0), CoordGrid(2738, 9117, 0),
                CoordGrid(2739, 9119, 0), CoordGrid(2739, 9127, 0), CoordGrid(2740, 9098, 0), CoordGrid(2740, 9105, 0), CoordGrid(2740, 9122, 0), CoordGrid(2740, 9135, 0),
                CoordGrid(2741, 9125, 0), CoordGrid(2742, 9097, 0), CoordGrid(2742, 9103, 0), CoordGrid(2742, 9107, 0), CoordGrid(2742, 9121, 0), CoordGrid(2743, 9100, 0),
                CoordGrid(2743, 9123, 0), CoordGrid(2744, 9098, 0), CoordGrid(2745, 9104, 0), CoordGrid(2745, 9106, 0), CoordGrid(2746, 9099, 0), CoordGrid(2746, 9101, 0),
                CoordGrid(2783, 9191, 0), CoordGrid(2784, 9200, 0), CoordGrid(2785, 9204, 0), CoordGrid(2790, 9193, 0), CoordGrid(2793, 9115, 0), CoordGrid(2796, 9112, 0),
                CoordGrid(2809, 9111, 0),
            )
    }
}
