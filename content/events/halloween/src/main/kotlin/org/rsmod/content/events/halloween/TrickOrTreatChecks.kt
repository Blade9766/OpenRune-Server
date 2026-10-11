package org.rsmod.content.events.halloween

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.Npc

private const val OUT_OF_SEASON = "Trick-or-treating season is over. Come back in October!"

internal suspend fun ProtectedAccess.startTrickOrTreat(season: HalloweenSeason, npc: Npc): Boolean {
    if (!season.isActive) {
        mes(OUT_OF_SEASON)
        return false
    }
    if (player.hasTreatCauldron()) {
        anim("seq.emote_trick_or_treat_${player.cauldronStyleIndex()}")
    }
    var ready = false
    startDialogue(npc) {
        chatPlayer(happy, "Trick or treat!")
        ready = readyToTrickOrTreat()
    }
    return ready
}

private suspend fun Dialogue.readyToTrickOrTreat(): Boolean {
    val hasCostume = player.hasCostume()
    if (!player.hasTreatCauldron()) {
        chatNpc(
            neutral,
            access.random.pick(
                "Spooky! But without a Treat cauldron, where will you store your treats?",
                "I admire your spirit, but you can't go Trick-or-Treating without a Treat " +
                    "cauldron!",
            ),
        )
        if (!hasCostume) {
            chatNpc(
                neutral,
                "You also need to wear a spooky costume. At least three Halloween items " +
                    "should do the trick!",
            )
        }
        sendToChildren()
        return false
    }
    if (!hasCostume) {
        chatNpc(
            neutral,
            access.random.pick(
                "That's not a very spooky costume! Try combining at least three Halloween " +
                    "items, that'll be sure to spook!",
                "Almost, but you can't go Trick-or-Treating without a spooky costume! At " +
                    "least three Halloween items should do the trick!",
            ),
        )
        sendToChildren()
        return false
    }
    if (player.treats >= MAX_TREATS) {
        chatNpc(
            neutral,
            "Your Treat cauldron is full of treats! You should deposit some into the Great " +
                "Cauldron in Varrock before Trick-or-Treating any more.",
        )
        return false
    }
    return true
}

private suspend fun Dialogue.sendToChildren() {
    chatNpc(
        neutral,
        "Speak to the children near the west bank in Varrock, they'll help you get in the " +
            "spooky spirit.",
    )
}
