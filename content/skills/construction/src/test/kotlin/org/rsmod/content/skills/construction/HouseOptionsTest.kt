package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.HouseStyle

@ResourceLock("ServerCacheManager")
class HouseOptionsTest {
    @Test
    fun `every house options button and varbit exists`() {
        "interface.poh_options".asRSCM(RSCMType.INTERFACE)
        listOf(
                "build_mode_on",
                "build_mode_off",
                "tele_on",
                "tele_off",
                "default_build_mode_on",
                "default_build_mode_off",
                "doors_closed",
                "icon_doors_closed",
                "doors_open",
                "icon_doors_open",
                "doors_none",
                "icon_doors_none",
                "expel_guests",
                "leave_house",
                "call_servant",
                "viewer",
                "roomcount",
            )
            .forEach { "component.poh_options:$it".asRSCM(RSCMType.COMPONENT) }
        listOf(
                "varbit.poh_building_mode",
                "varbit.poh_tele_toggle",
                "varbit.poh_teleport_building_mode",
                "varbit.poh_doors_option",
            )
            .forEach { it.asRSCM(RSCMType.VARBIT) }
    }

    @Test
    fun `every house style hangs a door that exists`() {
        for (style in HouseStyle.entries) {
            val doors = style.doors
            listOf(doors.left, doors.right, doors.leftOpen, doors.rightOpen)
                .forEach { it.asRSCM(RSCMType.LOC) }
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
