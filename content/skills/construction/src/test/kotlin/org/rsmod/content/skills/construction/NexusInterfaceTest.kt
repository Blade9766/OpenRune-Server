package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.Nexus
import org.rsmod.content.skills.construction.data.NexusConfig

class NexusInterfaceTest {
    private fun config(vararg slots: Int, leftClick: Int = 0, capacity: Int = 4) =
        NexusConfig(capacity, slots.toList(), leftClick)

    @Test
    fun `an added teleport fills the first empty slot, up to the nexus's size`() {
        val config = config(1, 0, 3)
        assertTrue(config.add(7))
        assertEquals(listOf(1, 7, 3), config.held)
        assertFalse(config.add(7))
        assertTrue(config.add(8))
        assertFalse(config.add(9))
        assertEquals(4, config.held.size)
    }

    @Test
    fun `removing leaves a gap and takes the left-click with it`() {
        val config = config(1, 2, 3, leftClick = 2 + Nexus.ALTERNATE_OFFSET)
        config.remove(2)
        assertEquals(listOf(1, 0, 3, 0), config.values.take(4))
        assertEquals(0, config.leftClick)
        config.leftClickSlot(3)
        assertEquals(3, config.leftClick)
    }

    @Test
    fun `slots swap, and the left-click picks a two-way teleport's place`() {
        val config = config(1, 2, 3)
        config.swap(1, 3)
        assertEquals(listOf(3, 2, 1), config.held)
        config.swap(1, 9)
        assertEquals(listOf(3, 2, 1), config.held)

        config.leftClickSlot(2)
        config.choosePlace(second = true, twoWay = true)
        assertEquals(2 + Nexus.ALTERNATE_OFFSET, config.leftClick)
        config.choosePlace(second = false, twoWay = true)
        assertEquals(2, config.leftClick)
        config.choosePlace(second = true, twoWay = false)
        assertEquals(2, config.leftClick)
    }

    @Test
    fun `only teleports the save does not already have are charged for`() {
        val saved = config(1, 2)
        val edited = config(2, 5, 1)
        assertEquals(listOf(5), edited.addedSince(saved))
        assertFalse(edited.sameAs(saved))
        assertTrue(config(1, 2).sameAs(saved))
    }

    @Test
    @ResourceLock("ServerCacheManager")
    fun `the nexus interfaces, working varbits and list order exist`() {
        ServerCacheManager.init(240).close()
        listOf("interface.telenexus", "interface.telenexus_teleport").forEach { it.asRSCM(RSCMType.INTERFACE) }
        listOf(
                "telenexus:non_slotted_list",
                "telenexus:slotted_list",
                "telenexus:scrolling1",
                "telenexus:scrolling2",
                "telenexus:list2",
                "telenexus:click_layer",
                "telenexus:radio_button_options",
                "telenexus:click_text",
                "telenexus:telenexus_donebutton",
                "telenexus:telenexus_confirm",
                "telenexus:telenexus_cancel",
                "telenexus_teleport:rows1",
                "telenexus_teleport:rows2",
                "telenexus_teleport:key_listeners",
                "telenexus_teleport:extra_key_listeners",
                "telenexus_teleport:options_layer",
                "telenexus_teleport:nexus_model",
                "telenexus_teleport:scry_portal",
            )
            .forEach { "component.$it".asRSCM(RSCMType.COMPONENT) }
        (Nexus.TEMP_SLOTS + Nexus.LEFT_CLICK_TEMP + Nexus.TIER_VARBIT).forEach { it.asRSCM(RSCMType.VARBIT) }
        "inv.telenexus_cost".asRSCM(RSCMType.INV)
        "varbit.poh_nexus_tele_scry_mode".asRSCM(RSCMType.VARBIT)
        val pool = ServerCacheManager.getObject("loc.poh_scrying_pool".asRSCM(RSCMType.LOC))!!
        assertEquals("Scry", pool.actions.getOpOrNull(1))
        assertEquals((1..41).toSet(), Nexus.ORDER.toSet())
        assertEquals(listOf(1, 2, 3, 4, 11), Nexus.ORDER.take(5))
    }
}
