package org.rsmod.content.skills.construction

import org.rsmod.api.combat.commons.hook.PvPAttackRestrictionHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.death.PvPAttackValidateHook
import org.rsmod.content.skills.construction.house.HouseDeathHooks
import org.rsmod.content.skills.construction.house.HousePvPHook
import org.rsmod.content.skills.construction.house.HouseRingRules
import org.rsmod.plugin.module.PluginModule

public class ConstructionModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerDeathHook>(HouseDeathHooks::class.java)
        addSetBinding<PlayerRespawnHook>(HouseDeathHooks::class.java)
        addSetBinding<PlayerDeathCleanupHook>(HouseDeathHooks::class.java)
        addSetBinding<PvPAttackValidateHook>(HousePvPHook::class.java)
        addSetBinding<PvPAttackRestrictionHook>(HouseRingRules::class.java)
    }
}
