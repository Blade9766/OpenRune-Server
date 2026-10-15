package dev.openrune.tables

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

data class LevelUpRow(
    val label: String,
    val layer: String,
    val jingle: Int,
    val rule: LevelUpRule,
    val alternateJingle: Int? = null,
    val threshold: Int? = null,
    val prefix: String? = null
)

data class StatRow(
    val rowName: String,
    val componentId: String,
    val statString: String,
    val bit: Int,
    val levelUp: LevelUpRow
)

object StatComponents {

    const val COL_COMPONENT = 0
    const val COL_STAT = 1
    const val COL_BIT = 2
    const val COL_LEVELUP_LABEL = 3
    const val COL_LEVELUP_LAYER = 4
    const val COL_LEVELUP_JINGLE = 5
    const val COL_LEVELUP_JINGLE_ALT = 6
    const val COL_LEVELUP_RULE = 7
    const val COL_LEVELUP_THRESHOLD = 8
    const val COL_LEVELUP_PREFIX = 9

    /**
     * Level-up jingles are stored as js5 archive 11 groups, the id the client plays. The `jingle.*`
     * gamevals are Jagex's internal midi ids and do not match these groups.
     */
    private fun levelUp(
        label: String,
        rule: LevelUpRule,
        jingle: Int,
        alternateJingle: Int? = null,
        threshold: Int? = null,
        prefix: String? = null
    ) = LevelUpRow(
        label, "component.levelup_display:${label.lowercase()}", jingle, rule,
        alternateJingle, threshold, prefix
    )

    private fun unlocks(label: String, jingle: Int) =
        levelUp(label, LevelUpRule.Unlocks, jingle, jingle + 1)

    fun statsComponents() = dbTable("dbtable.stat_components", serverOnly = true) {

        column("component", COL_COMPONENT, VarType.COMPONENT)
        column("stat", COL_STAT, VarType.STAT)
        column("bit", COL_BIT, VarType.INT)
        column("levelup_label", COL_LEVELUP_LABEL, VarType.STRING)
        column("levelup_layer", COL_LEVELUP_LAYER, VarType.COMPONENT)
        column("levelup_jingle", COL_LEVELUP_JINGLE, VarType.INT)
        column("levelup_jingle_alt", COL_LEVELUP_JINGLE_ALT, VarType.INT)
        column("levelup_rule", COL_LEVELUP_RULE, VarType.INT)
        column("levelup_threshold", COL_LEVELUP_THRESHOLD, VarType.INT)
        column("levelup_prefix", COL_LEVELUP_PREFIX, VarType.STRING)

        val skillsWithBits = listOf(
            StatRow("dbrow.agility_stat", "component.stats:agility", "stat.agility", 8,
                levelUp("Agility", LevelUpRule.Single, 28)),
            StatRow("dbrow.attack_stat", "component.stats:attack", "stat.attack", 1,
                unlocks("Attack", 29)),
            StatRow("dbrow.construction_stat", "component.stats:construction", "stat.construction", 22,
                levelUp("Construction", LevelUpRule.EveryTenth, 31, 32)),
            StatRow("dbrow.cooking_stat", "component.stats:cooking", "stat.cooking", 16,
                unlocks("Cooking", 33)),
            StatRow("dbrow.crafting_stat", "component.stats:crafting", "stat.crafting", 11,
                unlocks("Crafting", 35)),
            StatRow("dbrow.defence_stat", "component.stats:defence", "stat.defence", 5,
                unlocks("Defence", 37)),
            StatRow("dbrow.farming_stat", "component.stats:farming", "stat.farming", 21,
                levelUp("Farming", LevelUpRule.Unlocks, 10, 11)),
            StatRow("dbrow.firemaking_stat", "component.stats:firemaking", "stat.firemaking", 17,
                unlocks("Firemaking", 39)),
            StatRow("dbrow.fishing_stat", "component.stats:fishing", "stat.fishing", 15,
                unlocks("Fishing", 41)),
            StatRow("dbrow.fletching_stat", "component.stats:fletching", "stat.fletching", 19,
                unlocks("Fletching", 43)),
            StatRow("dbrow.herblore_stat", "component.stats:herblore", "stat.herblore", 9,
                unlocks("Herblore", 45)),
            StatRow("dbrow.hitpoints_stat", "component.stats:hitpoints", "stat.hitpoints", 6,
                levelUp("Hitpoints", LevelUpRule.FromLevel, 47, 48,
                    threshold = 50, prefix = "Your Hitpoints are now")),
            StatRow("dbrow.hunter_stat", "component.stats:hunter", "stat.hunter", 23,
                levelUp("Hunter", LevelUpRule.Parity, 49, 50)),
            StatRow("dbrow.magic_stat", "component.stats:magic", "stat.magic", 4,
                unlocks("Magic", 51)),
            StatRow("dbrow.mining_stat", "component.stats:mining", "stat.mining", 13,
                unlocks("Mining", 53)),
            StatRow("dbrow.prayer_stat", "component.stats:prayer", "stat.prayer", 7,
                unlocks("Prayer", 55)),
            StatRow("dbrow.ranged_stat", "component.stats:ranged", "stat.ranged", 3,
                unlocks("Ranged", 57)),
            StatRow("dbrow.runecraft_stat", "component.stats:runecraft", "stat.runecrafting", 12,
                unlocks("Runecraft", 59)),
            StatRow("dbrow.slayer_stat", "component.stats:slayer", "stat.slayer", 20,
                unlocks("Slayer", 61)),
            StatRow("dbrow.smithing_stat", "component.stats:smithing", "stat.smithing", 14,
                levelUp("Smithing", LevelUpRule.GuideList, 63, 64)),
            StatRow("dbrow.strength_stat", "component.stats:strength", "stat.strength", 2,
                levelUp("Strength", LevelUpRule.FromLevel, 65, 66,
                    threshold = 50)),
            StatRow("dbrow.thieving_stat", "component.stats:thieving", "stat.thieving", 10,
                unlocks("Thieving", 67)),
            StatRow("dbrow.woodcutting_stat", "component.stats:woodcutting", "stat.woodcutting", 18,
                unlocks("Woodcutting", 69)),
            StatRow("dbrow.sailing_stat", "component.stats:sailing", "stat.sailing", 24,
                levelUp("Sailing", LevelUpRule.MaxLevel, 830, 831))
        )

        skillsWithBits.forEach { row ->
            row(row.rowName) {
                columnRSCM(COL_COMPONENT, row.componentId)
                columnRSCM(COL_STAT, row.statString)
                column(COL_BIT, row.bit)

                val levelUp = row.levelUp
                column(COL_LEVELUP_LABEL, levelUp.label)
                columnRSCM(COL_LEVELUP_LAYER, levelUp.layer)
                column(COL_LEVELUP_JINGLE, levelUp.jingle)
                levelUp.alternateJingle?.let { column(COL_LEVELUP_JINGLE_ALT, it) }
                column(COL_LEVELUP_RULE, levelUp.rule.id)
                levelUp.threshold?.let { column(COL_LEVELUP_THRESHOLD, it) }
                levelUp.prefix?.let { column(COL_LEVELUP_PREFIX, it) }
            }
        }
    }
}
