package org.rsmod.content.travel.wayfinder.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.pack.PluginPack

class WayfinderPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> = listOf(WayfinderDestinationsTable.destinations())
}
