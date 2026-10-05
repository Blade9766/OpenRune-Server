package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.PohRoomRow
import org.rsmod.content.skills.construction.data.FurnitureRows
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.scripts.PohInterfaces

@ResourceLock("ServerCacheManager")
class PohInterfaceCacheTest {
    @Test
    fun `every furniture option has a cache furniture row`() {
        val missing =
            RoomType.entries.flatMap { room ->
                room.hotspots.flatMap { group ->
                    group.options
                        .filter { FurnitureRows.of(it) == null }
                        .map { "${room.label}/${group.key}/${it.label}" }
                }
            }
        assertTrue(missing.isEmpty(), "No furniture row for: $missing")
    }

    @Test
    fun `options on one hotspot resolve to distinct furniture rows`() {
        for (room in RoomType.entries) {
            for (group in room.hotspots) {
                val rows = group.options.mapNotNull { FurnitureRows.of(it)?.rowId }
                assertEquals(rows.size, rows.toSet().size, "${room.label}/${group.key}")
            }
        }
    }

    @Test
    fun `ambiguous furniture rows are resolved by name`() {
        val guessed =
            RoomType.entries.flatMap { room ->
                room.hotspots.flatMap { group ->
                    group.options.mapNotNull { option ->
                        val row = FurnitureRows.of(option) ?: return@mapNotNull null
                        val named = FurnitureRows.namesMatch(option, row)
                        if (named || !FurnitureRows.isAmbiguous(option)) null
                        else "${option.label} -> ${row.name}"
                    }
                }
            }
        assertTrue(guessed.isEmpty(), "Picked by position, not name: $guessed")
    }

    @Test
    fun `every hotspot fits the furniture creation menu`() {
        for (room in RoomType.entries) {
            for (group in room.hotspots) {
                assertTrue(
                    group.options.size <= PohInterfaces.MAX_FURNITURE_ENTRIES,
                    "${room.label}/${group.key}",
                )
            }
        }
    }

    @Test
    fun `room type ids match the room creation menu`() {
        val rows = PohRoomRow.all().associateBy { it.roomType }
        for (room in RoomType.entries) {
            val row = rows[room.roomTypeId]
            checkNotNull(row) { "No poh_room row for ${room.label} (${room.roomTypeId})" }
            assertEquals(row.cost, room.cost, "${room.label} cost")
            assertEquals(row.levelRequirement.first().t1, room.level, "${room.label} level")
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
