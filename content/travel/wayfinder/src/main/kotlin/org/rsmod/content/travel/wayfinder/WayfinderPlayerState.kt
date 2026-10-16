package org.rsmod.content.travel.wayfinder

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

/**
 * Favourite and recent destinations, saved by destination slot in permanent varps: favourites as
 * one bit per slot across [FAVOURITE_VARPS], recents as up to [RECENT_COUNT] 8-bit slots in one
 * varp, newest in the low byte.
 */
object WayfinderPlayerState {
    const val RECENT_COUNT = 4

    private const val RECENT_VARP = "varp.wayfinder_recent"
    private val FAVOURITE_VARPS =
        listOf(
            "varp.wayfinder_favourites_1",
            "varp.wayfinder_favourites_2",
            "varp.wayfinder_favourites_3",
            "varp.wayfinder_favourites_4",
        )

    fun recentSlots(player: Player): List<Int> = unpackRecent(player.vars[RECENT_VARP])

    fun addRecent(player: Player, slot: Int) {
        VarPlayerIntMapSetter.set(player, RECENT_VARP, pushRecent(player.vars[RECENT_VARP], slot))
    }

    fun isFavourite(player: Player, slot: Int): Boolean {
        val (varp, bit) = favouriteBit(slot)
        return player.vars[varp] and (1 shl bit) != 0
    }

    fun favouriteSlots(player: Player): Set<Int> =
        (1..WayfinderRegistry.MAX_SLOT).filterTo(LinkedHashSet()) { isFavourite(player, it) }

    /** @return `true` if [slot] is now a favourite. */
    fun toggleFavourite(player: Player, slot: Int): Boolean {
        val (varp, bit) = favouriteBit(slot)
        val packed = player.vars[varp] xor (1 shl bit)
        VarPlayerIntMapSetter.set(player, varp, packed)
        return packed and (1 shl bit) != 0
    }

    private fun favouriteBit(slot: Int): Pair<String, Int> {
        require(slot in 1..WayfinderRegistry.MAX_SLOT) { "Bad wayfinder slot $slot" }
        val index = slot - 1
        return FAVOURITE_VARPS[index / Int.SIZE_BITS] to index % Int.SIZE_BITS
    }

    fun unpackRecent(packed: Int): List<Int> =
        (0 until RECENT_COUNT).map { (packed ushr (it * 8)) and 0xFF }.filter { it != 0 }

    fun pushRecent(packed: Int, slot: Int): Int {
        require(slot in 1..WayfinderRegistry.MAX_SLOT) { "Bad wayfinder slot $slot" }
        val slots = (listOf(slot) + unpackRecent(packed).filter { it != slot }).take(RECENT_COUNT)
        return slots.foldIndexed(0) { index, acc, value -> acc or (value shl (index * 8)) }
    }
}
