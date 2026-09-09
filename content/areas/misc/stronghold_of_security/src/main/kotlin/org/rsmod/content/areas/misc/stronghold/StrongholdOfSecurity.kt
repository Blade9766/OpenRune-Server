package org.rsmod.content.areas.misc.stronghold

import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * The four levels of the Stronghold of Security and the cache facts each script needs.
 *
 * Every level is one map square: the Vault of War is 29_81 (x 1856-1919, z 5184-5247), the
 * Catacomb of Famine 31_81, the Pit of Pestilence 33_82 and the Sepulchre of Death 36_81. A level
 * counts as completed once its reward has been claimed, which is the same varbit that unlocks the
 * level's emote (`varp.sos_emote` 802 holds all four), so no extra progress var is needed.
 */
enum class Level(
    val title: String,
    /** The npc whose head the doors talk through (`npc.sos_door_*`, op "talk"). */
    val doorNpc: String,
    /** The name shown above the door's chat head. */
    val doorTitle: String,
    val doorFace: String,
    val doorMirror: String,
    val doorFaceOpen: String,
    val doorMirrorOpen: String,
    /** Set to 1 when the level's reward is claimed; also unlocks the emote. */
    val emoteVarbit: String,
    val emoteName: String,
    val coins: Int,
    /** Combat level that lets a player use the shortcut portal without having completed the level. */
    val portalCombatLevel: Int,
    /** Where the level's entrance ladder lands a player. */
    val start: CoordGrid,
    /** Where the shortcut portal lands a player, beside the reward. */
    val treasure: CoordGrid,
    /** Js5 jingle group (the OSRS wiki "Cache ID") played when the reward is claimed. */
    val jingle: Int,
    /** Message shown when the reward is claimed. */
    val rewardMessage: String,
) {
    WAR(
        title = "Vault of War",
        doorNpc = "npc.sos_door_war",
        doorTitle = "Gate of War",
        doorFace = "loc.sos_war_door_face",
        doorMirror = "loc.sos_war_door_face_mirr",
        doorFaceOpen = "loc.sos_war_door_face_open",
        doorMirrorOpen = "loc.sos_war_door_face_mirr_open",
        emoteVarbit = "varbit.sos_emote_flap",
        emoteName = "Flap",
        coins = 2000,
        portalCombatLevel = 26,
        start = CoordGrid(1859, 5243),
        treasure = CoordGrid(1905, 5222),
        jingle = 157,
        rewardMessage =
            "You open the chest and find 2,000 coins inside! You feel a sense of peace wash over " +
                "you, and you have unlocked the 'Flap' emote.",
    ),
    FAMINE(
        title = "Catacomb of Famine",
        doorNpc = "npc.sos_door_fam",
        doorTitle = "Rickety door",
        doorFace = "loc.sos_fam_door_face",
        doorMirror = "loc.sos_fam_door_face_mirr",
        doorFaceOpen = "loc.sos_fam_door_face_open",
        doorMirrorOpen = "loc.sos_fam_door_face_mirr_open",
        emoteVarbit = "varbit.sos_emote_doh",
        emoteName = "Slap Head",
        coins = 3000,
        portalCombatLevel = 51,
        start = CoordGrid(2042, 5245),
        treasure = CoordGrid(2024, 5216),
        jingle = 179,
        rewardMessage =
            "You search the sack of grain and find 3,000 coins hidden inside! You have also " +
                "unlocked the 'Slap Head' emote.",
    ),
    PESTILENCE(
        title = "Pit of Pestilence",
        doorNpc = "npc.sos_door_pest",
        doorTitle = "Oozing barrier",
        doorFace = "loc.sos_pest_door_face",
        doorMirror = "loc.sos_pest_door_face_mirr",
        doorFaceOpen = "loc.sos_pest_door_face_open",
        doorMirrorOpen = "loc.sos_pest_door_face_mirr_open",
        emoteVarbit = "varbit.sos_emote_idea",
        emoteName = "Idea",
        coins = 5000,
        portalCombatLevel = 76,
        start = CoordGrid(2123, 5252),
        treasure = CoordGrid(2145, 5281),
        jingle = 177,
        rewardMessage =
            "You open the box and find 5,000 coins inside! The medicine within restores you fully, " +
                "and you have unlocked the 'Idea' emote.",
    ),
    DEATH(
        title = "Sepulchre of Death",
        doorNpc = "npc.sos_door_death",
        doorTitle = "Portal of Death",
        doorFace = "loc.sos_death_door_face",
        doorMirror = "loc.sos_death_door_face_mirr",
        doorFaceOpen = "loc.sos_death_door_face_open",
        doorMirrorOpen = "loc.sos_death_door_face_mirr_open",
        emoteVarbit = "varbit.sos_emote_stamp",
        emoteName = "Stamp",
        coins = 0,
        portalCombatLevel = 101,
        start = CoordGrid(2358, 5215),
        treasure = CoordGrid(2346, 5214),
        jingle = 158,
        rewardMessage =
            "You search the cradle and find life in the midst of death. You have unlocked the " +
                "'Stamp' emote!",
    );

    fun isCompleted(player: Player): Boolean = player.vars[emoteVarbit] != 0

    companion object {
        fun forDoor(loc: String): Level? = entries.firstOrNull { it.doorFace == loc || it.doorMirror == loc }
    }
}

