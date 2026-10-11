package org.rsmod.content.events.halloween

import jakarta.inject.Inject
import org.rsmod.api.music.Music
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.player.music.MusicAreaTrackHook
import org.rsmod.api.random.GameRandom
import org.rsmod.api.table.MusicRow
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

class HalloweenMusic
@Inject
constructor(
    private val season: HalloweenSeason,
    private val random: GameRandom,
    private val repo: MusicRepository,
) : MusicAreaTrackHook {
    private val tracks: List<Music> by lazy {
        MusicRow.all().filter { it.holiday == HOLIDAY_HALLOWEEN }.mapNotNull(repo::forRow)
    }

    override fun overrideAreaTrack(player: Player, areaTracks: List<Music>): Music? {
        if (!season.isActive || tracks.isEmpty()) {
            return null
        }
        val coords = player.coords
        val atEvent = coords.level == 0 && coords.chebyshevDistance(EVENT_CENTRE) <= EVENT_RADIUS
        if (!atEvent && random.of(SEASONAL_ODDS) != 0) {
            return null
        }
        return random.pick(tracks)
    }

    private companion object {
        const val HOLIDAY_HALLOWEEN = 2
        const val SEASONAL_ODDS = 3
        const val EVENT_RADIUS = 20
        val EVENT_CENTRE = CoordGrid(3196, 3434, 0)
    }
}
