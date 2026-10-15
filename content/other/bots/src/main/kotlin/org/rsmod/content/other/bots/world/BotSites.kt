package org.rsmod.content.other.bots.world

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.toml.TomlFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.michaelbull.logging.InlineLogger
import java.nio.file.Files
import java.nio.file.Path
import org.rsmod.content.other.bots.core.BotRoleType
import org.rsmod.map.CoordGrid

@JsonIgnoreProperties(ignoreUnknown = true)
data class SiteTile(val x: Int = 0, val z: Int = 0, val level: Int = 0) {
    fun toCoords(): CoordGrid = CoordGrid(x, z, level)
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SiteArea(val x: Int = 0, val z: Int = 0, val level: Int = 0, val radius: Int = 10) {
    fun center(): CoordGrid = CoordGrid(x, z, level)

    fun contains(coords: CoordGrid): Boolean =
        coords.level == level && coords.chebyshevDistance(center()) <= radius
}

/**
 * Where a bot works. Routes are waypoint lists that start next to the bank; the pathfinder handles
 * the steps between waypoints, so consecutive waypoints should be at most ~40 tiles apart and the
 * route must not cross closed doors or gates.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class BotSite(
    val key: String = "",
    val role: String = "",
    val description: String = "",
    val resource: SiteArea = SiteArea(),
    val bank: SiteArea = SiteArea(radius = 4),
    val exchange: SiteArea = SiteArea(radius = 6),
    /** Products the bot may gather here (gameval obj names). */
    val products: List<String> = emptyList(),
    @JsonProperty("resource_route") val resourceRoute: List<SiteTile> = emptyList(),
    @JsonProperty("exchange_route") val exchangeRoute: List<SiteTile> = emptyList(),
    /** Runecrafting: the altar loc inside the ruins; [resource] surrounds the ruins outside. */
    @JsonProperty("altar_loc") val altarLoc: String = "",
    /** Runecrafting with mined essence: where the essence teleport NPC stands. */
    @JsonProperty("supply_npc") val supplyNpc: String = "",
    @JsonProperty("supply_area") val supplyArea: SiteArea? = null,
    @JsonProperty("supply_route") val supplyRoute: List<SiteTile> = emptyList(),
    /** Npc or loc gamevals (`npc.`, `loc.` or `content.`) the bot clicks to gather here. */
    val targets: List<String> = emptyList(),
    /** Which op (1-5) of a target the bot clicks. */
    @JsonProperty("target_op") val targetOp: Int = 1,
    /** The bot needs one of these in its inventory or equipment; they are never banked. */
    val tools: List<String> = emptyList(),
    /** Withdrawn from the bank before each trip (bait, food), best first. */
    val supplies: List<String> = emptyList(),
    /** How many supplies to carry; 0 fills the free inventory. */
    @JsonProperty("supply_amount") val supplyAmount: Int = 0,
) {
    val roleType: BotRoleType?
        get() = BotRoleType.of(role)
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class BotSiteFile(@JsonProperty("site") val sites: List<BotSite> = emptyList())

object BotSites {
    private val logger = InlineLogger()
    private const val RESOURCE = "bot-sites.toml"
    private val OVERRIDE: Path = Path.of(".data", "bots", "bot-sites.toml")

    private val mapper = ObjectMapper(TomlFactory()).registerKotlinModule()

    @Volatile private var cached: Map<String, BotSite>? = null

    fun all(): Map<String, BotSite> = cached ?: reload()

    operator fun get(key: String): BotSite? = all()[key.lowercase()]

    /** Bundled sites, replaced key-by-key by `.data/bots/bot-sites.toml` when it exists. */
    fun reload(): Map<String, BotSite> {
        val sites = LinkedHashMap<String, BotSite>()
        BotSites::class.java.classLoader.getResourceAsStream(RESOURCE)?.use { stream ->
            mapper.readValue(stream, BotSiteFile::class.java).sites.forEach { sites[it.key.lowercase()] = it }
        }
        if (Files.exists(OVERRIDE)) {
            try {
                Files.newInputStream(OVERRIDE).use { stream ->
                    mapper.readValue(stream, BotSiteFile::class.java).sites.forEach {
                        sites[it.key.lowercase()] = it
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Could not read $OVERRIDE; using bundled bot sites." }
            }
        }
        cached = sites
        return sites
    }
}
