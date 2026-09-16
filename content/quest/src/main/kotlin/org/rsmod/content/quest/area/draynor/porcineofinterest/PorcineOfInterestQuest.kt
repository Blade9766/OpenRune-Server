package org.rsmod.content.quest.area.draynor.porcineofinterest

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * A Porcine of Interest.
 *
 * The stage is `varbit.porcine` (bits 0-5 of `varp.porcine_main`), endstate 40 from
 * `dbrow.quest_porcineofinterest`. The rest of that varp is the quest's own flag varbits, so the
 * manager is handed the varbit rather than the varp and the flags are used as the cache declares
 * them - see [Player.porcineFootCut] and friends below.
 *
 * Every stage value is fixed by a cache multiloc, so they cannot be renumbered:
 * - [STAGE_STARTED] (10): `loc.porcine_tracking_*` swap in the trail of stolen produce and the
 *   damaged cart, and `loc.porcine_cart_fallentree` drops out.
 * - [STAGE_ROPE_TIED] (11): `loc.porcine_hole` turns from "Investigate" into "Climb-down".
 * - [STAGE_IN_CAVE] (15): `loc.porcine_skeleton` gains its "Investigate" op and
 *   `loc.porcine_skeleton_note` appears. Both are gone again at 16.
 * - [STAGE_AMBUSHED] (16): the ambush is over and the player has woken in Spria's house.
 * - [STAGE_GOGGLES] (20): the last stage `loc.porcine_fallen_rope` is visible at.
 * - [STAGE_SLAIN] (25) and [STAGE_BOUNTY] (30): the trail is still up (visible to 35).
 * - [STAGE_COMPLETE] (40): `npc.slayer_master_9` resolves to `npc.slayer_master_9_active`, the
 *   full slayer master. It resolves to nothing at all between 36 and 39, so nothing may rest
 *   there.
 */
