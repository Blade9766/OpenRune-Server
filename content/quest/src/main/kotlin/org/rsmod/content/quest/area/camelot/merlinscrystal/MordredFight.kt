package org.rsmod.content.quest.area.camelot.merlinscrystal

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.MORGAN_COORDS
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.MORGAN_LE_FAYE
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.SIR_MORDRED
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.SMOKE_PUFF_SOUND
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.SMOKE_PUFF_SPOTANIM
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.STAGE_SPOKEN_LANCELOT
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.STAGE_SPOKEN_MORGAN
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sir Mordred on the top floor of Keep Le Faye, and his mother's intervention.
 *
 * Mordred cannot be killed by anyone still on the quest: the blow that would finish him is capped
 * at one hitpoint and Morgan Le Faye appears in a puff of smoke to beg for his life. The price of
 * sparing him is the ritual that frees Merlin, which is the only place in the game it is told.
 * Refusing her - or telling her to go - leaves the fight exactly where it was, and Mordred
 * respawns for another attempt.
 *
 * Hit modifiers run when a hit is queued rather than when it lands, so an earlier in-flight hit
 * can still reduce him to zero; the impact handler clears his death queue before healing him.
 */
@Singleton
class MordredFight
@Inject
constructor(
    private val quest: MerlinsCrystalQuest,
    private val npcRepo: NpcRepository,
    private val worldRepo: WorldRepository,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    /** Players whose spared-Mordred scene is already running, so a second hit cannot start it. */
    private val pleading = HashSet<PlayerUid>()

    override fun ScriptContext.startup() {
        val mordred = npcType(SIR_MORDRED)

        onOpNpc1(SIR_MORDRED) { provoke(it.npc) }

        onOpNpc1(MORGAN_LE_FAYE) {
            startDialogue(it.npc) {
                chatNpc(angry, "I have nothing more to say to you. Leave my keep.")
            }
            if (it.npc.isSlotAssigned) {
                vanishNpc(it.npc)
            }
        }

        onModifyNpcHit(mordred) {
            if (!hit.isFromPlayer || hit.damage <= 0) {
                return@onModifyNpcHit
            }
            val source = hit.sourceUid?.let { PlayerUid(it).resolve(playerList) } ?: return@onModifyNpcHit
            if (hit.damage >= npc.hitpoints && isSpared(source)) {
                hit.damage = npc.hitpoints - 1
            }
        }

        onNpcHit(mordred) {
            if (npc.hitpoints > 1 || !hit.isFromPlayer) {
                return@onNpcHit
            }
            val source = hit.resolvePlayerSource(playerList) ?: return@onNpcHit
            if (!isSpared(source) || !pleading.add(source.uid)) {
                return@onNpcHit
            }
            beg(npc, source)
        }
    }

    /** True while this player is on the quest and has not yet heard Morgan's secret. */
    private fun isSpared(player: Player): Boolean {
        val stage = quest.stage(player)
        return stage >= STAGE_SPOKEN_LANCELOT && !quest.isComplete(player)
    }

    private suspend fun ProtectedAccess.provoke(mordred: Npc) {
        startDialogue(mordred) {
            chatNpc(angry, "You DARE to invade MY stronghold?!")
            chatNpc(angry, "Have at thee, knave!")
        }
        mordred.opPlayer2(player, aiInteractions)
    }

    /**
     * Puts Mordred back on his feet and brings his mother into the room. The scene runs on the
     * player, so a disconnect mid-plea simply leaves Mordred standing.
     */
    private fun beg(mordred: Npc, hero: Player) {
        mordred.clearQueue(DEATH_QUEUE)
        mordred.heal(mordred.baseHitpointsLvl - mordred.hitpoints, showHitsplat = true)
        mordred.clearInteraction()
        mordred.resetMode()
        mordred.facePlayer(hero)

        launcher.launch(hero) {
            try {
                stopAction()
                val morgan = summonMorgan(hero)
                startDialogue(morgan) { plead(mordred, morgan) }
            } finally {
                pleading.remove(hero.uid)
            }
        }
    }

    private fun ProtectedAccess.summonMorgan(hero: Player): Npc {
        val morganId = MORGAN_LE_FAYE.asRSCM(RSCMType.NPC)
        val already =
            npcRepo.findAll(MORGAN_COORDS).firstOrNull {
                it.id == morganId && it.isSlotAssigned
            }
        if (already != null) {
            already.facePlayer(hero)
            return already
        }
        val npc = Npc(MORGAN_LE_FAYE, MORGAN_COORDS)
        npc.respawns = false
        npcRepo.add(npc, SCENE_TICKS)
        npc.facePlayer(hero)
        spotanimMap(worldRepo, SMOKE_PUFF_SPOTANIM, MORGAN_COORDS, SMOKE_HEIGHT)
        soundSynth(SMOKE_PUFF_SOUND)
        return npc
    }

    private suspend fun Dialogue.plead(mordred: Npc, morgan: Npc) {
        chatNpc(sad, "STOP! Please... spare my son.")
        when (
            choice3(
                "Tell me how to untrap Merlin and I might.",
                Answer.Bargain,
                "No. He deserves to die.",
                Answer.Kill,
                "Ok then.",
                Answer.Spare,
            )
        ) {
            Answer.Bargain -> bargain(morgan)
            Answer.Kill -> {
                chatPlayer(angry, "No. He deserves to die.")
                access.ifClose()
                mordred.queueDeath()
                vanish(morgan)
            }
            Answer.Spare -> {
                chatPlayer(neutral, "Ok then.")
                vanish(morgan)
                mesbox("Morgan Le Faye vanishes.")
            }
        }
    }

    private suspend fun Dialogue.bargain(morgan: Npc) {
        chatPlayer(neutral, "Tell me how to untrap Merlin and I might.")
        chatNpc(neutral, "You have guessed correctly that I'm responsible for that.")
        chatNpc(
            neutral,
            "I suppose I can live with that fool Merlin being loose for the sake of my son.",
        )
        chatNpc(neutral, "Setting him free won't be easy though.")
        chatNpc(
            neutral,
            "You will need to find a magic symbol as close to the crystal as you can find.",
        )
        chatNpc(
            neutral,
            "You will then need to drop some bats' bones on the magic symbol while holding a lit " +
                "black candle.",
        )
        chatNpc(neutral, "This will summon a mighty spirit named Thrantax.")
        chatNpc(neutral, "You will need to bind him with magic words.")
        chatNpc(
            neutral,
            "Then you will need the sword Excalibur, with which the spell was bound, in order to " +
                "shatter the crystal.",
        )
        quest.advanceTo(access, STAGE_SPOKEN_MORGAN)
        questions(morgan)
    }

    private suspend fun Dialogue.questions(morgan: Npc) {
        var askedExcalibur = false
        var askedWords = false
        while (true) {
            val topic =
                when {
                    askedExcalibur && askedWords -> Topic.Done
                    askedExcalibur ->
                        choice2(
                            "What are the magic words?",
                            Topic.Words,
                            "OK, I will go do all that.",
                            Topic.Done,
                        )
                    askedWords ->
                        choice2(
                            "So where can I find Excalibur?",
                            Topic.Excalibur,
                            "OK, I will go do all that.",
                            Topic.Done,
                        )
                    else ->
                        choice3(
                            "So where can I find Excalibur?",
                            Topic.Excalibur,
                            "What are the magic words?",
                            Topic.Words,
                            "OK, I will go do all that.",
                            Topic.Done,
                        )
                }
            when (topic) {
                Topic.Excalibur -> {
                    askedExcalibur = true
                    chatPlayer(quiz, "So where can I find Excalibur?")
                    chatNpc(
                        neutral,
                        "The Lady of the Lake has it. I don't know if she'll give it to you " +
                            "though, she can be rather temperamental.",
                    )
                }
                Topic.Words -> {
                    askedWords = true
                    chatPlayer(quiz, "What are the magic words?")
                    chatNpc(neutral, "You will find the magic words at the base of one of the chaos altars.")
                    chatNpc(neutral, "Which chaos altar I cannot remember.")
                }
                Topic.Done -> {
                    chatPlayer(neutral, "OK, I will go do all that.")
                    vanish(morgan)
                    mesbox("Morgan Le Faye vanishes.")
                    return
                }
            }
        }
    }

    private fun Dialogue.vanish(morgan: Npc) {
        if (morgan.isSlotAssigned) {
            access.vanishNpc(morgan)
        }
    }

    private fun ProtectedAccess.vanishNpc(morgan: Npc) {
        spotanimMap(worldRepo, SMOKE_PUFF_SPOTANIM, morgan.coords, SMOKE_HEIGHT)
        soundSynth(SMOKE_PUFF_SOUND)
        npcRepo.del(morgan, Int.MAX_VALUE)
    }

    private fun npcType(name: String): NpcServerType =
        ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $name")

    private enum class Answer {
        Bargain,
        Kill,
        Spare,
    }

    private enum class Topic {
        Excalibur,
        Words,
        Done,
    }

    private companion object {
        const val DEATH_QUEUE = "queue.death"
        const val SCENE_TICKS = 200
        const val SMOKE_HEIGHT = 124
    }
}
