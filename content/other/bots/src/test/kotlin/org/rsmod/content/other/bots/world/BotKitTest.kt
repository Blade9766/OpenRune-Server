package org.rsmod.content.other.bots.world

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.other.bots.core.BotSave
import org.rsmod.content.other.bots.core.SavedObj
import org.rsmod.game.stat.PlayerSkillXPTable

@ResourceLock("ServerCacheManager")
class BotKitTest {
    private val rod by lazy { "obj.fly_fishing_rod".asRSCM(RSCMType.OBJ) }
    private val feather by lazy { "obj.feather".asRSCM(RSCMType.OBJ) }
    private val scimitar by lazy { "obj.iron_scimitar".asRSCM(RSCMType.OBJ) }
    private val bronzeScimitar by lazy { "obj.bronze_scimitar".asRSCM(RSCMType.OBJ) }

    private val kit =
        StarterKit(
            inv = listOf(KitItem("obj.fly_fishing_rod")),
            worn = listOf(KitItem("obj.iron_scimitar")),
            bank = listOf(KitItem("obj.feather", 1000)),
            levels = mapOf("fishing" to 30),
        )

    private fun apply(save: BotSave, kit: StarterKit = this.kit): BotSave? =
        BotKit.applyKit(save, kit) { name -> runCatching { name.asRSCM(RSCMType.OBJ) }.getOrNull() }

    private fun BotSave.total(id: Int): Int = (inv + worn + bank).sumOf { if (it.id == id) it.count else 0 }

    @Test
    fun `an empty bot gets the whole kit`() {
        val save = requireNotNull(apply(BotSave()))
        assertEquals(1, save.inv.single { it.id == rod }.count)
        assertEquals(scimitar, save.worn.single().id)
        assertEquals(1000, save.total(feather))
        assertEquals(30, PlayerSkillXPTable.getLevelFromFineXP(save.xp.getValue("stat.fishing")))
    }

    @Test
    fun `applying a kit twice never duplicates items`() {
        val once = apply(BotSave())!!
        val twice = apply(once)!!
        assertEquals(once, twice)
        assertEquals(1, twice.total(rod))
        assertEquals(1, twice.total(scimitar))
        assertEquals(1000, twice.total(feather))
    }

    @Test
    fun `partial stacks are topped up to the kit count`() {
        val start = BotSave(bank = listOf(SavedObj(0, feather, 400)))
        val save = apply(start)!!
        assertEquals(1000, save.total(feather))
        assertEquals(1, save.bank.count { it.id == feather })
    }

    @Test
    fun `a full inventory leaves the save unchanged`() {
        val full = BotSave(inv = (0 until 28).map { SavedObj(it, bronzeScimitar, 1) })
        assertNull(apply(full))
        assertEquals(28, full.total(bronzeScimitar))
    }

    @Test
    fun `an occupied worn slot is not replaced`() {
        val start = BotSave(worn = listOf(SavedObj(3, bronzeScimitar, 1)))
        assertNull(apply(start, kit.copy(inv = emptyList(), bank = emptyList(), levels = emptyMap())))
    }

    @Test
    fun `levels already above the kit are not lowered`() {
        val high = BotSave(xp = mapOf("stat.fishing" to PlayerSkillXPTable.getFineXPFromLevel(60)))
        val save = apply(high, kit.copy(inv = emptyList(), worn = emptyList(), bank = emptyList()))!!
        assertEquals(60, PlayerSkillXPTable.getLevelFromFineXP(save.xp.getValue("stat.fishing")))
    }

    @Test
    fun `an unknown item fails the whole kit`() {
        assertNull(apply(BotSave(), kit.copy(bank = listOf(KitItem("obj.not_a_real_item")))))
    }

    @Test
    fun `every bundled site kit applies to a new bot`() {
        for (site in BotSites.reload().values) {
            val kit = site.kit ?: continue
            assertNotNull(apply(BotSave(), kit), "kit for ${site.key}")
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
        }
    }
}
