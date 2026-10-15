package org.rsmod.api.player.music

import org.rsmod.api.music.Music
import org.rsmod.game.entity.Player

/**
 * Lets content swap the track area music is about to play, e.g. seasonal tracks. Returning `null`
 * keeps the area's own shuffled track.
 */
public fun interface MusicAreaTrackHook {
    public fun overrideAreaTrack(player: Player, areaTracks: List<Music>): Music?
}
