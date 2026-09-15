package org.rsmod.content.quest.area.gnomestronghold.grandtree

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.BLACK_DEMON
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.GLOUGH_BATTLE
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_DEMON_SLAIN
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_TRAPDOOR_OPEN
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Below the watchtower trapdoor. The first descent plays Glough's gloating before he sets his
 * black demon on the player; later descents just wake the demon again. The demon is a normal,
 * killable npc that only lives ten minutes, so a player who leaves can come back for a fresh one.
 */
class BlackDemonFight
@Inject
constructor(
    private val grandTree: GrandTreeQuest,
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
    private val aiInteractions: AiPlayerInteractions,
    private val death: NpcDeath,
    private val worldQueues: WorldQueueList,
    private val search: NpcSearch,
) : PluginScript() {

    private val demonType =
        ServerCacheManager.getNpc(BLACK_DEMON.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $BLACK_DEMON")

    override fun ScriptContext.startup() {
        onOpLoc1(TRAPDOOR_OPEN) { descend() }
        onNpcQueue(demonType, "queue.death") { slain() }
        onOpNpc1(GLOUGH_BATTLE) { startDialogue(it.npc) { chatNpc(angry, "Just die, human!") } }
    }

    private suspend fun ProtectedAccess.descend() {
        anim(GrandTree.LADDER_SEQ)
        delay(1)
        telejump(landNear(GrandTree.DEMON_LANDING))
        if (grandTree.stage(player) != STAGE_TRAPDOOR_OPEN) {
            return
        }
        if (grandTree.seenGloughScene.get(player)) {
            wakeDemon()
            return
        }
        // Marked before the scene so a broken scene is skipped next time rather than replayed.
        grandTree.seenGloughScene.set(player, true)
        gloughsPet()
    }

    private suspend fun ProtectedAccess.gloughsPet() {
        val glough = gloughInLair() ?: spawnGlough()
        glough.facePlayer(player)
        try {
            hideEntityOps()
            startDialogue(glough) {
                chatPlayer(quiz, "Hello?")
                chatPlayer(quiz, "Anybody?")
                chatPlayer(quiz, "Glough?")
                chatPlayer(quiz, "Glough?")
                chatNpc(angry, "You really are becoming a headache! Well, at least now you can die knowing you were right, it will save me having to hunt you down like all the other human filth of Gielinor!")
                chatPlayer(angry, "You're crazy, Glough!")
                chatNpc(laugh, "Bah! Well, soon you'll see, the gnomes are ready to fight, in three weeks this tree will be dead wood, in ten weeks it will be 30 battleships! Finally we will rid the world of the disease called humanity!")
                chatPlayer(angry, "What makes you think I'll let you get away with it?")
                chatNpc(laugh, "Fool... meet my little friend!")
            }
            fadeToBlack()
            wakeDemon()
            soundSynth(DEMON_APPROACH_SOUND)
            delay(1)
        } finally {
            showEntityOps()
            fadeFromBlack()
            closeFadeOverlay()
        }
    }

    private fun gloughInLair(): Npc? =
        search.find(GrandTree.GLOUGH_LAIR, GLOUGH_BATTLE, LAIR_RADIUS, HuntVis.Off)

    private fun spawnGlough(): Npc {
        val glough = Npc(GLOUGH_BATTLE, GrandTree.GLOUGH_LAIR)
        glough.mode = NpcMode.None
        npcRepo.add(glough, GLOUGH_TICKS)
        return glough
    }

    /** Spawns the demon unless one is already prowling the lair, then sets it on the player. */
    private fun ProtectedAccess.wakeDemon() {
        val existing = search.find(GrandTree.DEMON_SPAWN, BLACK_DEMON, LAIR_RADIUS, HuntVis.Off)
        val demon =
            existing
                ?: Npc(demonType, GrandTree.DEMON_SPAWN).also {
                    npcRepo.add(it, DEMON_TICKS)
                    it.anim(APPEAR_SEQ)
                }
        demon.opPlayer2(player, aiInteractions)
    }

    private suspend fun StandardNpcAccess.slain() {
        val hero = findHero(playerList)
        death.deathWithDrops(this, npc.coords)
        if (hero != null) {
            launcher.launch(hero) { victory() }
        }
    }

    private fun ProtectedAccess.victory() {
        if (grandTree.stage(player) == STAGE_TRAPDOOR_OPEN) {
            grandTree.advanceTo(this, STAGE_DEMON_SLAIN)
        }
        val glough = gloughInLair() ?: return
        glough.clearFacingLock()
        glough.say("Mummy!")
        worldQueues.add(FLEE_DELAY_TICKS) {
            if (glough.isSlotAssigned) {
                glough.walk(GrandTree.GLOUGH_FLEE)
            }
        }
        worldQueues.add(FLEE_DELAY_TICKS + FLEE_WALK_TICKS) {
            if (glough.isSlotAssigned) {
                npcRepo.del(glough, Int.MAX_VALUE)
            }
        }
        mes("Glough flees up the passage.")
    }

    private companion object {
        const val TRAPDOOR_OPEN = "loc.grandtree_trapdoortoweropen"
        const val APPEAR_SEQ = "seq.demon_appear"
        const val DEMON_APPROACH_SOUND = "synth.demon_approach"
        const val LAIR_RADIUS = 12

        /** The demon gives up and vanishes after ten minutes. */
        const val DEMON_TICKS = 1000
        const val GLOUGH_TICKS = 3000
        const val FLEE_DELAY_TICKS = 3
        const val FLEE_WALK_TICKS = 12
    }
}
