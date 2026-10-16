package org.rsmod.content.other.bots.meister.world

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.content.other.bots.meister.MeisterAllocation
import org.rsmod.content.other.bots.meister.MeisterCatalog
import org.rsmod.content.other.bots.meister.MeisterItem
import org.rsmod.content.other.grandexchange.GeItemData

/**
 * Every item the Grand Exchange can trade (the exchange's item list with a guide price) that the
 * cache also calls tradeable: no coins, notes, placeholders, dummy or transformation items, and
 * nothing the config excludes.
 */
object MeisterCatalogs {
    private val logger = InlineLogger()

    fun build(items: GeItemData, config: MeisterWorldConfig): MeisterCatalog {
        val excluded = ids(config.exclude) + ids(listOf(COINS))
        val sensitive = ids(config.sensitive)
        val noBuy = ids(config.noBuy)
        val noSell = ids(config.noSell)
        val overrides = config.allocationOverrides.mapNotNull { (name, count) -> id(name)?.let { it to count } }.toMap()
        var rejected = 0
        val catalog =
            items.all().mapNotNull { entry ->
                val type = ServerCacheManager.getItem(entry.id)
                val usable =
                    type != null && entry.guidePrice > 0 && entry.id !in excluded && type.stockmarket && type.tradeable &&
                        !type.isCert && !type.isPlaceholder && !type.isDummyItem && !type.isTransformation &&
                        type.name.isNotBlank() && !type.name.equals("null", ignoreCase = true)
                if (!usable) {
                    rejected++
                    return@mapNotNull null
                }
                MeisterItem(
                    id = entry.id,
                    name = type!!.name,
                    guidePrice = entry.guidePrice,
                    allocation = overrides[entry.id] ?: MeisterAllocation.countFor(entry.guidePrice, config.allocation),
                    sensitive = entry.id in sensitive || entry.guidePrice >= config.sensitivePrice,
                    noBuy = entry.id in noBuy,
                    noSell = entry.id in noSell,
                )
            }
        logger.info { "Master Meister deals in ${catalog.size} items (${rejected} exchange entries skipped)." }
        return MeisterCatalog(catalog)
    }

    private fun ids(names: List<String>): Set<Int> = names.mapNotNull(::id).toHashSet()

    private fun id(name: String): Int? {
        val id = runCatching { name.asRSCM(RSCMType.OBJ) }.getOrNull()
        if (id == null) {
            logger.warn { "Master Meister config names an unknown item: $name" }
        }
        return id
    }

    private const val COINS = "obj.coins"
}
