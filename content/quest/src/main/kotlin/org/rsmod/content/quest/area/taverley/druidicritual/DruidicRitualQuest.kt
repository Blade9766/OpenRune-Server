package org.rsmod.content.quest.area.taverley.druidicritual

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Druidic Ritual.
 *
 * Stages (stored in `varp.druidquest`, endstate 4 from `dbrow.quest_druidicritual`):
 * - [STAGE_STARTED]: Kaqemeex has sent the player to Sanfew.
 * - [STAGE_SPOKEN_SANFEW]: Sanfew has asked for the four meats.
 * - [STAGE_GAVE_INGREDIENTS]: Sanfew has the enchanted meats; Kaqemeex owes the reward.
 * - [STAGE_COMPLETE]: Kaqemeex has taught the player Herblore.
 */
@Singleton
class DruidicRitualQuest : QuestScript(
    "quest_druidicritual",
    "varp.druidquest",
    rewards {
        xp("stat.herblore", 250.0)
        extra("Ability to use the Herblore skill")
    },
    ItemRewardDisplay(MARRENTILL, zoom = 220),
) {
    override fun ScriptContext.init() {}

    override fun subTitle(): String =
        "talking to <col=800000>Kaqemeex</col> at the stone circle <col=800000>north of " +
            "Taverley</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "Dark wizards cursed the stone circle south of Varrock long ago. <red>Kaqemeex" +
                    "</red>, at the druids' circle north of Taverley, has asked me to help " +
                    "purify it.",
            ) {}

            objective(
                "I should speak to <red>Sanfew</red>, upstairs in the herblore shop in " +
                    "<red>Taverley</red>, who is preparing the ritual.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_STARTED }
            }

            objective(
                "Sanfew needs the raw meat of four different animals for his potion to honour " +
                    "Guthix. Each one has to be dipped in the <red>Cauldron of Thunder</red>, " +
                    "deep in the dungeon south of Taverley.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_SPOKEN_SANFEW }
            }

            objective("<red>Raw beef</red>. Cows are easy enough to find.") {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_SPOKEN_SANFEW }
                hasItem("enchanted_beef", "I have some enchanted beef.").strike()
            }

            objective("<red>Raw rat meat</red>. Giant rats will provide.") {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_SPOKEN_SANFEW }
                hasItem("enchanted_rat_meat", "I have some enchanted rat meat.").strike()
            }

            objective("<red>Raw bear meat</red>. There are bears in the woods around Taverley.") {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_SPOKEN_SANFEW }
                hasItem("enchanted_bear_meat", "I have some enchanted bear meat.").strike()
            }

            objective("<red>Raw chicken</red>. Any farm will have a few.") {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_SPOKEN_SANFEW }
                hasItem("enchanted_chicken", "I have an enchanted chicken.").strike()
            }

            objective(
                "Sanfew has his meats. I should go back to <red>Kaqemeex</red> at the stone " +
                    "circle north of Taverley to be taught Herblore.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_GAVE_INGREDIENTS }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Kaqemeex, of the druids of Guthix, asked me to help purify the stone circle " +
                    "south of Varrock, which dark wizards cursed many years ago.",
            )
            line(
                "His fellow Sanfew needed the raw meat of four different animals for the " +
                    "potion, each dipped in the Cauldron of Thunder beneath Taverley. I brought " +
                    "him beef, rat, bear and chicken.",
            )
            line("Kaqemeex kept his word and taught me the ancient art of Herblore.")
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun hasAllMeats(player: Player): Boolean = ENCHANTED_MEATS.all { player.inv.count(it) > 0 }

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_SPOKEN_SANFEW = 2
        const val STAGE_GAVE_INGREDIENTS = 3
        const val STAGE_COMPLETE = 4

        const val QUEST_KEY = "quest_druidicritual"

        const val MARRENTILL = "obj.marentill"

        const val RAW_BEEF = "obj.raw_beef"
        const val RAW_RAT_MEAT = "obj.raw_rat_meat"
        const val RAW_BEAR_MEAT = "obj.raw_bear_meat"
        const val RAW_CHICKEN = "obj.raw_chicken"

        const val ENCHANTED_BEEF = "obj.enchanted_beef"
        const val ENCHANTED_RAT_MEAT = "obj.enchanted_rat_meat"
        const val ENCHANTED_BEAR_MEAT = "obj.enchanted_bear_meat"
        const val ENCHANTED_CHICKEN = "obj.enchanted_chicken"

        const val KAQEMEEX = "npc.kaqemeex"
        const val SANFEW = "npc.sanfew"
        const val SUIT_OF_ARMOUR = "npc.suit_of_armour"

        /** Raw meat to the enchanted form the Cauldron of Thunder turns it into. */
        val CAULDRON_MEATS =
            mapOf(
                RAW_BEEF to ENCHANTED_BEEF,
                RAW_RAT_MEAT to ENCHANTED_RAT_MEAT,
                RAW_BEAR_MEAT to ENCHANTED_BEAR_MEAT,
                RAW_CHICKEN to ENCHANTED_CHICKEN,
            )

        val ENCHANTED_MEATS =
            listOf(ENCHANTED_BEEF, ENCHANTED_RAT_MEAT, ENCHANTED_BEAR_MEAT, ENCHANTED_CHICKEN)
    }
}
