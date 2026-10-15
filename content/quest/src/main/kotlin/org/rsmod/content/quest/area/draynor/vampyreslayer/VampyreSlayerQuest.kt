package org.rsmod.content.quest.area.draynor.vampyreslayer

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Vampyre Slayer.
 *
 * Stages (stored in `varp.vampire`, endstate 3 from `dbrow.quest_vampyreslayer`):
 * - [STAGE_STARTED]: Morgan asked for Count Draynor to be slain and sent the player to Dr Harlow.
 * - [STAGE_SPOKE_TO_HARLOW]: Dr Harlow wants a beer before he will help.
 * - [STAGE_COMPLETE]: the stake has been hammered into the Count.
 *
 * The finer steps (beer handed over, stake given, Morgan's garlic hint, the post-quest thanks)
 * are the cache's own varbits on `varp.vampire_secondary`, a separate varp from the stage, so
 * they are used as-is. `varbit.morgan_postquest_dialogue` also switches Morgan's multinpc.
 */
@Singleton
class VampyreSlayerQuest : QuestScript(
    "quest_vampyreslayer",
    "varp.vampire",
    rewards { xp("stat.attack", 4825.0) },
    ItemRewardDisplay(STAKE),
) {
    override fun ScriptContext.init() {}

    override fun subTitle(): String =
        "talking to <col=800000>Morgan</col> in his house in <col=800000>Draynor Village</col>, " +
            "just north of Ned's house."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "<red>Morgan</red> of Draynor Village wants me to put a stop to the evil vampyre " +
                    "<red>Count Draynor</red>, who lurks in the manor north of the village.",
            ) {}

            objective(
                "He told me to find the retired vampyre hunter <red>Dr Harlow</red>, who spends " +
                    "most of his time in the <red>Blue Moon Inn</red> in Varrock.",
            ) {
                visibleWhen { stage(access.player) == STAGE_STARTED }
            }

            objective(
                "Dr Harlow won't help me until I buy him a <red>beer</red>. The bartender in the " +
                    "Blue Moon Inn sells them.",
            ) {
                visibleWhen {
                    stage(access.player) == STAGE_SPOKE_TO_HARLOW && !access.player.harlowGivenBeer
                }
            }

            objective(
                "Dr Harlow gave me a <red>stake</red>. I need to find the vampyre in the " +
                    "<red>basement of Draynor Manor</red>, weaken him, then drive the stake in " +
                    "with a <red>hammer</red>. He also said vampyres don't like <red>garlic</red>.",
            ) {
                visibleWhen {
                    stage(access.player) == STAGE_SPOKE_TO_HARLOW && access.player.harlowGivenStake
                }
                custom(
                    !access.hasStakeAnywhere(),
                    "I have lost the stake. Dr Harlow may have a spare.",
                ).preserveObjective(strikeObjective = false)
                custom(
                    access.player.morganExtraDialogue,
                    "Morgan keeps garlic in the cupboard upstairs in his house.",
                ).preserveObjective(strikeObjective = false)
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Morgan of Draynor Village asked me to slay Count Draynor, the vampyre living in " +
                    "the manor north of the village.",
            )
            line(
                "After I bought him a beer, the retired vampyre hunter Dr Harlow gave me a stake " +
                    "and told me how to use it.",
            )
            line(
                "I found the Count in his coffin beneath Draynor Manor and, once he was weakened, " +
                    "hammered the stake into his chest.",
            )
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun ProtectedAccess.hasStakeAnywhere(): Boolean = inv.count(STAKE) > 0 || bank.count(STAKE) > 0

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_SPOKE_TO_HARLOW = 2
        const val STAGE_COMPLETE = 3

        const val RECOMMENDED_COMBAT = 20

        const val STAKE = "obj.stake"
        const val HAMMER = "obj.hammer"
        const val GARLIC = "obj.garlic"
        const val BEER = "obj.beer"
    }
}

var Player.morganExtraDialogue by boolVarBit("varbit.morgan_extra_dialogue")
var Player.harlowGivenBeer by boolVarBit("varbit.harlow_given_beer")
var Player.harlowGivenStake by boolVarBit("varbit.harlow_given_stake")
var Player.morganPostquestDialogue by boolVarBit("varbit.morgan_postquest_dialogue")
