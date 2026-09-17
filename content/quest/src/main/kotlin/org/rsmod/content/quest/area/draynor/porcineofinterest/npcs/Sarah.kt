package org.rsmod.content.quest.area.draynor.porcineofinterest.npcs

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.BOUNTY_COINS
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.COINS
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.SARAH
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.SARAH_SHOP
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.SOURHOG_FOOT
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.STAGE_BOUNTY
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.STAGE_ROPE_TIED
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.STAGE_SLAIN
import org.rsmod.content.quest.area.draynor.porcineofinterest.carries
import org.rsmod.content.quest.area.draynor.porcineofinterest.porcineRosie
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sarah, who runs the farming shop at the South Falador farm and posted the bounty the quest is
 * built around.
 */
class Sarah
@Inject
constructor(private val porcine: PorcineOfInterestQuest, private val shops: Shops) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(SARAH) { startDialogue(it.npc) { sarah(it.npc) } }
        onOpNpc3(SARAH) { player.openShop(it.npc) }
    }

    private fun Player.openShop(npc: Npc) {
        shops.open(this, npc, "Sarah's Farming shop", SARAH_SHOP)
    }

    private suspend fun Dialogue.sarah(npc: Npc) {
        chatNpc(happy, "Hello. How can I help you?")
        val stage = porcine.stage(player)
        if (stage == 0 || porcine.isComplete(player)) {
            farmSupplies(npc)
            return
        }
        val askAboutBounty =
            choice2("Talk about the bounty.", true, "Talk about something else.", false)
        if (askAboutBounty) {
            bounty(stage)
        } else {
            farmSupplies(npc)
        }
    }

    private suspend fun Dialogue.bounty(stage: Int) {
        when {
            stage == STAGE_BOUNTY -> afterPayment()
            stage == STAGE_SLAIN -> claimBounty()
            stage >= STAGE_ROPE_TIED -> foundTheLair()
            porcine.sarahBriefed.get(player) -> followUp()
            else -> firstBriefing()
        }
    }

    private suspend fun Dialogue.firstBriefing() {
        chatPlayer(neutral, "I've come about the bounty.")
        chatNpc(
            happy,
            "Oh thank Saradomin! It's about time someone responded, something needs to be done!",
        )
        chatPlayer(quiz, "Can you explain what happened?")
        chatNpc(
            neutral,
            "Of course. I was making my monthly delivery to The Sheared Ram in Lumbridge, heading " +
                "east near the Draynor Village crossroads.",
        )
        chatNpc(
            shocked,
            "Suddenly, out of the trees came a hulking monster! It chased me for a short " +
                "distance, but luckily Rosie managed to spook it enough to give me a headstart.",
        )
        chatPlayer(worried, "Sounds like it must have given you quite the scare!")
        chatNpc(sad, "It certainly did. I even lost my cart full of farm produce!")
        porcine.sarahBriefed.set(player, true)
        followUp()
    }

    private suspend fun Dialogue.followUp() {
        while (true) {
            val choice =
                choice5(
                    "What did the monster look like?",
                    1,
                    "Any tips on finding this thing?",
                    2,
                    "Whereabouts were you attacked again?",
                    3,
                    "You mentioned Rosie. Who's that?",
                    4,
                    "I think that'll be all for now.",
                    5,
                )
            when (choice) {
                1 -> describeMonster()
                2 -> giveTips()
                3 -> giveDirections()
                4 -> introduceRosie()
                else -> {
                    chatPlayer(neutral, "I think that'll be all for now.")
                    chatNpc(happy, "Excellent! Best of luck to you, I'll be waiting right here with your reward.")
                    return
                }
            }
            chatNpc(quiz, "Is there anything else you wanted?")
        }
    }

    private suspend fun Dialogue.describeMonster() {
        chatPlayer(quiz, "What did this monster look like?")
        chatNpc(
            worried,
            "I didn't get a very good look at it I'm afraid. It was getting dark and as soon as I " +
                "saw it charging at me, I ran.",
        )
        chatNpc(sad, "I can still hear the sound, though. A horrible grunting... squealing...")
    }

    private suspend fun Dialogue.giveTips() {
        chatPlayer(quiz, "Any tips on finding this thing?")
        chatNpc(angry, "Tips...? You're supposed to be the professional monster slayer!")
        chatNpc(
            neutral,
            "Although, I suppose a good place to start would be checking the area I was attacked. " +
                "See if you can find the remains of my cart.",
        )
        chatPlayer(neutral, "Ok, yes that could work.")
    }

    private suspend fun Dialogue.giveDirections() {
        chatPlayer(quiz, "Whereabouts were you attacked again?")
        chatNpc(neutral, "I remember being somewhere near the crossroads, north of Draynor Village.")
        chatNpc(sad, "Sorry that I'm not much help. It happened so fast, the whole thing is a bit of a blur.")
        chatPlayer(
            neutral,
            "That's okay. Crossroads north of Draynor... At least I have somewhere to start.",
        )
    }

    private suspend fun Dialogue.introduceRosie() {
        chatPlayer(quiz, "You mentioned that Rosie spooked the monster. Who's that?")
        chatNpc(
            happy,
            "Oh, that'll be our lady over there by the fire. She's such a good dog, always " +
                "accompanies me on deliveries.",
        )
        chatNpc(happy, "You can pet her if you like. She's very friendly.")
        player.porcineRosie = true
    }

    private suspend fun Dialogue.foundTheLair() {
        chatNpc(happy, "Oh it's you, my monster slayer! How goes the quest?")
        chatPlayer(neutral, "I'm still working on it... I've managed to find its lair though.")
        chatNpc(happy, "Sounds like you're making progress. Take care!")
    }

    private suspend fun Dialogue.claimBounty() {
        chatPlayer(happy, "That monster certainly won't be troubling you from now on.")
        chatNpc(happy, "Oh that's fantastic news! I'm so relie-")
        chatNpc(quiz, "Wait, how do I know you're not just saying that to get the bounty?")
        if (!access.carries(SOURHOG_FOOT)) {
            chatPlayer(sad, "I guess I'll go and get you some proof.")
            return
        }
        chatPlayer(happy, "How about this as proof?")
        objbox(SOURHOG_FOOT, "You pull the foot out of your backpack and show it to Sarah.")
        chatNpc(shocked, "Eugh! Yes, that certainly looks about right!")
        chatPlayer(laugh, "Indeed, the smell is... something quite special.")
        chatNpc(neutral, "I'll probably just give it to the dog.")
        chatNpc(
            neutral,
            "I'm glad you made it back. Some strange woman in a hood came asking about the job " +
                "shortly after you left. I didn't really want her back in here.",
        )
        chatNpc(happy, "Anyway, thank you so much! Here's your reward.")
        access.invDel(access.inv, SOURHOG_FOOT)
        access.invAdd(access.inv, COINS, BOUNTY_COINS)
        objbox(COINS, "Sarah hands you a heavy pouch of $BOUNTY_COINS coins.")
        porcine.advanceTo(access, STAGE_BOUNTY)
        chatPlayer(
            neutral,
            "Perhaps I should speak with Spria and tell her that the monster's been dealt with.",
        )
    }

    private suspend fun Dialogue.afterPayment() {
        chatPlayer(quiz, "Feeling better now that the monster's gone?")
        chatNpc(happy, "Very much so! Thanks again. What was it, anyway?")
        chatPlayer(neutral, "It was a Sourhog. A giant bipedal pig creature!")
        chatNpc(shocked, "Oh crumbs... I think I've heard of those... My grandfather was always rambling on about them.")
        chatNpc(neutral, "Nevermind... that's all history now. Weren't you heading off to see Spria?")
        chatPlayer(neutral, "Yes, I suppose I should get going.")
        chatNpc(happy, "Safe travels!")
    }

    private suspend fun Dialogue.farmSupplies(npc: Npc) {
        chatPlayer(quiz, "What do you sell?")
        chatNpc(happy, "Everything a farmer needs: seeds, tools, compost and a sack to carry it in.")
        player.openShop(npc)
    }
}
