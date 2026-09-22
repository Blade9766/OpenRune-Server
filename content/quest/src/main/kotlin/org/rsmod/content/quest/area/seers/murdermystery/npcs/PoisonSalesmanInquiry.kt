package org.rsmod.content.quest.area.seers.murdermystery.npcs

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.seers.murdermystery.MurderMysteryQuest
import org.rsmod.content.quest.area.seers.murdermystery.MurderMysteryQuest.Companion.POISON_ASKED_SALESMAN
import org.rsmod.content.quest.area.seers.murdermystery.MurderMysteryQuest.Companion.PUNGENT_POT
import org.rsmod.content.quest.area.seers.murdermystery.murderPoisonProgress
import org.rsmod.game.entity.Player

/**
 * Peter Potter the poison salesman's side of the murder case. His Talk-to belongs to the
 * Fremennik Trials, which asks here first so the case questions win while it is open.
 */
@Singleton
class PoisonSalesmanInquiry @Inject constructor(private val murder: MurderMysteryQuest) {

    fun isInvestigating(player: Player): Boolean = murder.isInvestigating(player)

    suspend fun Dialogue.murderInquiry() {
        chatPlayer(neutral, "I'm investigating the murder at the Sinclair house.")
        chatNpc(
            neutral,
            "There was a murder at the Sinclair House??? That's terrible! And I was only there the " +
                "other day too! They bought the last of my Patented Multi Purpose Poison!",
        )
        val topic =
            if (PUNGENT_POT in player.inv) {
                choice4(
                    "Patented Multi Purpose Poison?",
                    Topic.Poison,
                    "Who did you sell Poison to at the house?",
                    Topic.Buyers,
                    "Can I buy some Poison?",
                    Topic.Buy,
                    "I have this pot I found at the murder scene...",
                    Topic.Pot,
                )
            } else {
                choice3(
                    "Patented Multi Purpose Poison?",
                    Topic.Poison,
                    "Who did you sell Poison to at the house?",
                    Topic.Buyers,
                    "Can I buy some Poison?",
                    Topic.Buy,
                )
            }
        when (topic) {
            Topic.Poison -> salesPitch()
            Topic.Buyers -> buyers()
            Topic.Buy -> buyPoison()
            Topic.Pot -> pungentPot()
        }
    }

    private suspend fun Dialogue.salesPitch() {
        chatPlayer(neutral, "Patented Multi Purpose Poison?")
        chatNpc(neutral, "Aaaaah... a miracle of modern apothecaries!")
        chatNpc(
            happy,
            "This exclusive concoction has been tested on all known forms of life and been proven " +
                "to kill them all in varying dilutions from cockroaches to king dragons!",
        )
        chatNpc(
            happy,
            "So incredibly versatile, it can be used as pest control, a cleansing agent, drain " +
                "cleaner, metal polish and washes whiter than white,",
        )
        chatNpc(
            happy,
            "all with our uniquely fragrant concoction that is immediately recognisable across " +
                "the land as Peter Potter's Patented Poison potion!!!",
        )
        mesbox("The salesman stops for breath.")
        chatNpc(
            neutral,
            "I'd love to sell you some but I've sold out recently. That's just how good it is! " +
                "Three hundred and twenty eight people in this area alone cannot be wrong!",
        )
        chatNpc(neutral, "Nine out of Ten poisoners prefer it in controlled tests!")
        chatNpc(neutral, "Can I help you with anything else?")
        chatNpc(
            neutral,
            "Perhaps I can take your name and add it to our mailing list of poison users? We will " +
                "only send you information related to the use of poison and other Peter Potter " +
                "Products!",
        )
        chatPlayer(confused, "Uh... no, it's ok. Really.")
    }

    private suspend fun Dialogue.buyers() {
        chatPlayer(neutral, "Who did you sell Poison to at the house?")
        chatNpc(
            happy,
            "Well, Peter Potter's Patented Multi Purpose Poison is a product of such obvious " +
                "quality that I am glad to say I managed to sell a bottle to each of the " +
                "Sinclairs!",
        )
        chatNpc(
            happy,
            "Anna, Bob, Carol, David, Elizabeth and Frank all bought a bottle! In fact they " +
                "bought the last of my supplies!",
        )
        if (player.murderPoisonProgress < POISON_ASKED_SALESMAN) {
            player.murderPoisonProgress = POISON_ASKED_SALESMAN
        }
        chatNpc(
            happy,
            "Maybe I can take your name and address and I will personally come and visit you " +
                "when stocks return?",
        )
        chatPlayer(confused, "Uh... no, it's ok.")
    }

    private suspend fun Dialogue.buyPoison() {
        chatPlayer(neutral, "Can I buy some Poison?")
        chatNpc(
            neutral,
            "I'm afraid I am totally out of stock at the moment after my successful trip to the " +
                "Sinclairs' House the other day.",
        )
        chatNpc(
            happy,
            "But don't worry! Our factories are working overtime to produce Peter Potter's " +
                "Patented Multi Purpose Poison!",
        )
        chatNpc(
            happy,
            "Possibly the finest multi purpose poison and cleaner yet available to the general " +
                "market.",
        )
        chatNpc(
            happy,
            "And its unique fragrance makes it the number one choice for cleaners and " +
                "exterminators the whole country over!",
        )
    }

    private suspend fun Dialogue.pungentPot() {
        chatPlayer(neutral, "I have this pot I found at the murder scene...")
        mesbox(
            "You show the poison salesman the pot you found at the murder scene with the unusual " +
                "smell.",
        )
        chatNpc(
            confused,
            "Hmmm... yes, that smells exactly like my Patented Multi Purpose Poison, but I don't " +
                "see how it could be. It quite clearly says on the label of all bottles",
        )
        chatNpc(confused, "'Not to be taken internally - EXTREMELY POISONOUS'.")
        chatPlayer(confused, "Perhaps someone else put it in his wine?")
        chatNpc(confused, "Yes... I suppose that could have happened...")
    }

    private enum class Topic {
        Poison,
        Buyers,
        Buy,
        Pot,
    }
}
