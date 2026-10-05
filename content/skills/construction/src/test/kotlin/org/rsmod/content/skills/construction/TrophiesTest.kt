package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.data.Trophies
import org.rsmod.content.skills.construction.data.Trophies.Kind

@ResourceLock("ServerCacheManager")
class TrophiesTest {
    @Test
    fun `every display and trophy exists in the cache`() {
        Trophies.DISPLAYS.keys.forEach { it.asRSCM(RSCMType.LOC) }
        for (trophy in Trophies.ALL) {
            (trophy.raw + trophy.stuffed).forEach { it.asRSCM(RSCMType.OBJ) }
            assertEquals(trophy.raw.size, trophy.stuffed.size, trophy.key)
            trophy.skill?.asRSCM(RSCMType.STAT)
        }
        Trophies.COMBAT_SKILLS.forEach { it.asRSCM(RSCMType.STAT) }
        listOf(
                "varbit.poh_trophies_heads",
                "varbit.poh_trophies_fish",
                "varbit.poh_trophy_head_shown",
                "varbit.poh_trophy_fish_shown",
            )
            .forEach { it.asRSCM(RSCMType.VARBIT) }
    }

    @Test
    fun `mounted trophies fit their varbits`() {
        val heads = Trophies.ALL.count { it.kind == Kind.HEAD }
        val fish = Trophies.ALL.count { it.kind == Kind.FISH }
        assertTrue(heads <= 9 && fish <= 10, "heads=$heads fish=$fish")
        assertTrue(maxOf(heads, fish) <= 15, "shown index must fit four bits")
    }

    @Test
    fun `a display shows only mounted trophies of its tier or lower`() {
        val heads = Trophies.ALL.filter { it.kind == Kind.HEAD }
        val hand = heads.first { it.key == "crawlinghand" }
        val kbd = heads.first { it.key == "kbd" }
        val all = (1 shl heads.size) - 1
        assertEquals("loc.poh_trophy_crawlinghand_mahogany", Trophies.shownOn(Kind.HEAD, 2, heads.indexOf(hand) + 1, all))
        assertNull(Trophies.shownOn(Kind.HEAD, 2, heads.indexOf(kbd) + 1, all))
        assertNull(Trophies.shownOn(Kind.HEAD, 3, heads.indexOf(hand) + 1, 0))
        assertNull(Trophies.shownOn(Kind.HEAD, 3, 0, all))
    }

    @Test
    fun `a display showing a trophy maps back to the empty display`() {
        assertEquals("loc.poh_trophy_head_blank_gilded", Trophies.blankOf("loc.poh_trophy_kbd_gilded"))
        assertEquals("loc.poh_trophy_fish_blank_oak", Trophies.blankOf("loc.poh_trophy_bass_oak"))
        val built = RoomType.SKILL_HALL.hotspots.flatMap { it.options }.flatMap { it.built }
        for (kind in Kind.entries) {
            for (tier in 1..3) {
                assertTrue(kind.blank(tier) in built, kind.blank(tier))
            }
        }
    }

    @Test
    fun `every built loc in every room exists in the cache`() {
        val locs = RoomType.entries.flatMap { it.hotspots }.flatMap { it.options }.flatMap { it.built }
        locs.forEach { it.asRSCM(RSCMType.LOC) }
        val refunds = RoomType.entries.flatMap { it.hotspots }.flatMap { it.options }.flatMap { it.refund }
        refunds.forEach { it.obj.asRSCM(RSCMType.OBJ) }
    }

    @Test
    fun `every talking head has its chathead npc`() {
        val heads = listOf("crawlinghand", "cockatrice", "basilisk", "kurask", "abyssaldemon")
        val trophies = Trophies.ALL.filter { it.kind == Kind.HEAD }
        for ((key, npc) in listOf("crawlinghand", "cockatrice", "basilisk", "kurask", "abyssal").zip(heads)) {
            val trophy = trophies.first { it.key == key }
            for (tier in trophy.tier..3) {
                "npc.poh_mounted_${npc}_${Kind.HEAD.woods[tier - 1]}".asRSCM(RSCMType.NPC)
            }
        }
        listOf("kbd_left", "kbd_middle", "kbd_right", "kq", "vorkath", "hydra")
            .forEach { "npc.poh_mounted_$it".asRSCM(RSCMType.NPC) }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
