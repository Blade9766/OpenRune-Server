package org.rsmod.content.quest.area.varrock.dragonslayer

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.quest.area.varrock.demonslayer.beginCutscene
import org.rsmod.content.quest.area.varrock.demonslayer.endCutscene
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/**
 * The voyage to Crandor. It plays on a private copy of the Lady Lumbridge's berth at Port
 * Sarim: Ned and Jenkins on deck, clouds, then Elvarg out of the storm. Jenkins dies in the
 * fire, the ship runs aground, and the player comes round on the beach at Crandor with the
 * quest moved on to [DragonSlayerQuest.STAGE_ON_CRANDOR] and the hull holed again.
 *
 * Whatever happens inside the scene the player always ends up on the beach with the camera,
 * interface and fade overlay restored.
 */
@Singleton
class Voyage
@Inject
constructor(
    private val dragonSlayer: DragonSlayerQuest,
    private val instances: DragonSlayerInstances,
    private val worldRepo: WorldRepository,
) {
    /** The first crossing, with the full scene. */
    suspend fun ProtectedAccess.sail() {
        if (dragonSlayer.stage(player) != DragonSlayerQuest.STAGE_NED_ABOARD) {
            return
        }
        fadeToBlack()
        val visit = with(instances) { enterCopy(KEY, PLAYER_TILE, LadyLumbridge.DOCK_ARRIVAL) }
        if (visit == null) {
            fadeFromBlack()
            closeFadeOverlay()
            return
        }
        try {
            // Let the client rebuild the copy before the camera or overlay are touched.
            delay(1)
            playScene(visit)
        } catch (e: Exception) {
            logger.error(e) { "Dragon Slayer voyage failed for ${player.displayName}." }
        } finally {
            for (npc in instances.npcsIn(visit)) {
                instances.remove(npc)
            }
            fadeToBlack()
            endCutscene()
            with(instances) { leaveCopy() }
            runAground()
            anim(UNCONSCIOUS_SEQ)
            delay(1)
            fadeFromBlack()
            closeFadeOverlay()
        }
        mesbox("You are knocked unconscious and later awake on an ash-strewn beach.")
        resetAnim()
    }

    /** Later crossings once the ship has been patched up again: no scene, same wreck. */
    suspend fun ProtectedAccess.sailAgain() {
        fadeToBlack()
        runAground()
        anim(UNCONSCIOUS_SEQ)
        delay(1)
        fadeFromBlack()
        closeFadeOverlay()
        mesbox("Ned steers the Lady Lumbridge through the reefs, but the surf drives her onto the beach at Crandor once more.")
        resetAnim()
    }

    /** Puts the player on the beach, moves the quest on, and holes the hull again. */
    private fun ProtectedAccess.runAground() {
        telejump(DragonSlayerQuest.CRANDOR_BEACH, TeleportType.Exempt)
        dragonSlayer.setStage(this, DragonSlayerQuest.STAGE_ON_CRANDOR)
        dragonSlayer.repairStage.set(player, 0)
        dragonSlayer.syncVars(player)
    }

    private suspend fun ProtectedAccess.playScene(visit: DragonSlayerInstances.Visit) {
        beginCutscene()
        camMoveTo(visit.at(CAMERA_FROM), height = CAMERA_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
        camLookAt(visit.at(CAMERA_AT), height = LOOK_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)

        val ned = instances.spawn(visit, NED, NED_TILE, Direction.East)
        val jenkins = instances.spawn(visit, JENKINS, JENKINS_TILE, Direction.West)
        faceDirection(Direction.North)
        delay(1)

        fadeFromBlack()
        ned.say("Ah, it's good to feel the salt spray on my face once again!")
        delay(3)
        ned.say("And this is a mighty fine ship. She don't look like much, but she handles like a dream.")
        delay(4)
        say("How much longer until we reach Crandor?")
        delay(3)
        ned.say("Not long now! According to the chart we'd see Crandor already, if it wasn't for those clouds on the horizon.")
        delay(4)
        mes("Clouds surround the ship.")
        soundSynth(SOUND_THUNDER)
        delay(2)
        jenkins.say("Looks like there's a storm coming up, cap'n. Soon we won't be able to see anything!")
        delay(4)
        ned.say("Oh, well. The weather had been so good up until now.")
        delay(3)

        val elvarg = instances.spawn(visit, ELVARG, ELVARG_TILE, Direction.South)
        elvarg.anim(ELVARG_FLY_SEQ)
        soundSynth(SOUND_WINGS)
        delay(2)
        say("Did you see that?")
        delay(2)
        ned.say("See what?")
        delay(2)
        say("I thought I saw something up above us.")
        elvarg.anim(ELVARG_FLY_SEQ)
        delay(2)

        elvarg.anim(ELVARG_FIRE_SEQ)
        soundSynth(SOUND_SCREAM)
        delay(1)
        rainFire(visit, FIRE_TILES_FIRST)
        delay(2)
        ned.say("It's the dragon!")
        delay(3)

        elvarg.anim(ELVARG_FIRE_SEQ)
        soundSynth(SOUND_BREATH)
        delay(1)
        rainFire(visit, FIRE_TILES_SECOND)
        jenkins.say("Aaargh!")
        jenkins.anim(JENKINS_DEATH_SEQ)
        delay(3)
        instances.remove(jenkins)
        ned.say("We're going to sink!")
        delay(3)
        say("Look! Land ahead!")
        delay(2)
        ned.say("We're going to crash!")
        delay(2)
        soundSynth(SOUND_CRASH)
        mes("<col=800000>CRASH!</col>")
        delay(2)
    }

    private fun ProtectedAccess.rainFire(visit: DragonSlayerInstances.Visit, tiles: List<CoordGrid>) {
        soundSynth(SOUND_FIRE)
        for (tile in tiles) {
            spotanimMap(worldRepo, FIRE_SPOTANIM, visit.at(tile), height = FIRE_HEIGHT)
        }
    }

    private companion object {
        private val logger = InlineLogger()

        const val KEY = "dragonslayer_voyage"

        const val NED = "npc.dragonslayer_ned_cutscene"
        const val JENKINS = "npc.dragonslayer_jenkins_cutscene"
        const val ELVARG = "npc.dragonslayer_elvarg_cutscene"

        const val ELVARG_FLY_SEQ = "seq.dragon_slayer_qip_elvarg_fly"
        const val ELVARG_FIRE_SEQ = "seq.dragonslayer_elvarg_fire"
        const val JENKINS_DEATH_SEQ = "seq.human_death"

        /** The player lies knocked out on the beach until they click through the message. */
        const val UNCONSCIOUS_SEQ = "seq.human_unconscious"
        const val FIRE_SPOTANIM = "spotanim.dragon_ranged_fire_attack"
        const val FIRE_HEIGHT = 50

        const val SOUND_THUNDER = "synth.dragonslayer_thunder"
        const val SOUND_WINGS = "synth.dragonslayer_flapwings"
        const val SOUND_SCREAM = "synth.dragonslayer_dragonscream"
        const val SOUND_BREATH = "synth.dragonslayer_dragonbreath"
        const val SOUND_FIRE = "synth.dragonslayer_burningfire"
        const val SOUND_CRASH = "synth.dragonslayer_crash_ship"

        const val CAMERA_HEIGHT = 900
        const val LOOK_HEIGHT = 250
        const val CAMERA_RATE = 100

        /* World tiles on and around the Lady Lumbridge's deck; the deck is level 1. */
        val PLAYER_TILE = CoordGrid(3046, 3208, 1)
        val NED_TILE = CoordGrid(3044, 3208, 1)
        val JENKINS_TILE = CoordGrid(3048, 3209, 1)

        /** Over the water north of the bow; Elvarg is 4x4. */
        val ELVARG_TILE = CoordGrid(3045, 3212, 1)

        val CAMERA_FROM = CoordGrid(3053, 3200, 1)
        val CAMERA_AT = CoordGrid(3046, 3209, 1)

        val FIRE_TILES_FIRST = listOf(CoordGrid(3043, 3209, 1), CoordGrid(3045, 3207, 1), CoordGrid(3047, 3209, 1))
        val FIRE_TILES_SECOND = listOf(CoordGrid(3048, 3209, 1), CoordGrid(3046, 3208, 1), CoordGrid(3049, 3207, 1))
    }
}
