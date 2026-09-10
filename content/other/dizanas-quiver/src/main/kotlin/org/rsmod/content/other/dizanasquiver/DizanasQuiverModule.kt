package org.rsmod.content.other.dizanasquiver

import org.rsmod.api.death.PlayerDeathItemHook
import org.rsmod.plugin.module.PluginModule

class DizanasQuiverModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerDeathItemHook>(DizanasQuiverDeathHook::class.java)
    }
}
