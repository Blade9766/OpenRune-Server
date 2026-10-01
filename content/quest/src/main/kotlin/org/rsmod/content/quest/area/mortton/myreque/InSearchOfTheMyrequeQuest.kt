package org.rsmod.content.quest.area.mortton.myreque

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * In Search of the Myreque.
 *
 * The stage is the cache varp `varp.routequest`, endstate [STAGE_COMPLETE] (105). The cache ties
 * two things to it: Sani Piliu and Harold Evans stand in the hideout below stage 80, so
 * [STAGE_BETRAYED] is the moment they die. Everything else is a flag on `varp.routequestmulti`:
 * - `bridgerung1..3`: the three repaired sections of the Hollows rope bridge; together they are
 *   `route_bridgecomplete`, which turns the two tree bases' "Cross-bridge" op on at 7.
 * - `thsfm_vanstrom_hide`: who sits in the Canifis tavern (0 Vanstrom Klause, 1 the Stranger),
 *   derived from the stage on every sync.
 * - `route_met_*`: five server-only flags on spare low bits, one per Myreque member met.
 *
 * Vanstrom's true nature is never named in the journal before the betrayal.
 */
@Singleton
class InSearchOfTheMyrequeQuest :
    QuestScript(
        QUEST_KEY,
        "varp.routequest",
        rewards {
            xp("stat.attack", REWARD_XP)
            xp("stat.defence", REWARD_XP)
            xp("stat.strength", REWARD_XP)
            xp("stat.hitpoints", REWARD_XP)
            xp("stat.crafting", REWARD_XP)
            scroll(
                "600 Attack XP",
                "600 Defence XP",
                "600 Strength XP",
                "600 Hitpoints XP",
                "600 Crafting XP",
                "Canifis-Myreque shortcut",
            )
        },
        ItemRewardDisplay(STEEL_LONGSWORD, zoom = 250),
        completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
    ) {

    override fun ScriptContext.init() {
        quest.onVarSync(::syncWorld)
    }

    override fun subTitle(): String =
        "speaking to <col=800000>Vanstrom Klause</col> in the " +
            "<col=800000>Canifis</col> tavern."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = access.player
            val stage = stage(p)
            val accepted =
                "Vanstrom Klause, a man drinking in the Canifis tavern, asked me to take a " +
                    "bundle of steel weapons to the Myreque, a band fighting the vampyres of " +
                    "Morytania."
            if (stage < STAGE_BOATMAN_CONVINCED) {
                line(accepted)
                line("He said a boatman called <col=800000>Cyreg Paddlehorn</col> in <col=800000>Mort'ton</col> knows where they hide.")
                line("I need <col=800000>a steel longsword, two steel swords, a steel mace, a steel warhammer and a steel dagger</col> for the Myreque. These are for them, not for me to fight with.")
                return@questJournal
            }
            strike(accepted)
            if (stage < STAGE_REACHED_HOLLOWS) {
                strike("Cyreg was wary, but I convinced him the Myreque need these weapons.")
                line("Before he rows me out, Cyreg wants to see the weapons, <col=800000>6 planks</col>, <col=800000>225 steel nails</col>, a <col=800000>hammer</col>, <col=800000>10 coins</col> and a <col=800000>druid pouch with five charges</col> against the ghasts.")
                return@questJournal
            }
            strike("Cyreg rowed me through the swamp to the Hollows, taking three planks for his boat.")
            if (stage < STAGE_BRIDGE_REPAIRED) {
                val done = sectionsRepaired(p)
                line("The <col=800000>rope bridge</col> in the Hollows is broken. I should climb the tree beside it and repair it with a plank and 75 steel nails a section, using my hammer.")
                line("I have repaired $done of $BRIDGE_SECTIONS sections.")
                return@questJournal
            }
            strike("I repaired the rope bridge in the Hollows.")
            if (stage < STAGE_GUARD_PASSED) {
                line("A guard called <col=800000>Curpile Fyod</col> watches a door north of the bridge. He'll want proof I know the Myreque before he lets me pass.")
                return@questJournal
            }
            strike("I answered Curpile Fyod's questions and he let me into the tunnels.")
            if (stage < STAGE_MET_VELIAF) {
                line("I should find the Myreque's leader, <col=800000>Veliaf Hurtz</col>, somewhere in the tunnels. A tight squeeze past some stalagmites needs <col=800000>level 25 Agility</col>.")
                return@questJournal
            }
            if (stage < STAGE_WEAPONS_DELIVERED) {
                strike("I found Veliaf Hurtz in the Myreque's hideout.")
                if (stage < STAGE_MET_MEMBERS) {
                    line("Veliaf wants me to introduce myself to the rest of the group:")
                    for (member in Member.entries) {
                        if (hasMet(p, member)) strike(member.displayName) else line("<col=800000>${member.displayName}</col>")
                    }
                } else {
                    strike("I introduced myself to the rest of the Myreque.")
                    line("I should give the weapons to <col=800000>Veliaf</col>.")
                }
                return@questJournal
            }
            strike("I handed the weapons to Veliaf.")
            if (stage < STAGE_BETRAYED) {
                line("Something is happening in the hideout. I should speak to <col=800000>Veliaf</col>.")
                return@questJournal
            }
            strike("Vanstrom Klause followed me to the hideout. He was a vampyre all along, and killed Sani Piliu and Harold Evans.")
            if (stage < STAGE_HOUND_SLAIN) {
                line("He left a <col=800000>Skeleton Hellhound</col> behind to finish us. I must destroy it.")
                return@questJournal
            }
            strike("I destroyed the Skeleton Hellhound.")
            if (stage < STAGE_ROUTE_REVEALED) {
                line("I should speak to <col=800000>Veliaf</col> about what happened.")
                return@questJournal
            }
            if (stage < STAGE_SHORTCUT_OPENED) {
                strike("Veliaf told me of a hidden way back to Canifis.")
                line("I should search the <col=800000>tunnel wall</col> at the north-western end of the tunnels and climb out to Canifis.")
                return@questJournal
            }
            strike("I found the hidden passage to a ladder behind the Canifis tavern.")
            line("I should see who is sitting in Vanstrom's seat in the <col=800000>Canifis tavern</col>.")
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line("A man named Vanstrom Klause sent me to Mort'ton with weapons for the Myreque.")
            line("Cyreg Paddlehorn rowed me to the Hollows, where I mended a rope bridge and answered Curpile Fyod's questions.")
            line("Inside the hideout Vanstrom revealed himself as a vampyre. He killed Sani Piliu and Harold Evans and left a Skeleton Hellhound, which I destroyed.")
            line("A hidden passage now links the Myreque's tunnels to a trapdoor behind the Canifis tavern.")
            line("The stranger now sitting in Vanstrom's seat insists he has never heard of him.")
        }

    /** A nudge about the current obstacle only; nothing later in the quest is given away. */
    fun hint(player: Player): String {
        val stage = stage(player)
        return when {
            stage == 0 -> "Speak to the man drinking alone in the Canifis tavern."
            stage < STAGE_BOATMAN_CONVINCED -> "Cyreg Paddlehorn in Mort'ton won't talk to someone who threatens him. Tell him why you've come."
            stage < STAGE_REACHED_HOLLOWS -> "Cyreg checks for the six weapons, 6 planks, 225 steel nails, a hammer, 10 coins and a druid pouch with 5 charges before he rows."
            !isBridgeRepaired(player) -> "Climb the tree beside the broken rope bridge with a hammer, a plank and 75 steel nails per broken section. Repaired sections stay repaired."
            stage < STAGE_GUARD_PASSED -> "Curpile Fyod's questions are about what Cyreg and Vanstrom told you. Ask Cyreg about the Myreque again if you've forgotten."
            stage < STAGE_MET_VELIAF -> "Follow the tunnel north to a gap blocked by stalagmites; squeezing past needs 25 Agility. The cave beyond leads to the hideout."
            stage < STAGE_MET_MEMBERS -> "Veliaf wants you to introduce yourself to every other member of the group."
            stage < STAGE_WEAPONS_DELIVERED -> "Bring all six steel weapons to Veliaf, in your pack rather than worn."
            stage < STAGE_BETRAYED -> "Speak to Veliaf in the hideout."
            stage < STAGE_HOUND_SLAIN -> "The hellhound has no defence against magic. If it's too strong, fight from the gap by the north-east bed where it cannot reach you."
            stage < STAGE_ROUTE_REVEALED -> "Speak to Veliaf about what happened."
            stage < STAGE_SHORTCUT_OPENED -> "Search the wall at the north-western end of the tunnels."
            stage < STAGE_COMPLETE -> "Someone else is sitting in Vanstrom's seat in the Canifis tavern."
            else -> "You have completed this quest."
        }
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        if (stage > stage(access.player)) {
            quest.setQuestStage(access, stage)
        }
    }

    /** The hound dies outside the player's own script, so the stage is moved without an access. */
    fun markHoundSlain(player: Player) {
        if (stage(player) == STAGE_BETRAYED) {
            quest.jumpToStage(player, STAGE_HOUND_SLAIN)
        }
    }

    fun complete(access: ProtectedAccess) {
        quest.completeQuest(access)
    }

    fun meetsRequirements(player: Player, agility: Int): Boolean =
        agility >= REQUIRED_AGILITY && QuestRequirements.hasCompleted(player, NATURE_SPIRIT)

    fun isSectionRepaired(player: Player, section: Int): Boolean =
        when (section) {
            1 -> player.rung1 == 1
            2 -> player.rung2 == 1
            3 -> player.rung3 == 1
            else -> false
        }

    fun repairSection(player: Player, section: Int) {
        when (section) {
            1 -> player.rung1 = 1
            2 -> player.rung2 = 1
            3 -> player.rung3 = 1
        }
    }

    fun sectionsRepaired(player: Player): Int = (1..BRIDGE_SECTIONS).count { isSectionRepaired(player, it) }

    fun isBridgeRepaired(player: Player): Boolean = player.bridgeComplete == BRIDGE_COMPLETE

    fun hasMet(player: Player, member: Member): Boolean = player.vars[member.varbit] == 1

    fun markMet(player: Player, member: Member) {
        VarPlayerIntMapSetter.set(player, member.varbit, 1)
    }

    fun hasMetEveryone(player: Player): Boolean = Member.entries.all { hasMet(player, it) }

    fun isShortcutUnlocked(player: Player): Boolean = stage(player) >= STAGE_SHORTCUT_OPENED

    private fun syncWorld(player: Player) {
        val stage = stage(player)
        player.vanstromHide = if (stage >= STAGE_WEAPONS_DELIVERED) STRANGER else VANSTROM
        if (stage != 0) {
            return
        }
        player.rung1 = 0
        player.rung2 = 0
        player.rung3 = 0
        for (member in Member.entries) {
            VarPlayerIntMapSetter.set(player, member.varbit, 0)
        }
    }

    /** The five members the player must meet before the weapons are handed over. */
    enum class Member(val displayName: String, val npc: String, val varbit: String) {
        Sani("Sani Piliu", "npc.route_sani_piliu_vis", "varbit.route_met_sani"),
        Harold("Harold Evans", "npc.route_harold_evans_vis", "varbit.route_met_harold"),
        Ivan("Ivan Strom", "npc.route_ivan_strom", "varbit.route_met_ivan"),
        Polmafi("Polmafi Ferdygris", "npc.route_polmafi_ferdygris", "varbit.route_met_polmafi"),
        Radigad("Radigad Ponfit", "npc.route_radigad_ponfit", "varbit.route_met_radigad"),
    }

    companion object {
        const val QUEST_KEY = "quest_insearchofthemyreque"
        const val NATURE_SPIRIT = "quest_naturespirit"

        const val STAGE_STARTED = 5
        const val STAGE_BOATMAN_CONVINCED = 10
        const val STAGE_REACHED_HOLLOWS = 20
        const val STAGE_BRIDGE_REPAIRED = 30
        const val STAGE_GUARD_PASSED = 40
        const val STAGE_MET_VELIAF = 45
        const val STAGE_MET_MEMBERS = 50
        const val STAGE_WEAPONS_DELIVERED = 60
        const val STAGE_BETRAYED = 80
        const val STAGE_HOUND_SLAIN = 90
        const val STAGE_ROUTE_REVEALED = 95
        const val STAGE_SHORTCUT_OPENED = 100
        const val STAGE_COMPLETE = 105

        const val REQUIRED_AGILITY = 25
        const val REWARD_XP = 600.0

        const val BRIDGE_SECTIONS = 3
        const val BRIDGE_COMPLETE = 7
        const val NAILS_PER_SECTION = 75
        const val PLANKS_FOR_BOAT = 3
        const val PLANKS_NEEDED = 6
        const val NAILS_NEEDED = 225
        const val BOAT_FARE = 10
        const val POUCH_CHARGES_NEEDED = 5

        const val VANSTROM = 0
        const val STRANGER = 1

        const val STEEL_LONGSWORD = "obj.steel_longsword"
        const val STEEL_SWORD = "obj.steel_sword"
        const val STEEL_MACE = "obj.steel_mace"
        const val STEEL_WARHAMMER = "obj.steel_warhammer"
        const val STEEL_DAGGER = "obj.steel_dagger"
        const val PLANK = "obj.woodplank"
        const val NAILS = "obj.nails"
        const val HAMMER = "obj.hammer"
        const val COINS = "obj.coins"
        const val POUCH = "obj.druid_pouch"
        const val POUCH_EMPTY = "obj.druid_pouch_empty"
        const val BLESSED_SICKLE = "obj.silver_sickle_blessed"
        const val UNCUT_RUBY = "obj.uncut_ruby"
        const val BIG_BONES = "obj.big_bones"

        /** The delivery: what the Myreque asked for, counted in the pack only, never worn. */
        val WEAPONS =
            linkedMapOf(
                STEEL_LONGSWORD to 1,
                STEEL_SWORD to 2,
                STEEL_MACE to 1,
                STEEL_WARHAMMER to 1,
                STEEL_DAGGER to 1,
            )

        const val VANSTROM_SITTING = "npc.route_vanstrom_klause_sitting"
        const val STRANGER_NPC = "npc.canafis_stranger"
        const val CYREG = "npc.route_cyreg_paddlehorn"
        const val CURPILE = "npc.route_curpile_fyod_child"
        const val VELIAF = "npc.route_veliaf_hurtz"
        const val HELLHOUND = "npc.skeleton_hellhound"
    }
}

