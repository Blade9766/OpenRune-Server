package org.rsmod.content.skills.farming.pack

import dev.openrune.cache.tools.iftype.dsl.buildInterface
import dev.openrune.cache.tools.iftype.dsl.impl.layer
import dev.openrune.definition.type.widget.IfEvent

/**
 * Gives the tool store's four quantity buttons a click event.
 *
 * As shipped they carry none, and the engine only consults a component's own events when a click
 * lands on the component itself, so the server could never be told which quantity was picked -
 * `ifSetEvents` does not reach that far. The client meanwhile handles the press on its own and
 * rewrites its copy of `farming_tools_selectedquantity`, then reorders the "-1/-5/-X/-All" labels
 * around it, leaving the two ends disagreeing about what each option means.
 *
 * This is an overlay: it inherits the stock interface and only restates these four components.
 * Their positions have to be repeated because the merge takes x and y from the overlay unchanged,
 * and they are the values the interface already uses. No options are declared - the client sets
 * those labels itself each time it redraws, and naming any here would freeze them.
 */
fun buildFarmingToolStoreInterface() =
    buildInterface(
        internalName = "interface.farming_tools",
        width = UNIVERSE_WIDTH,
        height = UNIVERSE_HEIGHT,
    ) {
        inherit("interface.farming_tools")

        for ((name, x) in QUANTITY_BUTTONS) {
            layer(name) {
                position { x to BUTTON_Y }
                size { BUTTON_SIZE to BUTTON_SIZE }
                events = CLICK_EVENT
            }
        }
    }

/** The stock size of the interface's root layer, restated so the merge leaves it alone. */
private const val UNIVERSE_WIDTH = 492
private const val UNIVERSE_HEIGHT = 36

private const val BUTTON_Y = 5
private const val BUTTON_SIZE = 36

private val CLICK_EVENT = IfEvent.DeprecatedOp1.bitmask.toInt()

/** Button name to its x offset, which the interface measures from its right edge. */
private val QUANTITY_BUTTONS =
    listOf(
        "quantity_1" to 291,
        "quantity_5" to 222,
        "quantity_x" to 153,
        "quantity_all" to 84,
    )
