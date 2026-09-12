package org.rsmod.content.quest.area.varrock.dragonslayer

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Dragon Slayer I.
 *
 * The stage lives in `varp.dragonquest` (176, endstate 10 from `dbrow.quest_dragonslayer1`). The
 * cache multi-npcs read that varp directly, which fixes several values: Elvarg is shown in her
 * lair for values 1-8 and hidden from 9, Captain Ned appears on the Lady Lumbridge from 7 and on
 * the Crandor wreck from 8, and Cabin Boy Jenkins disappears from the deck at 8. The stages below
 * follow those values; 6 is unused.
 *
 * Everything else (which instructions the Guildmaster has given, what has been fed to the magic
 * door, how far the ship repair has got, whether the secret wall has been found) is stored in
 * quest attributes and mirrored into the `varbit.dragonslayer_*` varbits, which all sit on the
 * separate `varp.dragonquestvar` (177). Mirroring keeps `::resetquest` honest and lets the ship's
 * hull multiloc follow the repair.
 */
@Singleton
class DragonSlayerQuest : QuestScript(
    "quest_dragonslayer1",
    "varp.dragonquest",
    rewards {
        xp("stat.strength", 18650.0)
        xp("stat.defence", 18650.0)
        extra("Ability to wear rune platebodies")
    },
    ItemRewardDisplay(RUNE_PLATEBODY),
    completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
) {
    /* The Guildmaster's briefing, one flag per topic he has covered. */
    val explainedElvarg = quest.attribute(name = "EXPLAINED_ELVARG", default = false)
    val instructionsMelzar = quest.attribute(name = "INSTRUCTIONS_MELZAR", default = false)
    val instructionsOracle = quest.attribute(name = "INSTRUCTIONS_ORACLE", default = false)
    val instructionsWormbrain = quest.attribute(name = "INSTRUCTIONS_WORMBRAIN", default = false)
    val instructionsShip = quest.attribute(name = "INSTRUCTIONS_SHIP", default = false)
    val instructionsShield = quest.attribute(name = "INSTRUCTIONS_SHIELD", default = false)

    /** The goblin generals have pointed the player at Wormbrain. */
    val askedGenerals = quest.attribute(name = "ASKED_GENERALS", default = false)

    /** The Oracle has recited the riddle of the magic door. */
    val oracleAsked = quest.attribute(name = "ORACLE_ASKED", default = false)

    /* What has been fed to the magic door in the Dwarven Mine. */
    val usedSilk = quest.attribute(name = "USED_SILK", default = false)
    val usedBowl = quest.attribute(name = "USED_BOWL", default = false)
    val usedLobsterPot = quest.attribute(name = "USED_LOBSTER_POT", default = false)
    val usedMindBomb = quest.attribute(name = "USED_MIND_BOMB", default = false)

    /** Planks nailed over the hole in the Lady Lumbridge, 0 to [REPAIR_PLANKS]. */
    val repairStage = quest.attribute(name = "REPAIR_STAGE", default = 0)

    /** Ned has agreed to captain the ship, whether or not there was a ship to captain yet. */
    val askedNed = quest.attribute(name = "ASKED_NED", default = false)

    /** The secret wall between Elvarg's lair and the Karamja dungeon has been opened from Crandor. */
    val secretDoorFound = quest.attribute(name = "SECRET_DOOR_FOUND", default = false)

    private var Player.vbExplainedElvarg by intVarBit("varbit.dragonslayer_champion_explained_elvarg")
    private var Player.vbInstructionsMelzar by intVarBit("varbit.dragonslayer_instructions_melzar")
    private var Player.vbInstructionsOracle by intVarBit("varbit.dragonslayer_instructions_oracle")
    private var Player.vbInstructionsWormbrain by intVarBit("varbit.dragonslayer_instructions_wormbrain")
    private var Player.vbInstructionsShip by intVarBit("varbit.dragonslayer_instructions_ship")
    private var Player.vbInstructionsShield by intVarBit("varbit.dragonslayer_instructions_shield")
    private var Player.vbAskedGenerals by intVarBit("varbit.dragonslayer_asked_generals")
    private var Player.vbUsedSilk by intVarBit("varbit.dragonslayer_used_silk")
    private var Player.vbUsedBowl by intVarBit("varbit.dragonslayer_used_bowl_unfired")
    private var Player.vbUsedLobsterPot by intVarBit("varbit.dragonslayer_used_lobster_pot")
    private var Player.vbUsedMindBomb by intVarBit("varbit.dragonslayer_used_wizards_mind_bomb")
    private var Player.vbAskedNed by intVarBit("varbit.dragonslayer_asked_ned")
    private var Player.vbNedKnowsShipFixed by intVarBit("varbit.dragonslayer_ned_knows_ship_is_fixed")
    private var Player.vbShipOneThird by intVarBit("varbit.dragonslayer_ship_onethird_fixed")
    private var Player.vbShipTwoThirds by intVarBit("varbit.dragonslayer_ship_twothird_fixed")
    private var Player.vbShipFixed by intVarBit("varbit.dragonslayer_ship_fullyfixed")
    private var Player.vbSecretDoor by intVarBit("varbit.dragonslayer_crandor_found_secret_door")

    override fun ScriptContext.init() {
        // After the quest manager's own login hook, so the mirrors are applied last.
        onPlayerLogin { syncVars(player) }
    }

    override fun subTitle(): String =
        "talking to the <col=800000>Guildmaster</col> in the <col=800000>Champions' Guild</col>, " +
            "south-west of Varrock. You need 32 Quest Points to be let in."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "The <red>Guildmaster</red> of the Champions' Guild has a quest for me: if I " +
                    "want to earn the right to wear a rune platebody, I should speak to " +
                    "<red>Oziach</red>, the armourer who lives by the cliffs west of Edgeville.",
            ) {}

            objective(
                "Oziach will only let a true hero wear his rune platemail. To prove myself I " +
                    "must <red>slay Elvarg</red>, the dragon of Crandor. The Guildmaster can " +
                    "tell me more.",
            ) {
                visibleWhen { stage(access.player) == STAGE_OZIACH }
            }

            objective(
                "The Guildmaster says Crandor is ringed by reefs. To reach it I need a " +
                    "<red>map</red> of the route, a ship of <red>Crandorian design</red> with a " +
                    "<red>captain</red> willing to sail, and <red>protection</red> from the " +
                    "dragon's breath.",
            ) {
                visibleWhen { stage(access.player) in STAGE_BRIEFED..STAGE_NED_ABOARD }
            }

            objective(
                "The map was split in three by the wizards Melzar, Thalzar and Lozar. " +
                    "<red>Melzar's</red> piece is somewhere in his maze north of Rimmington; " +
                    "the Guildmaster gave me a key to the front door.",
            ) {
                visibleWhen { stage(access.player) in STAGE_BRIEFED..STAGE_SHIP_REPAIRED }
                custom(hasMapPiece(access.player, MAP_PART_MELZAR), "I have Melzar's map piece.").strike()
            }

            objective(
                "<red>Thalzar</red> hid his piece and took the secret to his grave. The " +
                    "<red>Oracle</red> on Ice Mountain may know where it is.",
            ) {
                visibleWhen { stage(access.player) in STAGE_BRIEFED..STAGE_SHIP_REPAIRED }
                custom(
                    oracleAsked.get(access.player) && !hasMapPiece(access.player, MAP_PART_THALZAR),
                    "The Oracle spoke of a door below the mountain, in the Dwarven Mine, that " +
                        "needs a mage's drink, worm string changed to sheet, a small crustacean " +
                        "cage and a bowl that has never seen heat.",
                )
                custom(hasMapPiece(access.player, MAP_PART_THALZAR), "I have Thalzar's map piece.").strike()
            }

            objective(
                "<red>Lozar</red> was killed by goblin raiders from the Goblin Village, and " +
                    "one of them took her piece.",
            ) {
                visibleWhen { stage(access.player) in STAGE_BRIEFED..STAGE_SHIP_REPAIRED }
                custom(
                    askedGenerals.get(access.player) && !hasMapPiece(access.player, MAP_PART_LOZAR),
                    "The goblin generals say the thief was <red>Wormbrain</red>, who is now " +
                        "locked up in the Port Sarim jail.",
                )
                custom(hasMapPiece(access.player, MAP_PART_LOZAR), "I have Lozar's map piece.").strike()
            }

            objective(
                "The <red>Duke of Lumbridge</red> keeps a shield enchanted against dragon's " +
                    "breath in his armoury.",
            ) {
                visibleWhen { stage(access.player) in STAGE_BRIEFED..STAGE_ON_CRANDOR }
                custom(access.player.ownsAntiDragonShield(), "I have an anti-dragon shield.").strike()
            }

            objective(
                "The last Crandorian ship is probably in <red>Port Sarim</red>. I will also " +
                    "need a captain brave, or foolish, enough to sail her to Crandor.",
            ) {
                visibleWhen { stage(access.player) == STAGE_BRIEFED }
            }

            objective(
                "I bought the <red>Lady Lumbridge</red> from Klarense in Port Sarim. Before she " +
                    "can sail I must patch the hole in her hold with <red>three planks</red>, " +
                    "<red>90 steel nails</red> and a <red>hammer</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_SHIP_BOUGHT }
                custom(
                    repairStage.get(access.player) in 1 until REPAIR_PLANKS,
                    "I have nailed ${repairStage.get(access.player)} of the $REPAIR_PLANKS planks over the hole.",
                )
            }

            objective(
                "The Lady Lumbridge is seaworthy again. <red>Ned</red>, the retired sailor " +
                    "who makes rope in Draynor Village, is desperate enough to captain her. " +
                    "He wants to see the map before he sails.",
            ) {
                visibleWhen { stage(access.player) == STAGE_SHIP_REPAIRED }
                custom(access.player.inv.contains(CRANDOR_MAP), "I have assembled the map to Crandor.")
            }

            objective(
                "Ned has the map and is waiting for me aboard the <red>Lady Lumbridge</red> in " +
                    "Port Sarim. I should make sure I have my anti-dragon shield, food and " +
                    "everything else I need before we set sail.",
            ) {
                visibleWhen { stage(access.player) == STAGE_NED_ABOARD }
            }

            objective(
                "The Lady Lumbridge ran aground on Crandor. Elvarg's lair is in the volcano " +
                    "at the centre of the island; the way in is a hole at the top. I must " +
                    "<red>slay Elvarg</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_ON_CRANDOR }
                attribute(
                    secretDoorFound,
                    "I found a secret wall in the dragon's lair that leads into the Karamja " +
                        "volcano dungeon, so I can get on and off the island without a ship.",
                )
            }

            objective(
                "I have slain Elvarg! I should take her head to <red>Oziach</red> in Edgeville " +
                    "and claim my reward.",
            ) {
                visibleWhen { stage(access.player) == STAGE_ELVARG_SLAIN }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Oziach the armourer would only let a true hero wear his rune platemail, and " +
                    "set me the task of slaying Elvarg, the dragon that laid waste to Crandor.",
            )
            line(
                "I gathered the three pieces of the map to Crandor from Melzar's Maze, the " +
                    "secret chest in the Dwarven Mine and the goblin thief Wormbrain, bought " +
                    "and repaired the Lady Lumbridge, and talked Ned the retired sailor into " +
                    "captaining her.",
            )
            line(
                "Elvarg attacked us at sea and the ship ran aground on Crandor, but with the " +
                    "Duke of Lumbridge's anti-dragon shield I climbed into the volcano and " +
                    "slew the dragon. Oziach has granted me the right to wear rune platebodies.",
            )
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    /** Moves the quest forward to [stage] if it is not already there or beyond. */
    fun setStage(access: ProtectedAccess, stage: Int) {
        val current = stage(access.player)
        if (stage > current) {
            quest.advanceQuestStage(access, stage - current)
        }
        syncVars(access.player)
    }

    /** Re-applies the client-facing varbits from the persisted attributes. */
    fun syncVars(player: Player) {
        player.vbExplainedElvarg = explainedElvarg.get(player).toBit()
        player.vbInstructionsMelzar = instructionsMelzar.get(player).toBit()
        player.vbInstructionsOracle = instructionsOracle.get(player).toBit()
        player.vbInstructionsWormbrain = instructionsWormbrain.get(player).toBit()
        player.vbInstructionsShip = instructionsShip.get(player).toBit()
        player.vbInstructionsShield = instructionsShield.get(player).toBit()
        player.vbAskedGenerals = askedGenerals.get(player).toBit()
        player.vbUsedSilk = usedSilk.get(player).toBit()
        player.vbUsedBowl = usedBowl.get(player).toBit()
        player.vbUsedLobsterPot = usedLobsterPot.get(player).toBit()
        player.vbUsedMindBomb = usedMindBomb.get(player).toBit()
        player.vbAskedNed = askedNed.get(player).toBit()
        player.vbNedKnowsShipFixed = (stage(player) >= STAGE_NED_ABOARD).toBit()
        val planks = repairStage.get(player)
        player.vbShipOneThird = (planks >= 1).toBit()
        player.vbShipTwoThirds = (planks >= 2).toBit()
        player.vbShipFixed = (planks >= REPAIR_PLANKS).toBit()
        player.vbSecretDoor = secretDoorFound.get(player).toBit()
    }

    /* Map pieces */

    /**
     * Whether [part] is accounted for: carried, folded into the assembled map, or already handed
     * to Ned (from [STAGE_NED_ABOARD] the map is his).
     */
    fun hasMapPiece(player: Player, part: String): Boolean =
        stage(player) >= STAGE_NED_ABOARD ||
            player.inv.contains(part) ||
            player.inv.contains(CRANDOR_MAP)

    fun hasAllMapParts(player: Player): Boolean = MAP_PARTS.all { player.inv.contains(it) }

    fun hasFullMap(player: Player): Boolean =
        player.inv.contains(CRANDOR_MAP) || hasAllMapParts(player)

    /* Ship */

    fun shipBought(player: Player): Boolean = stage(player) >= STAGE_SHIP_BOUGHT

    fun shipRepaired(player: Player): Boolean = stage(player) >= STAGE_SHIP_REPAIRED

    /* Magic door */

    fun magicDoorUnlocked(player: Player): Boolean =
        usedSilk.get(player) && usedBowl.get(player) && usedLobsterPot.get(player) && usedMindBomb.get(player)

    private fun Boolean.toBit(): Int = if (this) 1 else 0

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_OZIACH = 2
        const val STAGE_BRIEFED = 3
        const val STAGE_SHIP_BOUGHT = 4
        const val STAGE_SHIP_REPAIRED = 5
        const val STAGE_NED_ABOARD = 7
        const val STAGE_ON_CRANDOR = 8
        const val STAGE_ELVARG_SLAIN = 9
        const val STAGE_COMPLETE = 10

        const val QP_REQUIREMENT = 32
        const val RECOMMENDED_COMBAT = 45
        const val RECOMMENDED_MAGIC = 33

        const val RUNE_PLATEBODY = "obj.rune_platebody"
        const val ANTI_DRAGON_SHIELD = "obj.antidragonbreathshield"
        const val MAZE_KEY = "obj.melzarkey"
        const val ELVARGS_HEAD = "obj.dragon_slayer_qip_elvargs_head"
        const val COINS = "obj.coins"

        /** Melzar's piece, from the chest at the end of his maze. */
        const val MAP_PART_MELZAR = "obj.mappart1"

        /** Lozar's piece, stolen by Wormbrain. */
        const val MAP_PART_LOZAR = "obj.mappart2"

        /** Thalzar's piece, behind the magic door in the Dwarven Mine. */
        const val MAP_PART_THALZAR = "obj.mappart3"

        val MAP_PARTS = listOf(MAP_PART_MELZAR, MAP_PART_LOZAR, MAP_PART_THALZAR)
        const val CRANDOR_MAP = "obj.dragonmap"

        const val SHIP_PRICE = 2000
        const val WORMBRAIN_PRICE = 10_000
        const val REPAIR_PLANKS = 3
        const val NAILS_PER_PLANK = 30

        /** Where the Lady Lumbridge leaves the player when she runs aground. */
        val CRANDOR_BEACH = CoordGrid(2843, 3237, 0)

        fun Player.ownsAntiDragonShield(): Boolean =
            inv.contains(ANTI_DRAGON_SHIELD) || worn.contains(ANTI_DRAGON_SHIELD)
    }
}
