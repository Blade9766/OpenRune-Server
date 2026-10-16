package org.rsmod.content.travel.wayfinder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rsmod.map.CoordGrid

class WayfinderRegistryTest {
    @Test
    fun `disabled and unknown keys resolve to nothing`() {
        val registry = WayfinderRegistry.of(listOf(dest("lumbridge", 1), dest("old_spot", 2, enabled = false)))
        assertEquals("lumbridge", registry["lumbridge"]?.key)
        assertEquals("lumbridge", registry["LUMBRIDGE"]?.key)
        assertNull(registry["old_spot"])
        assertNull(registry.bySlot(2))
        assertNull(registry["nowhere"])
        assertEquals(listOf("lumbridge"), registry.all.map { it.key })
    }

    @Test
    fun `duplicate keys and slots are rejected`() {
        assertThrows<IllegalArgumentException> { WayfinderRegistry.validated(listOf(dest("a", 1), dest("a", 2))) }
        assertThrows<IllegalArgumentException> { WayfinderRegistry.validated(listOf(dest("a", 1), dest("b", 1))) }
        assertThrows<IllegalArgumentException> { WayfinderRegistry.validated(listOf(dest("a", 0))) }
        assertThrows<IllegalArgumentException> { WayfinderRegistry.validated(listOf(dest("a", WayfinderRegistry.MAX_SLOT + 1))) }
        assertThrows<IllegalArgumentException> { WayfinderRegistry.validated(listOf(dest("Bad Key", 1))) }
    }

    @Test
    fun `search matches names, keys and keywords with prefixes first`() {
        val registry =
            WayfinderRegistry.of(
                listOf(
                    dest("draynor_willows", 1, name = "Draynor Willows", keywords = listOf("woodcutting")),
                    dest("varrock_woods", 2, name = "Varrock Woods", keywords = listOf("woodcutting", "oak")),
                    dest("wood_guild", 3, name = "Woodcutting Guild"),
                ),
            )
        assertEquals(listOf("wood_guild", "draynor_willows", "varrock_woods"), registry.search("wood").map { it.key })
        assertEquals(listOf("varrock_woods"), registry.search("oak").map { it.key })
        assertTrue(registry.search("   ").isEmpty())
    }

    @Test
    fun `wilderness and high danger destinations need confirmation`() {
        assertTrue(dest("ditch", 1, category = WayfinderCategory.Wilderness).needsConfirmation)
        assertTrue(dest("kbd", 2, danger = WayfinderDanger.Deadly).needsConfirmation)
        assertFalse(dest("dungeon", 3, danger = WayfinderDanger.Low).needsConfirmation)
    }

    companion object {
        fun dest(
            key: String,
            slot: Int,
            name: String = key,
            category: WayfinderCategory = WayfinderCategory.Towns,
            danger: WayfinderDanger = WayfinderDanger.Safe,
            enabled: Boolean = true,
            botAllowed: Boolean = true,
            quest: String? = null,
            keywords: List<String> = emptyList(),
            coords: CoordGrid = CoordGrid(3222, 3218, 0),
        ) = WayfinderDestination(
            key = key,
            slot = slot,
            name = name,
            category = category,
            description = "",
            coords = coords,
            danger = danger,
            warning = null,
            quest = quest,
            botAllowed = botAllowed,
            keywords = keywords,
            enabled = enabled,
        )
    }
}
