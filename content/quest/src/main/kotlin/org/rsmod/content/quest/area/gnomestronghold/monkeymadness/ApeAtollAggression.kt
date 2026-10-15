package org.rsmod.content.quest.area.gnomestronghold.monkeymadness

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Gives the monkeys of Ape Atoll hunt modes that check `varp.mm_greegree_form`, so a player in
 * monkey form walks past archers and guards that would shoot a human on sight. The generic
 * aggression plugin only ever fills in a hunt mode the type does not already have, so setting
 * these here wins whichever script starts first.
 */
@Singleton
class ApeAtollAggression @Inject constructor() : PluginScript() {

    private val huntsById = MONKEY_HUNTS.mapKeys { it.key.asRSCM(RSCMType.NPC) }

    override fun ScriptContext.startup() {
        for ((npc, mode) in MONKEY_HUNTS) {
            val type = ServerCacheManager.getNpc(npc.asRSCM(RSCMType.NPC)) ?: continue
            type.huntMode = mode
        }
        onEvent<NpcStateEvents.Create> {
            val mode = huntsById[npc.type.id] ?: return@onEvent
            ServerCacheManager.getHunt(mode)?.let(npc::setHuntMode)
        }
    }

    private companion object {
        /** Hunt modes from `.data/gamevals/stalk.rscm`; there is no RSCM prefix for them. */
        const val HUNT_MONKEY_MELEE = 23
        const val HUNT_MONKEY_RANGED = 24

        val MONKEY_HUNTS =
            mapOf(
                "npc.mm_monkey_archer" to HUNT_MONKEY_RANGED,
                "npc.mm_posted_archer" to HUNT_MONKEY_RANGED,
                "npc.mm_ravine_archer" to HUNT_MONKEY_RANGED,
                "npc.mm_monkey_guard" to HUNT_MONKEY_MELEE,
                "npc.mm_religious_guard" to HUNT_MONKEY_MELEE,
                "npc.mm_religious_trapdoor_guard" to HUNT_MONKEY_MELEE,
                "npc.mm_duke" to HUNT_MONKEY_MELEE,
                "npc.mm_oipuis" to HUNT_MONKEY_MELEE,
                "npc.mm_uyoro" to HUNT_MONKEY_MELEE,
                "npc.mm_ouhai" to HUNT_MONKEY_MELEE,
                "npc.mm_uodai" to HUNT_MONKEY_MELEE,
                "npc.mm_padulah" to HUNT_MONKEY_MELEE,
                "npc.mm_zombie_monkey_large" to HUNT_MONKEY_MELEE,
                "npc.mm_zombie_monkey_large_guard" to HUNT_MONKEY_MELEE,
                "npc.mm_zombie_monkey_small" to HUNT_MONKEY_MELEE,
                "npc.mm_skeleton" to HUNT_MONKEY_MELEE,
            )
    }
}
