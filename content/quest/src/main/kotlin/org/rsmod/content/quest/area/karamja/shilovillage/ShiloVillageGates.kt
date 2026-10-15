package org.rsmod.content.quest.area.karamja.shilovillage

import dev.openrune.types.ObjectServerType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.MOSOL_REI
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The way into Shilo Village: the broken cart Mosol Rei dragged across the path, the metal gates
 * of the gatehouse and the wooden gates behind them. Until Rashiliyia is at rest the metal gates
 * only let the undead drag a player into the mist-filled gatehouse, and the wooden gates stay
 * shut; afterwards both open like any other guarded gate. Players inside can always leave.
 */
class ShiloVillageGates
@Inject
constructor(
    private val shilo: ShiloVillageQuest,
    private val undead: ShiloUndead,
    private val passages: GenericPassageScript,
    private val search: NpcSearch,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(BROKEN_CART) {
            mesbox("You see a broken cart has been placed here. You suspect it's intended to keep people out.")
        }
        onOpLoc2(BROKEN_CART) { searchCart(it.loc) }
        onOpLoc1(METAL_GATE_LEFT) { metalGate(it.loc, it.type) }
        onOpLoc1(METAL_GATE_RIGHT) { metalGate(it.loc, it.type) }
        onOpLoc1(WOODEN_GATE_LEFT) { woodenGate(it.loc, it.type) }
        onOpLoc1(WOODEN_GATE_RIGHT) { woodenGate(it.loc, it.type) }
    }

    private suspend fun ProtectedAccess.searchCart(cart: BoundLocInfo) {
        arriveDelay()
        val west = player.coords.x <= cart.coords.x
        if (west || shilo.isComplete(player)) {
            climbCart(cart, west)
            return
        }
        mesbox(
            "You approach the cart and see undead creatures gathering by the village gates. There is a note attached to the cart. The note says,<br><col=ff0000>:: Danger :: Deadly green mist DO NOT ENTER IF YOU VALUE YOUR LIFE!</col>",
        )
        val mosol = npcFind(player.coords, MOSOL_REI, MOSOL_RANGE, HuntVis.Off, search)
        if (mosol != null) {
            startDialogue(mosol) {
                if (random.randomBoolean()) {
                    chatNpc(shocked, "Hey, move away from the cart please! It's unsafe to go in there.")
                } else {
                    chatNpc(shocked, "You must be a maniac to go in there! The whole place is swarming with zombies!")
                }
            }
        }
        mesbox("It looks as if you can climb across the cart. Would you like to try?")
        var climb = false
        startDialogue { climb = choice2("Yes, I am very nimble and agile!", true, "No, I am happy where I am thanks!", false) }
        if (!climb) {
            mes("You think better of clambering over the cart, you might get dirty.")
            delay(2)
            say("I'd probably have just scraped my knees up as well.")
            return
        }
        climbCart(cart, west = false)
    }

    private suspend fun ProtectedAccess.climbCart(cart: BoundLocInfo, west: Boolean) {
        val dest = CoordGrid(if (west) cart.coords.x + CART_WIDTH else cart.coords.x - 1, cart.coords.z + 1, cart.coords.level)
        mes("You nimbly jump from one side of the cart...")
        anim(CLIMB_SEQ)
        exactMove(
            start = player.coords,
            end = dest,
            delay1 = 0,
            delay2 = CLIMB_TICKS * CLIENT_CYCLES_PER_TICK,
            dir = if (west) constants.em_face_east else constants.em_face_west,
            teleportType = TeleportType.Exempt,
        )
        delay(CLIMB_TICKS)
        resetAnim()
        mes("...to the other and climb down again.")
    }

    private suspend fun ProtectedAccess.metalGate(gate: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        val inside = player.coords.x < gate.coords.x
        if (inside) {
            if (shilo.isComplete(player)) {
                mes("You open the gates and make your way back out of the village.")
            }
            with(passages) { walkThrough(gate, type) }
            return
        }
        if (shilo.isComplete(player)) {
            mes("You open the gates and make your way through into the village.")
            with(passages) { walkThrough(gate, type) }
            return
        }
        mesbox("The gate feels very cold to your touch! Are you sure you want to go through?")
        var enter = false
        startDialogue { enter = choice2("Yes, I am very nimble and agile!", true, "No, actually, I have a bad feeling about this!", false) }
        if (!enter) {
            mes("You drag your quivering body away from the gates.")
            delay(2)
            mes("You look around, but you don't think anyone saw you.")
            return
        }
        mes("The gates slowly begin to open...")
        delay(1)
        mes("Suddenly some Zombies grab you and start dragging you inside!")
        undead.gatehouseMist(player.uid)
        with(passages) { walkThrough(gate, type) }
    }

    private suspend fun ProtectedAccess.woodenGate(gate: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        if (!shilo.isComplete(player)) {
            mes("The gate won't open.")
            return
        }
        val inside = player.coords.x <= gate.coords.x
        mes(if (inside) "You make your way out of Shilo Village." else "You make your way into Shilo Village.")
        with(passages) { walkThrough(gate, type) }
    }

    private companion object {
        const val BROKEN_CART = "loc.shilo_brokencart"
        const val METAL_GATE_LEFT = "loc.zqshilogateclosedl"
        const val METAL_GATE_RIGHT = "loc.zqshilogateclosedr"
        const val WOODEN_GATE_LEFT = "loc.zqwoodengateclosed_l"
        const val WOODEN_GATE_RIGHT = "loc.zqwoodengateclosed_r"

        const val MOSOL_RANGE = 8
        const val CART_WIDTH = 3
        const val CLIMB_TICKS = 2
        const val CLIENT_CYCLES_PER_TICK = 30
        const val CLIMB_SEQ = "seq.human_walk_crumbledwall"
    }
}
