package org.rsmod.content.quest.area.gnomestronghold.monkeymadness

import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.map.CoordGrid

/** Tiles, sounds and other shared facts the Monkey Madness scripts move between. */
object MonkeyMadness {
    /** The King's ground floor and the Grand Tree's first floor by the Blurberry Bar. */
    val DAERO_POST = CoordGrid(2484, 3486, 1)

    /** The underground military glider hangar Daero blindfolds the player into. */
    val HANGAR_ARRIVAL = CoordGrid(2391, 9889, 0)
    val HANGAR_DAERO = CoordGrid(2392, 9890, 0)
    val HANGAR_WAYDAR = CoordGrid(2392, 9895, 0)
    val HANGAR_GLOUGH = CoordGrid(2396, 9900, 0)
    val HANGAR_GLIDERS = listOf(CoordGrid(2384, 9886, 0), CoordGrid(2384, 9890, 0), CoordGrid(2384, 9894, 0), CoordGrid(2384, 9902, 0))

    /** Crash Island: the wrecked squad gliders with Waydar's landing spot between them. */
    val CRASH_ISLAND_LANDING = CoordGrid(2896, 2727, 0)
    val CRASH_ISLAND_WAYDAR = CoordGrid(2897, 2727, 0)
    val CRASH_ISLAND_LUMDO = CoordGrid(2891, 2724, 0)
    val CRASH_ISLAND_BOAT = CoordGrid(2892, 2723, 0)

    /** Ape Atoll's south-east beach where Lumdo's boat puts in. */
    val APE_ATOLL_LANDING = CoordGrid(2802, 2706, 0)
    val APE_ATOLL_LUMDO = CoordGrid(2803, 2707, 0)

    /** Marim jail: the player's cell lies south of the pickable door, the guard room north of it. */
    val JAIL_CELL = CoordGrid(2771, 2794, 0)
    val JAIL_DOOR = CoordGrid(2771, 2795, 0)
    val JAIL_DOORSTEP = CoordGrid(2771, 2796, 0)
    val JAIL_PATROL = listOf(CoordGrid(2766, 2796, 0), CoordGrid(2773, 2796, 0), CoordGrid(2773, 2802, 0), CoordGrid(2766, 2802, 0))
    const val JAIL_MIN_X = 2764
    const val JAIL_MAX_X = 2773
    const val JAIL_MIN_Z = 2796
    const val JAIL_MAX_Z = 2804
    val JAIL_CELLS_X = 2770..2776
    val JAIL_CELLS_Z = 2793..2795

    /** The eastern warehouse: the crate over the hole drops into the cavern below it. */
    val CRATE_HOLE = CoordGrid(2769, 2765, 0)
    val CAVERN_LANDING = CoordGrid(2769, 9165, 0)
    val CAVERN_ROPE_TOP = CoordGrid(2797, 2769, 0)
    val WAREHOUSE_TRAPDOOR_LANDING = CoordGrid(2764, 9169, 0)

    /** The temple of Marimbo: trapdoor to the wall of flames below. */
    val TEMPLE_TRAPDOOR = CoordGrid(2807, 2785, 0)
    val TEMPLE_UNDER_LANDING = CoordGrid(2805, 9199, 0)
    val TEMPLE_ROPE_TOP = CoordGrid(2806, 2785, 0)

    /** The dungeon under the south of the island: down the ladder near the beach, Zooknock at its far end. */
    val DUNGEON_LADDER_TOP = CoordGrid(2763, 2703, 0)
    val DUNGEON_LADDER_BOTTOM = CoordGrid(2763, 9103, 0)
    val ZOOKNOCK = CoordGrid(2804, 9145, 0)

    /** Kruk waits at the foot of the eastern watchtower; Awowogei's throne is in the palace. */
    val KRUK = CoordGrid(2731, 2765, 0)
    val THRONE_ROOM = CoordGrid(2804, 2766, 0)
    val THRONE_ROOM_ENTRY = CoordGrid(2805, 2764, 0)

    /** The banana plantation west of the jail, where the Monkey Child plays under his aunt's eye. */
    val MONKEY_CHILD = CoordGrid(2743, 2794, 0)
    val MONKEYS_AUNT = CoordGrid(2745, 2790, 0)
    val AUNT_PATROL = listOf(CoordGrid(2745, 2790, 0), CoordGrid(2739, 2790, 0), CoordGrid(2739, 2798, 0), CoordGrid(2747, 2798, 0))
    val PLANTATION_X = 2736..2748
    val PLANTATION_Z = 2789..2799

    /** Ardougne Zoo: the monkey pen and the path outside it where the Minder stands. */
    val ZOO_PEN = CoordGrid(2603, 3278, 0)
    val ZOO_OUTSIDE = CoordGrid(2596, 3277, 0)
    val ZOO_X = 2597..2609
    val ZOO_Z = 3272..3283

