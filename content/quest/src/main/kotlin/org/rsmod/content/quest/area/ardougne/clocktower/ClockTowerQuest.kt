package org.rsmod.content.quest.area.ardougne.clocktower

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Clock Tower.
 *
 * The stage is the cache varp `varp.cogquest` (endstate 8 from `dbrow.quest_clocktower`): it sits
 * at [STAGE_STARTED] for the whole repair and jumps to [STAGE_COMPLETE] when Brother Kojo pays.
 * Which cogs are in place, and whether this player has poisoned the rats in the cage, live in
 * server-only varbits on `varp.clocktower_state`.
 */
@Singleton
class ClockTowerQuest :
    QuestScript(
        QUEST_KEY,
        "varp.cogquest",
        rewards { item(COINS, REWARD_COINS) },
        ItemRewardDisplay(Cog.WHITE.obj, zoom = 200),
    ) {
    override fun ScriptContext.init() {
        quest.onVarSync(::normalise)
    }

    override fun subTitle(): String =
        "talking to <col=800000>Brother Kojo</col> at the <col=800000>Clock Tower</col> south " +
            "of <col=800000>East Ardougne</col>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = access.player
            line(
                "Brother Kojo's clock tower has broken down. He asked me to find the four " +
                    "missing cogs in the cellar beneath the tower and put one on a spindle on " +
                    "each of the tower's four levels.",
            )
            line("The cogs are too heavy to carry more than one at a time.")
            for (cog in Cog.entries) {
                if (isPlaced(p, cog)) strike(cog.journalDone) else line(cog.journalHint)
            }
            if (allPlaced(p)) {
                line("All four cogs are in place. I should tell Brother Kojo.")
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I found the four cogs in the dungeon beneath Brother Kojo's clock tower: the " +
                    "red one guarded by ogres, the blue one locked in a cell, the black one in a " +
                    "ring of fire and the white one behind a cage full of rats.",
            )
            line("With each cog on its spindle the clock works again, and Kojo paid me 500 coins.")
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isActive(player: Player): Boolean = stage(player) == STAGE_STARTED

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun isPlaced(player: Player, cog: Cog): Boolean = player.vars[cog.varbit] == 1

    fun place(player: Player, cog: Cog) {
        VarPlayerIntMapSetter.set(player, cog.varbit, 1)
    }

    fun placedCount(player: Player): Int = Cog.entries.count { isPlaced(player, it) }

    fun allPlaced(player: Player): Boolean = placedCount(player) == Cog.entries.size

    fun ratsPoisoned(player: Player): Boolean = player.vars[RATS_POISONED_VARBIT] == 1

    fun poisonRats(player: Player) {
        VarPlayerIntMapSetter.set(player, RATS_POISONED_VARBIT, 1)
    }

    private fun normalise(player: Player) {
        if (stage(player) != 0) {
            return
        }
        for (cog in Cog.entries) {
            VarPlayerIntMapSetter.set(player, cog.varbit, 0)
        }
        VarPlayerIntMapSetter.set(player, RATS_POISONED_VARBIT, 0)
    }

    companion object {
        const val QUEST_KEY = "quest_clocktower"

        const val STAGE_STARTED = 1
        const val STAGE_COMPLETE = 8

        const val REWARD_COINS = 500

        const val KOJO = "npc.brother_kojo"
        const val COINS = "obj.coins"

        const val RATS_POISONED_VARBIT = "varbit.clocktower_rats_poisoned"
    }
}

/**
 * One of the four cogs. [spindle] is the broken spindle the cog belongs on and [fittedSpindle] the
 * intact spindle of the same colour found on the other levels.
 */
enum class Cog(
    val label: String,
    val obj: String,
    val varbit: String,
    val spindle: String,
    val fittedSpindle: String,
    val journalHint: String,
    val journalDone: String,
) {
    RED(
        "red",
        "obj.redcog",
        "varbit.clocktower_red_cog",
        "loc.brokeclockpole_red",
        "loc.clockpole_red",
        "The red cog lies in the cellar among some ogres, beyond the south-east door.",
        "The red cog is fitted to its spindle on the ground floor.",
    ),
    BLACK(
        "black",
        "obj.blackcog",
        "varbit.clocktower_black_cog",
        "loc.brokeclockpole_black",
        "loc.clockpole_black",
        "The black cog sits in a ring of fire beyond the north-east door. It is too hot to " +
            "touch without some water or ice gloves.",
        "The black cog is fitted to its spindle in the cellar.",
    ),
    BLUE(
        "blue",
        "obj.bluecog",
        "varbit.clocktower_blue_cog",
        "loc.brokeclockpole_blue",
        "loc.clockpole_blue",
        "The blue cog is locked in a cell with a rat. There may be another way in from the " +
            "ladder south of the zoo.",
        "The blue cog is fitted to its spindle on the first floor.",
    ),
    WHITE(
        "white",
        "obj.whitecog",
        "varbit.clocktower_white_cog",
        "loc.brokeclockpole_white",
        "loc.clockpole_white",
        "The white cog is behind a gate past the rat cage beyond the north-west door. The rats " +
            "might be dealt with if I found some poison.",
        "The white cog is fitted to its spindle on the top floor.",
    );

    companion object {
        fun ofObj(obj: String): Cog? = entries.firstOrNull { it.obj == obj }
    }
}
