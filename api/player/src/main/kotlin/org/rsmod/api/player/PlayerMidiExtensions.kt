package org.rsmod.api.player

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import net.rsprot.protocol.game.outgoing.sound.MidiJingle
import net.rsprot.protocol.game.outgoing.sound.MidiSongV2
import org.rsmod.api.enums.NamedEnums.jingle_groups
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

public var Player.musicClocks: Int by intVarBit("varbit.music_curr_clocks")

private val logger = InlineLogger()

/**
 * Plays the `jingle.*` gameval [jingle] through its js5 archive 11 group in `enum.jingle_groups`.
 * A jingle missing from that enum is logged and not played.
 *
 * @see [MidiJingle]
 */
public fun Player.midiJingle(jingle: String) {
    val group = jingleGroup(jingle)
    if (group == null) {
        logger.warn { "No archive 11 group in enum.jingle_groups for $jingle" }
        return
    }
    midiJingle(group)
}

/**
 * The js5 archive 11 group the client plays for [jingle]. The `jingle.*` gamevals are Jagex's
 * internal midi ids, which share one numbering with music and do not match the archive groups.
 */
internal fun jingleGroup(jingle: String): Int? = jingle_groups[jingle.asRSCM(RSCMType.JINGLE)]

/** Plays the jingle stored as js5 archive 11 group [group]. */
public fun Player.midiJingle(group: Int) {
    musicClocks = 0 // Client restarts music when a jingle is played.
    client.write(MidiJingle(group))
}

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
