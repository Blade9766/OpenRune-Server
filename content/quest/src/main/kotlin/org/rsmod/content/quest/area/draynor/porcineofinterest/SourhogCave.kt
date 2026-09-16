package org.rsmod.content.quest.area.draynor.porcineofinterest

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.walkTo
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.fadeFromBlack
import org.rsmod.content.quest.area.ardougne.fadeToBlack
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.CORPSE_FOOT_CUT
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.PIG_THING
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.SOURHOG_FOOT
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.SPRIA_CUTSCENE
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.STAGE_AMBUSHED
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.STAGE_IN_CAVE
import org.rsmod.game.entity.Npc
import org.rsmod.game.map.Direction
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The far end of the Sourhog Cave: the gnawed skeleton with its scrawled note, the ambush that
 * ends the player's first visit, and the carcass left behind once the sourhog is dealt with.
 *
 * The skeleton only carries its "Investigate" op at [STAGE_IN_CAVE]; the cache multiloc takes it
 * away again the moment the ambush moves the stage on, so the scene can only ever play once.
 */
@Singleton
class SourhogCave
@Inject
constructor(
    private val porcine: PorcineOfInterestQuest,
    private val npcRepo: NpcRepository,
    private val worldRepo: WorldRepository,
    private val routeFactory: RouteFactory,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(SKELETON) { investigateSkeleton() }
        for (carcass in CARCASS_WITH_FOOT) {
            onOpLoc1(carcass) { cutFoot() }
            onOpLocU(carcass) { useOnCarcass(it.objType) }
        }
        for (carcass in CARCASS_WITHOUT_FOOT) {
            onOpLoc1(carcass) { alreadyCut() }
            onOpLocU(carcass) { alreadyCut() }
        }
    }

    private suspend fun ProtectedAccess.investigateSkeleton() {
        mesbox(
            "The skeleton seems fresh. It looks like the bones have been gnawed clean fairly " +
                "recently.",
        )
        startDialogue {
            chatPlayer(quiz, "There's also a scrawled note. It's barely readable...")
        }
        mesbox(
            "This is what I get for exploring uncharted caves. Looks like I'm trapped down here " +
                "- My entry rope slipped off and now there's nowhere to go but deeper. I think " +
                "I'd be safer just waiting and praying for rescue. If anyone finds this, get out " +
                "while you can.",
        )
        startDialogue { chatPlayer(worried, "Well, that's not exactly reassuring.") }
        playAmbush()
    }

    /**
     * The ambush plays where the player is standing rather than in a copy of the cave, so the two
     * actors are spawned into the world for the length of the scene and taken away again in a
     * `finally` - a disconnect mid-scene must not leave a Pig Thing standing in the cave.
     */
    private suspend fun ProtectedAccess.playAmbush() {
        val pig = spawn(PIG_THING, PorcineCoords.AMBUSH_PIG, Direction.West)
        var spria: Npc? = null
        try {
            beginAmbush()
            faceSquare(pig.coords)
            camMoveTo(CAMERA_FROM, CAMERA_HEIGHT, CAMERA_RATE, CAMERA_RATE)
            camLookAt(PorcineCoords.SKELETON, LOOK_HEIGHT, CAMERA_RATE, CAMERA_RATE)
            delay(1)

            startDialogue { chatPlayer(shocked, "Uhh... Nice piggy?") }

            pig.facePlayer(player)
            pig.anim(SPIT_SEQ)
            worldRepo.soundArea(pig, SPIT_SOUND, radius = SOUND_RADIUS)
            delay(2)
            spotanim(SPIT_IMPACT, height = IMPACT_HEIGHT)
            say(if (wearsGnomeGoggles()) GOGGLES_CRY else EYES_CRY)
            delay(2)
            anim(KNOCKED_DOWN_SEQ)
            delay(2)

            fadeToBlack()
            mesbox("Some time passes...")

            anim(UNCONSCIOUS_SEQ)
            camMoveTo(CAMERA_FROM, CAMERA_HEIGHT, CAMERA_RATE, CAMERA_RATE)
            camLookAt(player.coords, LOOK_HEIGHT, CAMERA_RATE, CAMERA_RATE)
            fadeFromBlack()

            pig.say("*Sniff* *Sniff*")
            delay(3)
            pig.say("*Snort*")
            delay(3)
            pig.walkTo(routeFactory, PIG_EXIT, speed = MoveSpeed.Walk)
            delay(PIG_EXIT_TICKS)
            npcRepo.del(pig, Int.MAX_VALUE)

            startDialogue { chatPlayer(sad, "...") }

            spria = spawn(SPRIA_CUTSCENE, PorcineCoords.AMBUSH_SPRIA, Direction.South)
            spria.say("Well then, what do we have here?")
            delay(3)
            spria.walkTo(routeFactory, PorcineCoords.SKELETON.translateX(-1))
            delay(4)
            spria.say("Still breathing, eh...?")
            delay(3)
            spria.say("Seems I came at just the right time.")
            delay(3)
            spria.say("We'd better get you out of here before that thing returns.")
            delay(3)

            fadeToBlack()
        } catch (e: Exception) {
            logger.error(e) { "A Porcine of Interest ambush failed for ${player.displayName}." }
        } finally {
            if (pig.isSlotAssigned) {
                npcRepo.del(pig, Int.MAX_VALUE)
            }
            spria?.let { if (it.isSlotAssigned) npcRepo.del(it, Int.MAX_VALUE) }
            porcine.advanceTo(this, STAGE_AMBUSHED)
            telejump(PorcineCoords.SPRIA_BEDSIDE, TeleportType.Exempt)
            endAmbush()
            delay(1)
            fadeFromBlack()
            startDialogue { chatPlayer(confused, "W-what happened? Where am I?") }
        }
    }

    /** Actors stand where they are put and do nothing but what the scene tells them to. */
    private fun ProtectedAccess.spawn(type: String, coords: CoordGrid, face: Direction): Npc {
        val npc = Npc(type, coords)
        npc.mode = NpcMode.None
        npc.respawnDir = face
        npcRepo.add(npc, SCENE_LIFETIME)
        return npc
    }

    /**
     * The scene keeps the chatbox up - most of it is spoken through it - so only the camera,
     * the minimap and the entity options are taken away.
     */
    private fun ProtectedAccess.beginAmbush() {
        camModeClose()
        hideEntityOps()
        minimapHideMap()
        closeTopLevelTabsLenient()
    }

    private fun ProtectedAccess.endAmbush() {
        camReset()
        camModeReset()
        showEntityOps()
        minimapReset()
        openTopLevelTabs()
    }

    private fun ProtectedAccess.wearsGnomeGoggles(): Boolean = player.worn.contains(GNOME_GOGGLES)

    private suspend fun ProtectedAccess.cutFoot() {
        if (carries(SOURHOG_FOOT)) {
            alreadyCut()
            return
        }
        if (!holdsCuttingEdge()) {
            mesbox("You try to pull a foot off with your bare hands, but to no avail.")
            startDialogue { chatPlayer(neutral, "Perhaps I should go and get something sharp.") }
            return
        }
        takeFoot()
    }

    private suspend fun ProtectedAccess.takeFoot() {
        if (player.inv.isFull()) {
            mes("You do not have enough space in your inventory.")
            return
        }
        arriveDelay()
        anim(CUT_SEQ)
        delay(CUT_TICKS)
        invAdd(inv, SOURHOG_FOOT)
        player.porcineFootCut = CORPSE_FOOT_CUT
        objbox(SOURHOG_FOOT, "You slice a foot off and place it inside your backpack.")
    }

    private suspend fun ProtectedAccess.useOnCarcass(obj: ItemServerType) {
        if (carries(SOURHOG_FOOT)) {
            alreadyCut()
            return
        }
        if (!cutsFlesh(obj)) {
            startDialogue {
                chatPlayer(neutral, "I don't think that's sharp enough to cut a foot off...")
            }
            return
        }
        takeFoot()
    }

    private suspend fun ProtectedAccess.alreadyCut() {
        startDialogue {
            chatPlayer(
                neutral,
                "I've already collected a foot from this thing. I should take it to Sarah.",
            )
        }
    }

    private companion object {
        private val logger = InlineLogger()

        const val SKELETON = "loc.porcine_skeleton_visible_op"

        /** Both rotations of the carcass, with and without the foot still attached. */
        val CARCASS_WITH_FOOT =
            listOf("loc.porcine_dead_sourhog_withfoot", "loc.porcine_dead_sourhog_withfoot9")
        val CARCASS_WITHOUT_FOOT =
            listOf("loc.porcine_dead_sourhog_withoutfoot", "loc.porcine_dead_sourhog_withoutfoot9")

        const val GNOME_GOGGLES = "obj.aluft_gnome_goggles"

        const val SPIT_SEQ = "seq.sourhog_attack_ranged"
        const val SPIT_SOUND = "synth.sourhog_attack_ranged"
        const val SPIT_IMPACT = "spotanim.sourhog_spit_impact"
        const val KNOCKED_DOWN_SEQ = "seq.human_death_backwards"
        const val UNCONSCIOUS_SEQ = "seq.human_unconscious"
        const val CUT_SEQ = "seq.human_pickuptable"
        const val CUT_TICKS = 2

        const val EYES_CRY = "Argh! My eyes!"
        const val GOGGLES_CRY = "Argh! My eyes! The goggles do nothing!"

        const val IMPACT_HEIGHT = 92
        const val SOUND_RADIUS = 10
        const val CAMERA_HEIGHT = 480
        const val LOOK_HEIGHT = 130
        const val CAMERA_RATE = 100

        /** Where the camera sits for the ambush: south-west of the skeleton, looking back at it. */
        val CAMERA_FROM = CoordGrid(3160, 9672, 0)

        /** North-east along the tunnel, where the pig wanders off to. */
        val PIG_EXIT = CoordGrid(3168, 9681, 0)
        const val PIG_EXIT_TICKS = 6

        /** Long enough for the whole scene; the actors are removed by hand as it ends. */
        const val SCENE_LIFETIME = 200
    }
}
