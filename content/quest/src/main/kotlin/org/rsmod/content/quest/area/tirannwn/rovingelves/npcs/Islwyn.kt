package org.rsmod.content.quest.area.tirannwn.rovingelves.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.tirannwn.rovingelves.CrystalSinging
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.CRYSTAL_BOW
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.CRYSTAL_SHIELD
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.FULL_CHARGES
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ISLWYN
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ISLWYN_TALK
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.ISLWYN_TRADE
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.REWARD_BOW
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.REWARD_CHARGES
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.REWARD_SHIELD
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.STAGE_ACCEPTED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.STAGE_LIED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.STAGE_PLANTED
import org.rsmod.content.quest.area.tirannwn.rovingelves.RovingElvesQuest.Companion.STAGE_PLANT_SEED
import org.rsmod.content.quest.area.tirannwn.rovingelves.rewardChoice
import org.rsmod.content.quest.area.tirannwn.rovingelves.toldLie
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Islwyn, Glarial's grandson, camped with Eluned in Isafdar. He starts the quest once the player
 * owns up to moving his grandmother's remains, and after the consecration lets them choose a
 * crystal bow or shield. From then on he sells either at full charge.
 */
class Islwyn
@Inject
constructor(private val roving: RovingElvesQuest, private val singing: CrystalSinging) : PluginScript() {

    override fun ScriptContext.startup() {
        for (type in listOf(ISLWYN, ISLWYN_TALK, ISLWYN_TRADE)) {
            onOpNpc1(type) { startDialogue(it.npc) { islwyn() } }
            onOpNpc3(type) { startDialogue(it.npc) { trade() } }
        }
    }

    private suspend fun Dialogue.islwyn() {
        val stage = roving.stage(player)
        when {
            roving.isComplete(player) -> afterQuest()
            stage == STAGE_PLANTED -> reward()
            stage >= STAGE_ACCEPTED -> waiting(stage)
            !roving.meetsRequirements(player) -> {
                chatNpc(neutral, "Forgive me, human, but I am in no mood for company. Leave me be.")
            }
            stage == STAGE_LIED -> secondChance()
            else -> firstMeeting()
        }
    }

    private suspend fun Dialogue.firstMeeting() {
        chatPlayer(neutral, "Hello there.")
        chatNpc(sad, "Greetings, human. Forgive me if I do not smile. My people have little reason to trust yours, and today less than ever.")
        chatPlayer(quiz, "Why, what's happened?")
        chatNpc(sad, "My grandmother Glarial was laid to rest by the river near Baxtorian Falls, long before you were born. Her tomb has been opened and her remains taken.")
        chatNpc(angry, "Elves have passed that place in peace for centuries. Only a human would disturb it. And you have the look of someone who has been near the falls.")
        chatNpc(quiz, "Do you know anything of this?")
        confess()
    }

    private suspend fun Dialogue.secondChance() {
        chatNpc(neutral, "You again. Have you come to tell me what really happened at my grandmother's tomb?")
        confess()
    }

    private suspend fun Dialogue.confess() {
        val truthful = menu("Yes." to true, "No." to false, title = "Tell the truth?")
        if (!truthful) {
            chatPlayer(shifty, "No, I've never been anywhere near the place.")
            chatNpc(angry, "Hmph. Then we have nothing more to say to each other.")
            player.toldLie = 1
            roving.advanceTo(access, STAGE_LIED)
            return
        }
        chatPlayer(worried, "It was me. I took Glarial's ashes from her tomb.")
        chatNpc(angry, "You!")
        chatPlayer(neutral, "Wait, please! I didn't take them for myself. I carried them into the caves beneath the falls and laid them in the chalice beside Baxtorian. They're together again.")
        chatNpc(neutral, "...Beside my grandfather? Then perhaps you meant no harm. Still, her old tomb was consecrated ground, and the rite that made it so was never performed where she lies now.")
        chatNpc(sad, "Until it is, her spirit cannot rest.")
        val help = menu("Maybe I could help." to true, "That's not my problem." to false)
        if (!help) {
            chatPlayer(neutral, "That's not really my problem.")
            chatNpc(angry, "No, I suppose it would not be. Good day.")
            return
        }
        chatPlayer(neutral, "Maybe I could help put things right.")
        chatNpc(happy, "You would do that? Then you have more honour than I gave you credit for.")
        chatNpc(neutral, "Eluned knows the old rites far better than I do. Speak with her; she is here with me.")
        roving.advanceTo(access, STAGE_ACCEPTED)
    }

    private suspend fun Dialogue.waiting(stage: Int) {
        chatPlayer(neutral, "Hello again, Islwyn.")
        when {
            stage == STAGE_ACCEPTED -> chatNpc(neutral, "Have you spoken to Eluned yet? She will tell you what the rite needs.")
            stage < STAGE_PLANT_SEED -> chatNpc(neutral, "Eluned tells me you have gone after the consecration seed. Be careful in that tomb.")
            else -> chatNpc(neutral, "The seed must be planted beside the chalice where my grandmother now rests. I await word of it.")
        }
    }

    /**
     * The reward is chosen in dialogue but given in one step that cannot be interrupted: the item
     * goes in, the choice is recorded and the quest completes together, so an empty slot is
     * checked first and a full pack leaves the choice open for next time.
     */
    private suspend fun Dialogue.reward() {
        chatPlayer(happy, "It's done. I planted the seed beside the chalice and a crystal tree grew there.")
        chatNpc(happy, "I know. I felt it, as if a long held breath had been let go. My grandmother is at peace, and I owe you more than I can say.")
        chatNpc(neutral, "Let me give you something in return. We elves sing crystal into shape. I can offer you a bow or a shield, each holding enough of the song for $REWARD_CHARGES uses.")
        val choice =
            menu(
                "The crystal bow." to REWARD_BOW,
                "The crystal shield." to REWARD_SHIELD,
                "Let me think about it." to 0,
                title = "Which would you like?",
            )
        if (choice == 0) {
            chatPlayer(neutral, "Let me think about it.")
            chatNpc(neutral, "Of course. The offer will stand.")
            return
        }
        val (obj, name) = if (choice == REWARD_BOW) CRYSTAL_BOW to "crystal bow" else CRYSTAL_SHIELD to "crystal shield"
        chatPlayer(neutral, "I'll take the $name, please.")
        if (player.inv.freeSpace() < 1) {
            chatNpc(neutral, "You have no room to carry it. Come back when you have a free space, and the $name will be waiting.")
            return
        }
        if (!singing.give(access, obj, REWARD_CHARGES)) {
            return
        }
        player.rewardChoice = choice
        roving.quest.completeQuest(access)
    }

    private suspend fun Dialogue.afterQuest() {
        chatPlayer(neutral, "Hello Islwyn.")
        chatNpc(happy, "Welcome, friend. My grandmother rests easy, thanks to you.")
        chatNpc(neutral, "If you ever need another bow or shield, I can sing you one with its full charge. Ask me to trade.")
    }

    private suspend fun Dialogue.trade() {
        if (!roving.isComplete(player)) {
            chatNpc(neutral, "I have nothing to trade with you, human.")
            return
        }
        chatNpc(neutral, "I can sing you a crystal bow for ${CrystalSinging.formatCoins(BOW_PRICE)} coins, or a crystal shield for ${CrystalSinging.formatCoins(SHIELD_PRICE)}.")
        val obj = menu(
            "A crystal bow." to CRYSTAL_BOW,
            "A crystal shield." to CRYSTAL_SHIELD,
            "Nothing, thanks." to null,
        ) ?: return
        val price = if (obj == CRYSTAL_BOW) BOW_PRICE else SHIELD_PRICE
        if (!singing.sell(access, obj, price, FULL_CHARGES)) {
            chatNpc(neutral, "You will need ${CrystalSinging.formatCoins(price)} coins and a free space for that.")
            return
        }
        objbox(obj, 400, "Islwyn sings the crystal into shape and hands it to you.")
    }

    private companion object {
        const val BOW_PRICE = 900_000
        const val SHIELD_PRICE = 750_000
    }
}
