package org.rsmod.content.quest.area.karamja.shilovillage

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The zombie undead ones that swarm the village gates (and Ah Za Rhoon) only hunt players who
 * have yet to lay Rashiliyia to rest: their hunt mode, `stalk.shilo_undead`, checks
 * `varp.zombiequeen` against the quest's endstate. The hunt mode is set on the npc types because
 * an npc falls back to its type's mode after every hunt; the generic aggression plugin only fills
 * in a mode the type does not already have.
 */
class ShiloGatehouseUndead : PluginScript() {
    override fun ScriptContext.startup() {
        val undead = ShiloVillageQuest.ZOMBIE_UNDEAD_ONES.mapTo(HashSet()) { it.asRSCM(RSCMType.NPC) }
        for (id in undead) {
            ServerCacheManager.getNpc(id)?.huntMode = HUNT_SHILO_UNDEAD
        }
        onEvent<NpcStateEvents.Create> {
            if (npc.type.id in undead) {
                ServerCacheManager.getHunt(HUNT_SHILO_UNDEAD)?.let(npc::setHuntMode)
            }
        }
    }

    private companion object {
        /** Hunt mode from `.data/gamevals/stalk.rscm`; there is no RSCM prefix for them. */
        const val HUNT_SHILO_UNDEAD = 26
    }
}
