package org.rsmod.content.quest.area.desert.icthlarin

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenFullOverlay
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_FREED
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_PRIEST_POSSESSED
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

/**
 * The two scenes that close the quest. At the ceremony the Wanderer teleports into the chamber
 * through the unholy symbol the player hid, reveals herself as Amascut the Devourer and turns one
 * of the priests against the rest. Afterwards, leaving the chamber brings back the last memory:
 * Icthlarin himself stopping the fleeing thief and breaking his sister's hold on them.
 *
 * The actors are spawned only for the scene and removed at its end, whatever happens in between.
 */
@Singleton
class Ceremony
@Inject
constructor(
    private val quest: IcthlarinsLittleHelperQuest,
    private val fights: PyramidFights,
    private val npcRepo: NpcRepository,
    private val eventBus: EventBus,
) {
    suspend fun ProtectedAccess.devourerRevealed() {
        val actors = mutableListOf<Npc>()
        try {
            startScene()
            telejump(SophanemCoords.CEREMONY_PLAYER, TeleportType.Exempt)
            delay(1)
            faceSquare(SophanemCoords.CEREMONY_HIGH_PRIEST)
            camMoveTo(SophanemCoords.CEREMONY_CAMERA, height = CAMERA_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
            camLookAt(SophanemCoords.CEREMONY_HIGH_PRIEST, height = LOOK_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
            startDialogue {
                chatNpcSpecific("High Priest", HIGH_PRIEST_HEAD, quiz, "Ah there you are. Do you have the holy symbol?")
                chatPlayer(shocked, "Wait! We can't begin the ceremony!")
                chatNpcSpecific("High Priest", HIGH_PRIEST_HEAD, confused, "What? But we've already started.")
                chatPlayer(worried, "But the wanderer is coming!")
            }
            ifClose()
            val wanderer = actor(WANDERER, SophanemCoords.CEREMONY_WANDERER)
            actors += wanderer
            wanderer.spotanim(ARRIVE_SPOT)
            soundSynth(ARRIVE_SOUND)
            wanderer.facePlayer(player)
            delay(2)
            startDialogue {
                chatNpcSpecific("High Priest", HIGH_PRIEST_HEAD, shocked, "What the...")
                chatNpcSpecific(
                    "Wanderer",
                    WANDERER,
                    laugh,
                    "You have done well, adventurer. All my brother's dogs in one place, with no one " +
                        "around to save them.",
                )
                chatNpcSpecific("High Priest", HIGH_PRIEST_HEAD, shocked, "It can't be... It's the Devourer!")
            }
            ifClose()
            npcRepo.del(wanderer, Int.MAX_VALUE)
            val amascut = actor(AMASCUT, SophanemCoords.CEREMONY_WANDERER)
            actors += amascut
            amascut.facePlayer(player)
            soundSynth(HYPNOTISE)
            startDialogue {
                chatNpcSpecific("Amascut", AMASCUT, angry, "Adventurer, kill these pathetic priests.")
                chatPlayer(angry, "I'm not your slave any more!")
                chatNpcSpecific("Amascut", AMASCUT, neutral, "Shame. Not to worry though, I have plenty more.")
            }
            ifClose()
            val possessed = actor(POSSESSED_ACTOR, SophanemCoords.CEREMONY_POSSESSED)
            actors += possessed
            amascut.faceSquare(possessed.coords)
            startDialogue {
                chatNpcSpecific("Amascut", AMASCUT, angry, "You! Priest of the dog lord. You are now mine. Kill your brethren!")
            }
            ifClose()
            possessed.spotanim(POSSESSION_SPOT)
            soundSynth(HYPNOTISE)
            delay(1)
            startDialogue {
                chatNpcSpecific("Possessed Priest", POSSESSED_ACTOR, neutral, "Yes mistress...")
                chatNpcSpecific("Amascut", AMASCUT, laugh, "Have fun...")
            }
            ifClose()
            amascut.spotanim(ARRIVE_SPOT)
            soundSynth(ARRIVE_SOUND)
            delay(1)
            npcRepo.del(amascut, Int.MAX_VALUE)
            startDialogue {
                chatNpcSpecific("High Priest", HIGH_PRIEST_HEAD, worried, "Protect us, adventurer! We must complete the ceremony!")
            }
            ifClose()
        } finally {
            for (actor in actors) {
                if (actor.isSlotAssigned) {
                    npcRepo.del(actor, Int.MAX_VALUE)
                }
            }
            endScene()
        }
        quest.advanceTo(this, STAGE_PRIEST_POSSESSED)
        fights.summonPriest(player)
    }

    /**
     * The final flashback. The player relives their flight from the pyramid with the stolen jar,
     * and Icthlarin barring the way. It ends outside the pyramid, free at last.
     */
    suspend fun ProtectedAccess.icthlarinIntervenes() {
        var icthlarin: Npc? = null
        try {
            ifClose()
            fadeToBlack()
            startScene()
            telejump(SophanemCoords.ICTHLARIN_PLAYER, TeleportType.Exempt)
            delay(1)
            camMoveTo(SophanemCoords.ICTHLARIN_CAMERA, height = CAMERA_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
            camLookAt(SophanemCoords.ICTHLARIN_LOOK, height = LOOK_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
            val god = actor(ICTHLARIN, SophanemCoords.ICTHLARIN_SPOT)
            icthlarin = god
            god.facePlayer(player)
            faceSquare(god.coords)
            fadeFromBlack()
            player.ifOpenFullOverlay(FLASHBACK_OVERLAY, eventBus)
            startDialogue {
                mesbox("All of a sudden, a final sense of deja-vu sweeps over you...")
                chatNpcSpecific("Icthlarin", ICTHLARIN, neutral, "And what do we have here?")
                chatPlayer(neutral, "I must do as the mistress commands...")
                chatNpcSpecific("Icthlarin", ICTHLARIN, neutral, "Did my sister really think that I would not sense her presence here?")
                chatNpcSpecific("Icthlarin", ICTHLARIN, angry, "Amascut! Release your hold on this human!")
            }
            ifClose()
            god.anim(ICTHLARIN_CAST_SEQ)
            spotanim(RELEASE_SPOT)
            soundSynth(RELEASE_SOUND)
            delay(2)
            startDialogue { chatPlayer(shocked, "Argh...") }
            ifClose()
            fadeToBlack()
        } finally {
            icthlarin?.let { if (it.isSlotAssigned) npcRepo.del(it, Int.MAX_VALUE) }
            player.ifCloseOverlay(FLASHBACK_OVERLAY, eventBus)
            endScene()
            telejump(SophanemCoords.PYRAMID_DOORSTEP, TeleportType.Exempt)
            delay(1)
            fadeFromBlack()
            closeFadeOverlay()
        }
        quest.advanceTo(this, STAGE_FREED)
        startDialogue {
            chatPlayer(
                neutral,
                "Well I guess that explains why Amascut's control over me was broken. Not every day " +
                    "the god of the dead comes to your rescue.",
            )
        }
    }

    /** Camera and scene control without hiding the chatbox the actors speak through. */
    private fun ProtectedAccess.startScene() {
        camModeClose()
        hideEntityOps()
        minimapHideMap()
    }

    private fun ProtectedAccess.endScene() {
        camReset()
        camModeReset()
        showEntityOps()
        minimapReset()
    }

    private fun actor(type: String, coords: CoordGrid): Npc {
        val npc = Npc(type, coords)
        npc.mode = NpcMode.None
        npcRepo.add(npc, SCENE_TICKS)
        npc.respawns = false
        return npc
    }

    private companion object {
        const val WANDERER = "npc.ics_little_redheadlady_ceremony"
        const val AMASCUT = "npc.ics_little_devourer_ceremony"
        const val POSSESSED_ACTOR = "npc.ics_little_priest_ceremony_possessed"
        const val ICTHLARIN = "npc.ics_little_ic"
        const val HIGH_PRIEST_HEAD = "npc.ics_little_hipriest_ceremony_op"

        const val FLASHBACK_OVERLAY = "interface.ics_flashback"

        const val ARRIVE_SPOT = "spotanim.smokepuff_large"
        const val ARRIVE_SOUND = "synth.smokepuff"
        const val POSSESSION_SPOT = "spotanim.curse_impact"
        const val HYPNOTISE = "synth.hypnotise"
        const val ICTHLARIN_CAST_SEQ = "seq.human_castwave"
        const val RELEASE_SPOT = "spotanim.curse_impact"
        const val RELEASE_SOUND = "synth.ics_spectre_remove"

        const val CAMERA_HEIGHT = 700
        const val LOOK_HEIGHT = 150
        const val CAMERA_RATE = 100
        const val SCENE_TICKS = 200
    }
}
