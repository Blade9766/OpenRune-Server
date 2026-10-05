package org.rsmod.content.skills.construction.data

/** One item in a build's bill of materials. */
class Material(val obj: String, val count: Int)

enum class BuildSound(val synth: String) {
    WOOD("synth.poh_build_wood"),
    STONE("synth.poh_build_stone"),
    METAL("synth.poh_build_metal"),
}

/**
 * One option on a hotspot.
 *
 * [built] is parallel to its [HotspotGroup.locs]: a rug fills its whole floor from three hotspot
 * variants - middle, side and corner - so one option has to name a built loc for each of them. A
 * slot that stays empty names its own hotspot loc.
 *
 * An [upgrade] is never offered in the build menu: it is built over the option before it in its
 * group with the Upgrade op, and its [materials] are only what that upgrade adds. A piece with
 * [upgradeMaterials] is both: offered in the menu for [materials], or built over the one before it
 * for [upgradeMaterials]. An upgrade with [upgradeFrom] is built over that option instead of the one
 * before it, so one piece can be reached from several. A [quest] has to be complete before the
 * piece can be built.
 *
 * [skill] is a second stat the piece needs ([skillLevel]) and trains ([skillXp]); [refund] is what
 * removing it gives back. A piece that is planted rather than built needs a [wateringCan] with water
 * in it.
 */
class Buildable(
    val label: String,
    val level: Int,
    val xp: Double,
    val materials: List<Material>,
    val built: List<String>,
    val sound: BuildSound = BuildSound.WOOD,
    val upgrade: Boolean = false,
    val quest: String? = null,
    val upgradeMaterials: List<Material>? = null,
    val skill: String? = null,
    val skillLevel: Int = 0,
    val skillXp: Double = 0.0,
    val refund: List<Material> = emptyList(),
    val upgradeFrom: Int? = null,
    val wateringCan: Boolean = false,
) {
    constructor(
        label: String,
        level: Int,
        xp: Double,
        materials: List<Material>,
        built: String,
        sound: BuildSound = BuildSound.WOOD,
        upgrade: Boolean = false,
        quest: String? = null,
        upgradeMaterials: List<Material>? = null,
        skill: String? = null,
        skillLevel: Int = 0,
        skillXp: Double = 0.0,
        refund: List<Material> = emptyList(),
        upgradeFrom: Int? = null,
        wateringCan: Boolean = false,
    ) : this(
        label,
        level,
        xp,
        materials,
        listOf(built),
        sound,
        upgrade,
        quest,
        upgradeMaterials,
        skill,
        skillLevel,
        skillXp,
        refund,
        upgradeFrom,
        wateringCan,
    )

    /** True when this piece can be built over the option before it in its group. */
    val upgradable: Boolean
        get() = upgrade || upgradeMaterials != null
}

/**
 * A hotspot the player can build on. [locs] lists every hotspot loc that is filled together; all of
 * them are replaced when an option is built and all of them come back when it is removed.
 */
class HotspotGroup(val key: String, val label: String, val locs: List<String>, val options: List<Buildable>) {
    constructor(
        key: String,
        label: String,
        loc: String,
        options: List<Buildable>,
    ) : this(key, label, listOf(loc), options)

    init {
        for (option in options) {
            require(option.built.size == locs.size) {
                "Option '${option.label}' of hotspot '$key' must name one built loc per hotspot loc."
            }
        }
    }
}
