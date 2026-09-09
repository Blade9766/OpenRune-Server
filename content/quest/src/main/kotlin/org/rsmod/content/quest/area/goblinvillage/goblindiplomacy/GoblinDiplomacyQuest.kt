package org.rsmod.content.quest.area.goblinvillage.goblindiplomacy

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Goblin Diplomacy.
 *
 * The stage lives in `varp.goblinquest` (endstate 6 from `dbrow.quest_goblindiplomacy`):
 * - [STAGE_WANT_ORANGE]: the generals asked for orange armour.
 * - [STAGE_WANT_BLUE]: Grubfoot tried the orange; now they want blue.
 * - [STAGE_WANT_BROWN]: blue was no good either; now they want brown.
 * - Complete: brown it is.
 *
 * The same varp carries the cache varbits for the three crates, Grubfoot's armour colour and
 * what the generals have explained, and the quest manager writes the whole varp on every stage
 * change. Those flags are therefore kept in quest attributes and mirrored back by [syncVars].
 */
@Singleton
class GoblinDiplomacyQuest : QuestScript(
    "quest_goblindiplomacy",
    "varp.goblinquest",
    rewards {
        xp("stat.crafting", CRAFTING_XP)
        item(GOLD_BAR, 1)
    },
    ItemRewardDisplay(GOBLIN_MAIL),
) {
    val crate1Searched = quest.attribute(name = "CRATE_1_SEARCHED", default = false)
    val crate2Searched = quest.attribute(name = "CRATE_2_SEARCHED", default = false)
    val crate3Searched = quest.attribute(name = "CRATE_3_SEARCHED", default = false)

    /** Which mail Grubfoot is wearing: [GRUBFOOT_BROWN], [GRUBFOOT_ORANGE] or [GRUBFOOT_BLUE]. */
    val grubfootColour = quest.attribute(name = "GRUBFOOT_COLOUR", default = GRUBFOOT_BROWN)

    /** The generals mentioned the spare armour in the village crates. */
    val knowAboutArmour = quest.attribute(name = "KNOW_ABOUT_ARMOUR", default = false)

    /** The generals mentioned the witch in Draynor who makes dye. */
    val knowAboutDye = quest.attribute(name = "KNOW_ABOUT_DYE", default = false)

    /** Aggie has introduced herself, which gives her the `Dyes` option. */
    val metAggie = quest.attribute(name = "MET_AGGIE", default = false)

    private var Player.crate1Var by intVarBit("varbit.gobdip_crate1_searched")
    private var Player.crate2Var by intVarBit("varbit.gobdip_crate2_searched")
    private var Player.crate3Var by intVarBit("varbit.gobdip_crate3_searched")
    private var Player.grubfootVar by intVarBit("varbit.gobdip_grubfoot_vis")
    private var Player.knowArmourVar by intVarBit("varbit.gobdip_know_about_armour")
    private var Player.knowDyeVar by intVarBit("varbit.gobdip_know_about_dye")
    private var Player.metAggieVar by intVarBit("varbit.gobdip_met_aggie")

    override fun ScriptContext.init() {
        // Registered after the quest manager's own login hook, which writes the whole varp and
        // therefore wipes the mirrored varbits.
        onPlayerLogin { syncVars(player) }
    }

    /** Re-applies the client-facing varbits from the persisted attributes. */
    fun syncVars(player: Player) {
        player.crate1Var = if (crate1Searched.get(player)) 1 else 0
        player.crate2Var = if (crate2Searched.get(player)) 1 else 0
        player.crate3Var = if (crate3Searched.get(player)) 1 else 0
        player.grubfootVar = grubfootColour.get(player)
        player.knowArmourVar = if (knowAboutArmour.get(player)) 1 else 0
        player.knowDyeVar = if (knowAboutDye.get(player)) 1 else 0
        player.metAggieVar = if (metAggie.get(player)) 1 else 0
    }

    /** Grubfoot's transform for this player only; used to hide him behind the curtain. */
    fun showGrubfoot(player: Player, colour: Int) {
        player.grubfootVar = colour
    }

    override fun subTitle(): String =
        "talking to <col=800000>General Bentnoze</col> or <col=800000>General Wartface</col> " +
            "in <col=800000>Goblin Village</col>, north of Falador."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "The goblins of <red>Goblin Village</red> are on the brink of civil war over " +
                    "the colour of their armour. Generals <red>Bentnoze</red> and " +
                    "<red>Wartface</red> want me to bring them armour in a new colour so they " +
                    "can decide.",
            ) {}

            objective("They want to try <red>orange goblin mail</red> first.") {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_WANT_ORANGE }
                hasItem("goblin_armour_orange", "I have some orange goblin mail to show them.").strike()
            }

            objective(
                "Grubfoot looked terrible in orange. Now the generals want to see " +
                    "<red>blue goblin mail</red>.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_WANT_BLUE }
                hasItem("goblin_armour_darkblue", "I have some blue goblin mail to show them.").strike()
            }

            objective(
                "Blue was no good either. Now they want <red>brown</red> armour, which is " +
                    "just plain goblin mail... the colour they started with.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_WANT_BROWN }
                hasItem("goblin_armour", "I have some plain goblin mail to show them.").strike()
            }

            objective(
                "There is spare <red>goblin mail</red> in crates around the village: behind " +
                    "the generals' hut, in the western hut, and up the ladder by the gate.",
            ) {
                visibleWhen { knowAboutArmour.get(access.player) && quest.isQuestInProgress(access.player) }
            }

            objective(
                "<red>Aggie</red>, the witch in Draynor Village, can make red, yellow and " +
                    "blue dye. Mixing red and yellow dye gives orange.",
            ) {
                visibleWhen { knowAboutDye.get(access.player) && quest.isQuestInProgress(access.player) }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "The goblins of Goblin Village were about to tear each other apart because " +
                    "General Bentnoze wanted red armour and General Wartface wanted green.",
            )
            line(
                "I brought them orange goblin mail, then blue, and poor Grubfoot had to model " +
                    "each one. Neither would do. In the end they settled on brown, which is " +
                    "exactly what they were wearing to begin with. Diplomacy at its finest.",
            )
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    /** The armour the generals are waiting for at the current stage, or null outside the quest. */
    fun wantedMail(player: Player): String? =
        when (stage(player)) {
            STAGE_WANT_ORANGE -> ORANGE_MAIL
            STAGE_WANT_BLUE -> BLUE_MAIL
            STAGE_WANT_BROWN -> GOBLIN_MAIL
            else -> null
        }

    companion object {
        const val STAGE_WANT_ORANGE = 1
        const val STAGE_WANT_BLUE = 2
        const val STAGE_WANT_BROWN = 3

        const val CRAFTING_XP = 200.0

        const val GOBLIN_MAIL = "obj.goblin_armour"
        const val ORANGE_MAIL = "obj.goblin_armour_orange"
        const val BLUE_MAIL = "obj.goblin_armour_darkblue"
        const val GOLD_BAR = "obj.gold_bar"

        /** Values of `varbit.gobdip_grubfoot_vis`: which of Grubfoot's forms the client shows. */
        const val GRUBFOOT_BROWN = 0
        const val GRUBFOOT_ORANGE = 1
        const val GRUBFOOT_BLUE = 2
        const val GRUBFOOT_HIDDEN = 3

        /** Colour words the generals use for each mail, in the order they ask for them. */
        fun colourName(mail: String): String =
            when (mail) {
                ORANGE_MAIL -> "orange"
                BLUE_MAIL -> "blue"
                GOBLIN_MAIL -> "brown"
                else -> "that"
            }
    }
}
