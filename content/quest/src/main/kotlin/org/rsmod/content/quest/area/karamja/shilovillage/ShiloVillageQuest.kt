package org.rsmod.content.quest.area.karamja.shilovillage

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Shilo Village.
 *
 * The stage lives in `varp.zombiequeen` (116), whose values are fixed by the cache: Mosol Rei's
 * multi npc and the broken cart outside the village switch to their post-quest forms at the
 * endstate [STAGE_COMPLETE] (15). The steps between follow Jagex's own script: 1 once Trufitus
 * takes the Wampum belt, 2-6 while the mound of earth is found, searched, dug, lit and roped, 7
 * inside Ah Za Rhoon, 8 after leaving it, 9 inside the Tomb of Bervirius, 10 once the bone key
 * opens Rashiliyia's tomb, 11 when the Beads of the Dead carry the player past its gate, 12 with
 * the tomb doors filled with bones and 14 with Rashiliyia's corpse in hand.
 *
 * The varp carries no varbits. The hidden hillside doors are `varbit.zqdoor_multi` on a shared
 * varp, so their state is kept in quest attributes and mirrored by [syncVars].
 */
@Singleton
class ShiloVillageQuest : QuestScript(
    "quest_shilovillage",
    "varp.zombiequeen",
    rewards {
        xp("stat.crafting", CRAFTING_XP)
        extra("Access to Shilo Village")
    },
    ItemRewardDisplay(BEADS_OF_THE_DEAD),
    completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
) {
    val decipheredPlaque = quest.attribute(name = "DECIPHERED_PLAQUE", default = false)
    val readTatteredScroll = quest.attribute(name = "READ_TATTERED_SCROLL", default = false)
    val readCrumpledScroll = quest.attribute(name = "READ_CRUMPLED_SCROLL", default = false)
    val copiedDolmenNotes = quest.attribute(name = "COPIED_DOLMEN_NOTES", default = false)
    val alwaysDrawCrystal = quest.attribute(name = "ALWAYS_DRAW_CRYSTAL", default = false)
    val examinedBoneLock = quest.attribute(name = "EXAMINED_BONE_LOCK", default = false)
    val revealedHillsideDoors = quest.attribute(name = "REVEALED_HILLSIDE_DOORS", default = false)
    val tableWoodUsed = quest.attribute(name = "TABLE_WOOD_USED", default = false)
    val tombDoorBones = quest.attribute(name = "TOMB_DOOR_BONES", default = 0)
    val nazastaroolForms = quest.attribute(name = "NAZASTAROOL_FORMS", default = 0)
    val postQuestChats = quest.attribute(name = "POST_QUEST_CHATS", default = 0)

    override fun ScriptContext.init() {
        onPlayerLogin { syncVars(player) }
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
        syncVars(access.player)
    }

    fun completedJunglePotion(player: Player): Boolean = QuestRequirements.hasCompleted(player, JUNGLE_POTION)

    fun syncVars(player: Player) {
        val doors =
            when {
                stage(player) >= STAGE_TOMB_UNLOCKED -> DOORS_OPEN
                revealedHillsideDoors.get(player) -> DOORS_REVEALED
                else -> DOORS_HIDDEN
            }
        if (player.vars[DOOR_VARBIT] != doors) {
            VarPlayerIntMapSetter.set(player, DOOR_VARBIT, doors)
        }
    }

    override fun subTitle(): String =
        "speaking to <col=800000>Mosol Rei</col> outside <col=800000>Shilo Village</col> in the " +
            "south of <col=800000>Karamja</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "<red>Mosol Rei</red> says <red>Rashiliyia</red>, the Queen of the Dead, has " +
                    "returned and overrun Shilo Village. He gave me a <red>Wampum belt</red> for " +
                    "the shaman <red>Trufitus</red> in Tai Bwo Wannai.",
            ) {
                visibleWhen { stage(access.player) == 0 }
            }

            objective(
                "Trufitus believes the legend of Rashiliyia was lost with the temple of " +
                    "<red>Ah Za Rhoon</red>, whose name means 'Magnificence floating on water'. " +
                    "It may lie somewhere between large bodies of water.",
            ) {
                visibleWhen { stage(access.player) in STAGE_STARTED..STAGE_SEARCHED_MOUND }
            }

            objective(
                "I dug into a mound of earth east of Shilo Village and found a fissure. I should " +
                    "see what lies below it, and how to get down safely.",
            ) {
                visibleWhen { stage(access.player) in STAGE_DUG_MOUND..STAGE_ROPED_MOUND }
            }

            objective(
                "I found the ruins of Ah Za Rhoon. I should search them for anything about " +
                    "Rashiliyia, her kin and Zadimus, and show what I find to Trufitus.",
            ) {
                visibleWhen { stage(access.player) in STAGE_ENTERED_TEMPLE..STAGE_LEFT_TEMPLE }
                hasItem(STONE_PLAQUE.removePrefix(OBJ_PREFIX), "I cut a stone-plaque from a strange stone.").strike()
                hasItem(TATTERED_SCROLL.removePrefix(OBJ_PREFIX), "I found a tattered scroll behind some loose rocks.").strike()
                hasItem(CRUMPLED_SCROLL.removePrefix(OBJ_PREFIX), "I found a crumpled scroll in some sacks.").strike()
                hasItem(ZADIMUS_CORPSE.removePrefix(OBJ_PREFIX), "I took a corpse down from the gallows.").strike()
                hasItem(BONE_SHARD.removePrefix(OBJ_PREFIX), "I buried Zadimus and his spirit gave me a bone shard.").strike()
            }

            objective(
                "Bervirius' tomb lies on a small island to the south west. Anything of his may " +
                    "let me approach Rashiliyia.",
            ) {
                visibleWhen { stage(access.player) == STAGE_ENTERED_BERVIRIUS }
                hasItem(BONE_KEY.removePrefix(OBJ_PREFIX), "I carved a bone key from Zadimus' shard.").strike()
                hasItem(BEADS_OF_THE_DEAD.removePrefix(OBJ_PREFIX), "I made the Beads of the Dead from Bervirius' sword pommel.").strike()
            }

            objective(
                "The bone key opened Rashiliyia's tomb north of Ah Za Rhoon. I must wear the " +
                    "<red>Beads of the Dead</red> inside and find her remains.",
            ) {
                visibleWhen { stage(access.player) in STAGE_TOMB_UNLOCKED until STAGE_CORPSE_RETRIEVED }
                custom(tombDoorBones.get(access.player) >= TOMB_DOOR_RECESSES, "I filled the tomb door's recesses with bones.")
            }

            objective(
                "I have Rashiliyia's remains. Perhaps putting them to rest where her son lies " +
                    "would release her spirit.",
            ) {
                visibleWhen { stage(access.player) == STAGE_CORPSE_RETRIEVED }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Mosol Rei sent me to Trufitus, who set me searching for the lost temple of Ah Za " +
                    "Rhoon. In its ruins I found Zadimus, whose spirit gave me a shard of bone.",
            )
            line(
                "In Bervirius' tomb I found his sword pommel and made the Beads of the Dead, then " +
                    "carved a key to open Rashiliyia's tomb and defeated her guardian Nazastarool.",
            )
            line(
                "I laid Rashiliyia's remains beside her son, and Shilo Village is free of her curse.",
            )
        }

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_FOUND_MOUND = 2
        const val STAGE_SEARCHED_MOUND = 3
        const val STAGE_DUG_MOUND = 4
        const val STAGE_LIT_MOUND = 5
        const val STAGE_ROPED_MOUND = 6
        const val STAGE_ENTERED_TEMPLE = 7
        const val STAGE_LEFT_TEMPLE = 8
        const val STAGE_ENTERED_BERVIRIUS = 9
        const val STAGE_TOMB_UNLOCKED = 10
        const val STAGE_PASSED_GATE = 11
        const val STAGE_TOMB_DOOR_OPEN = 12
        const val STAGE_CORPSE_RETRIEVED = 14
        const val STAGE_COMPLETE = 15

        const val CRAFTING_XP = 3875.0
        const val TOMB_DOOR_RECESSES = 3
        const val NAZASTAROOL_FORM_COUNT = 3

        const val JUNGLE_POTION = "quest_junglepotion"

        const val DOOR_VARBIT = "varbit.zqdoor_multi"
        const val DOORS_HIDDEN = 0
        const val DOORS_REVEALED = 1
        const val DOORS_OPEN = 2

        const val OBJ_PREFIX = "obj."
        const val WAMPUM_BELT = "obj.mosol_wampum_belt"
        const val STONE_PLAQUE = "obj.zqplaque"
        const val TATTERED_SCROLL = "obj.zqberviriusscroll"
        const val CRUMPLED_SCROLL = "obj.zqrashiliyiascroll"
        const val ZADIMUS_CORPSE = "obj.zqzadimusbones"
        const val BONE_SHARD = "obj.zqboneshard"
        const val BONE_KEY = "obj.zqbonekey"
        const val SWORD_POMMEL = "obj.zqbevsword"
        const val BONE_BEADS = "obj.zqbonebeads"
        const val BEADS_OF_THE_DEAD = "obj.zqdeadbeads"
        const val LOCATING_CRYSTAL = "obj.zqcrystal"
        const val BERVIRIUS_NOTES = "obj.zqberviriusscroll2"
        const val RASHILIYIA_CORPSE = "obj.zqcorpse"
        const val BRONZE_WIRE = "obj.bronzecraftwire"
        const val CHISEL = "obj.chisel"
        const val SPADE = "obj.spade"
        const val ROPE = "obj.rope"
        const val BONES = "obj.bones"
        const val COINS = "obj.coins"
        const val PAPYRUS = "obj.papyrus"
        const val CHARCOAL = "obj.charcoal"

        const val MOSOL_REI = "npc.mosol_rei_multi"
        const val TRUFITUS = "npc.trufitus"
        const val ZADIMUS = "npc.zadimus_ghost"
        const val RASHILIYIA = "npc.zqzombiequeen"
        const val NAZASTAROOL_ZOMBIE = "npc.zq_mainzombie1"
        const val NAZASTAROOL_SKELETON = "npc.zq_mainzombie2"
        const val NAZASTAROOL_GHOST = "npc.zq_mainzombie3"

        val UNDEAD_ONES =
            listOf("npc.zqskeleton_unarmed", "npc.zqskeleton_armed", "npc.zqzombie_unarmed", "npc.zqzombie_armed")

        val ZOMBIE_UNDEAD_ONES =
            listOf(
                "npc.zqzombie_unarmed",
                "npc.zqzombie_unarmed2",
                "npc.zqzombie_unarmed3",
                "npc.zqzombie_armed",
                "npc.zqzombie_armed2",
                "npc.zqzombie_armed3",
            )

        val NAZASTAROOL_FORMS = listOf(NAZASTAROOL_ZOMBIE, NAZASTAROOL_SKELETON, NAZASTAROOL_GHOST)
    }
}

