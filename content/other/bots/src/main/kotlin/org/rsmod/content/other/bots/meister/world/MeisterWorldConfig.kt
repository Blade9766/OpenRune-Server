package org.rsmod.content.other.bots.meister.world

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.toml.TomlFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.michaelbull.logging.InlineLogger
import java.nio.file.Files
import java.nio.file.Path
import org.rsmod.content.other.bots.meister.AllocationTier
import org.rsmod.content.other.bots.meister.MeisterAllocation
import org.rsmod.content.other.bots.meister.MeisterTrader
import org.rsmod.content.other.bots.world.SiteArea
import org.rsmod.content.other.bots.world.SiteTile

/**
 * Where Master Meister lives and what he starts with: `master-meister.toml` in the bots module,
 * replaced as a whole by `.data/bots/master-meister.toml` when that exists. Trading settings live
 * in his save instead, so they can be changed while the server runs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MeisterWorldConfig(
    val enabled: Boolean = true,
    val spawn: SiteTile = SiteTile(3164, 3480),
    /** He never leaves this area; one step outside and he is brought back to [spawn]. */
    val area: SiteArea = SiteArea(3164, 3489, radius = 14),
    /** How far a single idle stroll may take him. */
    @JsonProperty("stroll_radius") val strollRadius: Int = 5,
    /** Where he stands to place an offer, and the Exchange booth he faces there. */
    val booths: List<MeisterBooth> = emptyList(),
    @JsonProperty("booth_anim") val boothAnim: String = "seq.human_pickuptable",
    @JsonProperty("idle_anims") val idleAnims: List<String> = emptyList(),
    @JsonProperty("idle_lines") val idleLines: List<String> = emptyList(),
    @JsonProperty("buy_lines") val buyLines: List<String> = emptyList(),
    @JsonProperty("sell_lines") val sellLines: List<String> = emptyList(),
    /** Ticks between idle actions, before the random part. */
    @JsonProperty("idle_interval") val idleIntervalTicks: Int = 25,
    @JsonProperty("starting_cash") val startingCash: Long = MeisterTrader.MAX_CASH,
    val allocation: List<AllocationTier> = MeisterAllocation.DEFAULT_TIERS,
    /** Items worth at least this much are rare or economy-sensitive. */
    @JsonProperty("sensitive_price") val sensitivePrice: Long = 50_000_000,
    /** Extra items treated as sensitive whatever their price. */
    val sensitive: List<String> = emptyList(),
    /** Items he never holds or trades, on top of everything untradeable. */
    val exclude: List<String> = emptyList(),
    @JsonProperty("no_buy") val noBuy: List<String> = emptyList(),
    @JsonProperty("no_sell") val noSell: List<String> = emptyList(),
    /** Starting counts for single items, overriding [allocation]. */
    @JsonProperty("allocation_overrides") val allocationOverrides: Map<String, Int> = emptyMap(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MeisterBooth(val stand: SiteTile = SiteTile(), val face: SiteTile = SiteTile())

object MeisterWorldConfigs {
    private val logger = InlineLogger()
    private const val RESOURCE = "master-meister.toml"
    private val OVERRIDE: Path = Path.of(".data", "bots", "master-meister.toml")
    private val mapper = ObjectMapper(TomlFactory()).registerKotlinModule()

    fun load(): MeisterWorldConfig {
        if (Files.exists(OVERRIDE)) {
            try {
                return Files.newInputStream(OVERRIDE).use { mapper.readValue(it, MeisterWorldConfig::class.java) }
            } catch (e: Exception) {
                logger.error(e) { "Could not read $OVERRIDE; using the bundled Master Meister config." }
            }
        }
        val stream = MeisterWorldConfigs::class.java.classLoader.getResourceAsStream(RESOURCE) ?: return MeisterWorldConfig()
        return stream.use { mapper.readValue(it, MeisterWorldConfig::class.java) }
    }
}
