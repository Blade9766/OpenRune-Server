package org.rsmod.content.other.bots.world

import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.other.bots.core.BotConfig
import org.rsmod.content.other.bots.core.BotGlobalSettings
import org.rsmod.content.other.bots.core.BotRecord
import org.rsmod.content.other.bots.core.BotRoleType
import org.rsmod.content.other.bots.core.BotSave
import org.rsmod.content.other.bots.core.BotWrite
import org.rsmod.content.other.bots.core.PendingCollect
import org.rsmod.content.other.bots.core.PendingListing
import org.rsmod.content.other.bots.core.SavedObj

class FileBotStoreTest {
    @Test
    fun `records, pending exchange work and settings survive a reload`() {
        val dir = Files.createTempDirectory("bots")
        val path = dir.resolve("bots.json")
        val record =
            BotRecord(
                id = 7,
                name = "Sir Chops",
                config = BotConfig(role = BotRoleType.Mining, site = "varrock_east"),
                save = BotSave(inv = listOf(SavedObj(0, 1265, 1)), bank = listOf(SavedObj(3, 440, 1200))),
                pendingListing = PendingListing(2, 440, 1000, 150, placed = true, placedAtSave = 9),
                pendingCollects = listOf(PendingCollect(1, 123L, 98_000L, 0, taken = true, takenAtSave = 4)),
                salesCounted = mapOf(123L to 1000),
            )
        val store = FileBotStore(path)
        store.load()
        store.write(BotWrite(bots = listOf(record, record.copy(id = 8, name = "Gone"))))
        store.write(BotWrite(deleted = listOf(8), settings = BotGlobalSettings(masterEnabled = true, maxActiveBots = 3)))

        val loaded = FileBotStore(path).load()
        assertEquals(listOf(record), loaded.bots)
        assertEquals(BotGlobalSettings(masterEnabled = true, maxActiveBots = 3), loaded.settings)
        assertTrue(Files.list(dir).use { files -> files.noneMatch { it.fileName.toString().endsWith(".tmp") } })
    }

    @Test
    fun `a failed write changes nothing, so a later write cannot bring it back`() {
        val dir = Files.createTempDirectory("bots")
        val path = dir.resolve("bots.json")
        val store = FileBotStore(path)
        store.load()
        val config = BotConfig(role = BotRoleType.Mining, site = "varrock_east")
        store.write(BotWrite(bots = listOf(BotRecord(id = 1, name = "Kept", config = config))))
        Files.delete(path)
        Files.createDirectories(path.resolve("blocker"))

        assertThrows(Exception::class.java) {
            store.write(BotWrite(bots = listOf(BotRecord(id = 2, name = "Failed", config = config))))
        }
        Files.delete(path.resolve("blocker"))
        Files.delete(path)
        store.write(BotWrite(bots = listOf(BotRecord(id = 3, name = "Later", config = config))))

        assertEquals(listOf("Kept", "Later"), FileBotStore(path).load().bots.map { it.name })
        assertTrue(Files.list(dir).use { files -> files.noneMatch { it.fileName.toString().endsWith(".tmp") } })
    }

    @Test
    fun `writes from two threads never collide`() {
        val dir = Files.createTempDirectory("bots")
        val path = dir.resolve("bots.json")
        val store = FileBotStore(path)
        store.load()
        val config = BotConfig(role = BotRoleType.Mining, site = "varrock_east")
        val threads =
            (0 until 2).map { t ->
                Thread {
                    repeat(50) { i -> store.write(BotWrite(bots = listOf(BotRecord(id = t * 100L + i, name = "B$t$i", config = config)))) }
                }
            }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        assertEquals(100, FileBotStore(path).load().bots.size)
    }
}
