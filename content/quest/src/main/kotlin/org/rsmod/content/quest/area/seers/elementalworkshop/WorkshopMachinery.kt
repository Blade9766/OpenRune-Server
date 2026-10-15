package org.rsmod.content.quest.area.seers.elementalworkshop

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.BOWL
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.BRONZE_PICKAXE
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.COAL
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.COAL_PER_BAR
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.CONTROL_CLOSED
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.CONTROL_OPEN
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.HAMMER
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.LAVA_BOWL
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.LEATHER
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.METAL
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.NEEDLE
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.ORE
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.REQUIRED_LEVEL
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.SHIELD
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.SLASHED_BOOK
import org.rsmod.content.quest.area.seers.elementalworkshop.ElementalWorkshopQuest.Companion.THREAD
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The workshop's machinery and stores: the water controls, wheel and lever in the northern room,
 * the bellows and their lever in the east, the lava trough and furnace in the south, the
 * workbench and the supply crates. Every machine's look follows its cache varbit, so the wheel
 * turns, the bellows pump and the furnace glows for each player according to their own progress.
 *
 * The water controls only turn one way. The east control has to open before the west one; opening
 * the west first jams the east shut, and the wheel lever then resets both. Every check runs before
 * anything is taken from the player, and tools are never consumed.
 */
