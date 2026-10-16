package org.rsmod.content.other.bots.world

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rsmod.content.travel.wayfinder.WayfinderCategory
import org.rsmod.content.travel.wayfinder.WayfinderDanger
import org.rsmod.content.travel.wayfinder.WayfinderDestination
import org.rsmod.content.travel.wayfinder.WayfinderRegistry
import org.rsmod.map.CoordGrid

class BotWayfinderTest {
    private val bank = CoordGrid(3094, 3492, 0)
    private val anchors = listOf(bank, CoordGrid(3094, 3470, 0), CoordGrid(3104, 3432, 0))

    private val registry =
        WayfinderRegistry.of(
            listOf(
                dest("edgeville", 1, CoordGrid(3087, 3496, 0)),
                dest("wilderness_ditch", 2, CoordGrid(3087, 3523, 0), botAllowed = false, category = WayfinderCategory.Wilderness),
                dest("barbarian_village", 3, CoordGrid(3081, 3421, 0)),
                dest("lumbridge", 4, CoordGrid(3222, 3218, 0)),
                dest("closed", 5, CoordGrid(3094, 3493, 0), enabled = false),
            ),
        )

    @Test
    fun `picks the bot-allowed destination nearest the bank`() {
        assertEquals("edgeville", BotWayfinder.pickDestination(registry, "", bank, anchors, emptySet())?.key)
    }

    @Test
    fun `skips destinations already tried on this trip`() {
        assertEquals("barbarian_village", BotWayfinder.pickDestination(registry, "", bank, anchors, setOf("edgeville"))?.key)
        assertNull(BotWayfinder.pickDestination(registry, "", bank, anchors, setOf("edgeville", "barbarian_village")))
    }

    @Test
    fun `never picks a destination far from every route`() {
        val far = listOf(CoordGrid(2000, 2000, 0))
        assertNull(BotWayfinder.pickDestination(registry, "", CoordGrid(2000, 2000, 0), far, emptySet()))
    }

    @Test
    fun `a configured destination is honoured only while bots may use it`() {
        assertEquals("lumbridge", BotWayfinder.pickDestination(registry, "lumbridge", bank, anchors, emptySet())?.key)
        assertNull(BotWayfinder.pickDestination(registry, "wilderness_ditch", bank, anchors, emptySet()))
        assertNull(BotWayfinder.pickDestination(registry, "closed", bank, anchors, emptySet()))
        assertNull(BotWayfinder.pickDestination(registry, "nowhere", bank, anchors, emptySet()))
    }

    private fun dest(
        key: String,
        slot: Int,
        coords: CoordGrid,
        botAllowed: Boolean = true,
        enabled: Boolean = true,
        category: WayfinderCategory = WayfinderCategory.Towns,
    ) = WayfinderDestination(
        key = key,
        slot = slot,
        name = key,
        category = category,
        description = "",
        coords = coords,
        danger = if (botAllowed) WayfinderDanger.Safe else WayfinderDanger.High,
        warning = null,
        quest = null,
        botAllowed = botAllowed,
        keywords = emptyList(),
        enabled = enabled,
    )
}
