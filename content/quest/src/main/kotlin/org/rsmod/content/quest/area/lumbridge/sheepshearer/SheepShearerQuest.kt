package org.rsmod.content.quest.area.lumbridge.sheepshearer

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sheep Shearer.
 *
 * The quest varp (`varp.sheep`, endstate 21 from `dbrow.quest_sheepshearer`) counts progress
 * directly: 1 once Fred has asked for wool, then one more for every ball of wool handed in, so
 * the stage is `1 + balls delivered` and the quest completes with the twentieth ball.
 */
@Singleton
class SheepShearerQuest : QuestScript(
    "quest_sheepshearer",
    "varp.sheep",
    rewards {
        xp("stat.crafting", 150.0)
        item(COINS, REWARD_COINS)
    },
    ItemRewardDisplay(BALL_OF_WOOL),
) {
    /** The player has met The Thing in Fred's field. */
    val seenTheThing = quest.attribute(name = "SEEN_THE_THING", default = false)

    override fun ScriptContext.init() {}

    override fun subTitle(): String =
        "talking to <col=800000>Fred the Farmer</col> in his house by the sheep pen " +
            "<col=800000>north of Lumbridge Castle</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "<red>Fred the Farmer</red>, whose farm is north of Lumbridge, wants his sheep " +
                    "sheared and the wool spun. He will pay me for <red>20 balls of wool</red>.",
            ) {}

            objective(
                "I can shear the sheep in Fred's field with a pair of <red>shears</red>, then spin " +
                    "the wool into balls at the <red>spinning wheel</red> on the first floor of " +
                    "Lumbridge Castle.",
            ) {
                visibleWhen { quest.isQuestInProgress(access.player) }
                custom(
                    delivered(access.player) > 0,
                    "I have given Fred ${delivered(access.player)} of the 20 balls of wool he " +
                        "wants. I should shear and spin some more.",
                )
            }

            objective("Something in Fred's field is not quite a sheep. Fred might want to hear about it.") {
                visibleWhen { seenTheThing.get(access.player) && quest.isQuestInProgress(access.player) }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Fred the Farmer, north of Lumbridge, was worried about how woolly his sheep " +
                    "were getting, and about The Thing that lurks in his field.",
            )
            line(
                "I sheared his sheep, spun the wool into 20 balls at the spinning wheel in " +
                    "Lumbridge Castle, and delivered them. Fred paid me for my trouble.",
            )
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    /** Balls of wool Fred has been given so far. */
    fun delivered(player: Player): Int = (stage(player) - STAGE_STARTED).coerceIn(0, WOOL_REQUIRED)

    fun remaining(player: Player): Int = WOOL_REQUIRED - delivered(player)

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_COMPLETE = 21

        const val WOOL_REQUIRED = 20
        const val REWARD_COINS = 60

        const val SHEARS = "obj.shears"
        const val WOOL = "obj.wool"
        const val BALL_OF_WOOL = "obj.ball_of_wool"
        const val COINS = "obj.coins"
    }
}
