package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.Leagues
import org.rsmod.content.skills.construction.data.Leagues.League
import org.rsmod.content.skills.construction.data.RoomType

@ResourceLock("ServerCacheManager")
class LeagueHallTest {
    private val room = RoomType.LEAGUE_HALL

    @Test
    fun `the league hall is a unique room with doors east, south and west`() {
        assertEquals(29, room.roomTypeId)
        assertTrue(room.unique)
        assertEquals(listOf(0, 2, 3), (0..3).filter { room.hasDoor(it, 0) })
        for (group in room.hotspots) {
            group.locs.forEach { it.asRSCM(RSCMType.LOC) }
            group.options.flatMap { it.built }.forEach { it.asRSCM(RSCMType.LOC) }
            group.options.flatMap { it.materials }.forEach { it.obj.asRSCM(RSCMType.OBJ) }
        }
    }

    @Test
    fun `every display variant and league item exists`() {
        for ((base, variants) in Leagues.DISPLAYS) {
            base.asRSCM(RSCMType.LOC)
            for (variant in variants) {
                val type = ServerCacheManager.getObject(variant.asRSCM(RSCMType.LOC))!!
                assertTrue(type.actions.getOpOrNull(2)?.startsWith("Remove-") == true, variant)
            }
        }
        for (league in League.entries) {
            Leagues.METALS.indices.forEach { league.trophy(it).asRSCM(RSCMType.OBJ) }
            league.bannerObj.asRSCM(RSCMType.OBJ)
            (1..Leagues.OUTFIT_TIERS).flatMap { league.outfit(it) }.forEach { it.asRSCM(RSCMType.OBJ) }
        }
        (1..3).forEach {
            Leagues.pedestalLeague(it).asRSCM(RSCMType.VARBIT)
            Leagues.pedestalTrophy(it).asRSCM(RSCMType.VARBIT)
        }
        listOf(Leagues.BANNER_LEAGUE, Leagues.OUTFIT_LEAGUE, Leagues.OUTFIT_TIER).forEach { it.asRSCM(RSCMType.VARBIT) }
        Leagues.TROPHY_CASES.forEach { (closed, open) ->
            closed.asRSCM(RSCMType.LOC)
            open.asRSCM(RSCMType.LOC)
        }
    }

    @Test
    fun `a display shows what its varbits hold, and nothing when they are clear`() {
        val base = Leagues.pedestal(2, "decorative")
        val vars =
            mapOf(Leagues.pedestalLeague(2) to League.TWISTED.ordinal + 1, Leagues.pedestalTrophy(2) to Leagues.METALS.indexOf("dragon") + 1)
        assertEquals("loc.poh_leaguehall_pedestal_2_decorative_twisted_dragon", Leagues.shown(base) { vars[it] ?: 0 })
        assertEquals(base, Leagues.shown(base) { 0 })
        assertEquals(base, Leagues.baseOf("loc.poh_leaguehall_pedestal_2_decorative_twisted_dragon"))
        val outfit = Leagues.outfitStand("oak")
        val outfitVars = mapOf(Leagues.OUTFIT_LEAGUE to League.SHATTERED_RELICS.ordinal + 1, Leagues.OUTFIT_TIER to 3)
        assertEquals("loc.poh_leaguehall_outfitstand_oak_league_3_t3", Leagues.shown(outfit) { outfitVars[it] ?: 0 })
        assertEquals(null, Leagues.shown("loc.poh_wall_deco_1") { 0 })
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
