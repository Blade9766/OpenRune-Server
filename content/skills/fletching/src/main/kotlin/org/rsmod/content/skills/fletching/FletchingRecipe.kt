package org.rsmod.content.skills.fletching

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType

internal const val FEATHER = "obj.feather"

internal val FEATHERS =
    listOf(
        FEATHER,
        "obj.hunting_desert_feather",
        "obj.hunting_woodland_feather",
        "obj.hunting_jungle_feather",
        "obj.hunting_polar_feather",
        "obj.hunting_stripy_bird_feather",
        "obj.gryphon_feather",
        "obj.stymphike_feather",
    )

internal enum class FletchingTool(val objs: List<String>, val missing: String) {
    KNIFE(listOf("obj.knife", "obj.fletching_knife"), "You need a knife to do that."),
    CHISEL(listOf("obj.chisel"), "You need a chisel to do that."),
    HAMMER(
        listOf("obj.hammer", "obj.imcando_hammer", "obj.imcando_hammer_offhand"),
        "You need a hammer to do that.",
    ),
}

internal val KNIFE = FletchingTool.KNIFE
internal val CHISEL = FletchingTool.CHISEL
internal val HAMMER = FletchingTool.HAMMER

internal data class FletchingInput(val obj: String, val count: Int)

internal fun input(obj: String, count: Int = 1): FletchingInput = FletchingInput(obj, count)

/**
 * One fletching action. [inputs] are consumed and [outputCount] objs of [output] are produced per
 * action, except for [batch] recipes: those list their inputs per single output and make up to
 * [outputCount] at a time, so a short stack still finishes a partial set.
 *
 * [ticks] is the time per action; 0 makes the recipe instant, one action per click. [message]
 * interpolates `{count}` (objs made), `{an}` (the product with its article or count) and `{plural}`.
 *
 * [unlockVarbit] hides the recipe until that varbit is set, with [lockedMessage] explaining why,
 * and [completionVarbit] is set the first time the player finishes it, for content that has to
 * tell the player's own work from someone else's.
 */
internal data class FletchingRecipe(
    val output: String,
    val outputCount: Int = 1,
    val inputs: List<FletchingInput>,
    val batch: Boolean = false,
    val level: Int,
    val xp: Double,
    val ticks: Int,
    val tool: FletchingTool? = null,
    val anim: String? = null,
    val sound: String? = null,
    val message: String,
    val unlockVarbit: String? = null,
    val lockedMessage: String = BROAD_AMMO_LOCKED,
    val completionVarbit: String? = null,
) {
    val primary: String
        get() = inputs.first().obj

    val displayName: String by lazy {
        val name = ServerCacheManager.getItem(output.asRSCM(RSCMType.OBJ))?.name ?: output
        name.removeSuffix(" (u)").removeSuffix("(unf)").trim().lowercase()
    }

    internal companion object {
        const val BROAD_AMMO_LOCKED =
            "You need to learn how to fletch broad ammunition from a Slayer master first."
    }
}
