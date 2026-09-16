package org.rsmod.content.skills.farming.data

/**
 * One row of the tool leprechaun's store.
 *
 * [capacity] is how many of the item a leprechaun will hold. The numbers come from the client's own
 * `enum_2193`, which the interface reads to decide whether a slot shows the "-1/-5/-X/-All" options
 * or a single "Store"/"Remove", so the two have to agree or the menu offers an option the server
 * will refuse.
 *
 * [varbits] is the store's counter, least significant piece first. Old School grew these slots over
 * the years by bolting a second and third varbit onto the original one rather than widening it, so
 * a rake count is one bit in `farming_tools_rake` with the rest in `farming_tools_extrarakes`, and
 * an empty bucket count is spread over three. Writing them in this order keeps a save made before
 * the widening readable, exactly as the client's `farming_tools_getstored` reads it.
 */
enum class ToolSlot(
    val obj: String,
    val capacity: Int,
    private val component: String,
    val varbits: List<StoreVarbit>,
) {
    RAKE("obj.rake", 100, "rake", split("rake", 1, "extrarakes", 6)),
    DIBBER("obj.dibber", 100, "dibber", split("dibber", 1, "extradibbers", 6)),
    SPADE("obj.spade", 100, "spade", split("spade", 1, "extraspades", 6)),
    SECATEURS("obj.secateurs", 100, "secateurs", split("secateurs", 1, "extrasecateurs", 6)),
    WATERING_CAN("obj.watering_can_0", 1, "wateringcan", single("wateringcan", 4)),
    TROWEL("obj.gardening_trowel", 100, "trowel", split("trowel", 1, "extratrowels", 6)),
    PLANT_CURE("obj.plant_cure", 1000, "plantcure", single("plantcure", 10)),
    BOTTOMLESS_BUCKET(
        "obj.bottomless_compost_bucket",
        1,
        "bottomless_bucket",
        single("bottomless_bucket_type", 3),
    ),
    BUCKET(
        "obj.bucket_empty",
        1000,
        "bucket",
        listOf(
            StoreVarbit("varbit.farming_tools_buckets", 5),
            StoreVarbit("varbit.farming_tools_extrabuckets", 3),
            StoreVarbit("varbit.farming_tools_extra2buckets", 2),
        ),
    ),
    COMPOST("obj.bucket_compost", 1000, "compost", split("compost", 8, "extracompost", 2)),
    SUPERCOMPOST(
        "obj.bucket_supercompost",
        1000,
        "supercompost",
        split("supercompost", 8, "extrasupercompost", 2),
    ),
    ULTRACOMPOST("obj.bucket_ultracompost", 1000, "ultracompost", single("ultracompost", 10));

    val mainComponent: String = "component.farming_tools:$component"
    val sideComponent: String = "component.farming_tools_side:$component"

    /** Slots holding a single, variable item rather than a pile of identical ones. */
    val singleItem: Boolean = capacity == 1

    /** The largest count [varbits] can hold between them. */
    val maxStorable: Int = (1 shl varbits.sumOf(StoreVarbit::width)) - 1

    /** The value each of [varbits] takes to hold [value], least significant piece first. */
    fun split(value: Int): IntArray {
        var remaining = value.coerceIn(0, maxStorable)
        return IntArray(varbits.size) { index ->
            val width = varbits[index].width
            val part = remaining and ((1 shl width) - 1)
            remaining = remaining shr width
            part
        }
    }

    /** The count [parts] encode, least significant piece first. */
    fun join(parts: IntArray): Int {
        var total = 0
        var shift = 0
        for (index in varbits.indices) {
            total = total or ((parts.getOrElse(index) { 0 }) shl shift)
            shift += varbits[index].width
        }
        return total
    }
}

private fun single(name: String, width: Int) =
    listOf(StoreVarbit("varbit.farming_tools_$name", width))

private fun split(base: String, baseWidth: Int, extra: String, extraWidth: Int) =
    listOf(
        StoreVarbit("varbit.farming_tools_$base", baseWidth),
        StoreVarbit("varbit.farming_tools_$extra", extraWidth),
    )

/** One varbit holding [width] bits of a store counter. */
class StoreVarbit(val varbit: String, val width: Int)
