package org.rsmod.content.skills.construction.data

import dev.openrune.ServerCacheManager
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory

/**
 * The costume room's storage. Everything stored lives in one persisted inventory,
 * `inv.poh_costumes`; each piece of furniture only decides which of it it shows and accepts.
 *
 * What a piece holds comes from the cache enums the `poh_costumes` interface draws from: each
 * store's enum lists sets by a representative obj, enum 3077 maps a set to the enum of its members
 * (a set without one is just that obj), and enums 3304 and 3303 add each member's alternate
 * versions. The limits on how many sets a tier holds come from the Old School wiki.
 */
object Costumes {
    /**
     * A storage piece. [enums] are the interface pages it can show; [pages] is how many of them each
     * build option opens, and [limits] how many sets each option holds, `null` for no limit.
     */
    enum class Store(
        val group: String,
        private val locKey: String,
        private val woods: List<String>,
        val enums: List<Int>,
        val pages: List<Int>,
        val limits: List<Int?>,
        private val opens: Boolean = true,
    ) {
        MAGIC_WARDROBE(
            "magic_wardrobe",
            "magic_wardrobe",
            listOf("oak", "carved_oak", "teak", "carved_teak", "mahogany", "mahogany_gilded", "marble"),
            listOf(3289),
            List(7) { 1 },
            listOf(7, 14, 21, 28, 35, 42, null),
        ),
        ARMOUR_CASE("armour_case", "armour_case", WOODS, listOf(3290), List(3) { 1 }, listOf(25, 50, null)),
        FANCY_DRESS_BOX("fancy_dress_box", "fancy_dress_box", WOODS, listOf(3291), List(3) { 1 }, listOf(2, 4, null)),
        CAPE_RACK(
            "cape_rack",
            "cape_rack",
            listOf("oak", "teak", "mahogany", "mahogany_gilded", "marble", "magic_stone"),
            listOf(3292),
            List(6) { 1 },
            List(6) { null },
            opens = false,
        ),
        TOY_BOX("toy_box", "toy_box", WOODS, listOf(3299), List(3) { 1 }, List(3) { null }),
        TREASURE_CHEST("treasure_chest", "tresure_chest", WOODS, (3293..3298).toList(), listOf(2, 3, 6), List(3) { null });

        /** Each build option's loc, closed. */
        val closed: List<String>
            get() = woods.map { "loc.poh_cos_room_${locKey}_$it" }

        /** Each build option's open loc; the cape rack has none and is searched as it stands. */
        val opened: List<String>
            get() = if (opens) woods.map { "loc.poh_cos_room_${locKey}_open_$it" } else emptyList()

        companion object {
            fun ofGroup(key: String): Store? = entries.firstOrNull { it.group == key }
        }
    }

    private val WOODS = listOf("oak", "teak", "mahogany")

    /** A set as one interface page lists it, and every obj that counts as part of it. */
    class CostumeSet(val store: Store, val page: Int, val objs: Set<Int>)

    private const val SET_MEMBERS = 3077
    private const val ALTERNATE_LISTS = 3304
    private const val ALTERNATE = 3303

    const val INV: String = "inv.poh_costumes"

    val Player.costumeStorage: Inventory
        get() = invMap.getOrPut(INV)

    private val sets: List<CostumeSet> by lazy {
        val members = intEnum(SET_MEMBERS)
        val alternateLists = intEnum(ALTERNATE_LISTS)
        val alternates = intEnum(ALTERNATE)
        Store.entries.flatMap { store ->
            store.enums.withIndex().flatMap { (page, enumId) ->
                intEnum(enumId).values.map { set ->
                    val parts = members[set]?.let { intEnum(it).values.toList() } ?: listOf(set)
                    val objs = HashSet<Int>()
                    for (part in parts) {
                        objs += part
                        alternateLists[part]?.let { objs += intEnum(it).values }
                        alternates[part]?.let { objs += it }
                    }
                    CostumeSet(store, page, objs)
                }
            }
        }
    }

    private fun intEnum(id: Int): Map<Int, Int> =
        ServerCacheManager.getEnum(id)?.values?.entries?.associate { (key, value) ->
            (key as Number).toInt() to (value as Number).toInt()
        } ?: emptyMap()

    /** The sets of [store] whose pages the build option [option] opens. */
    fun setsOf(store: Store, option: Int): List<CostumeSet> {
        val pages = store.pages.getOrElse(option) { 1 }
        return sets.filter { it.store == store && it.page < pages }
    }

    /** The set of [store] (within [option]'s pages) that [obj] belongs to, if any. */
    fun setFor(store: Store, option: Int, obj: Int): CostumeSet? = setsOf(store, option).firstOrNull { obj in it.objs }

    /** How many of [store]'s sets have at least one piece in [storage]. */
    fun setsStored(store: Store, storage: Inventory): Int =
        sets.count { it.store == store && it.objs.any { obj -> storage.count(obj) > 0 } }

    private fun Inventory.count(obj: Int): Int = objs.sumOf { if (it?.id == obj) it.count else 0 }
}