/** Every fixed tile the quest moves players to or reads, checked against the map in tests. */
internal object MyrequeCoords {
    val MORTTON_LANDING = CoordGrid(3521, 3285, 0)
    val HOLLOWS_LANDING = CoordGrid(3499, 3381, 0)

    /** The rope bridge spans z 3427..3430 between the two tree bases. */
    val SOUTH_TREE = CoordGrid(3502, 3426, 0)
    val NORTH_TREE = CoordGrid(3502, 3431, 0)
    val BRIDGE_SOUTH_END = CoordGrid(3502, 3425, 0)
    val BRIDGE_NORTH_END = CoordGrid(3502, 3432, 0)
    const val BRIDGE_MID_Z = 3428

    val SURFACE_DOORS_OUTSIDE = CoordGrid(3509, 3445, 0)
    val TUNNEL_DOORS_INSIDE = CoordGrid(3501, 9811, 0)

    /** The stalagmite is spawned into the mouth of the pocket that hides the cave entrance. */
    val STALAGMITE = CoordGrid(3489, 9823, 0)
    val POCKET_OUTSIDE = CoordGrid(3489, 9822, 0)
    val POCKET_INSIDE = CoordGrid(3490, 9824, 0)
    val POCKET_CAVE = CoordGrid(3492, 9823, 0)
    val HIDEOUT_CAVE = CoordGrid(3505, 9831, 0)
    val HIDEOUT_ARRIVAL = CoordGrid(3505, 9832, 0)

