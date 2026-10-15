package org.rsmod.content.quest.area.varrock.shieldofarrav

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.BLACKARM_CERTIFICATE
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.BLACKARM_SHIELD
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.CERTIFICATE
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_CERTIFICATE
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_SHIELD
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.SHIELD_GIVEN_BLACKARM
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.SHIELD_GIVEN_PHOENIX

/*
 * The reward end of Shield of Arrav, spoken by npcs that belong to other quests' scripts: Curator
 * Haig Halen (The Dig Site) and King Roald (Priest in Peril) each call into these rather than
 * registering a second handler.
 */

const val ARRAV_OPTION = "I'm here about the Shield of Arrav."

private val SHIELD_HALVES = listOf(PHOENIX_SHIELD, BLACKARM_SHIELD)
private val CERTIFICATE_HALVES = listOf(PHOENIX_CERTIFICATE, BLACKARM_CERTIFICATE)

fun ShieldOfArravQuest.canAskCurator(access: ProtectedAccess): Boolean {
    val player = access.player
    if (!isInProgress(player)) {
        return false
    }
    if (player.shieldGiven != 0) {
        return true
    }
    val half = gangShield(player) ?: return false
    return access.inv.count(half) > 0
}

suspend fun Dialogue.curatorShieldOfArrav(arrav: ShieldOfArravQuest) {
    chatPlayer(neutral, "Hello there. $ARRAV_OPTION")
    val certificate = arrav.gangCertificate(player) ?: return
    if (player.shieldGiven != 0) {
        chatNpc(
            neutral,
            "I gave you two half-certificates for the half you brought me. If another " +
                "adventurer brings in the other half, they'll get the same.",
        )
        chatNpc(
            neutral,
            "Then you each swap one half to make a whole certificate, and the King will pay you " +
                "your reward.",
        )
        chatPlayer(quiz, "Could I have some more half-certificates?")
        chatNpc(neutral, "I don't see why not. They're no good to anyone without the other half.")
        giveCertificates(certificate)
        return
    }
    val half = arrav.gangShield(player) ?: return
    chatNpc(
        shocked,
        "The Shield of Arrav? The museum has been searching for it for years! The late King " +
            "Roald II offered a generous reward for its return!",
    )
    chatPlayer(happy, "Well, I've come to claim it.")
    chatNpc(shocked, "You've found the shield? Let me see!")
    objbox(half, "You show the shield half to the curator.")
    chatNpc(shocked, "This is remarkable! But where is the rest of it?")
    val (own, other) =
        if (half == PHOENIX_SHIELD) "Phoenix Gang" to "Black Arm Gang" else "Black Arm Gang" to "Phoenix Gang"
    chatPlayer(neutral, "I got this half from the $own. The $other might have the other half.")
    chatNpc(neutral, "That sounds likely. Let's hope another adventurer can recover it.")
    chatPlayer(quiz, "So do I get a reward for bringing back half of the shield?")
    chatNpc(
        neutral,
        "I'm afraid the reward is for the whole shield. What I can do is write you out two " +
            "half-certificates, and do the same for whoever returns the other half.",
    )
    chatNpc(
        neutral,
        "The two of you can then swap one half each to make a whole certificate. Take it to " +
            "the King and he'll give you your reward.",
    )
    if (access.inv.count(half) == 0) {
        return
    }
    access.invDel(access.inv, half)
    player.shieldGiven = if (half == PHOENIX_SHIELD) SHIELD_GIVEN_PHOENIX else SHIELD_GIVEN_BLACKARM
    giveCertificates(certificate)
}

private suspend fun Dialogue.giveCertificates(certificate: String) {
    if (access.inv.freeSpace() < 2) {
        chatNpc(neutral, "You'll need room for two certificates. Come back when you've space.")
        return
    }
    access.invAdd(access.inv, certificate, 2)
    doubleobjbox(certificate, certificate, "The curator gives you two half-certificates.")
}

fun ShieldOfArravQuest.canAskKing(access: ProtectedAccess): Boolean {
    val carried = (SHIELD_HALVES + CERTIFICATE_HALVES + CERTIFICATE).any { access.inv.count(it) > 0 }
    if (isComplete(access.player)) {
        return access.inv.count(CERTIFICATE) > 0
    }
    return isInProgress(access.player) && carried
}

suspend fun Dialogue.kingShieldOfArrav(arrav: ShieldOfArravQuest) {
    chatPlayer(neutral, ARRAV_OPTION)
    val inv = access.inv
    when {
        inv.count(CERTIFICATE) > 0 -> claimReward(arrav)
        CERTIFICATE_HALVES.any { inv.count(it) > 0 } -> {
            val half = CERTIFICATE_HALVES.first { inv.count(it) > 0 }
            objbox(half, "You show the certificate half to the king.")
            chatNpc(
                neutral,
                "That's only half of the reward certificate, I'm afraid. Find the other half and " +
                    "join them together if you want to claim the reward.",
            )
        }
        else -> {
            chatNpc(neutral, "The Shield of Arrav? Ah yes, I recall my father offered a reward for it.")
            chatPlayer(happy, "Well, I've found half of it!")
            chatNpc(
                neutral,
                "Have you now? Before we go any further, you'll need the curator at the museum " +
                    "to confirm that it's genuine.",
            )
            chatPlayer(neutral, "Okay. I'll head over there.")
        }
    }
}

private suspend fun Dialogue.claimReward(arrav: ShieldOfArravQuest) {
    if (!arrav.isGangMember(player)) {
        chatNpc(
            confused,
            "This certificate isn't made out to you! I can't give you the reward unless you did " +
                "the work yourself!",
        )
        return
    }
    if (arrav.isComplete(player)) {
        chatNpc(confused, "You've already claimed this reward. You can't claim it twice!")
        mesbox("Why not give this certificate to the adventurer who helped you recover the shield?")
        return
    }
    objbox(CERTIFICATE, "You show the certificate to the king.")
    chatNpc(
        happy,
        "Goodness me! This is for the reward my father offered all those years ago! I never " +
            "thought I'd live to see someone claim it!",
    )
    chatPlayer(neutral, "It took some doing. I couldn't have managed it without another adventurer's help.")
    chatNpc(
        happy,
        "Then it's only right that you both be rewarded. Half the bounty to you and half to " +
            "your companion - that makes 600 coins for you. You've more than earned it!",
    )
    if (access.inv.count(CERTIFICATE) == 0) {
        return
    }
    access.invDel(access.inv, CERTIFICATE)
    arrav.complete(access)
}
