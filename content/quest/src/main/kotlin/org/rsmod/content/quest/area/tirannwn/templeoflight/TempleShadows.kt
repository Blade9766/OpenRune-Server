package org.rsmod.content.quest.area.tirannwn.templeoflight

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.PlayerInvUpdateHook
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The temple's shadows hunt players like any always-aggressive melee monster (the wiki lists them
 * as aggressive; the generated aggression list misses them under their own name), except that
 * once the quest is done they leave alone anyone carrying the crystal trinket. Their hunt mode,
 * `stalk.mourning_shadow`, checks the server varp [PEACE_VARP], which follows the trinket in the
 * inventory and the quest's completion.
 */
@Singleton
class TempleShadows @Inject constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        val type = ServerCacheManager.getNpc(SHADOW.asRSCM(RSCMType.NPC))
        if (type != null) {
            type.huntMode = HUNT_SHADOW
        }
        val shadowId = SHADOW.asRSCM(RSCMType.NPC)
        onEvent<NpcStateEvents.Create> {
            if (npc.type.id == shadowId) {
                ServerCacheManager.getHunt(HUNT_SHADOW)?.let(npc::setHuntMode)
            }
        }
    }

    companion object {
        const val SHADOW = "npc.mourning_shadow_beast"
        const val PEACE_VARP = "varp.mourning2_shadow_peace"

        /** Hunt mode from `.data/gamevals/stalk.rscm`; there is no RSCM prefix for them. */
        const val HUNT_SHADOW = 25

        fun syncPeace(player: Player, quest: MourningsEndPart2Quest) {
            val calm = if (quest.unlocked(player) && player.inv.contains(MourningsEndPart2Quest.TRINKET)) 1 else 0
            if (player.vars[PEACE_VARP] != calm) {
                VarPlayerIntMapSetter.set(player, PEACE_VARP, calm)
            }
        }
    }
}

class TrinketPeaceHook @Inject constructor(private val quest: MourningsEndPart2Quest) : PlayerInvUpdateHook {
    override fun onInvUpdated(player: Player, inv: Inventory) {
        if (inv === player.inv) {
            TempleShadows.syncPeace(player, quest)
        }
    }
}
