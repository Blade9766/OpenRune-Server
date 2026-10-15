package org.rsmod.content.quest.area.burthorpe.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.CERTIFICATE
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.CLIMBING_BOOTS
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.COINS
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.DUNSTAN
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.IRON_BAR
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.SPIKED_BOOTS
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpCertificateHanded
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpCertificateIssued
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpDunstanAsked
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpMapDrawn
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpTenzingAsked
import org.rsmod.content.quest.area.burthorpe.deathplateau.owns
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.LAW_TALISMAN
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.STAGE_GODRIC_FREED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Dunstan, the smith by the anvil in north-east Burthorpe. He spikes Tenzing's climbing boots in
 * Death Plateau in exchange for his son Godric's place in the Imperial Guard, and ends Troll
 * Stronghold once Godric is home. Afterwards he spikes boots for anyone with an iron bar and
 * sells replacement law talismans.
 */
class Dunstan
@Inject
constructor(
    private val deathPlateau: DeathPlateauQuest,
    private val trollStronghold: TrollStrongholdQuest,
    private val objRepo: ObjRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(DUNSTAN) { startDialogue(it.npc) { dunstan() } }
    }

    private suspend fun Dialogue.dunstan() {
        when {
            trollStronghold.isStarted(player) && !trollStronghold.isComplete(player) ->
                rescueInProgress()
            deathPlateau.isComplete(player) || !deathPlateau.isStarted(player) -> smithy()
            player.dpMapDrawn -> spikedBootsDelivered()
            player.dpCertificateHanded -> makeTheSpikes(greeting = true)
            player.dpDunstanAsked -> askAboutTheCertificate()
            player.dpTenzingAsked && CLIMBING_BOOTS in player.inv -> tenzingsBoots()
            else -> smithy()
        }
    }

    /* Death Plateau */

    private suspend fun Dialogue.tenzingsBoots() {
        chatPlayer(happy, "Hi!")
        chatNpc(happy, "Hi! How can I help?")
        chatPlayer(
            neutral,
            "Tenzing has asked me to bring you his climbing boots, he needs to have spikes put on " +
                "them.",
        )
        chatNpc(
            angry,
            "He does, does he? Well I won't do it till he pays for the last set I made for him!",
        )
        chatPlayer(worried, "This is really important!")
        chatNpc(quiz, "How so?")
        chatPlayer(
            neutral,
            "Well, I need the Sherpa to show me a secret way up Death Plateau so that the " +
                "Imperial Guard can destroy the troll camp! He won't help me till I've got the " +
                "spikes!",
        )
        chatNpc(neutral, "Hmm. That's different!")
        chatNpc(neutral, "Tell you what, I'll make them for you on one condition.")
        chatPlayer(bored, "*sigh* What's the condition?")
        chatNpc(
            happy,
            "My son has just turned 16 and I'd very much like him to join the Imperial Guard. " +
                "The Prince's elite forces are invite only so it's very unlikely he'll get in. " +
                "If you can arrange that you have a deal!",
        )
        chatPlayer(
            happy,
            "That won't be a problem as I'm helping out the Imperial Guard!",
        )
        chatNpc(happy, "Excellent! You'll need to bring an Iron bar for the spikes!")
        player.dpDunstanAsked = true
    }

    private suspend fun Dialogue.askAboutTheCertificate() {
        chatPlayer(happy, "Hi!")
        chatNpc(quiz, "Have you managed to get my son signed up for the Imperial Guard?")
        if (CERTIFICATE in player.inv) {
            access.invDel(access.inv, CERTIFICATE)
            player.dpCertificateHanded = true
            objbox(CERTIFICATE, "You give Dunstan the certificate.")
            chatNpc(happy, "Thank you!")
            makeTheSpikes(greeting = false)
            return
        }
        if (player.dpCertificateIssued) {
            chatPlayer(sad, "I have but I don't have the entrance certificate on me.")
            chatNpc(neutral, "Good but I need to have the certificate.")
        } else {
            chatPlayer(neutral, "Not yet! I just need to speak to Denulth!")
        }
    }

    private suspend fun Dialogue.makeTheSpikes(greeting: Boolean) {
        if (access.owns(SPIKED_BOOTS)) {
            spikedBootsDelivered(greeting)
            return
        }
        if (greeting) {
            chatPlayer(happy, "Hi!")
            chatNpc(happy, "Hi!")
        }
        chatNpc(
            neutral,
            "Now to keep my end of the bargain. Give me the boots and an iron bar and I'll put on " +
                "the spikes.",
        )
        val hasBoots = CLIMBING_BOOTS in player.inv
        val hasBar = IRON_BAR in player.inv
        when {
            !hasBoots && !hasBar -> {
                chatPlayer(sad, "I don't have the iron bar or the climbing boots.")
                return
            }
            !hasBoots -> {
                chatPlayer(sad, "I don't have the climbing boots.")
                return
            }
            !hasBar -> {
                chatPlayer(sad, "I don't have the iron bar.")
                return
            }
        }
        spikeTheBoots()
        chatPlayer(happy, "Thank you!")
        chatNpc(happy, "No problem.")
    }

    private suspend fun Dialogue.spikeTheBoots() {
        access.invDel(access.inv, IRON_BAR)
        access.invDel(access.inv, CLIMBING_BOOTS)
        doubleobjbox(IRON_BAR, CLIMBING_BOOTS, "You give Dunstan an iron bar and the climbing boots.")
        access.mes("You give Dunstan an iron bar and the climbing boots.")
        npc?.anim(SMITHING_SEQ)
        delay(SMITHING_TICKS)
        access.invAddOrDrop(objRepo, SPIKED_BOOTS)
        objbox(SPIKED_BOOTS, "Dunstan has given you the spiked boots.")
        access.mes("Dunstan has given you the spiked boots.")
    }

    private suspend fun Dialogue.spikedBootsDelivered(greeting: Boolean = true) {
        if (greeting) {
            chatPlayer(happy, "Hi!")
            chatNpc(happy, "Hi!")
        }
        chatNpc(quiz, "I see you've got the spiked boots. Are you going to take them to the Sherpa?")
        chatPlayer(neutral, "I will do shortly.")
    }

    /* Troll Stronghold */

    private suspend fun Dialogue.rescueInProgress() {
        if (trollStronghold.stage(player) >= STAGE_GODRIC_FREED) {
            chatPlayer(happy, "Has Godric returned home?")
            chatNpc(happy, "He is safe and sound, thanks to you my friend!")
            chatPlayer(happy, "I'm glad to hear it.")
            chatNpc(
                happy,
                "I have very little to offer you in way of thanks, but perhaps you will accept " +
                    "this family heirloom. It was found by my great-great-grandfather, but we " +
                    "still don't have any idea what it does.",
            )
            trollStronghold.quest.completeQuest(access)
            return
        }
        chatNpc(worried, "Have you managed to rescue Godric yet?")
        chatPlayer(sad, "Not yet.")
        chatNpc(
            worried,
            "Please hurry! Who knows what they will do to him? Is there anything I can do in the " +
                "meantime?",
        )
        smithyOptions()
    }

    /* The smithy */

    private suspend fun Dialogue.smithy() {
        chatNpc(happy, "Hi! Did you want something?")
        smithyOptions()
    }

    private suspend fun Dialogue.smithyOptions() {
        while (true) {
            val talisman = trollStronghold.isComplete(player) && !access.owns(LAW_TALISMAN)
            val son = deathPlateau.isComplete(player)
            val options =
                buildList {
                    if (talisman) add("Can I have another law talisman?" to Topic.Talisman)
                    add("Can you put some spikes on my Climbing boots?" to Topic.Spikes)
                    if (son) add("How is your son getting on?" to Topic.Son)
                    add("Is it OK if I use your anvil?" to Topic.Anvil)
                    add("Nothing, thanks." to Topic.Nothing)
                }
            val topic = choose(options)
            when (topic) {
                Topic.Talisman -> {
                    anotherTalisman()
                    return
                }
                Topic.Spikes -> if (!spikesService()) return
                Topic.Son -> howIsYourSon()
                Topic.Anvil -> {
                    chatPlayer(quiz, "Is it OK if I use your anvil?")
                    chatNpc(quiz, "So you're a smithy are you?")
                    chatPlayer(neutral, "I dabble.")
                    chatNpc(happy, "A fellow smith is welcome to use my anvil!")
                    chatPlayer(happy, "Thanks!")
                }
                Topic.Nothing -> {
                    chatPlayer(neutral, "Nothing, thanks.")
                    return
                }
            }
            chatNpc(neutral, "Anything else before I get on with my work?")
        }
    }

    private suspend fun Dialogue.choose(options: List<Pair<String, Topic>>): Topic =
        when (options.size) {
            3 -> choice3(options[0].first, options[0].second, options[1].first, options[1].second, options[2].first, options[2].second)
            4 ->
                choice4(
                    options[0].first,
                    options[0].second,
                    options[1].first,
                    options[1].second,
                    options[2].first,
                    options[2].second,
                    options[3].first,
                    options[3].second,
                )
            else ->
                choice5(
                    options[0].first,
                    options[0].second,
                    options[1].first,
                    options[1].second,
                    options[2].first,
                    options[2].second,
                    options[3].first,
                    options[3].second,
                    options[4].first,
                    options[4].second,
                )
        }

    /** @return `true` if the conversation carries on afterwards. */
    private suspend fun Dialogue.spikesService(): Boolean {
        chatPlayer(quiz, "Can you put some spikes on my Climbing boots?")
        if (!deathPlateau.isComplete(player)) {
            chatNpc(neutral, "Only Tenzing wears boots like those round here. Bring them to me if he wants new spikes.")
            return true
        }
        chatNpc(happy, "For you, no problem.")
        chatNpc(
            neutral,
            "Do you realise that you can only use the Climbing boots right now? The Spiked boots " +
                "can only be used in the Icelands but no one's been able to get there for years!",
        )
        if (!choice2("Yes, but I still want them.", true, "Oh OK, I'll leave them thanks.", false)) {
            chatPlayer(neutral, "Oh OK, I'll leave them thanks.")
            return true
        }
        chatPlayer(neutral, "Yes, but I still want them.")
        if (IRON_BAR !in player.inv) {
            chatNpc(neutral, "Sorry, I'll need an Iron bar to make the spikes.")
            return true
        }
        if (CLIMBING_BOOTS !in player.inv) {
            chatNpc(quiz, "You'll need to bring me the Climbing boots too!")
            return true
        }
        spikeTheBoots()
        return false
    }

    private suspend fun Dialogue.howIsYourSon() {
        chatPlayer(quiz, "How is your son getting on?")
        when {
            trollStronghold.isComplete(player) -> {
                chatNpc(
                    happy,
                    "He is getting on fine! He has just been promoted to Sergeant! I'm really " +
                        "proud of him!",
                )
                chatPlayer(happy, "I'm happy for you!")
            }
            trollStronghold.isStarted(player) || deathPlateau.isComplete(player) -> {
                chatNpc(
                    sad,
                    "He was captured by those blasted trolls! I don't know what to do. Even the " +
                        "imperial guard are too afraid to go rescue him.",
                )
                chatPlayer(quiz, "What happened?")
                chatNpc(sad, "Talk to Denulth, he can tell you all about it.")
            }
        }
    }

    private suspend fun Dialogue.anotherTalisman() {
        chatPlayer(quiz, "Can I have another law talisman?")
        chatNpc(neutral, "Well, I've got one here you can have for 1,000 coins if you'd like it?")
        if (!choice2("Yes, I'll take it.", true, "No thanks...", false)) {
            chatPlayer(neutral, "No thanks...")
            return
        }
        chatPlayer(happy, "Yes, I'll take it.")
        if (access.invTotal(access.inv, COINS) < TALISMAN_PRICE) {
            chatNpc(
                neutral,
                "Looks like you don't have enough money to afford it just now, come back when " +
                    "you do.",
            )
            return
        }
        if (!player.inv.hasFreeSpace() && access.invTotal(access.inv, COINS) != TALISMAN_PRICE) {
            chatNpc(
                neutral,
                "Looks like you don't have any room to carry it just now, come back when you do.",
            )
            return
        }
        access.invDel(access.inv, COINS, TALISMAN_PRICE)
        access.invAdd(access.inv, LAW_TALISMAN)
        mesbox("You hand over the coins... and receive a law talisman.")
        chatPlayer(happy, "Thanks.")
    }

    private enum class Topic { Talisman, Spikes, Son, Anvil, Nothing }

    private companion object {
        const val TALISMAN_PRICE = 1000
        const val SMITHING_SEQ = "seq.human_smithing"
        const val SMITHING_TICKS = 3
    }
}
