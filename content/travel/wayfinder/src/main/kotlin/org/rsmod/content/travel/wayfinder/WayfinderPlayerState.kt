package org.rsmod.content.travel.wayfinder

import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

/**
 * Favourite and recent destinations, saved with the account as lists of destination keys so a
 * renumbered or removed row can never point a player somewhere else. Keys the registry no longer
 * knows are skipped when read and dropped on the next write.
 */
object WayfinderPlayerState {
    const val RECENT_LIMIT = 20

    private val FAVOURITES = AttributeKey<List<String>>(persistenceKey = "wayfinder_favourites")
    private val RECENT = AttributeKey<List<String>>(persistenceKey = "wayfinder_recent")

    fun favouriteKeys(player: Player): List<String> = read(player, FAVOURITES)

    fun recentKeys(player: Player): List<String> = read(player, RECENT)

    fun isFavourite(player: Player, key: String): Boolean = key in favouriteKeys(player)

    /** @return `true` if [key] is now a favourite. */
    fun toggleFavourite(player: Player, key: String, known: (String) -> Boolean): Boolean {
        val current = favouriteKeys(player).filter(known)
        val favourite = key !in current
        player.attr[FAVOURITES] = if (favourite) current + key else current - key
        return favourite
    }

    fun addRecent(player: Player, key: String, known: (String) -> Boolean) {
        player.attr[RECENT] = pushRecent(recentKeys(player).filter(known), key)
    }

    fun pushRecent(recent: List<String>, key: String): List<String> =
        (listOf(key) + recent.filter { it != key }).take(RECENT_LIMIT)

    private fun read(player: Player, key: AttributeKey<List<String>>): List<String> {
        val stored: Any = player.attr[key] ?: return emptyList()
        return (stored as? List<*>)?.filterIsInstance<String>()?.distinct().orEmpty()
    }
}