internal object ShiloCoords {
    /** Outside the metal gates, where Mosol Rei keeps watch. */
    val OUTSIDE_GATES = CoordGrid(2876, 2952, 0)

    /** Inside the wooden gates, where Mosol Rei leaves players after the quest. */
    val INSIDE_VILLAGE = CoordGrid(2866, 2952, 0)

    /** The walled yard between the metal gates (x 2875) and the wooden gates (x 2867). */
    val GATEHOUSE_DRAG = CoordGrid(2870, 2953, 0)

    fun inGatehouse(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 2868..2874 && coords.z in 2945..2959

    /** Tai Bwo Wannai's sacred ground in front of the tribal statue. */
    fun onSacredGround(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 2794..2798 && coords.z in 3087..3090

    /** Beneath the fissure, at the foot of the rope. */
    val TEMPLE_LANDING = CoordGrid(2898, 9401, 0)
    val FISSURE_SQUEEZE = CoordGrid(2922, 3000, 0)

    /** Either side of the two cave-ins that link the temple halls. */
    val CAVE_IN_SOUTH = CoordGrid(2888, 9283, 0)
    val CAVE_IN_NORTH = CoordGrid(2887, 9374, 0)

    /** The river through the temple, from the smashed table out through the waterfall. */
    val RAFT_LAUNCH = CoordGrid(2897, 9373, 0)
    val RAFT_ROUTE =
        listOf(
            CoordGrid(2892, 9361, 0),
            CoordGrid(2902, 9351, 0),
            CoordGrid(2918, 9350, 0),
            CoordGrid(2927, 9351, 0),
            CoordGrid(2935, 9351, 0),
            CoordGrid(2941, 9351, 0),
        )
    val WATERFALL_OUTSIDE = CoordGrid(2929, 2949, 0)

    /** The river banks beside the stepping stones east of the village, north and south of the falls. */
    val FALLS_SOUTH_BANK = CoordGrid(2929, 2946, 0)
    val FALLS_NORTH_BANK = CoordGrid(2929, 2952, 0)
    const val WATERFALL_ROCKS_MIDDLE_Z = 9351

    fun inTemple(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 2880..2943 && coords.z in 9280..9407

    /** Cairn Isle: the climbing rocks on the Karamja shore and the rope bridge behind them. */
    const val CLIMBING_ROCKS_X = 2793
    val BRIDGE_TILES = 2771..2782
    const val BRIDGE_Z = 2979
    val BRIDGE_SPLASHDOWN = CoordGrid(2778, 2977, 0)
    val BRIDGE_SHORE = CoordGrid(2778, 2974, 0)

    fun nearCairnCrossing(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 2760..2796 && coords.z in 2970..2990

    /** Inside the Tomb of Bervirius, below the crawl-way, and back out beside the handholds. */
    val BERVIRIUS_LANDING = CoordGrid(2760, 9389, 0)
    val BERVIRIUS_EXIT = CoordGrid(2764, 2976, 0)

    /** The hillside doors of Rashiliyia's tomb, found by the palm trees north of Ah Za Rhoon. */
    val HILLSIDE_DOORS = CoordGrid(2916, 3090, 0)
    val HILLSIDE_OUTSIDE = CoordGrid(2916, 3089, 0)

    /** Where the locating crystal blazes. */
    val CRYSTAL_TARGET = CoordGrid(2916, 3092, 0)

    /** Inside the tomb: the entrance hall, and the slope below its ancient gate. */
    val TOMB_ENTRY = CoordGrid(2929, 9525, 0)
    val GATE_NORTH = CoordGrid(2929, 9518, 0)
    val GATE_SOUTH = CoordGrid(2929, 9515, 0)
    const val GATE_Z = 9516
    val ROCKS_MIDWAY = CoordGrid(2928, 9513, 0)
    val ROCKS_BOTTOM = CoordGrid(2928, 9511, 0)
    const val ROCKS_TOP_Z = 9512

    fun inRashiliyiaTomb(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 2880..2943 && coords.z in 9472..9535

    /** Shilo's cart stop and Brimhaven's. */
    val SHILO_CART_STOP = CoordGrid(2834, 2951, 0)
    val BRIMHAVEN_CART_STOP = CoordGrid(2776, 3214, 0)

    /** Where the Lady of the Waves puts in. */
    val PORT_KHAZARD = CoordGrid(2680, 3150, 0)
    val PORT_SARIM = CoordGrid(3047, 3235, 0)
}

internal fun Player.wearsBeadsOfTheDead(): Boolean = worn.contains(ShiloVillageQuest.BEADS_OF_THE_DEAD)