@Singleton
class PorcineOfInterestQuest : QuestScript(
    "quest_porcineofinterest",
    "varp.porcine_main",
    rewards {
        xp("stat.slayer", SLAYER_XP)
        extra("$SLAYER_REWARD_POINTS Slayer reward points")
    },
    ItemRewardDisplay(REINFORCED_GOGGLES, zoom = 130),
    questVarbit = "varbit.porcine",
) {
    /** Set once Sarah has told the story of the attack, so she does not tell it twice. */
    val sarahBriefed = quest.attribute(name = "SARAH_BRIEFED", default = false)

    /** Set once Spria has explained the sourhog and handed over the goggles. */
    val spriaBriefed = quest.attribute(name = "SPRIA_BRIEFED", default = false)

    override fun ScriptContext.init() {
        quest.onVarSync(::clearFlagsWhenReset)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    /** `::resetquest` only puts the stage varbit back; the flags sharing the varp are ours. */
    private fun clearFlagsWhenReset(player: Player) {
        if (stage(player) != 0) {
            return
        }
        player.porcineFootCut = CORPSE_WITH_FOOT
        player.porcineInspectedCart = false
        player.porcineNeedRope = false
        player.porcineRosie = false
        player.porcineStopWarning = false
    }

    override fun subTitle(): String =
        "reading the <col=800000>notice board</col> behind the wine stall in <col=800000>Draynor " +
            "Village</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "The Draynor Village notice board carried a bounty from <red>Sarah</red> of the " +
                    "<red>Falador farm</red>, south of the city: a troublesome monster, and a " +
                    "reward for whoever deals with it.",
            ) {}

            objective("I should go and see <red>Sarah</red> at the farm to hear what happened.") {
                visibleWhen {
                    stage(access.player) == STAGE_STARTED &&
                        !access.player.porcineInspectedCart
                }
            }

            objective(
                "Sarah was attacked near the <red>crossroads north of Draynor Village</red> and " +
                    "lost a cart of farm produce there. I should look over what is left of it.",
            ) {
                visibleWhen {
                    stage(access.player) == STAGE_STARTED &&
                        !access.player.porcineInspectedCart
                }
            }

            objective(
                "Whatever took the cart went <red>north-east</red>, leaving a trail of gnawed " +
                    "produce and flattened trees behind it.",
            ) {
                visibleWhen {
                    stage(access.player) == STAGE_STARTED && access.player.porcineInspectedCart
                }
                custom(
                    access.player.porcineNeedRope,
                    "The trail ends at a <red>strange hole</red> by the river. I will need a " +
                        "<red>rope</red> to climb down it.",
                )
                    .preserveObjective(strikeObjective = false)
            }

            objective("I have tied my rope to the hole. Time to climb down.") {
                visibleWhen { stage(access.player) == STAGE_ROPE_TIED }
            }

            objective("I should follow the cave to its end and see what lives down there.") {
                visibleWhen { stage(access.player) == STAGE_IN_CAVE }
            }

            objective(
                "Something the size of a cart spat acid in my eyes and knocked me out cold. A " +
                    "woman named <red>Spria</red> dragged me back to her house in <red>Draynor " +
                    "Village</red>. I should talk to her.",
            ) {
                visibleWhen { stage(access.player) == STAGE_AMBUSHED }
            }

            objective(
                "Spria says the monster is a <red>sourhog</red>, and gave me a pair of " +
                    "<red>reinforced goggles</red> to keep its spit out of my eyes. It shrugs " +
                    "off slashing and magic, so I should <red>stab</red> it or shoot it.",
            ) {
                visibleWhen { stage(access.player) == STAGE_GOGGLES }
                custom(
                    !access.wearsGoggles(),
                    "I need to <red>wear</red> the goggles before going back down the hole.",
                )
                    .preserveObjective(strikeObjective = false)
            }

            objective(
                "The sourhog is dead. I need to cut a <red>foot</red> from the carcass with a " +
                    "<red>knife</red> or a slashing weapon to prove it to Sarah.",
            ) {
                visibleWhen {
                    stage(access.player) == STAGE_SLAIN && !access.carries(SOURHOG_FOOT)
                }
            }

            objective("I should take the <red>sourhog foot</red> to <red>Sarah</red> and claim the bounty.") {
                visibleWhen {
                    stage(access.player) == STAGE_SLAIN && access.carries(SOURHOG_FOOT)
                }
            }

            objective(
                "Sarah paid the bounty and mentioned a strange hooded woman asking after the " +
                    "job. I should go back to <red>Spria</red> in Draynor Village.",
            ) {
                visibleWhen { stage(access.player) == STAGE_BOUNTY }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "A notice board in Draynor Village carried Sarah's bounty on the monster that " +
                    "ambushed her near the crossroads and made off with her cart of produce.",
            )
            line(
                "The trail of gnawed carrots led to a hole by the river. At the end of the cave " +
                    "below, a sourhog spat acid in my eyes and left me for dead; the slayer " +
                    "master Spria carried me out and gave me a pair of reinforced goggles.",
            )
            line(
                "I went back with the goggles on, killed the sourhog, cut off a foot for Sarah, " +
                    "and collected the bounty.",
            )
        }

    companion object {
        const val STAGE_STARTED = 10
        const val STAGE_ROPE_TIED = 11
        const val STAGE_IN_CAVE = 15
        const val STAGE_AMBUSHED = 16
        const val STAGE_GOGGLES = 20
        const val STAGE_SLAIN = 25
        const val STAGE_BOUNTY = 30
        const val STAGE_COMPLETE = 40

        const val QUEST_KEY = "quest_porcineofinterest"

        const val SLAYER_XP = 1000.0
        const val SLAYER_REWARD_POINTS = 30
        const val BOUNTY_COINS = 5000
        const val RECOMMENDED_COMBAT = 20

        /** Values of `varbit.porcine_footcut`, which picks the `loc.porcine_dead_sourhog` form. */
        const val CORPSE_WITH_FOOT = 0
        const val CORPSE_FOOT_CUT = 1

        const val SARAH = "npc.farming_shopkeeper_1"
        const val SARAH_SHOP = "inv.farming_shop_3"
        const val ROSIE = "npc.porcine_sheepdog_multi"

        /** Spria's multinpc; she is `npc.porcine_spria` until the quest ends and the master after. */
        const val SPRIA = "npc.slayer_master_9"

        const val SOURHOG = "npc.sourhog"
        const val QUEST_SOURHOG = "npc.porcine_sourhog_second"
        const val PIG_THING = "npc.porcine_sourhog_cutscene"
        const val SPRIA_CUTSCENE = "npc.porcine_spria_cutscene"

        const val REINFORCED_GOGGLES = "obj.slayer_reinforced_goggles"
        const val SOURHOG_FOOT = "obj.porcine_sourhog_trophy"
        const val ROPE = "obj.rope"
        const val KNIFE = "obj.knife"
        const val COINS = "obj.coins"
    }
}

/** 0 shows the carcass with both feet, 1 shows it with one cut off, 2 removes it entirely. */
var Player.porcineFootCut by intVarBit("varbit.porcine_footcut")

/** Set once the damaged cart at the crossroads has been looked over. */
var Player.porcineInspectedCart by boolVarBit("varbit.porcine_inspected_cart")

/** Set when the hole has been found but there was no rope to tie to it. */
var Player.porcineNeedRope by boolVarBit("varbit.porcine_need_rope")

/** Set when Sarah introduces her sheepdog, which turns the nameless dog into Rosie. */
var Player.porcineRosie by boolVarBit("varbit.porcine_rosie")

/** Set once the blockage has warned the player that there is no rescue the second time. */
var Player.porcineStopWarning by boolVarBit("varbit.porcine_stop_warning")

internal fun setVarBit(player: Player, varbit: String, value: Int) {
    if (player.vars[varbit] != value) {
        VarPlayerIntMapSetter.set(player, varbit, value)
    }
}

/** Quest rewards land through the scroll; the slayer points balance is a plain varbit. */
internal fun Player.addSlayerRewardPoints(amount: Int) {
    setVarBit(this, "varbit.slayer_points", vars["varbit.slayer_points"] + amount)
}
