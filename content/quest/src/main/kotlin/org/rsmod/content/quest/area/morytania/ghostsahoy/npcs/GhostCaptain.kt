package org.rsmod.content.quest.area.morytania.ghostsahoy.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOAT_FARE
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOAT_FARE_CHARMED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.CAPTAIN
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.CHARMED_RING
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.ECTOTOKEN
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.MAP
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.SPADE
import org.rsmod.content.quest.area.morytania.ghostsahoy.hasGhostspeak
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The ghost captain who rows between the Port Phasmatys docks and Dragontooth Island. The
 * outward fare of [BOAT_FARE] ecto-tokens covers the trip back; wearing a Ring of Charos(a)
 * lets the player charm him down to [BOAT_FARE_CHARMED]. Tokens are taken in the same step as
 * the departure, so a refused or failed payment never moves the player.
 */
class GhostCaptain @Inject constructor(private val ahoy: GhostsAhoyQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(CAPTAIN) { captain(it.npc, travel = false) }
        onOpNpc3(CAPTAIN) { captain(it.npc, travel = true) }
    }

    private suspend fun ProtectedAccess.captain(npc: Npc, travel: Boolean) {
        if (!player.hasGhostspeak()) {
            startDialogue(npc) { chatNpc(neutral, "Woooo woo wooooo.") }
            mes("You can't understand what the ghost is saying.")
            return
        }
        var dest: CoordGrid? = null
        startDialogue(npc) {
            dest = if (onIsland(player.coords)) returnTrip(travel) else outwardTrip(travel)
        }
        val target = dest ?: return
        ifClose()
        telejump(target, TeleportType.Exempt)
        mes("The ghost captain rows you across the water.")
    }

    private suspend fun Dialogue.returnTrip(travel: Boolean): CoordGrid? {
        if (!travel) {
            chatNpc(neutral, "Ready to head back to Port Phasmatys? Your fare covers the return.")
            if (!menu("Yes, take me back." to true, "Not yet." to false)) {
                return null
            }
        }
        return PORT_LANDING
    }

    private suspend fun Dialogue.outwardTrip(travel: Boolean): CoordGrid? {
        val charmed = player.worn.contains(CHARMED_RING)
        if (!travel) {
            chatNpc(neutral, "I can row you out to Dragontooth Island. It'll cost you $BOAT_FARE ecto-tokens, there and back.")
        }
        var fare = BOAT_FARE
        if (charmed) {
            val charm = menu("Charm the captain." to true, "Pay $BOAT_FARE ecto-tokens." to false)
            if (charm) {
                chatPlayer(happy, "Surely a fine captain like you could do me a better price?")
                chatNpc(happy, "Well... for you, $BOAT_FARE_CHARMED ecto-tokens.")
                fare = BOAT_FARE_CHARMED
            }
        }
        if (player.inv.count(ECTOTOKEN) < fare) {
            chatNpc(neutral, "Come back when you have $fare ecto-tokens.")
            return null
        }
        if (!(player.inv.contains(MAP) && player.inv.contains(SPADE)) && ahoy.needsBook(player)) {
            mesbox("You don't have both the treasure map and a spade with you.")
        }
        if (!menu("Pay $fare ecto-tokens." to true, "No thanks." to false)) {
            return null
        }
        if (access.invDel(access.inv, ECTOTOKEN, fare).failure) {
            return null
        }
        return ISLAND_LANDING
    }

    private fun onIsland(coords: CoordGrid): Boolean = coords.x >= ISLAND_MIN_X

    private companion object {
        const val ISLAND_MIN_X = 3750
        val ISLAND_LANDING = CoordGrid(3793, 3559, 0)
        val PORT_LANDING = CoordGrid(3701, 3487, 0)
    }
}
