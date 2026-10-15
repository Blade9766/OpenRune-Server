package org.rsmod.content.quest.area.falador.knightssword

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Knight's Sword.
 *
 * The stage is the whole cache varp `varp.squire`, endstate 7 from `dbrow.quest_knightssword`:
 * - [STAGE_STARTED]: the squire has admitted losing Sir Vyvin's sword.
 * - [STAGE_RELDO]: Reldo has told the player about the Imcando dwarves and their love of pie.
 * - [STAGE_PIE_GIVEN]: Thurgo has eaten a redberry pie and will hear the player out.
 * - [STAGE_PICTURE_NEEDED]: Thurgo will make the sword, but needs to see what it looked like.
 * - [STAGE_PORTRAIT_LOCATED]: the squire has pointed the player at Sir Vyvin's cupboard.
 * - [STAGE_DESIGN_SHOWN]: Thurgo has studied the portrait and wants blurite ore and two iron bars.
 * - [STAGE_COMPLETE]: the squire has the replacement sword.
 *
 * Everything else is derived from the stage and the pack, except whether Thurgo has already forged
 * a sword, which is a server-only flag on `varp.knightssword_state` so the journal and Thurgo can
 * tell a player who lost the sword from one who never had it.
 */
@Singleton
class KnightsSwordQuest :
    QuestScript(
        QUEST_KEY,
        "varp.squire",
        rewards {
            xp("stat.smithing", REWARD_XP)
            scroll("12,725 Smithing XP")
        },
        ItemRewardDisplay(BLURITE_SWORD, zoom = 250),
        completionJingle = Quest.QUEST_COMPLETE_3_JINGLE,
    ) {
    override fun ScriptContext.init() {
        quest.onVarSync(::clearWhenReset)
    }

    override fun subTitle(): String =
        "talking to the <col=800000>Squire</col> in the courtyard of the " +
            "<col=800000>White Knights' Castle</col> in <col=800000>Falador</col>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = access.player
            val stage = stage(p)
            val start =
                "The squire in the White Knights' Castle has lost Sir Vyvin's ancestral sword, " +
                    "and I have agreed to find someone who can make a replacement."
            if (stage == STAGE_STARTED) {
                line(start)
                line("Reldo, the librarian in Varrock Palace, might know who could forge it.")
                return@questJournal
            }
            strike(start)
            val reldo =
                "Reldo told me the sword was the work of the Imcando dwarves. One of them, " +
                    "Thurgo, may still live on the coast south of Port Sarim."
            if (stage == STAGE_RELDO) {
                line(reldo)
                line("Reldo says the Imcando are fond of redberry pie. Bringing one might help.")
                return@questJournal
            }
            strike(reldo)
            val pie = "I found Thurgo near the Mudskipper Point coast and won him over with a redberry pie."
            if (stage == STAGE_PIE_GIVEN) {
                line(pie)
                line("Now that he's in a good mood, I should ask him about the sword.")
                return@questJournal
            }
            strike(pie)
            val picture = "Thurgo will make the sword, but needs a picture showing what it looked like."
            if (stage == STAGE_PICTURE_NEEDED) {
                line(picture)
                line("The squire might know where a picture of the sword can be found.")
                return@questJournal
            }
            strike(picture)
            val portrait =
                "The squire told me a portrait of Sir Vyvin's father, holding the sword, is kept in " +
                    "a cupboard in Sir Vyvin's room on the top floor of the castle."
            if (stage == STAGE_PORTRAIT_LOCATED) {
                line(portrait)
                if (PORTRAIT in p.inv) {
                    line("I have the portrait. I should show it to Thurgo.")
                } else {
                    line(
                        "I need to borrow it without Sir Vyvin seeing me. He keeps an eye on his " +
                            "cupboard, but he can't watch it while he's looking somewhere else.",
                    )
                }
                return@questJournal
            }
            strike(portrait)
            strike("I borrowed the portrait and showed it to Thurgo, who now knows the design.")
            if (p.swordForged == 1 && BLURITE_SWORD in p.inv) {
                line("Thurgo has forged the replacement sword. I should take it to the squire.")
                return@questJournal
            }
            if (p.swordForged == 1) {
                line(
                    "Thurgo forged the sword, but I no longer have it. He can make another from " +
                        "one blurite ore and two iron bars.",
                )
                return@questJournal
            }
            line("Thurgo needs one blurite ore and two iron bars to make the sword.")
            line(
                "Blurite can be mined in the Asgarnian Ice Dungeon, through the trapdoor east of " +
                    "Thurgo's hut. It needs a pickaxe and level 10 Mining. Ice warriors and ice " +
                    "giants guard the deposits.",
            )
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I helped the squire replace Sir Vyvin's ancestral sword. Reldo pointed me to " +
                    "Thurgo, the last Imcando dwarf, who I won over with a redberry pie.",
            )
            line(
                "I borrowed a portrait of the sword from Sir Vyvin's cupboard, and Thurgo forged a " +
                    "copy from blurite ore and iron. Sir Vyvin need never know.",
            )
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        if (stage > stage(access.player)) {
            quest.setQuestStage(access, stage)
        }
    }

    fun hasForgedSword(player: Player): Boolean = player.swordForged == 1

    fun markSwordForged(player: Player) {
        player.swordForged = 1
    }

    fun hint(player: Player): String {
        val stage = stage(player)
        return when {
            stage == 0 -> "The squire in the courtyard of the White Knights' Castle in Falador looks worried."
            stage == STAGE_STARTED -> "Ask Reldo in the Varrock Palace library about the Imcando dwarves."
            stage == STAGE_RELDO && REDBERRY_PIE !in player.inv ->
                "Thurgo is south of Port Sarim, but he won't help a stranger. Bring him a redberry pie."
            stage == STAGE_RELDO -> "Offer Thurgo your redberry pie. He lives by the coast south of Port Sarim."
            stage == STAGE_PIE_GIVEN -> "Ask Thurgo about the special sword."
            stage == STAGE_PICTURE_NEEDED -> "Ask the squire where a picture of the sword might be."
            stage == STAGE_PORTRAIT_LOCATED && PORTRAIT in player.inv -> "Show the portrait to Thurgo."
            stage == STAGE_PORTRAIT_LOCATED ->
                "Open and search Sir Vyvin's cupboard while he is looking out of the window."
            stage == STAGE_DESIGN_SHOWN && BLURITE_SWORD in player.inv && hasForgedSword(player) ->
                "Give the blurite sword to the squire."
            stage == STAGE_DESIGN_SHOWN && BLURITE_ORE !in player.inv ->
                "Mine blurite ore in the Asgarnian Ice Dungeon (pickaxe, level 10 Mining). Keep to " +
                    "the southern wall to slip past the ice warriors."
            stage == STAGE_DESIGN_SHOWN && player.inv.count(IRON_BAR) < IRON_BARS_NEEDED ->
                "You still need two iron bars for Thurgo."
            stage == STAGE_DESIGN_SHOWN -> "Bring the blurite ore and the two iron bars to Thurgo."
            else -> "You have completed this quest."
        }
    }

    /** The warning given on the way down to the ice, only while the ore is still needed. */
    fun dungeonWarning(player: Player, trapdoor: CoordGrid): String? {
        if (trapdoor != ICE_DUNGEON_TRAPDOOR) {
            return null
        }
        if (stage(player) != STAGE_DESIGN_SHOWN || BLURITE_ORE in player.inv) {
            return null
        }
        return "The air turns bitterly cold. Somewhere ahead, heavy footsteps crunch on ice: the " +
            "blurite deposits are guarded."
    }

    private fun clearWhenReset(player: Player) {
        if (stage(player) == 0) {
            player.swordForged = 0
        }
    }

    companion object {
        const val QUEST_KEY = "quest_knightssword"

        const val STAGE_STARTED = 1
        const val STAGE_RELDO = 2
        const val STAGE_PIE_GIVEN = 3
        const val STAGE_PICTURE_NEEDED = 4
        const val STAGE_PORTRAIT_LOCATED = 5
        const val STAGE_DESIGN_SHOWN = 6
        const val STAGE_COMPLETE = 7

        const val REWARD_XP = 12_725.0
        const val IRON_BARS_NEEDED = 2

        const val SQUIRE = "npc.squire"
        const val THURGO = "npc.thurgo"
        const val SIR_VYVIN = "npc.sir_vyvin"

        const val REDBERRY_PIE = "obj.redberry_pie"
        const val PORTRAIT = "obj.knights_portrait"
        const val BLURITE_ORE = "obj.blurite_ore"
        const val BLURITE_SWORD = "obj.faladian_sword"
        const val IRON_BAR = "obj.iron_bar"

        val ICE_DUNGEON_TRAPDOOR = CoordGrid(3008, 3150, 0)
    }
}

private var Player.swordForged: Int by intVarBit("varbit.knightssword_sword_forged")
