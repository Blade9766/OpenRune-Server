package org.rsmod.content.quest.area.gnomevillage.treegnomevillage

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.isInCombat
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.ORBS
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_ORB_RETURNED
import org.rsmod.content.quest.area.gnomevillage.treegnomevillage.TreeGnomeVillageQuest.Companion.STAGE_WARLORD_SLAIN
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Khazard warlord who carries the last two orbs, north of the stronghold behind West
 * Ardougne. `npc.khazard_warlord` is a varbit-multi npc that only resolves to his Talk-to form
 * (`varbit.khazard_warlord_fighting` at 1 maps to nothing at all, so the varbit must stay 0), so
 * provoking him transmogs the npc into the Attack form for the fight. He only ever fights whoever
 * spoke to him, loses interest after a while, and comes back after each defeat: "warriors blessed
 * by Khazard don't die".
 */
class KhazardWarlord
@Inject
constructor(
    private val treeGnomeVillage: TreeGnomeVillageQuest,
    private val objRepo: ObjRepository,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
    private val aiInteractions: AiPlayerInteractions,
    private val death: NpcDeath,
    private val worldQueues: WorldQueueList,
) : PluginScript() {

    /** Kept at 0: any other value makes the multi-npc vanish for that player. */
    private var Player.fightingVar by intVarBit(FIGHTING_VARBIT)

    private val combatType: NpcServerType by lazy {
        ServerCacheManager.getNpc(WARLORD_COMBAT.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $WARLORD_COMBAT")
    }

    override fun ScriptContext.startup() {
        onOpNpc1(WARLORD) { startDialogue(it.npc) { warlord(it.npc) } }
        for (name in listOf(WARLORD, WARLORD_CHAT, WARLORD_COMBAT)) {
            val type = ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $name")
            onNpcQueue(type, "queue.death") { slain() }
        }
        onPlayerLogin { player.fightingVar = 0 }
    }

    private suspend fun Dialogue.warlord(npc: Npc) {
        when (treeGnomeVillage.stage(player)) {
            in 0 until STAGE_ORB_RETURNED -> {
                chatPlayer(happy, "Hello, how are you?")
                chatNpc(angry, "Don't speak to me you insignificant wretch! Die in the name of Khazard!")
            }
            STAGE_ORB_RETURNED -> {
                chatPlayer(angry, "You there, stop!")
                chatNpc(angry, "Go back to your pesky little green friends.")
                chatPlayer(angry, "I've come for the orbs.")
                chatNpc(neutral, "You're out of your depth traveller. These orbs are part of a much larger picture.")
                chatPlayer(angry, "They're stolen goods, now give them here!")
                chatNpc(laugh, "Ha, you really think you stand a chance? I'll crush you.")
            }
            STAGE_WARLORD_SLAIN -> {
                chatPlayer(confused, "I thought I killed you?")
                chatNpc(angry, "Fool, warriors blessed by Khazard don't die. You can't kill that which is already dead. However I can kill you!")
            }
            else -> {
                chatPlayer(happy, "Hello there.")
                chatNpc(angry, "You think you're so clever. You know nothing!")
                chatPlayer(confused, "What?")
                chatNpc(angry, "I'll crush you and those pesky little green men!")
            }
        }
        access.provoke(npc)
    }

    private fun ProtectedAccess.provoke(npc: Npc) {
        player.fightingVar = 0
        if (npc.transmog == null) {
            npcChangeType(npc, combatType, Int.MAX_VALUE)
        }
        npc.opPlayer2(player, aiInteractions)
        val uid = player.uid
        worldQueues.add(PATIENCE_TICKS) { loseInterest(uid, npc) }
    }

    /** "Bah, enough of you!": a warlord left unfought goes back to his post and his Talk-to form. */
    @OptIn(InternalApi::class)
    private fun loseInterest(uid: PlayerUid, npc: Npc) {
        val player = uid.resolve(playerList) ?: return
        if (npc.transmog == null || !npc.isSlotAssigned) {
            return
        }
        if (player.isInCombat() || npc.isInCombat()) {
            worldQueues.add(PATIENCE_TICKS) { loseInterest(uid, npc) }
            return
        }
        npc.say("Bah, enough of you!")
        npc.resetMode()
        npc.resetTransmog()
        npc.assignUid()
    }

    /**
     * His death queue. The drop table gives his bones; the orbs are only left behind for the
     * player Bolren sent after him, and only while they are still missing.
     */
    private suspend fun StandardNpcAccess.slain() {
        val hero = findHero(playerList)
        val dropCoords = npc.coords
        death.deathWithDrops(this, dropCoords)
        if (hero == null) {
            return
        }
        val stage = treeGnomeVillage.stage(hero)
        val ownsOrbs = stage in STAGE_ORB_RETURNED..STAGE_WARLORD_SLAIN && !hero.inv.contains(ORBS)
        if (ownsOrbs) {
            objRepo.add(ORBS, dropCoords, ORBS_LINGER_TICKS, receiver = hero)
        }
        hero.soundSynth(SCREAM_SOUND)
        launcher.launch(hero) { victory(ownsOrbs) }
    }

    private suspend fun ProtectedAccess.victory(ownsOrbs: Boolean) {
        if (treeGnomeVillage.stage(player) == STAGE_ORB_RETURNED) {
            treeGnomeVillage.advanceTo(this, STAGE_WARLORD_SLAIN)
        }
        if (!ownsOrbs) {
            mes("As the warlord falls to the ground, a ghostly vapour floats upwards from his battle-worn armour.")
            return
        }
        objbox(
            ORBS,
            "As the warlord falls to the ground, a ghostly vapour floats upwards from his " +
                "battle-worn armour. Out of sight you hear a shrill scream in the still air. You " +
                "spot the orbs of protection among his remains.",
        )
    }

    private companion object {
        const val WARLORD = "npc.khazard_warlord"
        const val WARLORD_CHAT = "npc.khazard_warlord_chat"
        const val WARLORD_COMBAT = "npc.khazard_warlord_combat"
        const val FIGHTING_VARBIT = "varbit.khazard_warlord_fighting"
        const val SCREAM_SOUND = "synth.scream"

        /** Roughly thirty seconds without a blow exchanged before he gives up on the player. */
        const val PATIENCE_TICKS = 50
        const val ORBS_LINGER_TICKS = 300
    }
}
