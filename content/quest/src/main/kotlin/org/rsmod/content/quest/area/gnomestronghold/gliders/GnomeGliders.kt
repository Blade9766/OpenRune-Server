package org.rsmod.content.quest.area.gnomestronghold.gliders

import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.area.gnomestronghold.grandtree.landNear
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The gnome glider network, exactly as the client expects it: the `glidermap` interface with a
 * clickable landing site per route, `varp.pilot_journey` packing the origin and destination so
 * the client flies its little glider across the map, and `varbit.pilot_previous_destination`
 * driving the pilots' "Glider-to" option. Every pilot is a varbit-multi, so ops arrive on the
 * base types listed in [PILOTS]; the Grand Tree's Captain Errdo is shared with the Grand Tree
 * quest, which hands his post-quest ops here.
 */
@Singleton
class GnomeGliders @Inject constructor() : PluginScript() {

    enum class Destination(val index: Int, val title: String, val landing: CoordGrid, val button: String) {
        TA_QUIR_PRIW(0, "Ta Quir Priw", CoordGrid(2465, 3503, 3), "component.glidermap:grandtree_button"),
        GANDIUS(1, "Gandius", CoordGrid(2970, 2972, 0), "component.glidermap:karamja_button"),
        KAR_HEWO(2, "Kar-Hewo", CoordGrid(3284, 3213, 0), "component.glidermap:alkharid_button"),
        LEMANTO_ANDRA(3, "Lemanto Andra", CoordGrid(3321, 3432, 0), "component.glidermap:varrock_button"),
        SINDARPOS(4, "Sindarpos", CoordGrid(2847, 3500, 0), "component.glidermap:whitewolfmountain_button"),
        LEMANTOLLY_UNDRI(5, "Lemantolly Undri", CoordGrid(2544, 2972, 0), "component.glidermap:ogrearea_button"),
        OOKOOKOLLY_UNDRI(6, "Ookookolly Undri", CoordGrid(2712, 2803, 0), "component.glidermap:apeatoll_button"),
    }

    private val currentOrigin = mutableMapOf<Int, Destination>()

    override fun ScriptContext.startup() {
        for ((npc, home) in PILOTS) {
            onOpNpc1(npc) { glider(it.npc, home) }
            onOpNpc3(npc) { startDialogue(it.npc) { pilotTalk(it.npc, home) } }
            onOpNpc4(npc) { flyToLast(it.npc, home) }
        }
        onOpNpc3(DIGSITE_PILOT) { startDialogue(it.npc) { digsitePilot() } }
        for (destination in Destination.entries) {
            onIfModalButton(destination.button) { chooseDestination(destination) }
        }
        onIfModalButton(CLOSE_BUTTON) { ifClose() }
        onIfClose(MAP_INTERFACE) {
            VarPlayerIntMapSetter.set(player, JOURNEY_VARP, NO_JOURNEY)
            currentOrigin.remove(player.uid.packed)
        }
        onPlayerLogin { syncPilots(player) }
    }

    /** The multi that hides Gnormadium Avlafrim's glider options until One Small Favour; there is no such quest here. */
    fun syncPilots(player: Player) {
        VarPlayerIntMapSetter.set(player, GNORMADIUM_VISIBLE_VARBIT, 1)
        VarPlayerIntMapSetter.set(player, JOURNEY_VARP, NO_JOURNEY)
    }

    fun canFly(player: Player): Boolean = QuestRequirements.hasCompleted(player, GRAND_TREE)

    suspend fun ProtectedAccess.glider(npc: Npc, home: Destination) {
        if (!canFly(player)) {
            mes("The pilot only flies friends of the gnome people.")
            return
        }
        npc.facePlayer(player)
        openMap(home)
    }

    suspend fun Dialogue.pilotTalk(npc: Npc, home: Destination) {
        if (!canFly(player)) {
            chatNpc(neutral, "Sorry, the gliders are for gnomes and friends of gnomes only. King's orders.")
            return
        }
        chatNpc(happy, "Hello there! Fancy a flight? Gnome Air goes wherever a glider can land.")
        when (choice2("Yes please.", 1, "Not right now.", 2)) {
            1 -> {
                chatPlayer(happy, "Yes please.")
                npc.facePlayer(player)
                access.openMap(home)
            }
            2 -> chatPlayer(neutral, "Not right now.")
        }
    }

