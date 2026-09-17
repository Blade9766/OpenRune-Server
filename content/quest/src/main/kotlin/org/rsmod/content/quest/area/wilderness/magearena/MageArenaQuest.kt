package org.rsmod.content.quest.area.wilderness.magearena

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestAttribute
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Mage Arena I.
 *
 * Stages (stored in `varp.magearena`, endstate 8 from `dbrow.miniquest_magearena1`):
 * - [STAGE_DUEL]: Kolodion accepted the challenge; the player fights his five forms in the
 *   arena. Which form is up is kept in [kolodionForm] so a fight that is abandoned resumes
 *   where it left off, as it does in the real game.
 * - [STAGE_DEFEATED]: the demon form fell and Kolodion sent the player into the sparkling pool
 *   to choose a god.
 * - [STAGE_CAPE]: a god answered at one of the statues; the chamber guardian will hand over the
 *   matching staff.
 * - Complete: the guardian gave the staff.
 *
 * Nothing else shares the quest varp, so the stage is all that lives there. The cast counts of
 * the god spells (100 casts of any one of them opens Mage Arena II) and the Charge timer are
 * quest attributes.
 */
@Singleton
class MageArenaQuest
@Inject
constructor(private val mapClock: MapClock) :
    QuestScript(
        "miniquest_magearena1",
        "varp.magearena",
        rewards { extra("The three god spells; master one with 100 casts for Kolodion's next challenge.") },
        ItemRewardDisplay("obj.saradomin_cape"),
    ) {
    /** Index into the duel's form list of the Kolodion the player has to beat next. */
    val kolodionForm = quest.attribute(name = "KOLODION_FORM", default = 0)

    /** The guardian has handed over a staff (he only gives one for free). */
    val staffGiven = quest.attribute(name = "STAFF_GIVEN", default = false)

    /** Casts of each god spell, anywhere, capped at [CASTS_TO_UNLOCK]. */
    val casts: Map<God, QuestAttribute<Int>> =
        God.entries.associateWith { quest.attribute(name = "CASTS_${it.name}", default = 0) }

    /** Map clock cycle at which the Charge spell's boost runs out; not kept across logins. */
    val chargeExpiry = quest.attribute(name = "CHARGE_EXPIRY", default = 0, temp = true)

    override fun ScriptContext.init() {}

    override fun subTitle(): String =
        "talking to <col=800000>Kolodion</col> in the cave beneath the <col=800000>Mage Arena</col>, " +
            "deep in the Wilderness. You will need level 60 Magic."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "<red>Kolodion</red>, master of battle magic, will only let those who beat him in " +
                    "his <red>arena</red> learn its spells. I agreed to duel him.",
            ) {}

            objective(
                "Kolodion is a shape-shifter. I must defeat every one of his forms in the arena " +
                    "using <red>magic</red>; melee and ranged attacks are useless against him. If I " +
                    "leave, the lever by the arena wall will take me back in.",
            ) {
                visibleWhen { stage(access.player) == STAGE_DUEL }
            }

            objective(
                "I defeated Kolodion. He told me to step into the <red>sparkling pool</red> in " +
                    "his cave, which leads to a chamber where I must choose a god to represent.",
            ) {
                visibleWhen { stage(access.player) == STAGE_DEFEATED }
            }

            objective(
                "A god has granted me a cape. I should show it to the <red>Chamber guardian</red> " +
                    "so he can give me a staff to match.",
            ) {
                visibleWhen { stage(access.player) == STAGE_CAPE }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I defeated Kolodion in his arena, chose a god in the chamber beneath his cave " +
                    "and was given a god cape and staff.",
            )
            line(
                "I can cast the god spells. Kolodion says that once I have mastered one of them by " +
                    "casting it 100 times he will have a greater challenge for me.",
            )
            for (god in God.entries) {
                val count = casts(player.player, god)
                val text =
                    if (count >= CASTS_TO_UNLOCK) "${god.spellName}: mastered."
                    else "${god.spellName}: $count of $CASTS_TO_UNLOCK casts."
                line(text)
            }
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun casts(player: Player, god: God): Int = casts.getValue(god).get(player)

    fun spellUnlocked(player: Player, god: God): Boolean = casts(player, god) >= CASTS_TO_UNLOCK

    /** Mastering any one god spell is what Kolodion asks for before Mage Arena II. */
    fun anySpellUnlocked(player: Player): Boolean = God.entries.any { spellUnlocked(player, it) }

    /** Records a cast; returns true when this cast was the one that mastered the spell. */
    fun recordCast(player: Player, god: God): Boolean {
        val attribute = casts.getValue(god)
        val count = attribute.get(player)
        if (count >= CASTS_TO_UNLOCK) {
            return false
        }
        attribute.set(player, count + 1)
        return count + 1 == CASTS_TO_UNLOCK
    }

    fun isCharged(player: Player): Boolean = chargeExpiry.get(player) > mapClock.cycle

    fun charge(player: Player) {
        chargeExpiry.set(player, mapClock.cycle + CHARGE_TICKS)
    }

    companion object {
        const val STAGE_DUEL = 1
        const val STAGE_DEFEATED = 2
        const val STAGE_CAPE = 3

        const val MAGIC_REQ = 60
        const val CASTS_TO_UNLOCK = 100

        /** Seven minutes. */
        const val CHARGE_TICKS = 700

        /** Charge takes the god spells' max hit from 20 to 30. */
        const val CHARGE_MAX_HIT_BONUS = 10
    }
}