    val FALSE_WALL = CoordGrid(3480, 9837, 0)
    val FALSE_WALL_SOUTH = CoordGrid(3480, 9836, 0)
    val FALSE_WALL_NORTH = CoordGrid(3480, 9838, 0)
    val BASEMENT_LADDER = CoordGrid(3477, 9846, 0)
    val BASEMENT_FOOT = CoordGrid(3477, 9845, 0)
    val CANIFIS_TRAPDOOR = CoordGrid(3495, 3464, 0)
    val CANIFIS_EXIT = CoordGrid(3495, 3465, 0)

    val VELIAF = CoordGrid(3506, 9838, 0)
    val SANI = CoordGrid(3510, 9836, 0)
    val HAROLD = CoordGrid(3504, 9833, 0)
    val MIST_ENTRY = CoordGrid(3507, 9833, 0)
    val HOUND_SPAWN = CoordGrid(3507, 9835, 0)
    val SCENE_CAMERA = CoordGrid(3503, 9842, 0)
    val SCENE_LOOK = CoordGrid(3508, 9835, 0)

    fun inHideout(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 3502..3516 && coords.z in 9830..9846
}

private var Player.rung1: Int by intVarBit("varbit.bridgerung1")
private var Player.rung2: Int by intVarBit("varbit.bridgerung2")
private var Player.rung3: Int by intVarBit("varbit.bridgerung3")
private val Player.bridgeComplete: Int by intVarBit("varbit.route_bridgecomplete")
private var Player.vanstromHide: Int by intVarBit("varbit.thsfm_vanstrom_hide")
