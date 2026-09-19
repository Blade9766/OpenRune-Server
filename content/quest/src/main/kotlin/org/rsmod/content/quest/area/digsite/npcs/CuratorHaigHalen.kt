package org.rsmod.content.quest.area.digsite.npcs

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.desert.thegolem.CURATOR_STATUETTE_OPTION
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest
import org.rsmod.content.quest.area.desert.thegolem.askCuratorAboutStatuette
import org.rsmod.content.quest.area.desert.thegolem.canAskCuratorAboutStatuette
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CERTIFICATE_1
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CERTIFICATE_2
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CERTIFICATE_3
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CHOCOLATE_CAKE
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CURATOR
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.FRUIT_BLAST
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.PLAIN_LETTER
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAGE_LETTER
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAGE_STAMPED
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAMPED_LETTER
import org.rsmod.content.quest.area.digsite.carriesOrBanks
import org.rsmod.content.quest.area.digsite.setVarBit
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Curator Haig Halen in the Varrock Museum. He stamps the examiner's letter of recommendation and
 * afterwards takes the Earth Sciences certificates off the player's hands, paying for the level 3
 * one with something to eat or drink. During The Golem he can be asked about the Uzer statuette.
 */
class CuratorHaigHalen
@Inject
constructor(private val quest: TheDigSiteQuest, private val golem: TheGolemQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(CURATOR) { startDialogue(it.npc) { curator() } }
        onOpNpcU(CURATOR) { useOnCurator(it.npc, it.objType.internalName) }
    }

    private suspend fun ProtectedAccess.useOnCurator(npc: Npc, obj: String) {
        when (obj) {
            PLAIN_LETTER -> startDialogue(npc) { stampLetter() }
            STAMPED_LETTER ->
                startDialogue(npc) {
                    chatNpc(
                        neutral,
                        "No I don't want it back thank you, you'll need to take that back to the " +
                            "examiner at the Digsite.",
                    )
                }
            CERTIFICATE_1, CERTIFICATE_2, CERTIFICATE_3 ->
                startDialogue(npc) { handInCertificate(obj) }
            else -> mes("Nothing interesting happens.")
        }
    }

    private suspend fun Dialogue.curator() {
        chatNpc(neutral, "Welcome to the museum of Varrock.")
        if (golem.canAskCuratorAboutStatuette(player)) {
            val statuette = choice2(CURATOR_STATUETTE_OPTION, true, "Something else.", false)
            if (statuette) {
                askCuratorAboutStatuette(golem)
                return
            }
        }
        val stage = quest.stage(player)
        when {
            stage == STAGE_LETTER && access.player.inv.contains(PLAIN_LETTER) -> stampLetter()
            stage == STAGE_LETTER -> lostPlainLetter()
            stage == STAGE_STAMPED && access.player.inv.contains(STAMPED_LETTER) ->
                chatNpc(
                    neutral,
                    "I see you still have that letter I stamped for you. Why don't you run it " +
                        "back to the Examiner at the Exam Centre?",
                )
            stage == STAGE_STAMPED -> replaceStampedLetter()
            else -> offerCertificateExchange()
        }
    }

    private suspend fun Dialogue.stampLetter() {
        if (quest.stage(player) != STAGE_LETTER || !access.player.inv.contains(PLAIN_LETTER)) {
            chatNpc(neutral, "I have nothing to stamp for you.")
            return
        }
        chatPlayer(
            neutral,
            "I have been given this letter by the examiner at the digsite; can you stamp it for me?",
        )
        chatNpc(happy, "What have we here? A letter of recommendation indeed!")
        if (QuestRequirements.hasCompleted(player, SHIELD_OF_ARRAV)) {
            chatNpc(
                happy,
                "The letter here says your name is ${player.displayName}. Well, " +
                    "${player.displayName}, I wouldn't normally do this for just anyone, but as " +
                    "you did us such a great service with the Shield of Arrav I don't see why not.",
            )
            chatNpc(
                happy,
                "Run this letter back to the Examiner to begin your adventure into the world of " +
                    "Earth Sciences. Enjoy your studies, Student!",
            )
        } else {
            chatNpc(neutral, "Normally, I wouldn't do this... but in this instance I don't see why not.")
        }
        chatNpc(
            happy,
            "There you go, good luck student... Be sure to come back and show me your " +
                "certificates. I would like to see how you get on.",
        )
        access.invDel(access.inv, PLAIN_LETTER)
        access.invAdd(access.inv, STAMPED_LETTER)
        quest.advanceTo(access, STAGE_STAMPED)
        setVarBit(player, "varbit.itcuratorletter", 1)
        objbox(STAMPED_LETTER, "The curator stamps your letter with his seal.")
        chatPlayer(happy, "Ok, I will. Thanks; see you later.")
    }

    private suspend fun Dialogue.lostPlainLetter() {
        if (access.carriesOrBanks(PLAIN_LETTER)) {
            chatNpc(neutral, "I see you have a letter there. Bring it here and I shall stamp it.")
            return
        }
        chatPlayer(sad, "I seem to have lost the letter the examiner wrote for me.")
        chatNpc(neutral, "Then you had best ask her for another; I can only stamp what I am given.")
    }

    private suspend fun Dialogue.replaceStampedLetter() {
        if (access.carriesOrBanks(STAMPED_LETTER)) {
            chatNpc(
                neutral,
                "I see you still have that letter I stamped for you. Why don't you run it back to " +
                    "the Examiner at the Exam Centre?",
            )
            return
        }
        chatPlayer(sad, "I seem to have lost the letter of recommendation that you stamped for me.")
        chatNpc(neutral, "Yes, I saw you drop it as you walked off last time. Here it is.")
        access.invAdd(access.inv, STAMPED_LETTER)
        objbox(STAMPED_LETTER, "The curator hands you another stamped letter.")
        chatPlayer(happy, "Thanks!")
    }

    /**
     * The certificates are souvenirs he keeps for the museum; the level 3 one is the only one he
     * pays for, and the reward is only handed over once the player has picked what they want.
     */
    private suspend fun Dialogue.offerCertificateExchange() {
        val held = CERTIFICATES.firstOrNull { access.player.inv.contains(it) }
        if (held == null) {
            chatNpc(
                neutral,
                "Do come back and show me your Earth Sciences certificates. I would like to see " +
                    "how you get on.",
            )
            return
        }
        chatPlayer(happy, "I have an Earth Sciences certificate here.")
        handInCertificate(held)
    }

    private suspend fun Dialogue.handInCertificate(certificate: String) {
        if (!access.player.inv.contains(certificate)) {
            return
        }
        if (certificate != CERTIFICATE_3) {
            chatNpc(
                happy,
                "Well done indeed! I shall keep this safe for the museum's records.",
            )
            access.invDel(access.inv, certificate)
            return
        }
        chatNpc(
            happy,
            "Level 3! Very impressive. I shall keep this safe for the museum's records - and you " +
                "must let me get you something for your trouble.",
        )
        val cake =
            choice2(
                "Something to eat, please.",
                true,
                "Something to drink, please.",
                false,
            )
        val reward = if (cake) CHOCOLATE_CAKE else FRUIT_BLAST
        access.invDel(access.inv, certificate)
        access.invAdd(access.inv, reward)
        objbox(reward, "The curator takes your certificate and hands you a treat.")
    }

    private companion object {
        const val SHIELD_OF_ARRAV = "quest_shieldofarrav"

        val CERTIFICATES = listOf(CERTIFICATE_3, CERTIFICATE_2, CERTIFICATE_1)
    }
}