    suspend fun ProtectedAccess.flyToLast(npc: Npc, home: Destination) {
        if (!canFly(player)) {
            mes("The pilot only flies friends of the gnome people.")
            return
        }
        val last = Destination.entries.firstOrNull { it.index == player.vars[LAST_DESTINATION_VARBIT] }
        if (last == null || last == home) {
            openMap(home)
            return
        }
        npc.facePlayer(player)
        fly(home, last)
    }

    private suspend fun Dialogue.digsitePilot() {
        chatNpc(sad, "Don't look at me like that. The glider's in pieces; nobody flies out of here.")
        chatPlayer(quiz, "So how do I get back?")
        chatNpc(neutral, "Same way everyone else does: walk. Varrock's just west of here.")
    }

    fun ProtectedAccess.openMap(home: Destination) {
        currentOrigin[player.uid.packed] = home
        VarPlayerIntMapSetter.set(player, JOURNEY_VARP, NO_JOURNEY)
        ifOpenMainModal(MAP_INTERFACE)
        for (destination in Destination.entries) {
            ifSetEvents(destination.button, -1..-1, IfEvent.Op1)
        }
        ifSetEvents(CLOSE_BUTTON, -1..-1, IfEvent.Op1)
    }

    private suspend fun ProtectedAccess.chooseDestination(destination: Destination) {
        val origin = currentOrigin[player.uid.packed] ?: return
        when {
            destination == origin -> mes("You are already at ${destination.title}.")
            destination == Destination.OOKOOKOLLY_UNDRI -> mes("The gnomes have not charted a route to Ape Atoll yet.")
            else -> fly(origin, destination)
        }
    }

    /** Sets the journey varp so the client animates the flight, then lands the player. */
    private suspend fun ProtectedAccess.fly(origin: Destination, destination: Destination) {
        if (player.ui.getModalOrNull(MAP_MODAL_TARGET) == null) {
            ifOpenMainModal(MAP_INTERFACE)
        }
        VarPlayerIntMapSetter.set(player, JOURNEY_VARP, (origin.index shl JOURNEY_SHIFT) or destination.index)
        soundSynth(TAKE_OFF_SOUND)
        delay(FLIGHT_TICKS)
        telejump(landNear(destination.landing), TeleportType.Standard)
        VarPlayerIntMapSetter.set(player, LAST_DESTINATION_VARBIT, destination.index)
        VarPlayerIntMapSetter.set(player, JOURNEY_VARP, NO_JOURNEY)
        ifClose()
        currentOrigin.remove(player.uid.packed)
        mes("The glider lands at ${destination.title}.")
    }

    companion object {
        const val GRAND_TREE = "quest_grandtree"
        const val MAP_INTERFACE = "interface.glidermap"
        const val MAP_MODAL_TARGET = "component.toplevel_osrs_stretch:mainmodal"
        const val CLOSE_BUTTON = "component.glidermap:close"
        const val JOURNEY_VARP = "varp.pilot_journey"
        const val LAST_DESTINATION_VARBIT = "varbit.pilot_previous_destination"
        const val GNORMADIUM_VISIBLE_VARBIT = "varbit.pilot_multinpc_visible"
        const val NO_JOURNEY = -1
        const val JOURNEY_SHIFT = 14
        const val FLIGHT_TICKS = 7
        const val TAKE_OFF_SOUND = "synth.wing_unfold"
        const val DIGSITE_PILOT = "npc.pilot_digsite"

        /** Pilot base types by the site they fly from; the Grand Tree pilot is driven by the Grand Tree quest. */
        val PILOTS =
            mapOf(
                "npc.pilot_karamja" to Destination.GANDIUS,
                "npc.pilot_al_kharid" to Destination.KAR_HEWO,
                "npc.pilot_white_wolf" to Destination.SINDARPOS,
                "npc.gnormadium_avlafrim" to Destination.LEMANTOLLY_UNDRI,
            )
    }
}