class WorkshopMachinery
@Inject
constructor(private val ew: ElementalWorkshopQuest, private val objRepo: ObjRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(EAST_CONTROL) { turnEastControl() }
        onOpLoc1(WEST_CONTROL) { turnWestControl() }
        onOpLoc1(WATER_LEVER) { pullWaterLever() }

        onOpLoc1(BELLOWS) { fixBellows() }
        for (tool in listOf(NEEDLE, THREAD, LEATHER)) {
            onOpLocU(BELLOWS, tool) { fixBellows() }
        }
        onOpLoc1(BELLOWS_LEVER) { pullBellowsLever() }

        for (trough in TROUGHS) {
            onOpLocU(trough, BOWL) { fillBowl() }
            onOpLocU(trough, LAVA_BOWL) { mes("The bowl is already full of lava.") }
            onOpLocU(trough) { mes("That would just melt in the lava.") }
        }

        onOpLocU(FURNACE, LAVA_BOWL) { lightFurnace() }
        onOpLocU(FURNACE, BOWL) { mes("The bowl is empty. It needs filling with something hot.") }
        onOpLocU(FURNACE, ORE) { smelt() }
        onOpLocU(FURNACE, COAL) { smelt() }
        onOpLocU(FURNACE) { mes("Nothing interesting happens.") }

        for (bench in WORKBENCHES) {
            onOpLoc1(bench) { makeShield() }
            onOpLocU(bench, METAL) { makeShield() }
            onOpLocU(bench) { mes("Nothing interesting happens.") }
        }

        for ((crate, supply) in CRATES) {
            onOpLoc1(crate) { search(supply, it.type.name.lowercase()) }
        }
    }

    /* The water wheel */

    private suspend fun ProtectedAccess.turnEastControl() {
        if (waterAlreadyFlowing()) return
        val east = ew.eastControl(player)
        val west = ew.westControl(player)
        if (east == CONTROL_OPEN) {
            mes("The handle won't turn any further.")
            return
        }
        anim(TURN_SEQ)
        if (west == CONTROL_OPEN) {
            mes("The handle won't budge. Water from the western gate is pressing it shut.")
            return
        }
        delay(1)
        ew.setControls(player, east = CONTROL_OPEN, west = west)
        soundSynth(VALVE_SOUND)
        mes("You turn the handle.")
    }

    private suspend fun ProtectedAccess.turnWestControl() {
        if (waterAlreadyFlowing()) return
        val east = ew.eastControl(player)
        if (ew.westControl(player) == CONTROL_OPEN) {
            mes("The handle won't turn any further.")
            return
        }
        anim(TURN_SEQ)
        delay(1)
        ew.setControls(player, east = east, west = CONTROL_OPEN)
        soundSynth(VALVE_SOUND)
        mes("You turn the handle.")
        if (east == CONTROL_OPEN) {
            mes("You hear water rushing along the channel towards the wheel.")
        } else {
            mes("Water gurgles in the pipes, then falls silent.")
        }
    }

    private fun ProtectedAccess.waterAlreadyFlowing(): Boolean {
        if (!ew.isWheelRunning(player)) {
            return false
        }
        mes("Water is flowing freely to the wheel. It's best to leave the controls as they are.")
        return true
    }

    private suspend fun ProtectedAccess.pullWaterLever() {
        anim(LEVER_SEQ)
        soundSynth(LEVER_SOUND)
        delay(1)
        if (ew.isWheelRunning(player)) {
            mes("You pull the lever, but the water wheel is already turning.")
            return
        }
        val east = ew.eastControl(player)
        val west = ew.westControl(player)
        if (east == CONTROL_OPEN && west == CONTROL_OPEN) {
            ew.startWheel(player)
            soundSynth(WHEEL_SOUND)
            mes("You pull the lever. You hear the sound of a water wheel starting up.")
            return
        }
        if (east == CONTROL_CLOSED && west == CONTROL_CLOSED) {
            mes("You pull the lever, but nothing happens. No water is reaching the wheel.")
            return
        }
        ew.setControls(player, CONTROL_CLOSED, CONTROL_CLOSED)
        mes("You pull the lever. You hear the sound of the flow gates resetting.")
    }

    /* The bellows */

    private suspend fun ProtectedAccess.fixBellows() {
        if (ew.isBellowsFixed(player)) {
            mes("The bellows have already been repaired.")
            return
        }
        if (NEEDLE !in inv || THREAD !in inv || LEATHER !in inv) {
            mes("You need a piece of leather, some thread and a needle to fix this.")
            return
        }
        if (stat(CRAFTING) < REQUIRED_LEVEL) {
            mes("You need a Crafting level of $REQUIRED_LEVEL to repair the bellows.")
            return
        }
        anim(SEW_SEQ)
        delay(2)
        if (!invDel(inv, LEATHER, 1, THREAD, 1).success) {
            return
        }
        ew.fixBellows(player)
        mes("You stitch the leather over the hole in the bellows.")
    }

    private suspend fun ProtectedAccess.pullBellowsLever() {
        anim(LEVER_SEQ)
        soundSynth(LEVER_SOUND)
        delay(1)
        when {
            ew.isBellowsPumping(player) -> mes("You pull the lever, but the bellows are already pumping.")
            !ew.isBellowsFixed(player) ->
                mes(
                    "The bellows pump air out of the hole. Looks like they need fixing. You reset " +
                        "the lever.",
                )
            !ew.isWheelRunning(player) ->
                mes(
                    "You pull the lever, but the bellows don't move. Nothing is driving the " +
                        "workshop's machinery.",
                )
            else -> {
                ew.startBellows(player)
                soundSynth(BELLOWS_SOUND)
                mes("You pull the lever. The bellows start pumping air down into the furnace.")
            }
        }
    }

    /* The furnace */

    private suspend fun ProtectedAccess.fillBowl() {
        anim(FILL_SEQ)
        delay(1)
        if (!invDel(inv, BOWL).success) {
            return
        }
        invAdd(inv, LAVA_BOWL)
        mes("You fill the bowl with hot lava.")
    }

    private suspend fun ProtectedAccess.lightFurnace() {
        if (ew.isFurnaceLit(player)) {
            mes("The furnace is already burning.")
            return
        }
        anim(FURNACE_SEQ)
        delay(1)
        if (!invDel(inv, LAVA_BOWL).success) {
            return
        }
        invAdd(inv, BOWL)
        ew.lightFurnace(player)
        soundSynth(FURNACE_SOUND)
        mes("You empty the lava into the furnace. The furnace bursts to life.")
    }

    private suspend fun ProtectedAccess.smelt() {
        when {
            !ew.isFurnaceLit(player) -> {
                mes("The furnace is cold and dark. It will need lighting before it smelts anything.")
                return
            }
            !ew.isWheelRunning(player) || !ew.isBellowsPumping(player) -> {
                mes(
                    "The furnace isn't hot enough to smelt elemental ore. It needs air from the " +
                        "bellows.",
                )
                return
            }
            ORE !in inv -> {
                mes("You need some elemental ore to smelt.")
                return
            }
            invTotal(inv, COAL) < COAL_PER_BAR -> {
                mes("You need four heaps of coal to smelt elemental ore.")
                return
            }
            stat(SMITHING) < REQUIRED_LEVEL -> {
                mes("You need a Smithing level of $REQUIRED_LEVEL to smelt elemental ore.")
                return
            }
        }
        anim(FURNACE_SEQ)
        soundSynth(FURNACE_SOUND)
        delay(2)
        if (!invDel(inv, ORE, 1, COAL, COAL_PER_BAR).success) {
            return
        }
        invAdd(inv, METAL)
        ew.markMetalSmelted(player)
        mes("You place the elemental ore and four heaps of coal into the furnace. You retrieve a bar.")
    }

    /* The workbench */

    private suspend fun ProtectedAccess.makeShield() {
        when {
            SLASHED_BOOK !in inv -> {
                mes("This workbench is too complicated. You need instructions to follow.")
                return
            }
            HAMMER !in inv -> {
                mes("You need a hammer to work the metal with.")
                return
            }
            METAL !in inv -> {
                mes("You need some elemental metal to work into a shield.")
                return
            }
            stat(SMITHING) < REQUIRED_LEVEL -> {
                mes("You need a Smithing level of $REQUIRED_LEVEL to make an elemental shield.")
                return
            }
        }
        anim(SMITH_SEQ)
        soundSynth(ANVIL_SOUND)
        delay(3)
        if (!invDel(inv, METAL).success) {
            return
        }
        invAdd(inv, SHIELD)
        objbox(SHIELD, "Following the instructions in the book, you make an elemental shield.")
        if (!ew.isComplete(player)) {
            ew.complete(this)
        }
    }

    /* The stores */

    private enum class Supply { Leather, Thread, Needle, Bowl, Coal, Hammer, Pickaxe, Nothing }

    private suspend fun ProtectedAccess.search(supply: Supply, container: String) {
        anim(SEARCH_SEQ)
        mes("You search the $container...")
        delay(1)
        val found: Pair<String, Int>? =
            when (supply) {
                Supply.Leather -> (LEATHER to 1).takeIf { !ew.isBellowsFixed(player) && !owns(LEATHER) }
                Supply.Thread -> (THREAD to 1).takeIf { !ew.isBellowsFixed(player) && !owns(THREAD) }
                Supply.Needle -> (NEEDLE to 1).takeIf { !owns(NEEDLE) }
                Supply.Bowl -> (BOWL to 1).takeIf { !owns(BOWL) && !owns(LAVA_BOWL) }
                Supply.Hammer -> (HAMMER to 1).takeIf { !owns(HAMMER) }
                Supply.Coal -> {
                    val short = COAL_PER_BAR - invTotal(inv, COAL)
                    (COAL to short).takeIf { short > 0 && !ew.isComplete(player) }
                }
                Supply.Pickaxe -> (BRONZE_PICKAXE to 1).takeIf { !carriesPickaxe() && !ew.isComplete(player) }
                Supply.Nothing -> null
            }
        if (found == null) {
            mes("You find nothing of use.")
            return
        }
        val (obj, count) = found
        invAddOrDrop(objRepo, obj, count)
        objbox(obj, FOUND_TEXT.getValue(supply))
    }

    private fun ProtectedAccess.owns(obj: String): Boolean =
        inv.count(obj) > 0 || bank.count(obj) > 0 || worn.count(obj) > 0

    private fun ProtectedAccess.carriesPickaxe(): Boolean = ElementalRocks.findPickaxe(this) != null

    private companion object {
        const val EAST_CONTROL = "loc.elemental_workshop_valve_1"
        const val WEST_CONTROL = "loc.elemental_workshop_valve_2"
        const val WATER_LEVER = "loc.elemental_workshop_water_lever"
        const val BELLOWS = "loc.elemental_workshop_bellows_multiloc"
        const val BELLOWS_LEVER = "loc.elemental_workshop_air_lever"
        const val FURNACE = "loc.elemental_workshop_furnace"

        val TROUGHS = (1..5).map { "loc.elemental_workshop_trough_$it" }
        val WORKBENCHES = listOf("loc.elemental_workshop_workbench")

        val CRATES =
            mapOf(
                "loc.elemental_workshop_box_1" to Supply.Leather,
                "loc.elemental_workshop_box_2" to Supply.Needle,
                "loc.elemental_workshop_box_3" to Supply.Nothing,
                "loc.elemental_workshop_box_4" to Supply.Bowl,
                "loc.elemental_workshop_box_5" to Supply.Hammer,
                "loc.elemental_workshop_box_6" to Supply.Pickaxe,
                "loc.elemental_workshop_box_7" to Supply.Thread,
                "loc.elemental_workshop_box_8" to Supply.Coal,
            )

        val FOUND_TEXT =
            mapOf(
                Supply.Leather to "Tucked in the crate is a piece of leather, still supple.",
                Supply.Thread to "You find a reel of thread in the crate.",
                Supply.Needle to "Wedged between the slats you find a needle.",
                Supply.Bowl to "You find a heavy stone bowl among the boxes.",
                Supply.Hammer to "You find an old hammer at the bottom of the crate.",
                Supply.Coal to "The crate holds a few heaps of coal. You take what you need.",
                Supply.Pickaxe to "You find a battered old pickaxe in the crate.",
            )

        const val CRAFTING = "stat.crafting"
        const val SMITHING = "stat.smithing"

        const val TURN_SEQ = "seq.human_pickuptable"
        const val LEVER_SEQ = "seq.human_leverdown"
        const val SEW_SEQ = "seq.human_leather_crafting"
        const val FILL_SEQ = "seq.human_pickuptable"
        const val FURNACE_SEQ = "seq.human_furnace"
        const val SMITH_SEQ = "seq.human_smithing"
        const val SEARCH_SEQ = "seq.human_pickuptable"

        const val LEVER_SOUND = "synth.lever"
        const val VALVE_SOUND = "synth.tap_fill"
        const val WHEEL_SOUND = "synth.biglever"
        const val BELLOWS_SOUND = "synth.furnace"
        const val FURNACE_SOUND = "synth.furnace"
        const val ANVIL_SOUND = "synth.anvil02"
    }
}
