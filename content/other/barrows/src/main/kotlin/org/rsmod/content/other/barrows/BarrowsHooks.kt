package org.rsmod.content.other.barrows

import jakarta.inject.Inject
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.npc.owner.isSpawnOwnedByOther
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule

/**
 * Brothers and door monsters only fight the player who raised them, every kill below ground adds
 * its combat level to that player's reward potential, and dying sends their summons away.
 */
class BarrowsHooks @Inject constructor(private val spawns: BarrowsSpawns) :
    NpcAttackValidateHook, NpcDeathKillHook, PlayerDeathCleanupHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (!spawns.isBarrowsNpc(npc) || !npc.isSpawnOwnedByOther(player)) {
            return NpcAttackValidateResult.Pass
        }
        return NpcAttackValidateResult.Deny("That isn't after you.")
    }

    override fun onKill(context: NpcDeathKillContext) {
        val npc = context.npc
        val hero = context.hero
        spawns.forget(npc)
        if (!spawns.isBarrowsNpc(npc) || !BarrowsCoords.inUnderground(hero.coords)) {
            return
        }
        if (npc.isSpawnOwnedByOther(hero)) {
            return
        }
        BarrowsRun.creditKill(hero, spawns.brotherTypes[npc.id], npc.type.combatLevel)
    }

    override fun cleanup(player: Player) {
        spawns.dismissAll(player)
    }
}

internal class BarrowsModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(BarrowsHooks::class.java)
        addSetBinding<NpcDeathKillHook>(BarrowsHooks::class.java)
        addSetBinding<PlayerDeathCleanupHook>(BarrowsHooks::class.java)
    }
}
