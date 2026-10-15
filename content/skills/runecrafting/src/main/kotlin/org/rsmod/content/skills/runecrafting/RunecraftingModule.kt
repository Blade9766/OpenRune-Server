package org.rsmod.content.skills.runecrafting

import org.rsmod.api.inv.storage.PlayerItemStorageHook
import org.rsmod.content.skills.runecrafting.essence.EssenceMineArrivalHook
import org.rsmod.content.skills.runecrafting.essencepouch.EssencePouchStorageHook
import org.rsmod.plugin.module.PluginModule

public class RunecraftingModule : PluginModule() {
    override fun bind() {
        newSetBinding<EssenceMineArrivalHook>()
        addSetBinding<PlayerItemStorageHook>(EssencePouchStorageHook::class.java)
    }
}
