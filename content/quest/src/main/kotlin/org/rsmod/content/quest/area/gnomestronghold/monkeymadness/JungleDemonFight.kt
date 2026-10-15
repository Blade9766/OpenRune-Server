package org.rsmod.content.quest.area.gnomestronghold.monkeymadness

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.front
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.JUNGLE_DEMON
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.SIGIL
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_ALLIANCE
import org.rsmod.content.quest.area.varrock.demonslayer.beginCutscene
import org.rsmod.content.quest.area.varrock.demonslayer.endCutscene
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.content.quest.manager.QuestInstances
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.isType
import org.rsmod.game.map.Direction
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The battle with the Jungle Demon. Wearing the 10th squad sigil anywhere pulls the player into a
 * private copy of the cavern under Ape Atoll with the demon and the squad, after a short scene
 * of the gnomes taking up their positions. The demon is an ordinary killable npc; when it dies
 * the quest moves on and Zooknock's Talk-to takes the player back to the island.
 */
@Singleton
class JungleDemonFight
@Inject
constructor(
    private val monkeyMadness: MonkeyMadnessQuest,
    private val instances: QuestInstances,
    private val launcher: ProtectedAccessLauncher,
    private val playerList: PlayerList,
    private val death: NpcDeath,
    private val worldQueues: WorldQueueList,
) : PluginScript() {

    private val demonType = ServerCacheManager.getNpc(JUNGLE_DEMON.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $JUNGLE_DEMON")
    private val entering = monkeyMadness.quest.attribute(name = "ENTERING_ARENA", default = false, temp = true)

    override fun ScriptContext.startup() {
        onWearposChange {
            if (wearpos == Wearpos.Front && player.front?.isType(SIGIL) == true) {
                launcher.launch(player) { sigilWorn() }
            }
        }
        onNpcQueue(demonType, "queue.death") { slain() }
        onOpNpc1(ZOOKNOCK_BATTLE) { startDialogue(it.npc) { zooknock() } }
        onOpNpc1(GARKOR_BATTLE) { startDialogue(it.npc) { garkor() } }
    }

    private suspend fun ProtectedAccess.sigilWorn() {
        if (monkeyMadness.stage(player) != STAGE_ALLIANCE || !monkeyMadness.sigilGiven.get(player) || monkeyMadness.demonSlain.get(player)) {
            mes("The sigil is warm to the touch, but nothing happens.")
            return
        }
        if (with(instances) { insideCopy() } || entering.get(player)) {
            return
        }
        entering.set(player, true)
        try {
            mesbox("The sigil grows hot against your chest. Somewhere far below the island, Zooknock begins to chant.")
            fadeToBlack()
            val visit =
                with(instances) {
                    enterCopy(KEY, MonkeyMadness.ARENA_PLAYER, MonkeyMadness.POST_BATTLE_LANDING, listOf(InstanceNpc(JUNGLE_DEMON, MonkeyMadness.ARENA_DEMON)), bossName = "Jungle Demon")
                }
            if (visit == null) {
                fadeFromBlack()
                closeFadeOverlay()
                return
            }
            try {
                delay(1)
                arrival(visit)
            } catch (e: Exception) {
                logger.error(e) { "Jungle Demon arrival scene failed for ${player.displayName}." }
                endCutscene()
                fadeFromBlack()
                closeFadeOverlay()
            }
        } finally {
            entering.set(player, false)
        }
    }

    private suspend fun ProtectedAccess.arrival(visit: QuestInstances.Visit) {
        beginCutscene()
        val gnomes = SQUAD.zip(MonkeyMadness.ARENA_GNOMES).map { (type, tile) -> instances.spawn(visit, type, tile, Direction.North) }
        camMoveTo(visit.at(MonkeyMadness.ARENA_CAMERA_FROM), height = CAMERA_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
        camLookAt(visit.at(MonkeyMadness.ARENA_CAMERA_AT), height = LOOK_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
        faceDirection(Direction.North)
        delay(1)
        fadeFromBlack()
        player.midiJingle(ChapterCards.MEANWHILE_JINGLE)
        soundSynth(MonkeyMadness.SOUND_RUMBLING)
        delay(2)
        gnomes.firstOrNull()?.say("10th squad! Positions!")
        delay(3)
        gnomes.lastOrNull()?.say("The spell holds. It cannot leave this cavern, and neither can we.")
        delay(3)
        say("Then let's finish it.")
        delay(2)
        val demon = instances.npcsIn(visit).firstOrNull { it.type == demonType }
        demon?.say("Gnomes! Little gnomes! Come and be crushed!")
        delay(2)
        endCutscene()
        closeFadeOverlay()
        mesbox("The Jungle Demon towers over the cavern. Its melee reaches two squares; keep your distance and let the squad draw its attention.")
        if (demon != null) {
            worldQueues.add(1) {
                for (gnome in gnomes) {
                    if (gnome.isSlotAssigned) {
                        gnome.facePlayer(player)
                    }
                }
            }
        }
    }

    private suspend fun StandardNpcAccess.slain() {
        val hero = findHero(playerList)
        death.deathWithDrops(this, npc.coords)
        if (hero != null) {
            launcher.launch(hero) { victory() }
        }
    }

    private suspend fun ProtectedAccess.victory() {
        player.midiJingle(ChapterCards.MISSION_OVER_JINGLE)
        if (monkeyMadness.stage(player) == STAGE_ALLIANCE) {
            monkeyMadness.demonSlain.set(player, true)
        }
        mes("The Jungle Demon is dead! Talk to Sergeant Garkor.")
    }

    private suspend fun org.rsmod.api.player.dialogue.Dialogue.garkor() {
        if (!monkeyMadness.demonSlain.get(player)) {
            chatNpc(angry, "Don't stand there talking to me! Kill it!")
            return
        }
        chatNpc(happy, "It's done. I never thought I'd see a human fight like that.")
        chatNpc(neutral, "Awowogei will know the moment we're gone; the squad will scatter and make our own way home. Zooknock can send you back up to the island whenever you're ready.")
        chatNpc(neutral, "Tell the King everything. And tell him the 10th squad never failed him.")
    }

    private suspend fun org.rsmod.api.player.dialogue.Dialogue.zooknock() {
        if (!monkeyMadness.demonSlain.get(player)) {
            chatNpc(neutral, "Not now! The spell needs all my attention.")
            return
        }
        chatNpc(neutral, "Ready to go back up? You will come out in the jungle south of the town; keep your greegree to hand.")
        when (choice2("Yes, send me back.", 1, "Not yet.", 2)) {
            1 -> {
                chatPlayer(neutral, "Yes, send me back.")
                access.leaveArena()
            }
            2 -> chatPlayer(neutral, "Not yet.")
        }
    }

    private suspend fun ProtectedAccess.leaveArena() {
        fadeToBlack()
        val exit = with(instances) { leaveCopy() }
        telejump(exit ?: MonkeyMadness.POST_BATTLE_LANDING, TeleportType.Exempt)
        delay(1)
        fadeFromBlack()
        closeFadeOverlay()
        mes("Zooknock's spell sets you down in the jungle south of Marim.")
    }

    private companion object {
        val logger = InlineLogger()
        const val KEY = "monkeymadness_arena"
        const val ZOOKNOCK_BATTLE = "npc.mm_zooknock_final_battle"
        const val GARKOR_BATTLE = "npc.mm_garkor_final_battle"
        const val CAMERA_HEIGHT = 1200
        const val LOOK_HEIGHT = 300
        const val CAMERA_RATE = 2

        val SQUAD =
            listOf(
                GARKOR_BATTLE,
                ZOOKNOCK_BATTLE,
                "npc.mm_lumo_final_battle",
                "npc.mm_bunkdo_final_battle",
                "npc.mm_carado_final_battle",
                "npc.mm_waymottin_final_battle",
            )
    }
}
