package org.rsmod.content.travel.wayfinder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.Player

class WayfinderPlayerStateTest {
    @Test
    fun `recent journeys keep the newest first without duplicates`() {
        var recent = emptyList<String>()
        for (key in listOf("varrock", "catherby", "varrock", "edgeville")) {
            recent = WayfinderPlayerState.pushRecent(recent, key)
        }
        assertEquals(listOf("edgeville", "varrock", "catherby"), recent)
    }

    @Test
    fun `favourites and recents survive a save and reload by key`() {
        val known = setOf("catherby", "varrock", "edgeville")
        val player = Player()
        WayfinderPlayerState.toggleFavourite(player, "catherby") { it in known }
        WayfinderPlayerState.toggleFavourite(player, "varrock") { it in known }
        WayfinderPlayerState.toggleFavourite(player, "varrock") { it in known }
        WayfinderPlayerState.addRecent(player, "edgeville") { it in known }
        WayfinderPlayerState.addRecent(player, "catherby") { it in known }

        val saved = player.attr.toPersistentMap()
        val reloaded = Player().apply { attr.putAllFromPersistence(saved) }

        assertEquals(listOf("catherby"), WayfinderPlayerState.favouriteKeys(reloaded))
        assertEquals(listOf("catherby", "edgeville"), WayfinderPlayerState.recentKeys(reloaded))
    }

    @Test
    fun `removed destinations drop out on the next write`() {
        val player = Player()
        WayfinderPlayerState.toggleFavourite(player, "old_spot") { true }
        WayfinderPlayerState.toggleFavourite(player, "catherby") { it == "catherby" }
        assertEquals(listOf("catherby"), WayfinderPlayerState.favouriteKeys(player))
    }

    @Test
    fun `recent journeys are capped`() {
        var recent = emptyList<String>()
        for (i in 1..WayfinderPlayerState.RECENT_LIMIT + 5) {
            recent = WayfinderPlayerState.pushRecent(recent, "place_$i")
        }
        assertEquals(WayfinderPlayerState.RECENT_LIMIT, recent.size)
        assertEquals("place_${WayfinderPlayerState.RECENT_LIMIT + 5}", recent.first())
        assertEquals("place_6", recent.last())
    }
}
