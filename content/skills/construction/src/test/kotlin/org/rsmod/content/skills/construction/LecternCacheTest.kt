package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.scripts.LecternScript

@ResourceLock("ServerCacheManager")
class LecternCacheTest {
    @Test
    fun `every buildable lectern has a tablet list`() {
        val built =
            RoomType.STUDY.hotspot("lectern")!!.options.flatMap { it.built }.toSet()
        assertEquals(built, LecternScript.LECTERNS.keys)
    }

    @Test
    fun `every lectern lists tablets that fit the interface`() {
        for ((lectern, enum) in LecternScript.LECTERNS) {
            val tablets = LecternScript.tabletsOf(enum.asRSCM(RSCMType.ENUM))
            assertTrue(tablets.isNotEmpty(), "$lectern lists no tablets")
            assertTrue(tablets.size <= LecternScript.MAX_ROWS, "$lectern lists ${tablets.size}")
        }
    }

    @Test
    fun `every tablet points at a spell with a level, runes and experience`() {
        val tablets =
            LecternScript.LECTERNS.values
                .flatMap { LecternScript.tabletsOf(it.asRSCM(RSCMType.ENUM)) }
                .distinctBy { it.id }
        val broken =
            tablets
                .filter { tablet ->
                    val spell = tablet.paramOrNull(LecternScript.SPELL_PARENT)
                    spell == null ||
                        spell.paramOrNull(BaseParams.spell_levelreq) == null ||
                        spell.paramOrNull(BaseParams.spell_runetype_1) == null ||
                        spell.paramOrNull(BaseParams.spell_castxp) == null
                }
                .map { RSCM.getReverseMapping(RSCMType.OBJ, it.id) }
        assertTrue(broken.isEmpty(), "Tablets without a usable spell: $broken")
    }

    @Test
    fun `the interface has every component the script binds`() {
        val components =
            (1..LecternScript.MAX_ROWS).map(LecternScript::tabComponent) +
                LecternScript.CONFIRM +
                listOf("make_1", "make_5", "make_10", "make_x", "make_all", "make_some", "makex")
                    .map { "component.teletabs_craft_if:$it" }
        for (component in components) {
            component.asRSCM(RSCMType.COMPONENT)
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
