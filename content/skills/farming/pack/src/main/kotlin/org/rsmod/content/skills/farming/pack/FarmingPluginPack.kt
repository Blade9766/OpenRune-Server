package org.rsmod.content.skills.farming.pack

import dev.openrune.cache.filestore.definition.InterfaceType
import dev.openrune.pack.PluginPack

class FarmingPluginPack : PluginPack() {
    override fun interfaces(): List<InterfaceType> = listOf(buildFarmingToolStoreInterface())
}
