package org.rsmod.content.quest.area.varrock.romeojuliet

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.types.MoveRestrict
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.walkTo
import org.rsmod.content.quest.area.varrock.demonslayer.beginCutscene
import org.rsmod.content.quest.area.varrock.demonslayer.endCutscene
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.DRAUL
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.JULIET
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.PHILLIPA
import org.rsmod.game.entity.Npc
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/**
 * Juliet drinks the cadava potion on her balcony while Phillipa overacts her death and Draul comes
 * running. Plays in a private copy of Jagex's cutscene square; [play] returns false when the copy
 * could not be made so the caller can narrate the scene instead.
 */
@Singleton
class JulietScene
@Inject
constructor(private val scenes: RomeoJulietScenes, private val routes: RouteFactory) {

    suspend fun ProtectedAccess.play(): Boolean {
        val origin = player.coords
        fadeToBlack()
        val visit = with(scenes) { enterScene(KEY, PLAYER_TILE, origin) }
        if (visit == null) {
            fadeFromBlack()
            closeFadeOverlay()
            return false
        }
        var played = false
        try {
            delay(1)
            playScene(visit)
            played = true
        } catch (e: Exception) {
            logger.error(e) { "Romeo & Juliet balcony scene failed for ${player.displayName}." }
        } finally {
            fadeToBlack()
            endCutscene()
            with(scenes) { leaveScene() }
            telejump(origin, TeleportType.Exempt)
            delay(1)
            fadeFromBlack()
            closeFadeOverlay()
        }
        return played
    }

    private suspend fun ProtectedAccess.playScene(visit: RomeoJulietScenes.Visit) {
        beginCutscene()
        scenes.removeLoc(visit, BEDROOM_DOOR, DOOR_TILE)
        scenes.removeLoc(visit, BEDROOM_DOOR, INNER_DOOR_TILE)
        val juliet = scenes.spawn(visit, JULIET, JULIET_TILE, Direction.West)
        val phillipa = scenes.spawn(visit, PHILLIPA, PHILLIPA_START, Direction.South)
        val draul = scenes.spawn(visit, DRAUL, DRAUL_START, Direction.South)
        faceDirection(Direction.East)
        camMoveTo(visit.at(CAMERA_FROM), height = CAMERA_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
        camLookAt(visit.at(CAMERA_AT), height = LOOK_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
        delay(1)
        fadeFromBlack()

        walkTo(phillipa, visit.at(PHILLIPA_MARK))
        line(juliet) { chatNpcNoTurn(happy, "Ah, here comes Phillipa, my cousin. She's in on the plan too!") }
        line(juliet) { chatNpcNoTurn(happy, "She's going to make my passing look all the more convincing.") }
        phillipa.faceNpc(juliet)
        line(phillipa) { chatNpcNoTurn(happy, "I'm something of an actress, you know! Good luck, dear cousin!") }
        line(juliet) { chatNpcNoTurn(neutral, "Well... here goes nothing!") }

        juliet.anim(DRINK_SEQ)
        juliet.spotanim(DRINK_SPOTANIM)
        soundSynth(DRINK_SOUND)
        player.midiJingle(CADAVA_POTION_JINGLE)
        delay(DRINK_TICKS)
        line(juliet) { chatNpcNoTurn(shocked, "Urk!") }
        juliet.anim(COLLAPSE_SEQ)
        delay(2)

        line(phillipa) { chatNpcNoTurn(happy, "Oh no... Juliet has... died!") }
        line { chatPlayer(neutral, "It might be more believable if you weren't grinning while you said it...") }
        line(phillipa) { chatNpcNoTurn(confused, "Oh. Yes, I suppose you're right. From the top!") }
        line(phillipa) { chatNpcNoTurn(sad, "Oh no... Juliet has... died?") }
        line { chatPlayer(neutral, "Louder, perhaps? As if your own cousin has just dropped dead?") }
        line(phillipa) { chatNpcNoTurn(neutral, "Right. Yes. I think I've found my motivation now. One more time!") }
        phillipa.say("Oh noooo! Juliet!")
        line(phillipa) {
            chatNpcNoTurn(sad, "OH NO... JULIET HAS... DIED! Oooooh... (sob)... my poor, poor dead cousin!")
        }

        walkTo(draul, visit.at(DRAUL_MARK))
        line(draul) { chatNpcNoTurn(angry, "What is all that wailing about?") }
        draul.faceNpc(juliet)
        line(draul) { chatNpcNoTurn(sad, "My daughter! My poor Juliet... what has happened to you?") }
        line(phillipa) {
            chatNpcNoTurn(sad, "Poor, poor Juliet. We must prepare her body to be laid in the crypt...")
        }
        delay(2)
    }

    /**
     * Scene lines use `chatNpcNoTurn`: `chatNpc` puts the npc in player-face mode, which aborts
     * its route and resets it to wandering whenever the player stands beyond its max range.
     */
    private suspend fun ProtectedAccess.line(npc: Npc, block: suspend Dialogue.() -> Unit) {
        startDialogue(npc) { block() }
    }

    private suspend fun ProtectedAccess.line(block: suspend Dialogue.() -> Unit) {
        startDialogue { block() }
    }

    private suspend fun ProtectedAccess.walkTo(npc: Npc, dest: CoordGrid) {
        npc.clearFacingLock()
        npc.movementLocked = false
        npc.walkTo(routes, dest)
        for (tick in 0 until WALK_TIMEOUT) {
            if (npc.coords == dest) {
                break
            }
            delay(1)
        }
        npc.moveRestrict = MoveRestrict.NoMove
        npc.movementLocked = true
    }

    private companion object {
        private val logger = InlineLogger()

        const val KEY = "romeojuliet_balcony"

        const val BEDROOM_DOOR = "loc.fai_varrock_castle_door"
        const val DRINK_SEQ = "seq.human_drink_from_vial_cadava"
        const val DRINK_SPOTANIM = "spotanim.human_drink_from_vial_cadava_spotanim"
        const val DRINK_SOUND = "synth.drink"
        const val COLLAPSE_SEQ = "seq.human_death"
        const val DRINK_TICKS = 3

        /** The "Cadava Potion" jingle, js5 group 120 (the `jingle.juliet_dies_jingle` gameval is not its group). */
        const val CADAVA_POTION_JINGLE = 120

        const val CAMERA_HEIGHT = 700
        const val LOOK_HEIGHT = 150
        const val CAMERA_RATE = 100
        const val WALK_TIMEOUT = 8

        /** Jagex's cutscene square 60,76 is west Varrock shifted by (+704, +1472). */
        private fun scene(x: Int, z: Int, level: Int = 1) = CoordGrid(x + 704, z + 1472, level)

        val PLAYER_TILE = scene(3157, 3425)
        val JULIET_TILE = scene(3159, 3425)
        val DOOR_TILE = scene(3158, 3426)
        val INNER_DOOR_TILE = scene(3157, 3430)
        val PHILLIPA_START = scene(3158, 3429)
        val PHILLIPA_MARK = scene(3158, 3426)
        val DRAUL_START = scene(3157, 3433)
        val DRAUL_MARK = scene(3158, 3427)
        val CAMERA_FROM = scene(3158, 3418)
        val CAMERA_AT = scene(3158, 3424)
    }
}
