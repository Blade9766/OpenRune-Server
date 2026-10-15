package org.rsmod.content.events.halloween.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.pack.PluginPack

class HalloweenPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> =
        listOf(HalloweenRewardsTable.rewards(), HalloweenDecorationsTable.decorations())
}
