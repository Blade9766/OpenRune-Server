package org.rsmod.content.quest.area.baxtorianfalls.waterfall

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Waterfall Quest.
 *
 * The stage lives in `varp.waterfall_quest` (65), which carries no other varbits, so nothing needs
 * mirroring. The values follow the client's quest helper data: 1 once Almera asks for help, 2
 * after meeting Hudon, 3 once the book on Baxtorian has been read, 4 on entering Glarial's tomb, 5
 * on entering the waterfall, 6 when all six pillars hold their runes and 8 once the floor has
 * risen to the chalice. Nothing in the cache transforms on the stage, so the gaps are harmless.
 */
@Singleton
class WaterfallQuest : QuestScript(
    "quest_waterfall",
    "varp.waterfall_quest",
    rewards {
        xp("stat.attack", COMBAT_XP)
        xp("stat.strength", COMBAT_XP)
        item("obj.diamond", 2)
        item("obj.gold_bar", 2)
        item("obj.mithril_seed", 40)
    },
    ItemRewardDisplay(URN_FULL),
    completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
) {
    /** Set once Gerald has told the player about the treasure. */
    val heardOfTreasure = quest.attribute(name = "HEARD_OF_TREASURE", default = false)

    /** Set once Golrie has handed over the pebble for the first time. */
    val metGolrie = quest.attribute(name = "MET_GOLRIE", default = false)

    /** Three bits per pillar (air, water, earth); see [WaterfallDungeon]. */
    val pillarRunes = quest.attribute(name = "PILLAR_RUNES", default = 0)

    override fun ScriptContext.init() {}

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    fun allRunesPlaced(player: Player): Boolean = pillarRunes.get(player) == ALL_PILLAR_RUNES

    override fun subTitle(): String =
        "talking to <col=800000>Almera</col> in her house on top of <col=800000>Baxtorian " +
            "Falls</col>, south of the Barbarian Outpost."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "<red>Almera</red> is worried about her son <red>Hudon</red>, who has gone " +
                    "treasure hunting on the river. She said I could take the <red>log raft</red> " +
                    "behind her house to find him.",
            ) {
                visibleWhen { stage(access.player) == STAGE_STARTED }
            }

            objective(
                "I found Hudon on an island in the river, but he would not come home. He " +
                    "thinks there is treasure hidden in the falls. <red>Hadley</red>, the tourist " +
                    "guide south of the falls, might know more about it.",
            ) {
                visibleWhen { stage(access.player) == STAGE_MET_HUDON }
                hasItem(BOOK.removePrefix(OBJ_PREFIX), "I found a book on Baxtorian in the tourist centre.").strike()
            }

            objective(
                "The book says <red>Glarial's pebble</red> opens her tomb, and that a gnome " +
                    "family living beneath the <red>Tree Gnome Village</red> may still have it.",
            ) {
                visibleWhen { stage(access.player) == STAGE_READ_BOOK }
                hasItem(PEBBLE.removePrefix(OBJ_PREFIX), "I have Glarial's pebble.").strike()
            }

            objective(
                "I should use the pebble on <red>Glarial's Tombstone</red> north-west of the " +
                    "Fishing Guild. Only visitors with peaceful intent may enter, so I must leave " +
                    "weapons, armour and runes behind.",
            ) {
                visibleWhen { stage(access.player) == STAGE_READ_BOOK && access.player.inv.contains(PEBBLE) }
            }

            objective(
                "Inside Glarial's tomb I need to find her <red>amulet</red> and her <red>urn</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_ENTERED_TOMB }
                custom(access.player.hasAmulet(), "I have Glarial's amulet.").strike()
                hasItem(URN_FULL.removePrefix(OBJ_PREFIX), "I have Glarial's urn.").strike()
            }

            objective(
                "With Glarial's amulet I should be able to enter the waterfall. The book says " +
                    "Baxtorian used <red>air, water and earth runes</red> to command nature, so I " +
                    "should bring six of each, and a <red>rope</red> to reach the ledge.",
            ) {
                visibleWhen {
                    stage(access.player) == STAGE_ENTERED_TOMB &&
                        access.player.hasAmulet() &&
                        access.player.inv.contains(URN_FULL)
                }
            }

            objective(
                "I am inside the waterfall. A key somewhere in these caves should open the way " +
                    "to Baxtorian's tomb.",
            ) {
                visibleWhen { stage(access.player) == STAGE_ENTERED_FALLS }
                hasItem(BAXTORIAN_KEY.removePrefix(OBJ_PREFIX), "I have a key from the caves.").strike()
            }

            objective(
                "Six small pillars stand before the statues of Baxtorian and Glarial. Each " +
                    "has a dent shaped for a rune.",
            ) {
                visibleWhen { stage(access.player) == STAGE_ENTERED_FALLS }
                custom(allRunesPlaced(access.player), "Every pillar holds an air, water and earth rune.").strike()
            }

            objective(
                "The pillars are charged. I should place <red>Glarial's amulet</red> on her statue.",
            ) {
                visibleWhen { stage(access.player) == STAGE_RUNES_PLACED }
            }

            objective(
                "The floor rose up to the <red>chalice</red>. I should lay Glarial to rest beside " +
                    "Baxtorian by pouring her ashes into it.",
            ) {
                visibleWhen { stage(access.player) == STAGE_FLOOR_RISEN }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Almera asked me to look for her son Hudon, who was hunting for treasure on the " +
                    "river. He would not come home, but he set me on the trail of the elf king " +
                    "Baxtorian, who sealed himself beneath the falls.",
            )
            line(
                "A gnome called Golrie gave me Glarial's pebble. With it I entered her tomb and " +
                    "took her amulet and the urn holding her ashes.",
            )
            line(
                "Inside the waterfall I charged the six pillars with runes, placed the amulet on " +
                    "Glarial's statue and poured her ashes into the chalice of eternity, uniting " +
                    "her with Baxtorian. I kept the treasure the chalice held.",
            )
        }

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_MET_HUDON = 2
        const val STAGE_READ_BOOK = 3
        const val STAGE_ENTERED_TOMB = 4
        const val STAGE_ENTERED_FALLS = 5
        const val STAGE_RUNES_PLACED = 6
        const val STAGE_FLOOR_RISEN = 8

        const val COMBAT_XP = 13750.0
        const val RECOMMENDED_COMBAT = 25
        const val PILLAR_COUNT = 6
        const val RUNES_PER_PILLAR = 3
        const val ALL_PILLAR_RUNES = (1 shl (PILLAR_COUNT * RUNES_PER_PILLAR)) - 1

        const val OBJ_PREFIX = "obj."
        const val BOOK = "obj.baxtorian_book_waterfall_quest"
        const val GOLRIE_KEY = "obj.golrie_key_waterfall_quest"
        const val PEBBLE = "obj.glarials_pebble_waterfall_quest"
        const val AMULET = "obj.glarials_amulet_waterfall_quest"
        const val URN_FULL = "obj.glarials_urn_full_waterfall_quest"
        const val URN_EMPTY = "obj.glarials_urn_empty_waterfall_quest"
        const val BAXTORIAN_KEY = "obj.baxtorian_key_waterfall_quest"
        const val ROPE = "obj.rope"

        const val ALMERA = "npc.almera_waterfall_quest"
        const val HUDON = "npc.hudon_waterfall_quest"
        const val GERALD = "npc.gerald_waterfall_quest"
        const val HADLEY = "npc.hadley_waterfall_quest"
        const val GOLRIE = "npc.golrie_waterfall_quest"
    }
}
