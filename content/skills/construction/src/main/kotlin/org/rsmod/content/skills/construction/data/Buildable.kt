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
 * variants - middle, side and corner - so one option has to name a built loc for each of them.
 */
class Buildable(
    val label: String,
    val level: Int,
    val xp: Double,
    val materials: List<Material>,
    val built: List<String>,
    val sound: BuildSound = BuildSound.WOOD,
) {
    constructor(
        label: String,
        level: Int,
        xp: Double,
        materials: List<Material>,
        built: String,
        sound: BuildSound = BuildSound.WOOD,
    ) : this(label, level, xp, materials, listOf(built), sound)
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
