package org.rsmod.content.quest.area.lumbridge.tearsofguthix

import jakarta.inject.Singleton
import java.time.LocalDate
import java.time.ZoneOffset
import org.rsmod.api.attr.AttributeKey
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player

/** Who may drink from the Tears of Guthix, when, and what the tears give. */
@Singleton
class TearsOfGuthixRules {
    var today: () -> Int = { LocalDate.now(ZoneOffset.UTC).toEpochDay().toInt() }

    fun hasVisited(player: Player): Boolean = player.togLastVisitDay != 0

    fun daysUntilReturn(player: Player): Int {
        if (!hasVisited(player)) {
            return 0
        }
        return (player.togLastVisitDay + DAYS_BETWEEN_VISITS - today()).coerceAtLeast(0)
    }

    fun hadAdventures(player: Player): Boolean =
        !hasVisited(player) ||
            player.togQuestPoints > player.togQpAtLastVisit ||
            totalXp(player) >= xpDue(player)

    fun isEligible(player: Player): Boolean = hadAdventures(player) && daysUntilReturn(player) == 0

    fun xpDue(player: Player): Long = xpAtLastVisit(player) + XP_BETWEEN_VISITS

    fun timeLimit(player: Player): Int = player.togQuestPoints.coerceIn(1, MAX_COUNTDOWN)

    fun recordVisit(player: Player) {
        val xp = totalXp(player)
        player.togLastVisitDay = today()
        player.togQpAtLastVisit = player.togQuestPoints.coerceAtMost(MAX_RECORDED_QP)
        player.togXpBillionsAtLastVisit = (xp / BILLION).toInt()
        player.togXpAtLastVisit = (xp % BILLION).toInt()
    }

    fun xpAtLastVisit(player: Player): Long =
        player.togXpBillionsAtLastVisit * BILLION + player.togXpAtLastVisit

    fun totalXp(player: Player): Long = SKILLS.sumOf { player.statMap.getXP(it.stat).toLong() }

    /** The skill with the least experience, ties going to the first eligible skill in order. */
    fun rewardSkill(player: Player): Skill =
        SKILLS.filter { canTrain(player, it) }.minBy { player.statMap.getFineXP(it.stat) }

    fun reward(player: Player, skill: Skill, tears: Int): Double {
        val perTear = xpPerTear(player.statMap.getXP(skill.stat))
        val bonus = if (hasDiaryBonus(player)) DIARY_BONUS else 1.0
        return tears * perTear * bonus
    }

    fun hasDiaryBonus(player: Player): Boolean = player.vars[LUMBRIDGE_HARD_DIARY] != 0

    fun journalNote(player: Player): String {
        if (!hasVisited(player)) {
            return "Juna will let me into the cave as soon as I tell her a story."
        }
        val days = daysUntilReturn(player)
        val wait = if (days == 0) "now" else returnText(days)
        if (hadAdventures(player)) {
            return "I may visit the Tears of Guthix again $wait."
        }
        return "I may visit the Tears of Guthix again $wait, once I have gained another quest " +
            "point or reached ${"%,d".format(xpDue(player))} total XP."
    }

    private fun canTrain(player: Player, skill: Skill): Boolean =
        when (skill) {
            Skill.Herblore -> QuestRequirements.hasCompleted(player, DRUIDIC_RITUAL)
            Skill.Runecraft -> QuestRequirements.hasCompleted(player, RUNE_MYSTERIES)
            Skill.Construction -> ownsHouse(player)
            Skill.Sailing -> QuestRequirements.hasCompleted(player, PANDEMONIUM)
            else -> true
        }

    private fun ownsHouse(player: Player): Boolean =
        player.attr[HOUSE_STATE]?.startsWith(HOUSE_OWNED_PREFIX) == true

    enum class Skill(val stat: String, val message: String) {
        Attack("stat.attack", "You feel a brief surge of aggression!"),
        Strength("stat.strength", "Your muscles bulge!"),
        Ranged("stat.ranged", "Your aim improves."),
        Magic("stat.magic", "You feel magical power coursing through your body."),
        Defence("stat.defence", "You feel very defensive!"),
        Hitpoints("stat.hitpoints", "You feel more healthy."),
        Prayer("stat.prayer", "You suddenly feel very close to the gods."),
        Agility("stat.agility", "You feel very nimble."),
        Herblore("stat.herblore", "You gain a deep understanding of all kinds of strange plants."),
        Thieving("stat.thieving", "You feel your respect for others' property slipping away."),
        Crafting("stat.crafting", "Your fingers feel nimble and suited to delicate work."),
        Runecraft("stat.runecrafting", "You gain a deep understanding of runes."),
        Mining("stat.mining", "You gain a deep understanding of the stones of the earth."),
        Smithing("stat.smithing", "You gain a deep understanding of metal."),
        Fishing("stat.fishing", "You gain a deep understanding of the creatures of the sea."),
        Cooking("stat.cooking", "You have a brief urge to cook some food."),
        Firemaking("stat.firemaking", "You have a brief urge to set light to something!"),
        Woodcutting("stat.woodcutting", "You gain a deep understanding of the trees in the forest."),
        Fletching("stat.fletching", "You gain a deep understanding of wooden sticks."),
        Slayer("stat.slayer", "You gain a deep understanding of many strange creatures."),
        Farming("stat.farming", "You gain a deep understanding of the cycles of nature."),
        Construction("stat.construction", "You feel homesick."),
        Hunter("stat.hunter", "You briefly experience the joy of the hunt."),
        Sailing("stat.sailing", "You gain a deep understanding of navigating the sea."),
    }

    companion object {
        const val DAYS_BETWEEN_VISITS = 7
        const val XP_BETWEEN_VISITS = 100_000L
        const val MAX_COUNTDOWN = 1023
        const val MAX_RECORDED_QP = 1023
        const val BILLION = 1_000_000_000L
        const val DIARY_BONUS = 1.1

        const val DRUIDIC_RITUAL = "quest_druidicritual"
        const val RUNE_MYSTERIES = "quest_runemysteries"
        const val PANDEMONIUM = "quest_pandemonium"
        const val LUMBRIDGE_HARD_DIARY = "varbit.lumbridge_diary_hard_complete"

        private val HOUSE_STATE = AttributeKey<String>(persistenceKey = "poh_house")
        private const val HOUSE_OWNED_PREFIX = "1,"

        val SKILLS = Skill.entries

        fun xpPerTear(xp: Int): Double = minOf(MAX_XP_PER_TEAR, BASE_XP_PER_TEAR + (xp / 27) / 10.0)

        fun returnText(days: Int): String = if (days == 1) "tomorrow" else "in $days days"

        private const val BASE_XP_PER_TEAR = 10.0
        private const val MAX_XP_PER_TEAR = 60.0
    }
}
