package org.rsmod.content.areas.misc.waterbirth

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule

class WaterbirthModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(DoorSupportAttackHook::class.java)
    }
}

internal class DoorSupportAttackHook : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        val support = WaterbirthPath.ofSupportId(npc.id) ?: return NpcAttackValidateResult.Pass
        if (WaterbirthDoors.canStrike(support, player.coords)) {
            return NpcAttackValidateResult.Pass
        }
        return NpcAttackValidateResult.Deny("This door does not seem to be openable from this side...")
    }
}