/** Places outside the four levels that the scripts move players between. */
object Stronghold {
    /** `loc.sos_dung_ent_open`, the hole in the Barbarian Village mine (Climb-down). */
    val ENTRANCE_HOLE = CoordGrid(3081, 3420)

    /** Where the ladders back to the surface land a player, beside the hole. */
    val SURFACE = CoordGrid(3081, 3421)

    const val ENTRANCE_LOC = "loc.sos_dung_ent_open"

    /* Vault of War */
    const val WAR_LADDER_UP = "loc.sos_war_ladd_up"
    const val WAR_LADDER_DOWN = "loc.sos_war_ladd_down"
    const val WAR_PORTAL = "loc.sos_war_portal"
    const val WAR_CHEST = "loc.sos_war_chest"
    const val DEAD_EXPLORER = "loc.sos_skelly_bag"
    val WAR_START_LADDER = CoordGrid(1859, 5244)
    val WAR_END_LADDER_UP = CoordGrid(1913, 5226)
    val WAR_LADDER_DOWN_LANDING = CoordGrid(1902, 5223)

    /* Catacomb of Famine */
    const val FAMINE_LADDER_UP = "loc.sos_fam_ladd_up"
    const val FAMINE_LADDER_DOWN = "loc.sos_fam_ladd_down"
    const val FAMINE_ROPE_UP = "loc.sos_fam_rope_up"
    const val FAMINE_PORTAL = "loc.sos_fam_portal"
    const val FAMINE_SACK = "loc.sos_fam_sack"
    val FAMINE_LADDER_DOWN_LANDING = CoordGrid(2026, 5217)

    /* Pit of Pestilence */
    const val PESTILENCE_LADDER_UP = "loc.sos_pest_ladd_up"
    const val PESTILENCE_LADDER_DOWN = "loc.sos_pest_ladd_down"
    const val PESTILENCE_VINE_UP = "loc.sos_pest_rope_up"
    const val PESTILENCE_PORTAL = "loc.sos_pest_portal"
    const val PESTILENCE_CHEST = "loc.sos_pest_chest"
    val PESTILENCE_LADDER_DOWN_LANDING = CoordGrid(2148, 5283)

    /* Sepulchre of Death */
    const val DEATH_LADDER_UP = "loc.sos_death_ladd_up"
    const val DEATH_ROPE_UP = "loc.sos_death_rope_up"
    const val DEATH_PORTAL = "loc.sos_death_portal"
    const val DEATH_CRADLE = "loc.sos_death_pram"
    const val DEATH_NOTICE = "loc.sos_thankplayers"

    /* Objs */
    const val STRONGHOLD_NOTES = "obj.sos_stronghold_book"
    const val SECURITY_BOOK = "obj.sos_security_book"
    const val FANCY_BOOTS = "obj.sos_boots"
    const val FIGHTING_BOOTS = "obj.sos_boots2"
    const val FANCIER_BOOTS = "obj.sos_boots3"
    const val COINS = "obj.coins"

    /* Npcs */
    const val COUNT_CHECK = "npc.count_check"
    const val SOLZTUN = "npc.sos_barb_spirit"

    /* Vars */
    const val TELEPORTED_BY_COUNT = "varbit.sos_teleported_by_count"
    const val SCEPTRE_IMBUED = "varbit.sos_sceptre_imbued"
    const val SCEPTRE_CHARGES = "varbit.sos_sceptre_charges"

    /* Anims and sounds */
    const val CLIMB_ANIM = "seq.human_reachforladder"
    const val CLIMB_DOWN_ANIM = "seq.human_pickupfloor"
    const val CHEST_ANIM = "seq.human_openchest"
    const val SEARCH_ANIM = "seq.human_pickupfloor"
    const val DOOR_OPEN_SOUND = "synth.door_open"
    const val DOOR_CLOSE_SOUND = "synth.door_close"
}
