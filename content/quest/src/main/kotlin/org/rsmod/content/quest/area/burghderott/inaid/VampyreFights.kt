package org.rsmod.content.quest.area.burghderott.inaid

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * What the quest's vampyres have in common, for both the blood tithe fight and Ivan's escort.
 *
 * - Every quest npc spawned for a fight belongs to one player; nobody else may attack it.
 * - The juvinates are tier 2 vampyres: only silver weapons hurt them in melee, and Efaritay's aid
 *   lets anything else through at half damage. Hits from the Myreque and other npcs always land.
 * - A juvinate is never killed. Once it is down to a quarter of its hitpoints, or would die, it
 *   laughs, turns to mist and is gone; that counts as dealt with.
 * - Gadderanks does not die either: he falls, and the fight that owns him takes it from there.
 */
@Singleton
class VampyreFights
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
) : PluginScript() {

    fun interface Listener {
        fun dispatched(npc: Npc, owner: PlayerUid)

        fun hitByPlayer(npc: Npc) {}
    }

    private val owners = HashMap<Npc, PlayerUid>()
    private val listeners = HashMap<Npc, Listener>()

    override fun ScriptContext.startup() {
        onEvent<NpcStateEvents.Delete> {
            owners.remove(npc)
            listeners.remove(npc)
        }
        for (juvinate in JUVINATES) {
            val type = npcType(juvinate)
            onModifyNpcHit(type) { applyTierTwo(hit) }
            onNpcHit(type) {
                if (hit.isFromPlayer) {
                    listeners[npc]?.hitByPlayer(npc)
                }
                if (npc.hitpoints * MIST_FRACTION <= npc.baseHitpointsLvl && npc.hitpoints > 0) {
                    mist(npc)
                }
            }
            onNpcQueue(type, "queue.death") { mist(npc) }
        }
        onNpcQueue(npcType(GADDERANKS_FIGHTING), "queue.death") { dispatch(npc) }
    }

    fun own(npc: Npc, player: Player, listener: Listener) {
        owners[npc] = player.uid
        listeners[npc] = listener
    }

    fun ownerOf(npc: Npc): PlayerUid? = owners[npc]

    fun isQuestFighter(npc: Npc): Boolean = npc in owners

    /** Applies the tier 2 rules to [hit]; exposed so tests can check a weapon without a fight. */
    fun applyTierTwo(hit: HitBuilder) {
        if (!hit.isFromPlayer) {
            return
        }
        val source = hit.sourceUid?.let { PlayerUid(it).resolve(playerList) } ?: return
        hit.damage = tierTwoDamage(hit.damage, hit.type, hit.righthandType()?.id, EFARITAYS_AID in source.worn)
    }

    internal fun mist(npc: Npc) {
        if (!npc.isSlotAssigned) {
            return
        }
        npc.say(MIST_LINE)
        npc.spotanim(MIST_SPOT)
        dispatch(npc)
    }

    internal fun dispatch(npc: Npc) {
        val owner = owners.remove(npc)
        val listener = listeners.remove(npc)
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
        if (owner != null && listener != null) {
            listener.dispatched(npc, owner)
        }
    }

    private fun npcType(name: String) =
        ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $name")

    companion object {
        const val GADDERANKS_FIGHTING = "npc.burgh_gadderanks_attackable"
        const val EFARITAYS_AID = "obj.vampyre_ring"
        const val MIST_LINE = "Ha, ha, ha, time to disappear and rejuvenate!"
        const val MIST_SPOT = "spotanim.misty"
        const val MIST_FRACTION = 4

        val JUVINATES =
            listOf(
                "npc.burgh_vampire_juve_1_attackable",
                "npc.burgh_vampire_juve_2_attackable",
                "npc.burgh_ivan_temple_vampire_juve_1",
                "npc.burgh_ivan_temple_vampire_juve_2",
            )

        /** Silver weaponry that harms tier 2 vampyres in melee; none of it needs Efaritay's aid. */
        val SILVER_WEAPONS =
            listOf(
                "obj.silver_sickle",
                "obj.silver_sickle_blessed",
                "obj.dagger_wolfbane",
                "obj.anma_axe",
                "obj.silverlight",
                "obj.agrith_silverlight_dyed",
                "obj.darklight",
                "obj.arclight",
                "obj.ivandis_flail",
                "obj.blisterwood_sickle",
            ) + (1..10).map { "obj.burgh_rod_command_final_$it" }

        private val silverIds by lazy { SILVER_WEAPONS.map { it.asRSCM(RSCMType.OBJ) }.toSet() }

        /**
         * Silver in melee: full damage, 10% more with Efaritay's aid. Anything else: half damage
         * with Efaritay's aid, nothing without it.
         */
        fun tierTwoDamage(damage: Int, type: HitType, weapon: Int?, efaritay: Boolean): Int {
            val silver = type == HitType.Melee && weapon != null && weapon in silverIds
            return when {
                silver && efaritay -> damage + damage / 10
                silver -> damage
                efaritay -> damage / 2
                else -> 0
            }
        }
    }
}

/** A fight's npcs are their owner's alone. */
class VampyreFightAttackHook @Inject constructor(private val fights: VampyreFights) : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        val owner = fights.ownerOf(npc) ?: return NpcAttackValidateResult.Pass
        if (owner != player.uid) {
            return NpcAttackValidateResult.Deny("That's someone else's fight.")
        }
        return NpcAttackValidateResult.Pass
    }
}
