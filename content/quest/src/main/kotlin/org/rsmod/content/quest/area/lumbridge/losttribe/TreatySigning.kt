package org.rsmod.content.quest.area.lumbridge.losttribe

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.table.MusicRow
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.RING_OF_LIFE
import org.rsmod.content.quest.area.varrock.demonslayer.beginCutscene
import org.rsmod.content.quest.area.varrock.demonslayer.endCutscene
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietScenes
import org.rsmod.game.entity.Npc
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/**
 * The signing of the Lumbridge-Dorgeshuun Treaty in the castle dining room, played in a private
 * copy of Lumbridge Castle's map square: the Duke and Ur-tag at either end of the long table, the
 * player and Mistag as witnesses, and Sigmund listening at the doors. [play] returns false when the
 * copy could not be made, so the caller can finish the quest without the scene.
 */
@Singleton
class TreatySigning
@Inject
constructor(private val scenes: RomeoJulietScenes, private val musicPlayer: MusicPlayer) {

    private val treatyTrack: MusicRow by lazy { MusicRow.getRow(TREATY_TRACK) }

    suspend fun ProtectedAccess.play(): Boolean {
        val origin = player.coords
        fadeToBlack()
        ifSetText(FADE_MESSAGE, TITLE)
        val visit = with(scenes) { enterScene(KEY, PLAYER_TILE, origin) }
        if (visit == null) {
            fadeFromBlack()
            closeFadeOverlay()
            return false
        }
        var played = false
        try {
            delay(TITLE_TICKS)
            playScene(visit)
            played = true
        } catch (e: Exception) {
            logger.error(e) { "Lost Tribe treaty scene failed for ${player.displayName}." }
        } finally {
            fadeToBlack()
            endCutscene()
            with(scenes) { leaveScene() }
            telejump(origin, TeleportType.Exempt)
            musicPlayer.skipTrack(player)
            delay(1)
            fadeFromBlack()
            closeFadeOverlay()
        }
        return played
    }

    private suspend fun ProtectedAccess.playScene(visit: RomeoJulietScenes.Visit) {
        beginCutscene()
        val duke = scenes.spawn(visit, DUKE, DUKE_TILE, Direction.South)
        val urtag = scenes.spawn(visit, URTAG, URTAG_TILE, Direction.North)
        val mistag = scenes.spawn(visit, MISTAG, MISTAG_TILE, Direction.East)
        val sigmund = scenes.spawn(visit, SIGMUND, SIGMUND_TILE, Direction.West)
        faceDirection(Direction.West)
        camMoveTo(visit.at(CAMERA_FROM), height = CAMERA_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
        camLookAt(visit.at(CAMERA_AT), height = LOOK_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
        playTreatyTrack()
        delay(1)
        fadeFromBlack()

        line { chatPlayer(neutral, "Your grace, I present Ur-tag, headman of the Dorgeshuun.") }
        mistag.anim(GOBLIN_BOW_SEQ)
        line(duke) { chatNpcNoTurn(neutral, "Welcome, Ur-tag. I am sorry that your race came under suspicion.") }
        line(duke) { chatNpcNoTurn(neutral, "I assure you that the warmongering element has been dealt with.") }
        line(urtag) { chatNpcNoTurn(sad, "I apologize for the damage to your cellar. I will send workers to repair the hole.") }
        line(duke) { chatNpcNoTurn(happy, "No, let it stay. It can be a route of commerce between our lands.") }
        line(duke) {
            chatNpcNoTurn(
                happy,
                "${player.displayName}, Lumbridge is in your debt. Please accept this ring as a token of my thanks.",
            )
        }
        line(duke) { chatNpcNoTurn(happy, "It is enchanted to save you in your hour of need.") }
        line { objbox(RING_OF_LIFE, "The Duke hands you a ring of life.") }
        line(urtag) { chatNpcNoTurn(happy, "I too thank you. Accept the freedom of the Dorgeshuun mines.") }
        line(urtag) {
            chatNpcNoTurn(
                neutral,
                "These are strange times. I never dreamed that I would see the surface, still less that I " +
                    "would be on friendly terms with its people.",
            )
        }

        camMoveTo(visit.at(SIGMUND_CAMERA_FROM), height = CAMERA_HEIGHT, rate = PAN_RATE, rate2 = PAN_RATE)
        camLookAt(visit.at(SIGMUND_TILE), height = LOOK_HEIGHT, rate = PAN_RATE, rate2 = PAN_RATE)
        delay(PAN_TICKS)
        line(sigmund) { chatNpcNoTurn(angry, "Prattle on, goblin.") }
        line(sigmund) { chatNpcNoTurn(angry, "Soon you will be destroyed!") }
        delay(1)
    }

    private fun ProtectedAccess.playTreatyTrack() {
        runCatching { musicPlayer.unlockAndPlay(player, treatyTrack) }
            .onFailure { musicPlayer.play(player, treatyTrack) }
    }

    private suspend fun ProtectedAccess.line(npc: Npc, block: suspend Dialogue.() -> Unit) {
        startDialogue(npc) { block() }
    }

    private suspend fun ProtectedAccess.line(block: suspend Dialogue.() -> Unit) {
        startDialogue { block() }
    }

    private companion object {
        private val logger = InlineLogger()

        const val KEY = "losttribe_treaty"
        const val TITLE = "~ The Signing of the Lumbridge-Dorgeshuun Treaty ~"
        const val FADE_MESSAGE = "component.fade_overlay:message"
        const val TREATY_TRACK = "dbrow.music_dorgeshuun_treaty"

        const val DUKE = "npc.lost_tribe_cutscene_duke"
        const val URTAG = "npc.lost_tribe_cutscene_urtag"
        const val MISTAG = "npc.lost_tribe_cutscene_mistag"
        const val SIGMUND = "npc.lost_tribe_cutscene_sigmund"
        const val GOBLIN_BOW_SEQ = "seq.cave_goblin_bow"

        const val TITLE_TICKS = 4
        const val CAMERA_HEIGHT = 600
        const val LOOK_HEIGHT = 150
        const val CAMERA_RATE = 100
        const val PAN_RATE = 4
        const val PAN_TICKS = 5

        /** The dining room on the castle's ground floor; the long table runs north to south. */
        val PLAYER_TILE = CoordGrid(3211, 3225, 0)
        val DUKE_TILE = CoordGrid(3209, 3225, 0)
        val URTAG_TILE = CoordGrid(3209, 3218, 0)
        val MISTAG_TILE = CoordGrid(3207, 3219, 0)

        /** Outside the dining room's double doors, listening in. */
        val SIGMUND_TILE = CoordGrid(3214, 3222, 0)

        val CAMERA_FROM = CoordGrid(3211, 3214, 0)
        val CAMERA_AT = CoordGrid(3209, 3222, 0)
        val SIGMUND_CAMERA_FROM = CoordGrid(3219, 3222, 0)
    }
}
