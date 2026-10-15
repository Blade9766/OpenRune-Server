package org.rsmod.content.quest.area.desert.shadowofthestorm

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.GHOST_SPAWN
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_CHASE
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_RECRUITING
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.TANYA
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.TANYA_PASSAGE_SPAWN
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.TANYA_SIGIL
import org.rsmod.game.entity.Npc
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The temple passage, after the first summoning.
 *
 * Tanya, Eric and Evil Dave all made it out of the throne room ahead of the player; none of them
 * made it much further. Tanya and the ghost that runs her down stand in the passage while
 * `varbit.agrith_quest` is on [STAGE_CHASE] and disappear the moment the player takes her sigil;
 * Eric stays under `loc.agrith_wizard_rubble` for the rest of the quest, and Evil Dave is handled
 * by his own script because the player has to talk him back into the circle.
 */
@Singleton
class TheChase
@Inject
constructor(
    private val sots: ShadowOfTheStormQuest,
    private val objRepo: ObjRepository,
    private val npcRepo: NpcRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (name in listOf(TANYA_PASSAGE_SPAWN, TANYA, TANYA_SIGIL)) {
            onOpNpc1(name) { tanya(it.npc) }
        }
    }

    /**
     * Tanya is still on her feet when the player reaches her. She does not stay that way: the
     * temple's ghosts have been following the noise since the circle broke.
     */
    private suspend fun ProtectedAccess.tanya(npc: Npc) {
        if (sots.stage(player) != STAGE_CHASE) {
            startDialogue(npc) {
                chatNpc(happy, "Denath picked you himself, did he? He has an eye for it.")
                chatPlayer(quiz, "An eye for what?")
                chatNpc(shifty, "People who will stand where they are told.")
            }
            return
        }
        startDialogue(npc) {
            chatNpc(shocked, "Stay back! Stay away from me!")
            chatPlayer(quiz, "Tanya - what happened to Denath?")
            chatNpc(
                shocked,
                "There was no Denath. Do you understand? There never was. We have been bowing to " +
                    "a demon in a man's coat for two years.",
            )
            chatNpc(worried, "And now the whole temple is awake, and it is coming down the -")
        }
        val ghost = ghostBeside(npc)
        ghost?.faceSquare(npc.coords)
        ghost?.walk(npc.coords)
        ghost?.anim(GHOST_ATTACK_SEQ)
        soundSynth(GHOST_ATTACK_SOUND)
        delay(2)
        npc.say("No -")
        npc.anim(DEATH_SEQ)
        delay(3)
        mesbox("Tanya goes down under the ghost and does not get up. Her sigil rolls free of her hand.")
        objRepo.add(SIGIL, npc.coords, SIGIL_DURATION, player)
        ghost?.spotanim(VANISH_SPOTANIM)
        soundSynth(GHOST_VANISH_SOUND)
        // Both of them are world spawns that only render on the chase stage, so advancing takes
        // them off this player's screen without touching anyone else running the same scene.
        sots.advanceTo(this, STAGE_RECRUITING)
        mes("The ghost thins out into the dark and is gone.")
    }

    /** The temple ghost standing over her; it is a world spawn on the same stage varbit. */
    private fun ghostBeside(tanya: Npc): Npc? =
        npcRepo
            .findAll(ZoneKey.from(tanya.coords), GHOST_SEARCH_ZONES)
            .filter { it.isType(GHOST_SPAWN) }
            .minByOrNull { it.coords.chebyshevDistance(tanya.coords) }

    private companion object {
        const val GHOST_ATTACK_SEQ = "seq.ghost_update_normal_attack"
        const val DEATH_SEQ = "seq.human_death"
        const val VANISH_SPOTANIM = "spotanim.smokepuff"
        const val GHOST_ATTACK_SOUND = "synth.ghost_attack"
        const val GHOST_VANISH_SOUND = "synth.ghost_disappear"
        const val GHOST_SEARCH_ZONES = 1

        /** Long enough that a player who walks away can still come back for it. */
        const val SIGIL_DURATION = 2000
    }
}
