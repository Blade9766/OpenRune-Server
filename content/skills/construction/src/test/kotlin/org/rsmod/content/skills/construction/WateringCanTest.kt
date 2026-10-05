package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.RoomType

@ResourceLock("ServerCacheManager")
class WateringCanTest {
    @Test
    fun `every habitat needs a watering can`() {
        val habitats = RoomType.MENAGERIE_OUTDOOR.hotspot("habitat")!!.options
        assertEquals(5, habitats.size)
        assertTrue(habitats.all { it.wateringCan })
    }

    @Test
    fun `planted pieces need a watering can and built ones do not`() {
        val planted =
            RoomType.entries.flatMap { room -> room.hotspots.flatMap { it.options } }
                .filter { option -> option.materials.any { "sapling" in it.obj } }
                .filterNot { it.label.endsWith("theme") }
        assertTrue(planted.isNotEmpty())
        assertTrue(planted.all { it.wateringCan }, planted.filterNot { it.wateringCan }.joinToString { it.label })
        val built = RoomType.PARLOUR.hotspots.flatMap { it.options }
        assertTrue(built.none { it.wateringCan })
        Construction.WATERING_CANS.forEach { it.asRSCM(RSCMType.OBJ) }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
