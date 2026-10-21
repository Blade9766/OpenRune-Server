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
data class SiteTile(
    val x: Int = 0,
    val z: Int = 0,
    val level: Int = 0,
    /** A ladder or trapdoor (display name) the bot uses here to reach the next waypoint. */
    val climb: String = "",
    /** The [climb] loc's or [talk] npc's option, such as `Climb-down` or `Talk-to`. */
    val op: String = "",
    /** An npc (gameval or `name:`) the bot talks to here to travel to the next waypoint, like a ferryman. */
    val talk: String = "",
    /** What the bot answers on each of the [talk] npc's chat menus, first offered wins. */
    val options: List<String> = emptyList(),
) {
    val travels: Boolean
        get() = climb.isNotBlank() || talk.isNotBlank()

    fun toCoords(): CoordGrid = CoordGrid(x, z, level)
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SiteArea(val x: Int = 0, val z: Int = 0, val level: Int = 0, val radius: Int = 10) {
    fun center(): CoordGrid = CoordGrid(x, z, level)

    fun contains(coords: CoordGrid): Boolean =
        coords.level == level && coords.chebyshevDistance(center()) <= radius
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class KitItem(val item: String = "", val count: Int = 1)

/** One slayer task a slayer site can work: where its monsters are and what to loot there. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SlayerHunt(
    /** The task's lowercase name, as the slayer task table spells it ("goblins"). */
    val task: String = "",
    val resource: SiteArea = SiteArea(),
    @JsonProperty("resource_route") val resourceRoute: List<SiteTile> = emptyList(),
    val products: List<String> = emptyList(),
)

/** Another site whose hunts a slayer site also works, reached from this site's bank by [route]. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class HuntSource(val site: String = "", val route: List<SiteTile> = emptyList())

/** What a new bot at a site starts with; applying it tops up to these counts and levels. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class StarterKit(
    val inv: List<KitItem> = emptyList(),
    val worn: List<KitItem> = emptyList(),
    val bank: List<KitItem> = emptyList(),
    /** Minimum levels keyed by stat name without the `stat.` prefix. */
    val levels: Map<String, Int> = emptyMap(),
)

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
    /** Arceuus runecrafting: from [supplyArea] past the Dark Altar to [resource], walked while carrying blocks. */
    @JsonProperty("craft_route") val craftRoute: List<SiteTile> = emptyList(),
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
    /** Products carried that send the bot to bank; 0 waits for a full inventory (stacking loot never fills it). */
    @JsonProperty("trip_load") val tripLoad: Int = 0,
    val kit: StarterKit? = null,
    /**
     * Wayfinder destination key a stray bot, or one coming back from an [exchangeByWayfinder] trip,
     * asks for; blank picks the bot-allowed one nearest the bank.
     */
    val wayfinder: String = "",
    /** Slayer: the tasks this site can work; [supplyNpc] is the slayer master. */
    val hunts: List<SlayerHunt> = emptyList(),
    /** Slayer: hunts borrowed from another site; merged into [hunts] when the sites load. */
    @JsonProperty("hunts_from") val huntsFrom: HuntSource? = null,
    /**
     * Slayer: a master who swaps another master's task for one of their own for free (Turael),
     * visited when a task has no hunt and the bot cannot pay [supplyNpc] to cancel it.
     */
    @JsonProperty("reroll_npc") val rerollNpc: String = "",
    @JsonProperty("reroll_area") val rerollArea: SiteArea? = null,
    @JsonProperty("reroll_route") val rerollRoute: List<SiteTile> = emptyList(),
    /**
     * Sites too far to walk to the exchange travel like a player: a wayfinder mage (after a Home
     * Teleport to Lumbridge when none is in reach) to the exchange, and Vaelor back to [wayfinder].
     */
    @JsonProperty("exchange_by_wayfinder") val exchangeByWayfinder: Boolean = false,
    /** From the bank to where the [wayfinder] destination lands on the way back. */
    @JsonProperty("return_route") val returnRoute: List<SiteTile> = emptyList(),
) {
    val roleType: BotRoleType?
        get() = BotRoleType.of(role)

    /** Everything a bot may gather here, slayer hunt loot included. */
    val allProducts: Set<String>
        get() = (products + hunts.flatMap { it.products }).toSet()
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
        sites.replaceAll { _, site -> site.withBorrowedHunts(sites) }
        cached = sites
        return sites
    }

    private fun BotSite.withBorrowedHunts(sites: Map<String, BotSite>): BotSite {
        val source = huntsFrom ?: return this
        val other = sites[source.site.lowercase()] ?: return this
        val borrowed =
            other.hunts
                .filter { hunt -> hunts.none { it.task.equals(hunt.task, ignoreCase = true) } }
                .map { it.copy(resourceRoute = source.route + it.resourceRoute) }
        return copy(hunts = hunts + borrowed)
    }
}
