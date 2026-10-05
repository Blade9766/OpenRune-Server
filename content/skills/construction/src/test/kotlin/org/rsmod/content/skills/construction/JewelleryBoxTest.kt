package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.JewelleryBox
import org.rsmod.content.skills.construction.data.RoomType

@ResourceLock("ServerCacheManager")
class JewelleryBoxTest {
    @Test
    fun `the box's buttons follow the interface's panels`() {
        val names = JewelleryBox.DESTINATIONS.map { it.name }
        assertEquals(27, names.size)
        assertEquals(listOf("Emir's Arena", "Burthorpe", "Warriors' Guild", "Fishing Guild", "Miscellania", "Edgeville"),
            listOf(names[0], names[4], names[9], names[13], names[19], names[23]))
        assertEquals(listOf(9, 19, 27), JewelleryBox.BOXES.values.map(JewelleryBox::buttons))
    }

    @Test
    fun `every box opens its teleport menu and the mounted glory names its four places`() {
        for (box in JewelleryBox.BOXES.keys) {
            assertEquals("Teleport Menu", ops("${box}_base")[1], box)
        }
        assertEquals(JewelleryBox.GLORY.map { it.name }, ops(JewelleryBox.MOUNTED_GLORY).take(4))
        JewelleryBox.LAST_DESTINATION_VARBIT.asRSCM(RSCMType.VARBIT)
        "clientscript.[clientscript,poh_jewellery_box_init]".asRSCM(RSCMType.CLIENTSCRIPT)
        "component.poh_jewellery_box:universe".asRSCM(RSCMType.COMPONENT)
    }

    @Test
    fun `the achievement gallery is unique and builds the jewellery box`() {
        val room = RoomType.ACHIEVEMENT_GALLERY
        assertEquals(true, room.unique)
        assertEquals(27, room.roomTypeId)
        assertEquals(JewelleryBox.BOXES.keys.toList(), room.hotspot("jewellery_box")!!.options.map { it.built.single() })
    }

    private fun ops(loc: String): List<String?> {
        val type = ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC))!!
        return (0..4).map { type.actions.getOpOrNull(it) }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
