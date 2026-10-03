package org.rsmod.content.quest.area.falador.doricsquest

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Doric's Quest.
 *
 * The stage is the cache varp `varp.doricquest` (endstate 100 from `dbrow.quest_dorics`):
 * [STAGE_STARTED] once Doric has asked for his materials, [STAGE_COMPLETE] once they are handed
 * over and his anvils are open to the player.
 */
@Singleton
class DoricsQuest :
    QuestScript(
        QUEST_KEY,
        "varp.doricquest",
        rewards {
            xp("stat.mining", REWARD_XP)
            item(COINS, REWARD_COINS)
            scroll("1,300 Mining XP", "180 Coins", "Use of Doric's anvils")
        },
        ItemRewardDisplay(BRONZE_PICKAXE, zoom = 200),
    ) {

    override fun ScriptContext.init() {}

    override fun subTitle(): String =
        "talking to <col=800000>Doric</col>, who lives <col=800000>north of Falador</col>, " +
            "just east of the gate to Taverley."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "<red>Doric</red> the dwarf will let me use his anvils if I bring him the " +
                    "materials he needs to make his pickaxes:",
            ) {}

            for (material in MATERIALS) {
                objective("${material.count} <red>${material.label}</red>") {
                    custom(
                        access.inv.count(material.obj) >= material.count,
                        "${material.count} ${material.label}",
                    ).strike()
                }
            }

            objective(
                "The rocks just inside the <red>Dwarven Mine</red>, east of Doric's house, " +
                    "have all of them.",
            ) {
                visibleWhen { !hasMaterials(access.player) }
            }

            objective("I have everything Doric asked for. I should take it back to him.") {
                visibleWhen { hasMaterials(access.player) }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Doric the dwarf, north of Falador, needed clay, copper ore and iron ore to make " +
                    "his pickaxes.",
            )
            line(
                "I brought him 6 clay, 4 copper ore and 2 iron ore, and in return he paid me and " +
                    "lets me use his anvils whenever I like.",
            )
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isStarted(player: Player): Boolean = stage(player) >= STAGE_STARTED

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    /**
     * Doric's anvils are open to the player: the quest really completed, or never started while the
     * requirement policy counts it complete.
     */
    fun anvilsUnlocked(player: Player): Boolean =
        isComplete(player) || (stage(player) == 0 && QuestRequirements.hasCompleted(player, QUEST_KEY))

    fun hasMaterials(player: Player): Boolean =
        MATERIALS.all { player.inv.count(it.obj) >= it.count }

    fun hasExactMaterials(player: Player): Boolean =
        MATERIALS.all { player.inv.count(it.obj) == it.count }

    fun start(access: ProtectedAccess) {
        if (stage(access.player) == 0) {
            quest.setQuestStage(access, STAGE_STARTED)
        }
    }

    /** Takes all twelve materials in one transaction; nothing is taken if any are missing. */
    fun takeMaterials(access: ProtectedAccess): Boolean =
        access.player.invTransaction(access.inv) {
            val inventory = select(access.inv)
            for (material in MATERIALS) {
                delete {
                    from = inventory
                    obj = material.obj.asRSCM()
                    strictCount = material.count
                }
            }
        }.success

    fun complete(access: ProtectedAccess) {
        quest.completeQuest(access)
    }

    data class Material(val obj: String, val count: Int, val label: String)

    companion object {
        const val QUEST_KEY = "quest_dorics"

        const val STAGE_STARTED = 10
        const val STAGE_COMPLETE = 100

        const val CLAY = "obj.clay"
        const val COPPER_ORE = "obj.copper_ore"
        const val IRON_ORE = "obj.iron_ore"
        const val BRONZE_PICKAXE = "obj.bronze_pickaxe"
        const val COINS = "obj.coins"

        const val REWARD_XP = 1300.0
        const val REWARD_COINS = 180

        val MATERIALS =
            listOf(
                Material(CLAY, 6, "clay"),
                Material(COPPER_ORE, 4, "copper ore"),
                Material(IRON_ORE, 2, "iron ore"),
            )
    }
}