    /** The Jungle Demon's cavern: a private copy of the map square around the great gorilla statue. */
    val ARENA_PLAYER = CoordGrid(2713, 9175, 1)
    val ARENA_DEMON = CoordGrid(2715, 9196, 1)
    val ARENA_GNOMES = listOf(CoordGrid(2711, 9173, 1), CoordGrid(2716, 9173, 1), CoordGrid(2709, 9176, 1), CoordGrid(2718, 9176, 1), CoordGrid(2712, 9171, 1), CoordGrid(2715, 9171, 1))
    val ARENA_TRAPDOORS = listOf(CoordGrid(2690, 9163, 1), CoordGrid(2736, 9159, 1), CoordGrid(2741, 9205, 1), CoordGrid(2696, 9212, 1))
    val ARENA_CAMERA_FROM = CoordGrid(2713, 9165, 1)
    val ARENA_CAMERA_AT = CoordGrid(2715, 9190, 1)

    /** Where Zooknock's teleport drops the squad after the battle. */
    val POST_BATTLE_LANDING = CoordGrid(2801, 2704, 0)

    /** Marim's town walls; a human inside them is a prisoner in waiting. */
    val MARIM_X = 2723..2812
    val MARIM_Z = 2755..2812
    const val MARIM_GATE_X = 2722
    val MARIM_GATE_Z = 2764..2769

    const val SOUND_HUMAN_INTO_MONKEY = "synth.human_into_monkey"
    const val SOUND_MONKEY_INTO_HUMAN = "synth.monkey_into_human"
    const val SOUND_HUMAN_INTO_GORILLA = "synth.human_into_gorilla"
    const val SOUND_GORILLA_INTO_HUMAN = "synth.gorilla_into_human"
    const val SOUND_HUMAN_INTO_SMALLMONKEY = "synth.human_into_smallmonkey"
    const val SOUND_SMALLMONKEY_INTO_HUMAN = "synth.smallmonkey_into_human"
    const val SOUND_HUMAN_INTO_ZOMBIE = "synth.human_into_zombiemonkey"
    const val SOUND_ZOMBIE_INTO_HUMAN = "synth.zombiemonkey_into_human"
    const val SOUND_MONKEY_CALLS = "synth.monkeycalls"
    const val SOUND_OOKS = "synth.ooks"
    const val SOUND_GORILLA_PUNCH = "synth.gorilla_bigpunch"
    const val SOUND_WING_UNFOLD = "synth.wing_unfold"
    const val SOUND_RUMBLING = "synth.rumbling"
    const val SOUND_RUMBLING_FADE = "synth.rumbling_fade"
    const val SOUND_FIRE = "synth.fire_loop"
    const val SOUND_SNORE = "synth.snore"
    const val SOUND_HERO_MONKEY = "synth.heromonkey"
    const val SOUND_MONKEY_CAUGHT = "synth.monkey_caught"
    const val SOUND_MONKEY_ESCAPE = "synth.monkey_escape"
    const val SOUND_SKELETON_RESURRECT = "synth.skeleton_resurrect"
    const val SOUND_TELEPORT = "synth.teleport_all"
    const val SOUND_UNLOCK = "synth.unlock"
    const val SOUND_TRAPDOOR_OPEN = "synth.trapdoor_open"
    const val SOUND_TRAPDOOR_CLOSE = "synth.trapdoor_close"
    const val SOUND_PICK_BANANA = "synth.pick"
    const val SOUND_FALL_LAND = "synth.fall_land"
    const val SOUND_JUMP_AND_FALL = "synth.jump_and_fall"
    const val SOUND_ROPECLIMB = "synth.ropeclimb"
    const val SOUND_HUMAN_HIT = "synth.human_hit_1"

    const val LADDER_SEQ = "seq.human_reachforladder"
    const val SEARCH_SEQ = "seq.human_pickupfloor"
    const val UNCONSCIOUS_SEQ = "seq.human_unconscious"
    const val TELEPORT_SEQ = "seq.human_castteleport"
    const val TELEPORT_SPOTANIM = "spotanim.teleport_casting"
    const val ROCKFALL_SPOTANIM = "spotanim.mm_roofcollapse"
    const val GLIDER_UNFOLD_SEQ = "seq.m_glider_unfold"
    const val JUMP_SEQ = "seq.human_falling"
    const val ROCKFALL_SEQ = "seq.human_rockfall"

    const val KING_NAME = "King Narnode Shareen"
}

/** Fades the screen out, drops the player on [dest] and fades back in. */
internal suspend fun ProtectedAccess.mmFadeTeleport(dest: CoordGrid) {
    fadeToBlack()
    telejump(dest, TeleportType.Exempt)
    delay(1)
    fadeFromBlack()
    closeFadeOverlay()
}

internal fun ProtectedAccess.inMarim(): Boolean {
    val coords = player.coords
    return coords.level == 0 && coords.x in MonkeyMadness.MARIM_X && coords.z in MonkeyMadness.MARIM_Z
}

internal fun ProtectedAccess.inJail(): Boolean {
    val coords = player.coords
    return coords.level == 0 && coords.x in MonkeyMadness.JAIL_CELLS_X && coords.z in MonkeyMadness.JAIL_CELLS_Z
}
