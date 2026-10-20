package org.rsmod.content.areas.zeah.catacombs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.baseVar
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class CatacombsOfKourendCacheTest {

    @Test fun `each vine opens the hole that leads back down to it, for good`() {
        for (exit in CatacombsOfKourend.EXITS) {
            assertEquals("Climb-up", loc(exit.vine).actions.getOpOrNull(0), exit.vine)
            val hole = loc(exit.hole)
            val unlock = exit.unlock.asRSCM(RSCMType.VARBIT)
            assertEquals(unlock, hole.multiVarBit, exit.hole)
            val open = hole.multiLoc[1] and 0xFFFF
            assertEquals("Enter", checkNotNull(ServerCacheManager.getObject(open)).actions.getOpOrNull(0))
            val varbit = checkNotNull(ServerCacheManager.getVarbit(unlock)) { exit.unlock }
            assertEquals(VarpLifetime.Perm, ServerCacheManager.getVarp(varbit.baseVar.id)!!.scope)
        }
    }

    private fun loc(name: String) =
        checkNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { name }

    private companion object {
        lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic @BeforeAll fun load() {
            cache = ServerCacheManager.init(241)
        }

        @JvmStatic @AfterAll fun close() {
            cache.close()
        }
    }
}
