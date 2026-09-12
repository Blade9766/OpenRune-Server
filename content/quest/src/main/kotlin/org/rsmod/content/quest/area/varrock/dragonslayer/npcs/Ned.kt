package org.rsmod.content.quest.area.varrock.dragonslayer.npcs

import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.COINS
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.CRANDOR_MAP
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.MAP_PARTS
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_BRIEFED
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_NED_ABOARD
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_ON_CRANDOR
import org.rsmod.content.quest.area.varrock.dragonslayer.LadyLumbridge
import org.rsmod.content.quest.area.varrock.dragonslayer.Voyage
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Ned the ropemaker of Draynor Village, retired from the sea and desperate enough to captain
 * the Lady Lumbridge to Crandor. Once he has the map he waits on the ship (the varp-driven
 * `npc.dragonslayer_ned_on_ship` form) and, after the wreck, on the beach at Crandor.
 */
class Ned
@Inject
constructor(
    private val dragonSlayer: DragonSlayerQuest,
    private val voyage: Voyage,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(NED) { startDialogue(it.npc) { ned() } }
        onOpNpc3(NED) { startDialogue(it.npc) { ropeOffer() } }
        onOpNpc1(NED_ON_SHIP) { startDialogue(it.npc) { nedOnShip() } }
        onOpNpc1(NED_ON_CRANDOR) { startDialogue(it.npc) { nedOnCrandor() } }
    }

    /* Draynor */

    private suspend fun Dialogue.ned() {
        val stage = dragonSlayer.stage(player)
        val asked = dragonSlayer.askedNed.get(player)
        if (asked || stage >= STAGE_NED_ABOARD) {
            chatNpc(happy, "Hello again, ${lad()}. Something I can help you with? Want to buy some rope, perhaps?")
        } else {
            chatNpc(happy, "Why, hello there, ${lad()}. Me friends call me Ned. I was a man of the sea, but it's past me now. Could I be making or selling you some rope?")
        }
        val crandorOption =
            when {
                stage >= STAGE_ON_CRANDOR -> null
                stage == STAGE_NED_ABOARD || asked -> "Will you take me to Crandor now, then?"
                stage >= STAGE_BRIEFED -> "You're a sailor? Could you take me to Crandor?"
                else -> null
            }
        if (crandorOption == null) {
            when (
                choice2(
                    "Yes, I would like some rope.", 1,
                    "No thanks, Ned. I don't need any.", 2,
                )
            ) {
                1 -> ropeOffer()
                2 -> chatPlayer(neutral, "No thanks, Ned. I don't need any.")
            }
            return
        }
        when (
            choice3(
                crandorOption, 1,
                "Yes, I would like some rope.", 2,
                "No thanks, Ned. I don't need any.", 3,
            )
        ) {
            1 -> crandor(stage, asked)
            2 -> ropeOffer()
            3 -> chatPlayer(neutral, "No thanks, Ned. I don't need any.")
        }
    }

    private suspend fun Dialogue.crandor(stage: Int, asked: Boolean) {
        if (stage == STAGE_NED_ABOARD) {
            chatPlayer(quiz, "Will you take me to Crandor now, then?")
            chatNpc(happy, "I said I would, and old Ned is a man of his word! I'll meet you on board the Lady Lumbridge in Port Sarim.")
            return
        }
        if (asked) {
            chatPlayer(quiz, "Will you take me to Crandor now, then?")
            chatNpc(happy, "I said I would, and old Ned is a man of his word!")
        } else {
            chatPlayer(quiz, "You're a sailor? Could you take me to the island of Crandor?")
            chatNpc(sad, "Well, I was a sailor. I've not been able to get work at sea these days, though. They say I'm too old.")
            chatNpc(quiz, "Sorry, where was it you said you wanted to go?")
            chatPlayer(neutral, "To the island of Crandor.")
            chatNpc(shocked, "Crandor?")
            chatNpc(neutral, "But... it would be a chance to sail a ship once more. I'd sail anywhere for the chance to sail again.")
            chatNpc(worried, "Then again, no captain in his right mind would sail to that island...")
            chatNpc(happy, "Ah, you only live once! I'll do it!")
            dragonSlayer.askedNed.set(player, true)
            dragonSlayer.syncVars(player)
        }
        chatNpc(quiz, "So, where's your ship?")
        when {
            !dragonSlayer.shipBought(player) -> {
                chatPlayer(sad, "Well, I don't actually have a ship yet.")
                chatNpc(neutral, "Ah, well. Let me know when you do. The place to look would be Port Sarim.")
            }
            !dragonSlayer.shipRepaired(player) -> {
                chatPlayer(happy, "It's the Lady Lumbridge, in Port Sarim.")
                chatNpc(confused, "That old pile of junk? Last I heard, she wasn't seaworthy.")
                chatPlayer(sad, "Yes, good point. I'd better go and fix her up.")
            }
            else -> {
                chatPlayer(happy, "It's the Lady Lumbridge, in Port Sarim.")
                chatNpc(confused, "That old pile of junk? Last I heard, she wasn't seaworthy.")
                chatPlayer(happy, "I fixed her up!")
                chatNpc(happy, "You did? Excellent!")
                chatNpc(happy, "Just show me the map and we can get ready to go!")
                if (dragonSlayer.hasFullMap(player)) {
                    chatPlayer(happy, "Here you go.")
                    takeMap()
                    objbox(CRANDOR_MAP, "You hand the map to Ned.")
                    chatNpc(happy, "Excellent! I'll meet you at the ship, then.")
                    dragonSlayer.setStage(access, STAGE_NED_ABOARD)
                } else {
                    chatPlayer(worried, "Uh... yeah... about that. I don't actually have a map on me.")
                    chatNpc(neutral, "You'd better go and find it, then. I'm not sailing off without a chart to show me where to head!")
                }
            }
        }
    }

    private fun Dialogue.takeMap() {
        if (player.inv.contains(CRANDOR_MAP)) {
            access.invDel(access.inv, CRANDOR_MAP, 1)
            return
        }
        for (part in MAP_PARTS) {
            access.invDel(access.inv, part, 1)
        }
    }

    private suspend fun Dialogue.ropeOffer() {
        chatPlayer(happy, "Yes, I would like some rope.")
        chatNpc(happy, "I can sell you a coil for $ROPE_PRICE coins, or if you bring me $WOOL_PER_ROPE balls of wool I'll make you one for free.")
        when (
            choice3(
                "I'll buy one for $ROPE_PRICE coins.", 1,
                "Here's some wool, could you make me a rope?", 2,
                "Not right now, thanks.", 3,
            )
        ) {
            1 -> {
                chatPlayer(happy, "I'll buy one for $ROPE_PRICE coins.")
                if (player.inv.count(COINS) < ROPE_PRICE) {
                    chatNpc(sad, "You don't seem to have enough coins on you, ${lad()}.")
                    return
                }
                if (access.invAdd(access.inv, ROPE).failure) {
                    chatNpc(sad, "You don't have room for it.")
                    return
                }
                access.invDel(access.inv, COINS, ROPE_PRICE)
                objbox(ROPE, "Ned hands you a coil of rope.")
            }
            2 -> {
                chatPlayer(happy, "Here's some wool, could you make me a rope?")
                if (player.inv.count(WOOL) < WOOL_PER_ROPE) {
                    chatNpc(sad, "I need $WOOL_PER_ROPE balls of wool to make a rope, ${lad()}. You don't have enough.")
                    return
                }
                access.invDel(access.inv, WOOL, WOOL_PER_ROPE)
                access.invAdd(access.inv, ROPE)
                objbox(ROPE, "Ned twists the wool into a sturdy rope and hands it to you.")
            }
            3 -> chatPlayer(neutral, "Not right now, thanks.")
        }
    }

    /* On the Lady Lumbridge */

    private suspend fun Dialogue.nedOnShip() {
        if (dragonSlayer.stage(player) >= STAGE_ON_CRANDOR) {
            sailAgain()
            return
        }
        captain(happy, "Ah, it's good to be on board a ship again! No matter how long I live on land, a ship will always seem better. Are you ready to depart?")
        when (
            choice2(
                "Yes, let's go!", 1,
                "No, I'm not quite ready yet.", 2,
            )
        ) {
            1 -> {
                chatPlayer(happy, "Yes, let's go!")
                with(voyage) { access.sail() }
            }
            2 -> {
                chatPlayer(neutral, "No, I'm not quite ready yet.")
                captain(neutral, "Well, you go and do whatever you need to do. I'll wait here for you until you're ready.")
            }
        }
    }

    /** After the wreck the tide brings her back to Port Sarim, holed again; Ned will sail once she is patched. */
    private suspend fun Dialogue.sailAgain() {
        captain(happy, "Ah, the Lady Lumbridge. What a voyage that was! They say the tide washed her back into dock all by herself.")
        if (dragonSlayer.repairStage.get(player) < DragonSlayerQuest.REPAIR_PLANKS) {
            captain(worried, "She's not going anywhere with that hole in her hull, mind. Patch her up again and I'll take you back to Crandor.")
            return
        }
        captain(quiz, "Back to Crandor, is it? Are you ready to depart?")
        when (
            choice2(
                "Yes, let's go!", 1,
                "No, I'm not quite ready yet.", 2,
            )
        ) {
            1 -> {
                chatPlayer(happy, "Yes, let's go!")
                with(voyage) { access.sailAgain() }
            }
            2 -> {
                chatPlayer(neutral, "No, I'm not quite ready yet.")
                captain(neutral, "I'll wait here for you until you're ready.")
            }
        }
    }

    /* On Crandor */

    private suspend fun Dialogue.nedOnCrandor() {
        captain(sad, "Now I see why all the other captains said I'd be mad to go near Crandor. It looks like we're stranded.")
        if (dragonSlayer.secretDoorFound.get(player)) {
            chatPlayer(happy, "Don't worry about that. I found a secret passage in the dragon's lair that leads off the island!")
            captain(happy, "Oh, that's good. I'll be able to get back to Draynor somehow...")
            captain(worried, "...if I can sneak past all the skeletons and demons on this island!")
        }
    }

    private suspend fun Dialogue.captain(mood: MesAnimType, text: String) =
        chatNpcSpecific("Captain Ned", LadyLumbridge.NED_CAPTAIN, mood, text)

    private fun Dialogue.lad(): String =
        if (player.appearance.bodyType == Constants.bodytype_a) "lad" else "lass"

    private companion object {
        const val NED = "npc.ned"
        const val NED_ON_SHIP = "npc.dragonslayer_ned_on_ship"
        const val NED_ON_CRANDOR = "npc.dragonslayer_ned_on_crandor"

        const val ROPE = "obj.rope"
        const val WOOL = "obj.ball_of_wool"
        const val ROPE_PRICE = 15
        const val WOOL_PER_ROPE = 4
    }
}
