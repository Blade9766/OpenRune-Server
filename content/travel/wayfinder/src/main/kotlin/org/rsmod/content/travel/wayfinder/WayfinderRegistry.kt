package org.rsmod.content.travel.wayfinder

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.table.WayfinderDestinationsRow

/**
 * Every destination on the wayfinder network, read once from `dbtable.wayfinder_destinations`.
 * The mages, the teleport interface and bots all look destinations up here, so adding a row to
 * the table is all a new destination needs.
 */
@Singleton
class WayfinderRegistry(private val source: () -> List<WayfinderDestination>) {
    @Inject constructor() : this(::fromCache)

    private val loaded: List<WayfinderDestination> by lazy { validated(source()) }
    private val byKey: Map<String, WayfinderDestination> by lazy { loaded.associateBy { it.key } }
    private val bySlot: Map<Int, WayfinderDestination> by lazy { loaded.associateBy { it.slot } }

    /** Enabled destinations, in table order. */
    val all: List<WayfinderDestination>
        get() = loaded.filter { it.enabled }

    /** An enabled destination by its key; disabled and unknown keys both give `null`. */
    operator fun get(key: String): WayfinderDestination? = byKey[key.lowercase()]?.takeIf { it.enabled }

    fun bySlot(slot: Int): WayfinderDestination? = bySlot[slot]?.takeIf { it.enabled }

    fun inCategory(category: WayfinderCategory): List<WayfinderDestination> = all.filter { it.category == category }

    fun search(query: String): List<WayfinderDestination> {
        val needle = query.trim().lowercase()
        val matches = all.filter { it.matches(needle) }
        return matches.sortedWith(
            compareBy<WayfinderDestination> { !it.name.lowercase().startsWith(needle) }.thenBy { it.name },
        )
    }

    companion object {
        const val MAX_SLOT = 127

        private val KEY_FORMAT = Regex("[a-z0-9_]+")

        fun of(destinations: List<WayfinderDestination>): WayfinderRegistry = WayfinderRegistry { destinations }

        fun validated(destinations: List<WayfinderDestination>): List<WayfinderDestination> {
            for (destination in destinations) {
                require(KEY_FORMAT.matches(destination.key)) { "Bad wayfinder key '${destination.key}'." }
                require(destination.slot in 1..MAX_SLOT) {
                    "Wayfinder destination ${destination.key} has slot ${destination.slot}; slots run 1..$MAX_SLOT."
                }
            }
            val keys = destinations.groupBy { it.key }.filterValues { it.size > 1 }.keys
            require(keys.isEmpty()) { "Duplicate wayfinder keys: $keys" }
            val slots = destinations.groupBy { it.slot }.filterValues { it.size > 1 }.keys
            require(slots.isEmpty()) { "Duplicate wayfinder slots: $slots" }
            return destinations
        }

        private fun fromCache(): List<WayfinderDestination> =
            WayfinderDestinationsRow.all().map { row ->
                WayfinderDestination(
                    key = row.key,
                    slot = row.slot,
                    name = row.name,
                    category = checkNotNull(WayfinderCategory.of(row.category)) { "Bad category on ${row.key}" },
                    description = row.description,
                    coords = row.coords,
                    danger = WayfinderDanger.of(row.danger),
                    warning = row.warning?.takeIf { it.isNotBlank() },
                    quest = row.quest?.takeIf { it.isNotBlank() },
                    botAllowed = row.botAllowed,
                    keywords = row.keywords?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }.orEmpty(),
                    enabled = row.enabled,
                )
            }
    }
}
