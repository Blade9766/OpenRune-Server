package org.rsmod.content.quest.area.wilderness.magearena

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestAttribute
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Mage Arena II.
 *
 * The stage lives in `varbit.ma2_progress` (endstate 4 from `dbrow.miniquest_magearena2`). Its
 * varp, `varp.ma2_perm_2`, also carries the Wilderness warning toggles, so the quest manager is
 * told the varbit rather than the varp.
 * - [STAGE_HUNTING]: Kolodion handed over the enchanted symbol; the three followers are loose
 *   in the Wilderness.
 * - [STAGE_COMPONENTS]: all three remains are with Kolodion; he waits for a cape to imbue.
 * - Complete: the first cape was imbued.
 *
 * Which remains have been handed in is mirrored into the `varbit.ma2_*_component` bits from
 * [handed], so the client sees the same flags as the real game.
 */
@Singleton
class MageArena2Quest
@Inject
constructor(private val mapClock: MapClock) :
    QuestScript(
        "miniquest_magearena2",
        "varp.ma2_perm_2",
        rewards { extra("Kolodion can now imbue god capes.") },
        ItemRewardDisplay("obj.ma2_saradomin_cape"),
        questVarbit = "varbit.ma2_progress",
    ) {
    /** Remains handed to Kolodion during the hunt. */
    val handed: Map<God, QuestAttribute<Boolean>> =
        God.entries.associateWith { quest.attribute(name = "HANDED_${it.name}", default = false) }

    /** Capes of each god Kolodion has agreed to imbue (one per set of remains after the quest). */
    val imbueCredits: Map<God, QuestAttribute<Int>> =
        God.entries.associateWith { quest.attribute(name = "IMBUE_CREDITS_${it.name}", default = 0) }

    /**
     * Followers the player has killed this quest. The symbol stops tracking a dead follower until
     * the quest is complete, after which they can all be hunted again for more remains.
     */
    val killed: Map<God, QuestAttribute<Boolean>> =
        God.entries.associateWith { quest.attribute(name = "KILLED_${it.name}", default = false) }

    /** Index into [FollowerSpawns.SPAWN_POINTS] of where each follower currently hides. */
    val spawnIndex: Map<God, QuestAttribute<Int>> =
        God.entries.associateWith { quest.attribute(name = "SPAWN_${it.name}", default = -1) }

    /** Map clock cycle of the last spawn rotation. */
    val spawnRotation = quest.attribute(name = "SPAWN_ROTATION", default = -1)

    /** Map clock cycle until which a follower's Tele Block holds; not kept across logins. */
    val teleBlockExpiry = quest.attribute(name = "TELEBLOCK_EXPIRY", default = 0, temp = true)

    override fun ScriptContext.init() {
        onPlayerLogin { syncVars(player) }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Kolodion</col> in the cave beneath the <col=800000>Mage Arena</col> " +
            "once you have completed Mage Arena I, have cast one of the god spells 100 times " +
            "and have level 75 Magic."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "<red>Kolodion</red> has sensed three beings in the <red>Wilderness</red> whose " +
                    "power he could bind into a god cape. He gave me an <red>enchanted symbol</red> " +
                    "to track them: a justiciar of Saradomin, a demon of Zamorak and a Guthixian ent.",
            ) {}

            objective(
                "Each being can only be harmed by the god spell of its own god. The symbol grows " +
                    "hotter as I near one, and takes some of my blood each time I use it.",
            ) {
                visibleWhen { stage(access.player) == STAGE_HUNTING }
            }

            for (god in God.entries) {
                objective("Bring Kolodion the ${god.remainsName} of ${god.followerName}.") {
                    visibleWhen { stage(access.player) == STAGE_HUNTING }
                    attribute(handed.getValue(god), "I gave Kolodion the ${god.remainsName}.").strike()
                }
            }

            objective(
                "Kolodion has all three remains. I should hand him the <red>god cape</red> I want " +
                    "him to imbue.",
            ) {
                visibleWhen { stage(access.player) == STAGE_COMPONENTS }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "With the enchanted symbol Kolodion gave me I tracked down Justiciar Zachariah, " +
                    "Porazdir and Derwen in the Wilderness and brought him their remains.",
            )
            line(
                "He bound their power into one of my god capes. He will imbue another cape of a " +
                    "god each time I bring him that god's follower's remains.",
            )
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isHanded(player: Player, god: God): Boolean = handed.getValue(god).get(player)

    fun allHanded(player: Player): Boolean = God.entries.all { isHanded(player, it) }

    fun isKilled(player: Player, god: God): Boolean = killed.getValue(god).get(player)

    fun credits(player: Player, god: God): Int = imbueCredits.getValue(god).get(player)

    fun isTeleBlocked(player: Player): Boolean = teleBlockExpiry.get(player) > mapClock.cycle

    fun teleBlock(player: Player, ticks: Int) {
        teleBlockExpiry.set(player, mapClock.cycle + ticks)
    }

    fun clearTeleBlock(player: Player) {
        teleBlockExpiry.set(player, 0)
    }

    /** Re-applies the component varbits from the stored attributes. */
    fun syncVars(player: Player) {
        for (god in God.entries) {
            val value = if (isHanded(player, god)) 1 else 0
            val varbit = COMPONENT_VARBITS.getValue(god)
            if (player.vars[varbit] != value) {
                VarPlayerIntMapSetter.set(player, varbit, value)
            }
        }
    }

    companion object {
        const val STAGE_HUNTING = 1
        const val STAGE_COMPONENTS = 2

        const val MAGIC_REQ = 75

        val COMPONENT_VARBITS: Map<God, String> =
            mapOf(
                God.SARADOMIN to "varbit.ma2_saradomin_component",
                God.GUTHIX to "varbit.ma2_guthix_component",
                God.ZAMORAK to "varbit.ma2_zamorak_component",
            )
    }
}
