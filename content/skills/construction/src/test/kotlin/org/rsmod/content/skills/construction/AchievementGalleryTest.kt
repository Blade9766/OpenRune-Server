package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.Gallery
import org.rsmod.content.skills.construction.data.Gallery.Cape
import org.rsmod.content.skills.construction.data.Gallery.Lair
import org.rsmod.content.skills.construction.data.RoomType

@ResourceLock("ServerCacheManager")
class AchievementGalleryTest {
    private val room = RoomType.ACHIEVEMENT_GALLERY

    @Test
    fun `every gallery space has furniture and every piece exists`() {
        assertEquals(
            listOf("altar", "jewellery_box", "adventure_log", "boss_lair", "display", "quest_list"),
            room.hotspots.map { it.key },
        )
        for (group in room.hotspots) {
            group.locs.forEach { it.asRSCM(RSCMType.LOC) }
            group.options.flatMap { it.built }.forEach { it.asRSCM(RSCMType.LOC) }
            group.options.flatMap { it.materials }.forEach { it.obj.asRSCM(RSCMType.OBJ) }
        }
    }

    @Test
    fun `every lair, jar and cape exists with its ops`() {
        for (lair in Lair.entries) {
            val type = ServerCacheManager.getObject(lair.loc.asRSCM(RSCMType.LOC))!!
            assertEquals(listOf("Configure", "Jars"), (0..1).map { type.actions.getOpOrNull(it) }, lair.name)
            lair.jar.asRSCM(RSCMType.OBJ)
        }
        for (cape in Cape.entries) {
            cape.obj.asRSCM(RSCMType.OBJ)
            val type = ServerCacheManager.getObject(cape.loc.asRSCM(RSCMType.LOC))!!
            assertEquals("Take", type.actions.getOpOrNull(3), cape.name)
        }
        Gallery.LAIR_VARP.asRSCM(RSCMType.VARP)
        Gallery.CAPE_VARP.asRSCM(RSCMType.VARP)
    }

    @Test
    fun `the lair display keeps its jars and the lair on show apart`() {
        var varp = Gallery.withJar(0, Lair.VORKATH, held = true)
        varp = Gallery.withJar(varp, Lair.MAD_ANGEL, held = true)
        varp = Gallery.withShown(varp, Lair.MAD_ANGEL)
        assertTrue(Gallery.hasJar(varp, Lair.VORKATH))
        assertFalse(Gallery.hasJar(varp, Lair.KRAKEN))
        assertEquals(Lair.MAD_ANGEL, Gallery.shownLair(varp))
        assertEquals(Lair.MAD_ANGEL.loc, Gallery.shown(Gallery.LAIR_BLANK, varp, 0))
        assertEquals(Gallery.LAIR_BLANK, Gallery.shown(Gallery.LAIR_BLANK, Gallery.withShown(varp, null), 0))
        assertEquals(Cape.MAX_INFERNAL.loc, Gallery.shown(Gallery.CAPE_BLANK, 0, Cape.MAX_INFERNAL.ordinal + 1))
        assertEquals(Gallery.CAPE_BLANK, Gallery.baseOf(Cape.QUEST_T.loc))
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
