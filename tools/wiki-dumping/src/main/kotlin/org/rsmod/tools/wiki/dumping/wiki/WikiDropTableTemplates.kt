package org.rsmod.tools.wiki.dumping.wiki

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

/**
 * Expands the shared tertiary templates `{{CatacombsDropTable}}` and `{{WildernessSlayerDropTable}}`
 * into drops, using the rate formulas from the templates' own wikitext.
 */
object WikiDropTableTemplates {
    const val CATACOMBS_AREA = "area.catacombs_of_kourend"
    const val GIANTS_DEN_AREA = "area.giantsden"

    fun expand(body: String, subsection: String): List<ParsedWikiDrop> =
        WikiTemplateParser.extractTemplates(body, "CatacombsDropTable").flatMap { content ->
            catacombs(WikiTemplateParser.parseParams(content), subsection)
        } +
            WikiTemplateParser.extractTemplates(body, "WildernessSlayerDropTable").flatMap { content ->
                wildernessSlayer(WikiTemplateParser.parseParams(content), subsection)
            }

    fun catacombs(params: Map<String, String>, subsection: String): List<ParsedWikiDrop> {
        val hitpoints = params.int("hitpoints") ?: return emptyList()
        val superior = params.flag("superior")
        val area =
            if (params.flag("giantsden")) "$CATACOMBS_AREA,$GIANTS_DEN_AREA" else CATACOMBS_AREA
        val shardDenominator = floor(2.0 / 3.0 * (500 - min(hitpoints, 400))).toInt()
        val totemRarity = if (superior) "Always" else "1/${500 - hitpoints}"
        val quantity = params["quantity"]?.trim()?.ifBlank { null } ?: "1"
        return listOf(
            tertiary("Ancient shard", "1/$shardDenominator", subsection, quantity).copy(area = area),
            tertiary("Dark totem base", totemRarity, subsection).copy(area = area, darkTotemPiece = true),
        )
    }

    fun wildernessSlayer(params: Map<String, String>, subsection: String): List<ParsedWikiDrop> {
        val combat = params.int("combat") ?: return emptyList()
        val hitpoints = params.int("hitpoints") ?: return emptyList()
        val keyRarity =
            if (params.flag("superior")) {
                "Always"
            } else {
                "1/${boostedKeyDenominator(combat, params.flag("slayerbonus"), params.flag("revenant"))}"
            }
        val enchantmentRarity =
            if (params.flag("boss")) {
                "1/30"
            } else {
                "1/${320 - floor(min(hitpoints, 300) * 0.8).toInt()}"
            }
        return listOf(
            tertiary("Larran's key", keyRarity, subsection).copy(wildernessSlayerTask = true),
            tertiary("Slayer's enchantment", enchantmentRarity, subsection).copy(wildernessSlayerTask = true),
        )
    }

    fun keyDenominator(combat: Int): Int =
        when {
            combat < 81 -> 100 + floor(0.3 * (80 - combat).toDouble().pow(2)).toInt()
            combat < 351 -> floor(99 - (combat - 81) * (49.0 / (350 - 81))).toInt()
            else -> 50
        }

    fun boostedKeyDenominator(combat: Int, slayerBonus: Boolean, revenant: Boolean): Int {
        val base = keyDenominator(combat)
        return when {
            slayerBonus && revenant -> floor(floor(base / 1.2) / 1.15).toInt()
            slayerBonus -> floor(base / 1.2).toInt()
            revenant -> floor(base / 1.15).toInt()
            else -> base
        }
    }

    private fun tertiary(name: String, rarity: String, subsection: String, quantity: String = "1") =
        ParsedWikiDrop(
            name = name,
            quantity = quantity,
            rarity = rarity,
            section = WikiDropSection.Tertiary,
            subsection = subsection,
        )

    private fun Map<String, String>.int(key: String): Int? = this[key]?.trim()?.toIntOrNull()

    private fun Map<String, String>.flag(key: String): Boolean = !this[key]?.trim().isNullOrEmpty()
}
