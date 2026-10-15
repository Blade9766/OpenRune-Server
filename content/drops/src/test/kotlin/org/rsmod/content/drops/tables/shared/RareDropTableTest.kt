package org.rsmod.content.drops.tables.shared

import dtx.core.Single
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.droptable.DropRollItem

class RareDropTableTest {
    @Test
    fun `rare drop table weights total 128`() {
        assertEquals(128.0, rareDropTable.tableEntries.sumOf { it.weight })
    }

    @Test
    fun `key halves are each 20 in 128`() {
        val weights =
            rareDropTable.tableEntries.mapNotNull { entry ->
                val item = (entry.rollable as? Single<*, *>)?.result as? DropRollItem
                item?.let { it.obj to entry.weight }
            }.toMap()

        assertEquals(20.0, weights["obj.keyhalf1"])
        assertEquals(20.0, weights["obj.keyhalf2"])
    }
}
