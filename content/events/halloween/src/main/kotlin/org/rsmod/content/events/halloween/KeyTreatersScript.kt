package org.rsmod.content.events.halloween

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc4
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class KeyTreatersScript @Inject constructor(private val season: HalloweenSeason) : PluginScript() {
    override fun ScriptContext.startup() {
        for (treater in KeyTreater.entries) {
            for (npc in treater.npcs) {
                onOpNpc4(npc) { trickOrTreat(it.npc, treater) }
            }
        }
    }

    private suspend fun ProtectedAccess.trickOrTreat(npc: Npc, treater: KeyTreater) {
        if (!startTrickOrTreat(season, npc)) {
            return
        }
        val firstVisit = vars[treater.varbit] == 0
        startDialogue(npc) {
            if (firstVisit) {
                firstVisit(treater)
            } else {
                repeatVisit(treater)
            }
        }
        if (firstVisit) {
            vars[treater.varbit] = 1
            addTreats(KEY_TREATS)
        }
        startDialogue(npc) { pointToNextTreater() }
    }

    private suspend fun Dialogue.pointToNextTreater() {
        val remaining = KeyTreater.entries.filter { vars[it.varbit] == 0 }
        if (remaining.isEmpty()) {
            chatNpc(happy, "I believe you've visited the most generous of us!")
            chatNpc(
                happy,
                "The citizens are still as eager as ever, though, and I hear Varrock is the " +
                    "place to be for Trick-or-Treaters!",
            )
            return
        }
        chatNpc(happy, access.random.pick(remaining).pointer)
    }

    private suspend fun Dialogue.firstVisit(treater: KeyTreater) {
        when (treater) {
            KeyTreater.KingRoald -> kingRoaldFirst()
            KeyTreater.Aggie -> aggieFirst()
            KeyTreater.Hetty -> hettyFirst()
            KeyTreater.DukeHoracio -> dukeFirst()
            KeyTreater.PartyPete -> partyPeteFirst()
            KeyTreater.Iffie -> iffieFirst()
            KeyTreater.Ned -> nedFirst()
            KeyTreater.LumbridgeGuide -> guideFirst()
            KeyTreater.TownCrier -> townCrierFirst()
            KeyTreater.Elsie -> elsieFirst()
        }
    }

    private suspend fun Dialogue.repeatVisit(treater: KeyTreater) {
        when (treater) {
            KeyTreater.KingRoald -> kingRoaldRepeat()
            KeyTreater.Aggie -> aggieRepeat()
            KeyTreater.Hetty -> hettyRepeat()
            KeyTreater.DukeHoracio -> dukeRepeat()
            KeyTreater.PartyPete -> partyPeteRepeat()
            KeyTreater.Iffie -> iffieRepeat()
            KeyTreater.Ned -> nedRepeat()
            KeyTreater.LumbridgeGuide -> guideRepeat()
            KeyTreater.TownCrier -> townCrierRepeat()
            KeyTreater.Elsie -> elsieRepeat()
        }
    }

    private suspend fun Dialogue.kingRoaldFirst() {
        chatNpc(happy, "Greetings, Trick-or-Treater!")
        chatNpc(happy, "What a superb costume you've put together!")
        chatPlayer(happy, "Thank you, Your Majesty.")
        chatNpc(quiz, "Curious, isn't it?")
        chatPlayer(quiz, "What is?")
        chatNpc(neutral, "This Trick-or-Treating business.")
        chatNpc(neutral, "The people come to me and exclaim, 'trick or treat!'")
        chatNpc(
            angry,
            "As if to threaten me, their King, with petty tricks if I do not hand over treats!",
        )
        chatPlayer(quiz, "Would you prefer they just exclaim 'treat'?")
        chatNpc(
            neutral,
            "It'd certainly be more representative of the outcome, assuming they don't wish " +
                "to be locked up.",
        )
        chatPlayer(neutral, "You could trick them instead like some of the people are doing.")
        chatNpc(
            neutral,
            "Yes, I had noticed the tricks being played. Even my own guards are doing it!",
        )
        chatNpc(confused, "I'm not sure how, though...")
        chatPlayer(shifty, "Dark magic, perhaps!")
        chatNpc(shocked, "Dark magic? In my court?!")
        chatNpc(neutral, "Be a good adventurer and root it out, would you?")
        chatPlayer(neutral, "I'll keep an eye out, Sire.")
        chatPlayer(quiz, "But first, is there anyone else I should visit?")
    }

    private suspend fun Dialogue.kingRoaldRepeat() {
        chatNpc(quiz, "So, have you rooted out the Forces of Darkness?")
        chatPlayer(neutral, "I really don't thi-")
        chatNpc(shocked, "Wait! They could be listening at this very moment!")
        mesbox("King Roald starts talking louder.")
        chatNpc(happy, "It was lovely talking to you today, my loyal subject!")
        mesbox("King Roald winks at you.")
        chatPlayer(confused, "Okay...")
        chatPlayer(quiz, "Is anyone else giving out treats?")
    }

    private suspend fun Dialogue.aggieFirst() {
        chatNpc(happy, "My, what a fabulous costume!")
        chatNpc(happy, "I do love seeing all the colour combinations people use!")
        chatPlayer(quiz, "Maybe you could make some spooky dyes for everyone?")
        chatNpc(happy, "Now that is a good idea!")
        chatNpc(quiz, "A pumpkin dye, perhaps?")
        chatPlayer(quiz, "Wouldn't that just be orange dye?")
        chatNpc(laugh, "No silly, this would be a pumpkin dye!")
        chatNpc(neutral, "You know, for dyeing pumpkins.")
        chatPlayer(happy, "Oh, that does sound interesting!")
        chatNpc(happy, "I'd better get to work right away.")
        chatPlayer(neutral, "I'll leave you to it then.")
        chatPlayer(quiz, "Is there anyone else who'd like to see my costume?")
    }

    private suspend fun Dialogue.aggieRepeat() {
        chatNpc(happy, "Oh hello again!")
        chatNpc(happy, "You'll be pleased to hear my pumpkin dye is coming along wonderfully!")
        chatPlayer(happy, "That is good news! When will you be able to dye my pumpkins?")
        chatNpc(
            neutral,
            "As soon as I've figured out how to make it change the colour of the pumpkin...",
        )
        chatPlayer(quiz, "So you've not got very far at all, then?")
        chatNpc(neutral, "Well, I definitely know what doesn't work...")
        chatPlayer(neutral, "I suppose that's a start....")
        chatPlayer(quiz, "In the meantime, do you know who else would like to see my costume?")
    }

    private suspend fun Dialogue.hettyFirst() {
        chatNpc(happy, "Ah, the spookiest of seasons is upon us. My favourite!")
        chatNpc(quiz, "Perhaps a trick is what you seek?")
        chatPlayer(neutral, "Umm, no, the citizens of Gielinor are playing enough of those.")
        chatPlayer(neutral, "I was hoping you had some treats...")
        chatNpc(
            neutral,
            "Ah yes, they do appear to have tapped into some dark force they likely have no " +
                "business meddling with...",
        )
        chatPlayer(shocked, "What?")
        chatNpc(happy, "Oh I wouldn't worry, it's all good spooky fun!")
        mesbox("Hetty mutters to herself.")
        chatNpc(neutral, "...though I'd best keep an eye on it once the season ends.")
        chatPlayer(worried, "So it's not fine?")
        chatNpc(quiz, "Hmm?")
        chatNpc(
            neutral,
            "Oh, uh, I just find it interesting, that's all. I'm sure there will be a call for " +
                "adventurers if any are needed!",
        )
        chatNpc(happy, "For now, just enjoy the tricks and treats!")
        chatPlayer(neutral, "If you say so...")
        chatPlayer(quiz, "Is there anyone else I should spook?")
    }

    private suspend fun Dialogue.hettyRepeat() {
        chatNpc(happy, "Ah, if it isn't the spookiest Trick-or-Treater!")
        chatPlayer(quiz, "How goes the monitoring of the dark forces?")
        chatNpc(happy, "Oh very well indeed!")
        chatNpc(
            happy,
            "Everyone seems to be having a great time so I'd say it's been quite successful!",
        )
        chatPlayer(neutral, "That's good, I suppose...")
        chatPlayer(quiz, "Is anyone else giving out treats?")
    }

    private suspend fun Dialogue.dukeFirst() {
        chatNpc(happy, "Treat, treat!")
        chatNpc(happy, "With all these tricks being played I think treats are in order!")
        chatPlayer(quiz, "Do you not like the tricks?")
        chatNpc(happy, "Oh, I appreciate a good trick as much as anyone!")
        chatNpc(happy, "But I also like to balance out the tricks with plenty of treats!")
        chatPlayer(neutral, "You must be getting a lot of visitors.")
        chatNpc(
            happy,
            "Indeed! It is wonderful to see so many visitors to Lumbridge in their costumes.",
        )
        chatPlayer(quiz, "Speaking of visiting places, is there anyone else I should visit?")
    }

    private suspend fun Dialogue.dukeRepeat() {
        chatNpc(happy, "Hello again!")
        chatNpc(neutral, "I'm afraid you've had treats from me, but please, no tricks!")
        chatPlayer(neutral, "That's okay, I'm just looking for treats.")
        chatPlayer(quiz, "Is anyone else giving them out?")
    }

    private suspend fun Dialogue.partyPeteFirst() {
        chatNpc(happy, "More Trick-or-Treaters, woohoo!")
        chatPlayer(quiz, "Enjoying the spooky holiday, Pete?")
        chatNpc(
            happy,
            "Definitely! All these costumes, tricks and, of course, treats are so much fun!",
        )
        chatPlayer(happy, "Maybe you should host a spooky party here!")
        chatNpc(happy, "Now that is a spooktacular idea!")
        chatNpc(neutral, "It'd need to be the right balance of frightening and fun.")
        chatPlayer(laugh, "Frightfully fun, you could say!")
        chatNpc(happy, "Now you're thinking like a party planner!")
        chatPlayer(neutral, "Well, I'll leave you to your planning.")
        chatPlayer(quiz, "Do you know if anyone else is looking for some trick-or-treating fun?")
    }

    private suspend fun Dialogue.partyPeteRepeat() {
        chatNpc(quiz, "What about a trick AND treat?")
        chatPlayer(happy, "Maybe that can be in your frightfully fun party!")
        chatNpc(happy, "What a party that will be!")
        chatPlayer(quiz, "When do you think you'll be ready?")
        chatNpc(neutral, "A party like this cannot be rushed!")
        chatNpc(neutral, "Plus I want to see if I can get some special guests.")
        chatPlayer(quiz, "Like who?")
        chatNpc(happy, "Someone spooky, like Death himself!")
        chatPlayer(quiz, "Do you think he'd be willing?")
        chatNpc(
            happy,
            "I've heard he's done similar appearances before, so I'm sure he'd be up for it!",
        )
        chatPlayer(happy, "I can't wait to see that.")
        chatPlayer(quiz, "Until then, is anyone else looking for some trick-or-treating fun?")
    }

    private suspend fun Dialogue.iffieFirst() {
        chatNpc(happy, "Oooh dearie! What a beautiful costume!")
        chatNpc(laugh, "I would have jumped out of my skin if I could still jump.")
        chatNpc(quiz, "Say, while you're here, would you care for a scary story?")
        chatPlayer(happy, "Sure, why not!")
        chatNpc(
            neutral,
            "We have bowls of treats we keep to treat Trick-or-Treaters. We left the bowls " +
                "outside the shop so the Trick-or-Treaters wouldn't come in and frighten the " +
                "customers.",
        )
        chatNpc(
            neutral,
            "However we found the treats vanishing in an instant, as if a greedy passer-by " +
                "would help themselves without leaving any for anyone else.",
        )
        chatNpc(
            angry,
            "That's not very polite if you ask me. I was ready to be up in arms about today's " +
                "youth, but I thought it would be prudent to witness the thief first.",
        )
        chatPlayer(shocked, "That sounds dangerous! What did you do?")
        chatNpc(
            neutral,
            "We refilled the bowls and waited, but we must have been too visible. Everyone " +
                "was behaving perfectly. We went to sit back and the sweets vanished once again!",
        )
        chatPlayer(quiz, "Was it ghosts? I bet it was ghosts.")
        chatNpc(
            neutral,
            "We refilled again and this time hid very carefully. For a while nothing happened. " +
                "Charlie came by, but he only took a couple of sweets before strolling back.",
        )
        chatNpc(
            shocked,
            "Then when we least expected it, the most unsettling monster appeared from behind " +
                "the shop, crammed its head into one of the bowls and wolfed the whole contents " +
                "out!",
        )
        chatPlayer(shocked, "Oh no! What did it look like?")
        chatNpc(
            worried,
            "Knee-height, eyes as dark as the night, a large gaping mouth, four legs, skin " +
                "peeling and covered in fleas!",
        )
        chatNpc(
            laugh,
            "Eventually we understood that someone had been putting Halloween costumes on the " +
                "hungry stray dogs! We now keep the bowls where we can see them!",
        )
        chatPlayer(laugh, "Oh, I was not expecting that plot twist!")
        chatPlayer(
            happy,
            "That was a lovely story, Iffie. Before I take my leave, is there anyone else I " +
                "should visit?",
        )
    }

    private suspend fun Dialogue.iffieRepeat() {
        chatNpc(quiz, "So, did you spot the spookiest stray dogs?")
        chatPlayer(neutral, "I know they lurk around here and am in no rush to find them!")
        chatNpc(happy, "I saw one dressed as a huge spider earlier!")
        chatNpc(happy, "I can add that to my list of jumpers I ought to try knitting.")
        chatPlayer(
            shocked,
            "What, a jumper featuring a dog dressed as a spider? That sounds ambitious!",
        )
        chatNpc(happy, "Or maybe just spider-themed. Maybe next year, who knows!")
        chatPlayer(quiz, "While I'm here, do you know of anyone else giving out treats?")
    }

    private suspend fun Dialogue.nedFirst() {
        chatNpc(happy, "Aye, what a good costume!")
        chatNpc(happy, "That would scare any old sailor, and I've seen my fair share of things!")
        chatPlayer(quiz, "Do you get much business around Halloween?")
        chatNpc(
            neutral,
            "Actually, not too bad! For the most part they need some ropes for tying hay bales.",
        )
        chatNpc(neutral, "But the bulk of my business at the moment is sailing rigs.")
        chatPlayer(quiz, "Do they require particular maintenance at this time of year?")
        chatNpc(
            neutral,
            "No, but I have heard that they are considering allowing less experienced sailors " +
                "to sail the seas!",
        )
        chatNpc(
            laugh,
            "I think it'll just mean an increase of shipwrecks, if you ask me! But it is good " +
                "for my business.",
        )
        chatPlayer(happy, "Oh, that does sound interesting! I wonder if they'll let me?")
        chatNpc(neutral, "Maybe in a year or so? Meanwhile, I'd better get to work right away.")
        chatPlayer(neutral, "I'll leave you to it then.")
        chatPlayer(quiz, "Is there anyone else who'd like to see my costume?")
    }

    private suspend fun Dialogue.nedRepeat() {
        chatNpc(happy, "Oh hello again!")
        chatNpc(quiz, "Here for some rope or to show me the results of your sweet plundering?")
        chatPlayer(
            angry,
            "I have not been plundering, I have been Trick-or-Treating properly!",
        )
        chatPlayer(
            neutral,
            "People have been kind to me, for the most part! I might have been victim of the " +
                "odd trick myself.",
        )
        chatNpc(happy, "That sounds like quite a tale! Maybe for some other time!")
        chatPlayer(
            quiz,
            "Maybe! In the meantime, do you know who else would like to see my costume?",
        )
    }

    private suspend fun Dialogue.guideFirst() {
        chatNpc(
            happy,
            "Greetings, adventurer! Are you looking for advice or are you looking for treats?",
        )
        chatPlayer(quiz, "Can you guess from looking at my outfit?")
        chatNpc(
            neutral,
            "I have seen adventurers sporting a wide range of interesting get-ups no matter " +
                "the season.",
        )
        chatNpc(happy, "This said, it's the season to be spooky and you do a fine job of it!")
        chatNpc(
            neutral,
            "I will not ask whether this is also your choice of clothes the rest of the year.",
        )
        chatNpc(happy, "After all, you are more than welcome to dress how you like, when you like.")
        chatPlayer(happy, "That is very accepting of you!")
        chatNpc(
            neutral,
            "I meet a lot of people in my line of work and hear a lot of stories. Believe it or " +
                "not, people tell stories of adventures, past and future, just by the way they " +
                "dress.",
        )
        chatPlayer(quiz, "And what does my outfit say?")
        chatNpc(laugh, "That you are very interested in gathering sweet treats!")
        chatPlayer(
            laugh,
            "You're spot on! While we're on the topic, is there anyone else I should visit?",
        )
    }

    private suspend fun Dialogue.guideRepeat() {
        chatNpc(happy, "Hello again! Are you looking for advice or are you looking for treats?")
        chatPlayer(happy, "I would take more treats if you're offering!")
        chatNpc(
            neutral,
            "I should have expected this! I will have to admit that my treat supply is limited " +
                "and I have many more adventurers to reward.",
        )
        chatPlayer(
            neutral,
            "I suppose that is fair enough. In that case, I will take your advice to help me " +
                "find someone else to visit!",
        )
    }

    private suspend fun Dialogue.townCrierFirst() {
        chatNpc(happy, "Hear ye! Hear ye! Come and check out the spookiest costume in town!")
        chatPlayer(quiz, "Oh, where?")
        chatNpc(
            laugh,
            "Yours, of course, unless you tell me this isn't supposed to be a costume?",
        )
        chatPlayer(happy, "Oh it is! I am flattered!")
        chatNpc(happy, "Then you hopefully won't mind me calling everyone to see it!")
        chatPlayer(
            neutral,
            "I appreciate the attention, although I was just really hoping for some treats!",
        )
        chatNpc(
            happy,
            "Straight to the point I see. Fine, please take these with my thanks! Have a happy " +
                "Halloween!",
        )
        chatPlayer(
            happy,
            "Thank you! While I'm here, can you think of anyone else I should spook?",
        )
    }

    private suspend fun Dialogue.townCrierRepeat() {
        chatNpc(happy, "Ah, if it isn't the spookiest Trick-or-Treater!")
        chatPlayer(quiz, "Have you seen any better costumes?")
        chatNpc(quiz, "Why, are you looking for inspiration for next year?")
        chatPlayer(shifty, "Maybe? Possibly?")
        chatNpc(
            happy,
            "I've seen quite a few good costumes but I still liked yours a lot. So as for next " +
                "year, who can tell? Not me!",
        )
        chatPlayer(quiz, "Fair enough then! Is anyone else giving out treats?")
    }

    private suspend fun Dialogue.elsieFirst() {
        chatNpc(
            happy,
            "Oooh! What a lovely costume! You simply will have to show my sister Iffie later.",
        )
        chatPlayer(quiz, "I somehow did not think Halloween was a holiday you'd enjoy.")
        chatNpc(
            happy,
            "Well dearie, appearances can be deceiving! Let me tell you a little story of my " +
                "youth.",
        )
        chatNpc(
            neutral,
            "My sister and I have always loved knitting. Between Christmas jumpers and other " +
                "woolly scarves, we have a little family tradition.",
        )
        chatNpc(
            laugh,
            "As a way to challenge ourselves, when we were a bit younger, we would knit the " +
                "most horrifying Halloween costumes, then wear them and scare passers-by out " +
                "of their wits!",
        )
        chatPlayer(quiz, "What kind of costumes are we talking about?")
        chatNpc(
            happy,
            "Iffie's favourite costume represented the Chaos Elemental. Mine was a fearsome " +
                "three-headed dragon!",
        )
        chatPlayer(laugh, "Sorry, but I have to see it to believe it!")
        chatNpc(
            sad,
            "Sorry dearie! We were banned from wearing those years ago because of the mass " +
                "hysteria they caused! I reused the yarn to make mittens for the church sale.",
        )
        chatNpc(
            happy,
            "Anyway, thank you for taking the time to listen to my silly story. Have some " +
                "well-deserved treats. They're not all mints, I promise.",
        )
        chatPlayer(
            happy,
            "Thank you Elsie! Do you know if anyone else is looking for some trick-or-treating " +
                "fun?",
        )
    }

    private suspend fun Dialogue.elsieRepeat() {
        chatNpc(happy, "Hello dearie, are you here for another tale?")
        chatPlayer(neutral, "I suppose I could lend an ear.")
        chatNpc(
            neutral,
            "Ok, a little short one then. One day a very kind adventurer came and brought me a " +
                "warm cup of tea.",
        )
        chatNpc(
            happy,
            "It's always nice to have a warm cup of tea, you know. Keeps the fingers nimble.",
        )
        chatNpc(
            shocked,
            "I was just telling them one of my stories when suddenly the biggest spider I had " +
                "ever seen appeared in front of my eyes!",
        )
        chatPlayer(quiz, "How big are we talking?")
        chatNpc(
            worried,
            "A spider that took the entirety of my field of vision! Bigger and fuzzier still " +
                "than the ones you get near here.",
        )
        chatPlayer(quiz, "Did the adventurer not help you out?")
        chatNpc(
            laugh,
            "Unfortunately, they were busy being covered in tea. I had become rather startled, " +
                "you see! Thankfully, all that movement caused the tiny spider to fall off my " +
                "glasses!",
        )
        chatPlayer(
            laugh,
            "I don't know if I would prefer a large spider far away or a small spider on my face!",
        )
        chatPlayer(
            quiz,
            "I won't keep you any longer Elsie, but before I go, do you know of anyone that " +
                "might have treats left?",
        )
    }

    private companion object {
        const val KEY_TREATS = 100
    }
}
