package org.rsmod.content.skills.construction.house

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

/**
 * Per-player house data. The decoded state is cached on the player for as long as they are online
 * so the registry and the build scripts all mutate the same object; [save] writes it back to the
 * persistent attribute after every change.
 */
@Singleton
class HouseStore @Inject constructor() {
    fun state(player: Player): HouseState {
        val cached = player.attr[CACHE]
        if (cached != null) {
            return cached
        }
        val decoded = HouseState.decode(player.attr[SAVED].orEmpty())
        player.attr[CACHE] = decoded
        return decoded
    }

    fun save(player: Player) {
        val state = player.attr[CACHE] ?: return
        player.attr[SAVED] = state.encode()
    }

    fun update(player: Player, block: (HouseState) -> Unit) {
        val state = state(player)
        block(state)
        save(player)
    }

    private companion object {
        val SAVED = AttributeKey<String>(persistenceKey = "poh_house")
        val CACHE = AttributeKey<HouseState>()
    }
}
