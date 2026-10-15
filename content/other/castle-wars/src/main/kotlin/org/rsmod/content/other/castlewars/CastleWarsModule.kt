package org.rsmod.content.other.castlewars

import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.PlayerDeathItemHook
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.death.PvPAttackValidateHook
import org.rsmod.api.death.PvPCombatXpHook
import org.rsmod.api.death.PvPMaxHitHook
import org.rsmod.api.death.RangedAmmoSaveHook
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.plugin.module.PluginModule

internal class CastleWarsModule : PluginModule() {
    override fun bind() {
        bindInstance<CastleWarsGame>()
        addSetBinding<PvPAttackValidateHook>(CastleWarsHooks::class.java)
        addSetBinding<NpcAttackValidateHook>(CastleWarsHooks::class.java)
        addSetBinding<PvPCombatXpHook>(CastleWarsHooks::class.java)
        addSetBinding<PvPMaxHitHook>(CastleWarsHooks::class.java)
        addSetBinding<RangedAmmoSaveHook>(CastleWarsHooks::class.java)
        addSetBinding<PlayerRestrictionHook>(CastleWarsHooks::class.java)
        addSetBinding<PlayerTeleportValidateHook>(CastleWarsHooks::class.java)
        addSetBinding<PlayerDeathHook>(CastleWarsHooks::class.java)
        addSetBinding<PlayerDeathItemHook>(CastleWarsHooks::class.java)
        addSetBinding<PlayerDeathCleanupHook>(CastleWarsHooks::class.java)
        addSetBinding<PlayerRespawnHook>(CastleWarsHooks::class.java)
    }
}
