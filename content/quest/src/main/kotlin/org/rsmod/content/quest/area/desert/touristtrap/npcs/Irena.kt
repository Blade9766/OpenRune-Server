package org.rsmod.content.quest.area.desert.touristtrap.npcs

import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_AT_PASS_NPC
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_BARREL_HEAD
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_GONE_HOME
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_IN_A_BARREL
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_WITH_IRENA
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.IRENA_HAPPY
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.IRENA_SAD
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.SKILL_REWARD_XP
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_ESCAPED
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_PICKED_FIRST_SKILL
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_SAVED_ANA
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.WROUGHT_IRON_KEY
import org.rsmod.content.quest.area.desert.touristtrap.ttAnaLocation
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Irena, crying for her daughter just south of the Shantay Pass, and Ana once she is home.
 *
 * Irena is two multi-npcs on `varbit.tourtrap_qip_irena_state`, sad until Ana is back and happy
 * after; Ana appears beside her while she still has the wrought iron key to hand over.
 */
class Irena
@Inject
constructor(private val quest: TouristTrapQuest, private val npcSearch: NpcSearch) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(IRENA_SAD) { startDialogue(it.npc) { talk() } }
        onOpNpc1(IRENA_HAPPY) { startDialogue(it.npc) { talk() } }
        onOpNpc1(ANA_AT_PASS_NPC) { startDialogue(it.npc) { anaAtThePass() } }
    }

    private suspend fun Dialogue.talk() {
        val stage = quest.stage(player)
        when {
            stage == 0 -> startQuest()
            stage == STAGE_ESCAPED && ANA_IN_A_BARREL in player.inv -> anaReturned()
            stage < STAGE_SAVED_ANA -> stillMissing()
            stage < STAGE_COMPLETE && player.ttAnaLocation == ANA_WITH_IRENA ->
                chatNpc(
                    happy,
                    "Thanks for returning Ana to me, I think she has something else to say to " +
                        "you though!",
                )
            stage < STAGE_COMPLETE -> reward()
            else ->
                chatNpc(
                    happy,
                    "Thanks so much for returning my daughter to me. I expect that she will go " +
                        "on another trip soon though. She is the adventurous type... a bit like " +
                        "yourself really! Okay, see you around then!",
                )
        }
    }

    private suspend fun Dialogue.startQuest() {
        mesbox("Irena seems to be very upset and cries as you approach her.")
        chatNpc(sad, "Boo hoo! Oh dear, my only daughter....")
        val opener = choice2("What's the matter?", true, "Cheer up, it might never happen.", false)
        if (opener) {
            chatPlayer(quiz, "What's the matter?")
            chatNpc(
                sad,
                "Oh dear... my daughter, Ana, has gone missing in the desert. I fear that she is " +
                    "lost, or perhaps... *sob* even worse.",
            )
        } else {
            chatPlayer(happy, "Cheer up, it might never happen.")
            chatNpc(
                angry,
                "It may already have happened you thoughtless oaf! My daughter, Ana, has gone " +
                    "missing in the desert. I fear that she is lost, or perhaps... *sobs* even " +
                    "worse.",
            )
        }
        chatPlayer(quiz, "When did she go into the desert?")
        chatNpc(
            sad,
            "*Sob* she went in there just a few days ago, *Sob* she said she would be back " +
                "yesterday.",
        )
        chatNpc(sad, "*Sob* And she's not...")
        val accept = choice2("Yes.", true, "No.", false, title = "Start The Tourist Trap quest?")
        if (!accept) {
            chatPlayer(neutral, "Oh... Well I hope you find her.")
            return
        }
        chatPlayer(happy, "I'll find her for you!")
        quest.advanceTo(access, STAGE_STARTED)
        chatNpc(
            happy,
            "Oh thank you! You've made me a very happy mother, I just hope it's not too late!",
        )
        chatPlayer(quiz, "Do you have any ideas on where she may have gone?")
        chatNpc(
            worried,
            "I did go looking for her myself and I came across some footprints just a little " +
                "way south. I'm worried that they lead to the desert mining camp!",
        )
    }

    private suspend fun Dialogue.stillMissing() {
        chatNpc(
            sad,
            "Please bring my daughter back to me. She is most likely lost in the Desert " +
                "somewhere. I miss her so much... Wahhhhh! *Sob*",
        )
        while (true) {
            val option =
                choice4(
                    "Do you know where Ana went?",
                    1,
                    "Is there anywhere around here that she would go?",
                    2,
                    "Where do you think I should start my search?",
                    3,
                    "Okay, thanks.",
                    4,
                )
            when (option) {
                1 -> {
                    chatPlayer(quiz, "Do you know where Ana went?")
                    chatNpc(
                        sad,
                        "I did look for her myself and a little way south. I came across some " +
                            "footprints which may be hers. I'm worried that she may have been " +
                            "caught by some guards and taken to work in that terrible desert " +
                            "mine to the south.",
                    )
                }
                2 -> {
                    chatPlayer(quiz, "Is there anywhere around here that she would go?")
                    chatNpc(
                        sad,
                        "I think she may have just gone a little way into the desert. It's " +
                            "unlike her to go too far away. She is an adventurous girl, but she " +
                            "knows how I worry. She wouldn't go too far.",
                    )
                }
                3 -> {
                    chatPlayer(quiz, "Where do you think I should start my search?")
                    chatNpc(
                        sad,
                        "I think I saw some of her footprints to the south, you could start " +
                            "there. Hopefully she'll just be over a few sand dunes, but you never " +
                            "know. She may have been taken to that awful desert mining camp or " +
                            "captured by brigands!",
                    )
                }
                else -> {
                    chatPlayer(neutral, "Okay, thanks.")
                    return
                }
            }
        }
    }

    /** Ana, still in her barrel, is shown to her mother; she climbs out and the quest winds up. */
    private suspend fun Dialogue.anaReturned() {
        chatNpc(happy, "Hey, great you've found Ana!")
        chatNpcSpecific(
            "Ana (in a Barrel)",
            ANA_BARREL_HEAD,
            happy,
            "<col=0000ff>-- You show Irena the barrel with Ana in it. --</col> Hey great, " +
                "there's my Mum!",
        )
        if (access.invDel(access.inv, ANA_IN_A_BARREL).failure) {
            return
        }
        quest.advanceTo(access, STAGE_SAVED_ANA)
        quest.moveAna(player, ANA_WITH_IRENA)
        anaThanks()
    }

    private suspend fun Dialogue.anaAtThePass() {
        if (quest.stage(player) < STAGE_SAVED_ANA || player.ttAnaLocation != ANA_WITH_IRENA) {
            chatNpc(happy, "Thanks again for getting me out of that mine!")
            return
        }
        anaThanks()
    }

    private suspend fun Dialogue.anaThanks() {
        chatNpcSpecific(
            "Ana",
            ANA_HEAD,
            happy,
            "Great! Thanks for getting me out of that mine! And that barrel wasn't too bad " +
                "anyway! Pop by again sometime, I'm sure we'll have a barrel of laughs!",
        )
        if (WROUGHT_IRON_KEY !in player.inv && WROUGHT_IRON_KEY !in access.bank) {
            chatNpcSpecific(
                "Ana",
                ANA_HEAD,
                happy,
                "Oh! I nearly forgot! Here's a key I found in the tunnels. It might be of some use " +
                    "to you, not sure what it opens. Sorry, but I have to go now.",
            )
            if (access.invAdd(access.inv, WROUGHT_IRON_KEY).success) {
                objbox(WROUGHT_IRON_KEY, "Ana gives you a wrought iron key...")
            }
        }
        access.mes("Ana spots Irena and waves...")
        quest.moveAna(player, ANA_GONE_HOME)
        npcSearch.find(player.coords, IRENA_HAPPY, IRENA_SEARCH_RADIUS, HuntVis.Off)?.say("Hi Ana!")
        delay(1)
        chatNpcSpecific(
            "Irena",
            IRENA_HAPPY_HEAD,
            happy,
            "Thank you very much for returning my daughter to me. I'm really very grateful... I " +
                "would like to reward you for your bravery and daring.",
        )
        chooseSkills()
    }

    private suspend fun Dialogue.reward() {
        val remaining = if (quest.stage(player) >= STAGE_PICKED_FIRST_SKILL) "one" else "two"
        chatNpc(
            happy,
            "Thank you very much for returning my daughter to me. I'm really very grateful... I " +
                "would like to reward you for your bravery and daring. I can offer you increased " +
                "knowledge in $remaining of the following areas.",
        )
        chooseSkills(askedAlready = true)
    }

    /**
     * Irena teaches two skills, one choice at a time; the stage records how many have been
     * chosen, so a player who walks off between them is offered only the one that is left.
     */
    private suspend fun Dialogue.chooseSkills(askedAlready: Boolean = false) {
        if (!askedAlready) {
            chatNpcSpecific(
                "Irena",
                IRENA_HAPPY_HEAD,
                happy,
                "I can offer you increased knowledge in two of the following areas.",
            )
        }
        while (quest.stage(player) < STAGE_COMPLETE - 1) {
            val skill =
                choice4(
                    "Fletching.",
                    "stat.fletching",
                    "Agility.",
                    "stat.agility",
                    "Smithing.",
                    "stat.smithing",
                    "Thieving",
                    "stat.thieving",
                )
            access.statAdvance(skill, SKILL_REWARD_XP)
            quest.advanceTo(access, quest.stage(player) + 1)
            if (quest.stage(player) == STAGE_PICKED_FIRST_SKILL) {
                chatNpcSpecific("Irena", IRENA_HAPPY_HEAD, happy, "Okay, now choose your second skill.")
            }
        }
        chatNpcSpecific("Irena", IRENA_HAPPY_HEAD, happy, "Okay, that's all the skills I can teach you!")
        quest.completeQuest(access)
    }

    private companion object {
        const val ANA_HEAD = "npc.ana"
        const val IRENA_HAPPY_HEAD = "npc.tourtrap_qip_irena_happy"
        const val IRENA_SEARCH_RADIUS = 8
    }
}
