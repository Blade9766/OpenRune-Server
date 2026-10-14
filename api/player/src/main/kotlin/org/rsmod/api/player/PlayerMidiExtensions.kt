package org.rsmod.api.player

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import net.rsprot.protocol.game.outgoing.sound.MidiJingle
import net.rsprot.protocol.game.outgoing.sound.MidiSongV2
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

public var Player.musicClocks: Int by intVarBit("varbit.music_curr_clocks")

/** @see [MidiJingle] */
public fun Player.midiJingle(jingle: String) {
    midiJingle(jingleGroup(jingle.asRSCM(RSCMType.JINGLE)))
}

/** Plays the jingle stored as js5 archive 11 group [group]; see [jingleGroup]. */
public fun Player.midiJingle(group: Int) {
    musicClocks = 0 // Client restarts music when a jingle is played.
    client.write(MidiJingle(group))
}

/**
 * Converts a `jingle.*` gameval id to the js5 archive 11 group the client plays.
 *
 * The jingle gameval table numbers music tracks and jingles in one space: music keeps its archive 6
 * group, and jingles follow after the last music track, so their group is the id minus
 * [JINGLE_GAMEVAL_OFFSET]. The few jingles whose id falls inside the music range already equal
 * their group.
 */
public fun jingleGroup(gamevalId: Int): Int =
    if (gamevalId >= JINGLE_GAMEVAL_OFFSET) gamevalId - JINGLE_GAMEVAL_OFFSET else gamevalId

private const val JINGLE_GAMEVAL_OFFSET = 886

/** @see [MidiSongV2] */
public fun Player.midiSong(
    midi: String,
    fadeOutDelay: Int = 0,
    fadeOutSpeed: Int = 0,
    fadeInDelay: Int = 0,
    fadeInSpeed: Int = 0,
) {
    client.write(MidiSongV2(midi.asRSCM(RSCMType.MIDI), fadeOutDelay, fadeOutSpeed, fadeInDelay, fadeInSpeed))
}

public fun Player.midiSong(
    midi: Int,
    fadeOutDelay: Int = 0,
    fadeOutSpeed: Int = 0,
    fadeInDelay: Int = 0,
    fadeInSpeed: Int = 0,
) {
    client.write(MidiSongV2(midi, fadeOutDelay, fadeOutSpeed, fadeInDelay, fadeInSpeed))
}
