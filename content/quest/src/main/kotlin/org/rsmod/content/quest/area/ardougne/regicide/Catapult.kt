package org.rsmod.content.quest.area.ardougne.regicide

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.BARREL_BOMB
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.CATAPULT_GUARD
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.CATAPULT_GUARD_VIS
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.RABBITS
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_MAKE_BOMB
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_TYRAS_DEAD
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.TINDERBOX
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.UNFUSED_BOMB
import org.rsmod.content.quest.area.misthalin.SceneCamera
import org.rsmod.content.quest.area.misthalin.SceneCreepRate
import org.rsmod.content.quest.area.misthalin.SceneRegion
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The catapult north of Tyras Camp and the guard who is meant to be watching it. He has lived on
 * field rations for a year, and a cooked or roast rabbit is enough to send him off to eat it
 * (`varbit.regicide_given_rabbit`, which also hides him through his multinpc). With him gone,
 * the barrel bomb can be loaded and its fuse lit with a tinderbox.
 *
 * Loading the bomb is the commit: the bomb is taken and the stage set to Tyras's death in the
 * same cycle, before the launch is shown. The launch itself plays in a private copy of the camp,
 * so if it is cut short by a logout the progress is already kept and the bomb is not lost for
 * nothing.
 */
class Catapult
@Inject
constructor(
    private val regicide: RegicideQuest,
    private val scenes: RegicideScenes,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (guard in listOf(CATAPULT_GUARD, CATAPULT_GUARD_VIS)) {
            onOpNpc1(guard) { startDialogue(it.npc) { catapultGuard() } }
            for (rabbit in RABBITS) {
                onOpNpcU(guard, rabbit) { startDialogue(it.npc) { offerRabbit(rabbit) } }
            }
        }
        onOpLocU(CATAPULT, BARREL_BOMB) { loadBomb() }
        onOpLocU(CATAPULT, UNFUSED_BOMB) { mes("The barrel needs a fuse before it can be lit.") }
    }

    private suspend fun Dialogue.catapultGuard() {
        if (regicide.isComplete(player) || regicide.stage(player) >= STAGE_TYRAS_DEAD) {
            chatPlayer(neutral, "All right.")
            chatNpc(quiz, "They say the king died from a flying fire ball. Are you certain no one messed with the catapult while I was eating?")
            chatPlayer(neutral, "I was the only one here. I was watching the whole time.")
            return
        }
        chatPlayer(neutral, "Good day.")
        chatNpc(neutral, "Yeah, what ya want?")
        chatPlayer(quiz, "I was wondering what you were doing out here?")
        chatNpc(neutral, "I was just wondering what I was doing here too. I'm meant to be looking after this catapult but I can't keep my mind on the job. We've been living off field rations for almost a year now.")
        chatNpc(sad, "I keep seeing rabbits in this forest. What I wouldn't give to eat one, but I'm not quick enough to kill any and I'm no good with a bow.")
        val rabbit = RABBITS.firstOrNull { player.inv.contains(it) }
        if (rabbit == null || regicide.stage(player) != STAGE_MAKE_BOMB) {
            chatPlayer(neutral, "I can see how that would be distracting.")
            return
        }
        giveRabbit(rabbit)
    }

    private suspend fun Dialogue.offerRabbit(rabbit: String) {
        if (regicide.stage(player) != STAGE_MAKE_BOMB || player.givenRabbit == 1) {
            chatNpc(neutral, "I'm not hungry right now, thanks.")
            return
        }
        giveRabbit(rabbit)
    }

    private suspend fun Dialogue.giveRabbit(rabbit: String) {
        chatPlayer(happy, "Well I have one here for you.")
        if (access.invDel(player.inv, rabbit).failure) {
            return
        }
        player.givenRabbit = 1
        chatNpc(happy, "You cooked me a rabbit! Thanks a lot. You don't mind watching things while I take five to eat it, do you?")
    }

    private suspend fun ProtectedAccess.loadBomb() {
        arriveDelay()
        if (commitLaunch()) {
            launch()
        }
    }

    /** Takes the bomb and records Tyras's death together, or does nothing at all. */
    internal suspend fun ProtectedAccess.commitLaunch(): Boolean {
        if (regicide.stage(player) != STAGE_MAKE_BOMB) {
            mes("You have no reason to fire the catapult.")
            return false
        }
        if (player.givenRabbit == 0) {
            startDialogue { chatNpcSpecific("Tyras guard", CATAPULT_GUARD_VIS, angry, "Oi! Don't mess with that.") }
            return false
        }
        if (!inv.contains(TINDERBOX)) {
            mes("You need a tinderbox to light the fuse.")
            return false
        }
        if (invDel(inv, BARREL_BOMB).failure) {
            return false
        }
        regicide.advanceTo(this, STAGE_TYRAS_DEAD)
        player.givenRabbit = 0
        return true
    }

    private suspend fun ProtectedAccess.launch() {
        val returnTo = coords
        with(scenes) {
            privateScene(
                template = TEMPLATE,
                vantage = VANTAGE,
                faceAt = CATAPULT_TILE,
                camera = CAMERA,
                returnTo = returnTo,
            ) { r ->
                mes("You load the barrel bomb into the catapult and wind it back...")
                anim(WIND_SEQ)
                catapultAnim(r, LOC_WIND_SEQ)
                delay(3)
                mes("...light the fuse with your tinderbox, and release the catapult.")
                anim(TRIGGER_SEQ)
                catapultAnim(r, LOC_FIRE_SEQ)
                delay(1)
                worldRepo.projAnim(
                    ProjAnim(
                        spotanim = FLIGHT_SPOTANIM.asRSCM(RSCMType.SPOTANIM),
                        startHeight = FLIGHT_HEIGHT,
                        endHeight = 0,
                        startTime = 0,
                        endTime = FLIGHT_CYCLES,
                        angle = FLIGHT_ANGLE,
                        progress = 0,
                        sourceIndex = 0,
                        targetIndex = 0,
                        startCoord = r[CATAPULT_TILE],
                        endCoord = r[TENT_TARGET],
                    ),
                )
                camLookAtV3(r[TENT_TARGET], height = 0, rate = SceneCreepRate, rate2 = SceneCreepRate)
                delay(FLIGHT_TICKS)
                if (r.instanced) {
                    spotanimMap(worldRepo, EXPLOSION_SPOTANIM, r[TENT_TARGET])
                    setTentAlight(r)
                }
                mes("The barrel lands on Tyras's tent and explodes in a ball of flames!")
                delay(HOLD_TICKS)
            }
        }
    }

    private fun catapultAnim(r: SceneRegion, seq: String) {
        val type = ServerCacheManager.getObject(CATAPULT.asRSCM(RSCMType.LOC)) ?: return
        val loc = locRepo.findExact(r[CATAPULT_TILE], type) ?: return
        worldRepo.locAnim(loc, seq)
    }

    /** Swaps the walls of Tyras's tent in the private copy for their burning version. */
    private fun setTentAlight(r: SceneRegion) {
        val wall = TENT_WALL.asRSCM(RSCMType.LOC)
        val burning = ServerCacheManager.getObject(TENT_WALL_BURNING.asRSCM(RSCMType.LOC)) ?: return
        for (x in TENT_MIN.x..TENT_MAX.x) {
            for (z in TENT_MIN.z..TENT_MAX.z) {
                for (loc in locRepo.findAll(r[CoordGrid(x, z, 0)]).filter { it.id == wall }.toList()) {
                    locRepo.change(loc, burning, RegicideScenes.SCENE_LIFESPAN)
                }
            }
        }
    }

    companion object {
        const val CATAPULT = "loc.regicide_catapult"
        const val TENT_WALL = "loc.regicide_tent_human"
        const val TENT_WALL_BURNING = "loc.regicide_tent_human_fire"

        const val WIND_SEQ = "seq.regicide_catapultwind"
        const val TRIGGER_SEQ = "seq.regicide_catapault_trigger"
        const val LOC_WIND_SEQ = "seq.wind_catapult"
        const val LOC_FIRE_SEQ = "seq.fire_catapult"
        const val FLIGHT_SPOTANIM = "spotanim.regicide_barrelflight"
        const val EXPLOSION_SPOTANIM = "spotanim.regicide_barrelflight_exploding"
        const val FLIGHT_HEIGHT = 80
        const val FLIGHT_ANGLE = 40
        const val FLIGHT_CYCLES = 120
        const val FLIGHT_TICKS = 4
        const val HOLD_TICKS = 5

        val CATAPULT_TILE = CoordGrid(2185, 3183, 0)
        val VANTAGE = CoordGrid(2185, 3181, 0)
        val TENT_TARGET = CoordGrid(2186, 3142, 0)
        val TENT_MIN = CoordGrid(2184, 3140, 0)
        val TENT_MAX = CoordGrid(2189, 3143, 0)

        internal val CAMERA =
            SceneCamera(eye = CoordGrid(2192, 3176, 0), eyeHeight = 900, lookAt = CoordGrid(2185, 3183, 0), lookAtHeight = 150)

        /** Tyras Camp and the catapult north of it: zones 272-274 by 392-398, x 2176-2199 and z 3136-3191. */
        internal val TEMPLATE: RegionTemplate =
            RegionTemplate.create {
                copy(272, 392, 0) {
                    regionZoneX = 4
                    regionZoneZ = 4
                    zoneWidth = 3
                    zoneLength = 7
                }
            }

        val SCENE_TILES: List<CoordGrid>
            get() = listOf(CATAPULT_TILE, VANTAGE, TENT_TARGET, TENT_MIN, TENT_MAX, CAMERA.eye, CAMERA.lookAt)
    }
}
