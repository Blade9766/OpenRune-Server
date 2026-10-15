package org.rsmod.content.quest.area.varrock.romeojuliet

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.area.varrock.demonslayer.beginCutscene
import org.rsmod.content.quest.area.varrock.demonslayer.endCutscene
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.HEART_SPOTANIM
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.PHILLIPA
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.ROMEO
import org.rsmod.game.entity.Npc
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/**
 * The player takes Romeo down into the crypt, where Juliet lies on her tomb. Romeo gives up on her
 * the moment Phillipa steps out of the shadows. Plays in a private copy of the crypt square;
 * [play] returns false when the copy could not be made so the caller can narrate it instead.
 */
@Singleton
class CryptScene
@Inject
constructor(private val scenes: RomeoJulietScenes) {

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
            logger.error(e) { "Romeo & Juliet crypt scene failed for ${player.displayName}." }
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
        val romeo = scenes.spawn(visit, ROMEO, ROMEO_START, Direction.West)
        val phillipa = scenes.spawn(visit, PHILLIPA, PHILLIPA_TILE, Direction.West)
        faceDirection(Direction.West)
        camMoveTo(visit.at(CAMERA_FROM), height = CAMERA_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
        camLookAt(visit.at(CAMERA_AT), height = LOOK_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
        delay(1)
        fadeFromBlack()

        line(romeo) { chatNpcNoTurn(worried, "It's awfully spooky down here...") }
        line { chatPlayer(neutral, "Oh, hush...") }
        line { chatPlayer(happy, "Here we are. Look, there's Juliet, over on that tomb!") }
        line { chatPlayer(neutral, "You go on over to her. I'll hang back over here...") }
        line(romeo) { chatNpcNoTurn(worried, "Umm... alright then...") }

        walk(visit.at(PLAYER_MARK))
        walkAlong(romeo, ROMEO_ROUTE.map(visit::at))
        romeo.lockFacingDirection(Direction.South)
        camMoveTo(visit.at(CLOSE_CAMERA_FROM), height = CLOSE_CAMERA_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
        camLookAt(visit.at(CLOSE_CAMERA_AT), height = LOOK_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
        line(romeo) { chatNpcNoTurn(happy, "Psst... Juliet...") }
        line(romeo) { chatNpcNoTurn(quiz, "Juliet....?") }
        line(romeo) { chatNpcNoTurn(confused, "Oh dear. You do seem to be rather dead.") }

        phillipa.faceNpc(romeo)
        line(phillipa) { chatNpcNoTurn(happy, "Hello, Romeo... I'm Phillipa!") }
        player.midiJingle(ROMEO_FALLS_IN_LOVE_JINGLE)
        romeo.clearFacingLock()
        romeo.faceNpc(phillipa)
        romeo.spotanim(HEART_SPOTANIM)
        line(romeo) { chatNpcNoTurn(happy, "Well, hello! Aren't you a vision!") }
        line(phillipa) {
            chatNpcNoTurn(happy, "Such a pity about Juliet... but perhaps you and I could meet up some time?")
        }
        romeo.spotanim(HEART_SPOTANIM)
        line(romeo) { chatNpcNoTurn(confused, "Juliet? Who's Juliet?") }
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

    /**
     * The crypt floor west of the pillars carries `BLOCK_NPCS` collision, so scene npcs walk as
     * [MoveRestrict.PassThru] along waypoints that are open to players, then are pinned in place.
     */
    private suspend fun ProtectedAccess.walkAlong(npc: Npc, route: List<CoordGrid>) {
        npc.clearFacingLock()
        npc.movementLocked = false
        npc.moveRestrict = MoveRestrict.PassThru
        npc.mode = NpcMode.None
        npc.walk(route)
        val dest = route.last()
        for (tick in 0 until WALK_TIMEOUT) {
            if (npc.coords == dest) {
                break
            }
            if (npc.routeDestination.isEmpty()) {
                val nearest = route.indices.minBy { npc.coords.chebyshevDistance(route[it]) }
                npc.walk(route.subList(nearest, route.size))
            }
            delay(1)
        }
        npc.moveRestrict = MoveRestrict.NoMove
        npc.movementLocked = true
    }

    private companion object {
        private val logger = InlineLogger()

        const val KEY = "romeojuliet_crypt"

        /** The "Romeo Falls in Love" jingle, js5 group 168 (the `jingle.romeo_cutscene` gameval is not its group). */
        const val ROMEO_FALLS_IN_LOVE_JINGLE = 168

        const val CAMERA_HEIGHT = 900
        const val LOOK_HEIGHT = 150
        const val CAMERA_RATE = 100
        const val WALK_TIMEOUT = 20

        val PLAYER_TILE = CoordGrid(2333, 4644, 0)
        val PLAYER_MARK = CoordGrid(2332, 4646, 0)
        val ROMEO_START = CoordGrid(2332, 4644, 0)

        /** Around the coffin at 2329,4644 and the pillar block at 2327..2330,4640..4643. */
        val ROMEO_ROUTE =
            listOf(
                CoordGrid(2331, 4645, 0),
                CoordGrid(2329, 4645, 0),
                CoordGrid(2328, 4644, 0),
                CoordGrid(2325, 4644, 0),
                CoordGrid(2324, 4643, 0),
                CoordGrid(2323, 4643, 0),
            )

        /** Beside the cross-topped tomb behind Juliet's, where she waits for Romeo to notice her. */
        val PHILLIPA_TILE = CoordGrid(2326, 4645, 0)
        val CAMERA_FROM = CoordGrid(2332, 4647, 0)
        val CAMERA_AT = CoordGrid(2325, 4644, 0)

        /** Low and close from the south, framing Romeo behind Juliet's tomb. */
        const val CLOSE_CAMERA_HEIGHT = 500
        val CLOSE_CAMERA_FROM = CoordGrid(2323, 4638, 0)
        val CLOSE_CAMERA_AT = CoordGrid(2323, 4643, 0)
    }
}
