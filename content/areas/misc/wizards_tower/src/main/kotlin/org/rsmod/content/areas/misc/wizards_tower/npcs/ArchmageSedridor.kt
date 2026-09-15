package org.rsmod.content.areas.misc.wizards_tower.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest.Companion.AIR_TALISMAN
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest.Companion.RESEARCH_NOTES
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest.Companion.RESEARCH_PACKAGE
import org.rsmod.content.quest.area.lumbridge.rmBackstory
import org.rsmod.content.quest.area.lumbridge.rmKnowName
import org.rsmod.content.quest.area.lumbridge.rmKnowOthers
import org.rsmod.content.quest.area.lumbridge.rmNotesGiven
import org.rsmod.content.quest.area.lumbridge.rmOwedTalisman
import org.rsmod.content.quest.area.lumbridge.rmPackage
import org.rsmod.content.quest.area.lumbridge.rmTalismanGiven
import org.rsmod.content.skills.runecrafting.essence.EssenceMineTeleporter
import org.rsmod.content.skills.runecrafting.essence.RuneEssenceTeleports
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ArchmageSedridor
@Inject
constructor(
    private val runeMysteries: RuneMysteriesQuest,
    private val teleports: RuneEssenceTeleports,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.head_wizard") { startDialogue(it.npc) { sedridorDialogue(it.npc) } }
        onOpNpc3("npc.head_wizard") { teleport(it.npc) }
    }

    private suspend fun ProtectedAccess.teleport(npc: Npc) {
        if (!runeMysteries.isComplete(player)) {
            return
        }
        teleports.teleportToMine(this, npc, EssenceMineTeleporter.Sedridor)
    }

    private suspend fun Dialogue.sedridorDialogue(npc: Npc) {
        when (runeMysteries.stage(player)) {
            0 -> beforeQuest()
            RuneMysteriesQuest.STAGE_TALISMAN -> talismanDelivery()
            RuneMysteriesQuest.STAGE_TALISMAN_GIVEN -> {
                chatPlayer(quiz, "So is that talisman of any use to you?")
                examineTalisman()
            }
            RuneMysteriesQuest.STAGE_PACKAGE -> packageFollowUp()
            RuneMysteriesQuest.STAGE_PACKAGE_DELIVERED -> awaitingAubury()
            RuneMysteriesQuest.STAGE_NOTES -> receiveNotes(npc)
            else -> afterQuest(npc)
        }
    }

    private suspend fun Dialogue.beforeQuest() {
        chatNpc(
            happy,
            "Welcome adventurer, to the world renowned Wizards' Tower, home to the Order of " +
                "Wizards. How may I help you?",
        )
        chatPlayer(neutral, "I'm just looking around.")
        chatNpc(
            neutral,
            "Well, take care adventurer. You stand on the ruins of the old destroyed Wizards' " +
                "Tower. Strange and powerful magicks lurk here.",
        )
    }

    private suspend fun Dialogue.talismanDelivery() {
        if (player.rmTalismanGiven) {
            chatNpc(quiz, "Welcome back, adventurer. Do you have that talisman now?")
            if (AIR_TALISMAN !in player.inv) {
                chatPlayer(sad, "Not yet.")
                chatNpc(neutral, "Well come back when you have it.")
                return
            }
            chatPlayer(happy, "Here you go.")
            handOverTalisman()
            return
        }

        chatNpc(
            happy,
            "Welcome adventurer, to the world renowned Wizards' Tower, home to the Order of " +
                "Wizards. We are the oldest and most prestigious group of wizards around. Now, " +
                "how may I help you?",
        )
        chatPlayer(quiz, "Are you Sedridor?")
        chatNpc(quiz, "Sedridor? What is it you want with him?")
        chatPlayer(
            neutral,
            "The Duke of Lumbridge sent me to find him. I have this talisman he found. He said " +
                "Sedridor would be interested in it.",
        )
        chatNpc(
            neutral,
            "Did he now? Well hand it over then, and we'll see what all the hubbub is about.",
        )
        when (choice2("Okay, here you are.", 1, "No, I'll only give it to Sedridor.", 2)) {
            1 -> offerTalisman()
            2 -> {
                chatPlayer(neutral, "No, I'll only give it to Sedridor.")
                chatNpc(
                    happy,
                    "Well good news, for I am Sedridor! Now, hand it over and let me have a " +
                        "proper look at it, hmm?",
                )
                when (
                    choice2("Okay, here you are.", 1, "No, I don't think you are Sedridor.", 2)
                ) {
                    1 -> offerTalisman()
                    2 -> doubtIdentity()
                }
            }
        }
    }

    private suspend fun Dialogue.doubtIdentity() {
        chatPlayer(neutral, "No, I don't think you are Sedridor.")
        chatNpc(
            neutral,
            "Hmm... Well, I admire your caution adventurer. Perhaps I can prove myself? I will " +
                "use my mental powers to discover...",
        )
        chatNpc(neutral, "Your name is... ${player.displayName}!")
        player.rmKnowName = true
        chatPlayer(shocked, "You're right! How did you know that?")
        chatNpc(
            laugh,
            "Well I am the Archmage you know! You don't get to my position without learning a " +
                "few tricks along the way!",
        )
        chatNpc(
            quiz,
            "So now that I have proved myself to you, why don't you hand over that talisman, hmm?",
        )
        chatPlayer(neutral, "Okay, here you are.")
        offerTalisman(alreadySaid = true)
    }

    private suspend fun Dialogue.offerTalisman(alreadySaid: Boolean = false) {
        if (!alreadySaid) {
            chatPlayer(neutral, "Okay, here you are.")
        }
        if (AIR_TALISMAN !in player.inv) {
            player.rmTalismanGiven = true
            chatNpc(neutral, "...")
            chatPlayer(neutral, "...")
            chatNpc(quiz, "Well?")
            chatPlayer(sad, "I don't seem to have it with me.")
            chatNpc(confused, "Hmm? You are a very odd person. Come back again when you have it.")
            return
        }
        handOverTalisman()
    }

    private suspend fun Dialogue.handOverTalisman() {
        if (access.invDel(access.inv, AIR_TALISMAN).failure) {
            return
        }
        player.rmTalismanGiven = true
        runeMysteries.advanceTo(access, RuneMysteriesQuest.STAGE_TALISMAN_GIVEN)
        objbox(AIR_TALISMAN, "You hand the talisman to Sedridor.")
        examineTalisman()
    }

    private suspend fun Dialogue.examineTalisman() {
        chatNpc(
            neutral,
            "Hmm... Doesn't seem to be anything too special. Just a normal air talisman by the " +
                "looks of things. Still, looks can be deceiving. Let me take a closer look...",
        )
        objbox(AIR_TALISMAN, "Sedridor murmurs some sort of incantation and the talisman glows slightly.")
        chatNpc(
            shocked,
            "How interesting... It would appear I spoke too soon. There's more to this talisman " +
                "than meets the eye. In fact, it may well be the last piece of the puzzle.",
        )
        chatPlayer(quiz, "Puzzle?")
        chatNpc(
            happy,
            "Indeed! The lost legacy of the first tower. This talisman may in fact be key to " +
                "finding the forgotten essence mine!",
        )
        chatPlayer(confused, "First tower? Forgotten essence mine? What are you on about?")
        chatNpc(neutral, "Ah, my apologies, adventurer. Allow me to fill you in.")
        when (choice2("Go ahead.", 1, "Actually, I'm not interested.", 2)) {
            1 -> backstory()
            2 -> {
                chatPlayer(neutral, "Actually, I'm not interested.")
                chatNpc(
                    sad,
                    "Oh... Well I guess the short of it is that this talisman could be key to " +
                        "helping us rediscover an important teleportation incantation.",
                )
                chatNpc(
                    neutral,
                    "With it, we'll be able to access a hidden essence mine, our lost source of " +
                        "rune essence.",
                )
                player.rmBackstory = true
                askToVisitAubury()
            }
        }
    }

    private suspend fun Dialogue.backstory() {
        chatPlayer(neutral, "Go ahead.")
        chatNpc(
            neutral,
            "As you are likely aware, when we cast spells, we do so using the power of runes.",
        )
        chatNpc(
            neutral,
            "These runes are crafted from a highly unique material, and then imbued with magical " +
                "power from various runic altars. Different altars create different runes with " +
                "different magical effects.",
        )
        chatNpc(
            neutral,
            "The process of imbuing runes is called runecrafting. Legend has it that this was " +
                "once a common art, but the secrets of how to do it were lost until just under " +
                "two hundred years ago.",
        )
        chatNpc(
            neutral,
            "The rediscovery of runecrafting had such a large impact on the world, that it " +
                "marked the dawn of the Fifth Age. It also resulted in the birth of our order, " +
                "and the construction of the first Wizards' Tower.",
        )
        chatPlayer(
            quiz,
            "If it was the first tower, I'm guessing it doesn't exist anymore? What happened?",
        )
        chatNpc(
            angry,
            "It was burnt down by traitorous members of our own order. They followed the evil " +
                "god of chaos, Zamorak, and they wished to claim our magical discoveries in his " +
                "name.",
        )
        chatNpc(
            sad,
            "When the tower burnt down, much was lost, including an important incantation. A " +
                "spell that could be used to teleport to a hidden essence mine.",
        )
        chatPlayer(quiz, "The essence mine you mentioned earlier, I assume?")
        chatNpc(
            neutral,
            "Precisely. Rune essence is the material used to make runes, but it is incredibly " +
                "rare. That essence mine was the only place it could be found that our order " +
                "knew of.",
        )
        chatNpc(
            sad,
            "Since the incantation was lost, we have struggled to maintain our stocks of rune " +
                "essence.",
        )
        chatNpc(
            neutral,
            "There are seemingly those out there that still know where to find some, but while " +
                "they have been willing to sell essence to us, they have refused to share " +
                "knowledge on how to find it ourselves.",
        )
        chatPlayer(
            quiz,
            "I'm starting to see why this is so important. So you think this talisman can help " +
                "you rediscover that incantation?",
        )
        chatNpc(
            happy,
            "I do! All magic leaves traces, and from what I can tell, this talisman was used " +
                "heavily during the time of the first tower.",
        )
        chatNpc(
            neutral,
            "It would have been taken to the essence mine many times, and the magical energies " +
                "there will have left an imprint on it. To think that it was hidden in Lumbridge " +
                "all this time!",
        )
        player.rmBackstory = true
        chatPlayer(quiz, "So what happens now?")
        askToVisitAubury()
    }

    private suspend fun Dialogue.askToVisitAubury() {
        chatNpc(
            neutral,
            "It is critical I share this discovery with my associate, Aubury, as soon as " +
                "possible. He's not much of a wizard, but he's an expert on runecrafting, and his " +
                "insight will be essential.",
        )
        chatNpc(
            quiz,
            "Would you be willing to visit him for me? I would go myself, but I wish to study " +
                "this talisman some more.",
        )
        when (choice2("Yes, certainly.", 1, "No, I'm busy.", 2)) {
            1 -> {
                chatPlayer(happy, "Yes, certainly.")
                runeMysteries.advanceTo(access, RuneMysteriesQuest.STAGE_PACKAGE)
                chatNpc(
                    happy,
                    "He runs a rune shop in the south east of Varrock. Please, take this package " +
                        "of research notes to him. If all goes well, the secrets of the essence " +
                        "mine may soon be ours once more!",
                )
                handOverPackage()
            }
            2 -> {
                chatPlayer(neutral, "No, I'm busy.")
                chatNpc(
                    neutral,
                    "As you wish adventurer. I will continue to study this talisman you have " +
                        "brought me. Return here if you find yourself with some spare time to " +
                        "help me.",
                )
            }
        }
    }

    private suspend fun Dialogue.packageFollowUp() {
        if (!player.rmPackage) {
            chatNpc(
                neutral,
                "Hello again, adventurer. Please, take this package of research notes to Aubury " +
                    "in Varrock. He runs a rune shop in the south east of the city.",
            )
            handOverPackage()
            return
        }
        chatNpc(quiz, "Hello again, adventurer. Did you take that package to Aubury?")
        if (RESEARCH_PACKAGE in player.inv) {
            chatPlayer(neutral, "Not yet.")
            chatNpc(
                neutral,
                "He runs a rune shop in the south east of Varrock. Please deliver it to him soon.",
            )
            return
        }
        chatPlayer(sad, "I lost it. Could I have another?")
        chatNpc(neutral, "Well it's a good job I have copies of everything.")
        removeBankedCopies(RESEARCH_PACKAGE)
        handOverPackage()
    }

    private suspend fun Dialogue.handOverPackage() {
        if (access.invAdd(access.inv, RESEARCH_PACKAGE).failure) {
            player.rmPackage = false
            mesbox("You don't have enough inventory space to take the package.")
            return
        }
        player.rmPackage = true
        objbox(RESEARCH_PACKAGE, "Sedridor hands you a package.")
        chatNpc(happy, "Best of luck, ${player.displayName}.")
        if (!player.rmKnowName) {
            chatPlayer(confused, "I don't remember telling you my name... How do you know it?")
            chatNpc(laugh, "Really now? I am the Archmage you know.")
        }
    }

    private suspend fun Dialogue.awaitingAubury() {
        chatNpc(
            quiz,
            "Ah, ${player.displayName}. How goes your quest? Have you delivered my research to " +
                "Aubury yet?",
        )
        chatPlayer(neutral, "Yes, I have. He's still looking through it.")
        chatNpc(
            neutral,
            "Then go and speak to him again. I'm eager to hear what he makes of it.",
        )
    }

    private suspend fun Dialogue.receiveNotes(npc: Npc) {
        if (player.rmNotesGiven) {
            readNotes(npc)
            return
        }
        chatNpc(
            quiz,
            "Ah, ${player.displayName}. How goes your quest? Have you delivered my research to " +
                "Aubury yet?",
        )
        chatPlayer(happy, "Yes, I have. He gave me some notes to give to you.")
        chatNpc(happy, "Wonderful! Let's have a look then.")
        if (access.invDel(access.inv, RESEARCH_NOTES).failure) {
            chatPlayer(sad, "Err, you're not going to believe this...")
            chatNpc(quiz, "What?")
            chatPlayer(sad, "I don't have them.")
            chatNpc(
                angry,
                "Right... You're rather careless aren't you. I suggest you go and speak to " +
                    "Aubury once more. With luck he will have made copies.",
            )
            return
        }
        player.rmNotesGiven = true
        objbox(RESEARCH_NOTES, "You hand the notes to Sedridor.")
        readNotes(npc)
    }

    private suspend fun Dialogue.readNotes(npc: Npc) {
        chatNpc(neutral, "Alright, let's see what Aubury has for us...")
        chatNpc(shocked, "Yes, this is it! The lost incantation!")
        chatPlayer(quiz, "So you'll be able to access that essence mine now?")
        chatNpc(
            happy,
            "That's right! Because of you, our order finally has a proper source of rune " +
                "essence again! Thank you, friend.",
        )
        chatNpc(
            happy,
            "If you ever want to access the essence mine yourself, just let me know. It's the " +
                "least I can do.",
        )
        chatNpc(
            neutral,
            "I will also share the incantation with others, including Aubury. When I do, I'll " +
                "let them know that you are to be given unlimited access to the mine.",
        )
        chatNpc(
            neutral,
            "Oh, and you can have this air talisman back as well. I have no further need of it, " +
                "and I'm sure you will find it useful.",
        )
        if (access.invAdd(access.inv, AIR_TALISMAN).failure) {
            player.rmOwedTalisman = true
            mesbox(
                "You don't have enough inventory space for the talisman. Sedridor will keep it " +
                    "for you until you do.",
            )
        } else {
            objbox(AIR_TALISMAN, "Sedridor hands you an air talisman.")
        }
        chatNpc(
            neutral,
            "In case you didn't know, the talisman can be used to craft air runes. Just take it " +
                "to the Air Altar south of Falador along with some rune essence.",
        )
        chatNpc(
            neutral,
            "Don't worry if you can't find the altar. The talisman can guide you there. You may " +
                "find talismans for other altars as well while adventuring. They'll let you " +
                "craft other types of rune.",
        )
        chatPlayer(happy, "Great! Thanks!")
        chatNpc(happy, "My pleasure!")
        runeMysteries.advanceTo(access, RuneMysteriesQuest.STAGE_COMPLETE)
        afterQuestOptions(npc, leaveOption = "I'd better get going.")
    }

    private suspend fun Dialogue.afterQuest(npc: Npc) {
        chatPlayer(neutral, "Hello there.")
        chatNpc(neutral, "Hello again, ${player.displayName}. What can I do for you?")
        if (player.rmOwedTalisman) {
            returnOwedTalisman()
        }
        afterQuestOptions(npc, leaveOption = "Nothing thanks, I'm just looking around.")
    }

    private suspend fun Dialogue.returnOwedTalisman() {
        chatNpc(neutral, "Ah, before I forget - I still have that air talisman for you.")
        if (access.invAdd(access.inv, AIR_TALISMAN).failure) {
            mesbox("You don't have enough inventory space for the talisman.")
            return
        }
        player.rmOwedTalisman = false
        objbox(AIR_TALISMAN, "Sedridor hands you an air talisman.")
    }

    private suspend fun Dialogue.afterQuestOptions(npc: Npc, leaveOption: String) {
        val choice =
            choice4(
                "Can you teleport me to the Rune Essence Mine?",
                1,
                "Who else knows the teleport to the Rune Essence Mine?",
                2,
                "Could you tell me about the old Wizards' Tower?",
                3,
                leaveOption,
                4,
            )
        when (choice) {
            1 -> teleportFromDialogue(npc)
            2 -> otherTeleporters(npc)
            3 -> {
                oldTowerStory()
                afterQuestOptions(npc, leaveOption)
            }
            4 -> {
                chatPlayer(neutral, leaveOption)
                if (leaveOption.startsWith("Nothing")) {
                    chatNpc(
                        neutral,
                        "Well, take care. You stand on the ruins of the old destroyed Wizards' " +
                            "Tower. Strange and powerful magicks lurk here.",
                    )
                }
            }
        }
    }

    private suspend fun Dialogue.teleportFromDialogue(npc: Npc) {
        chatPlayer(quiz, "Can you teleport me to the Rune Essence Mine?")
        teleports.teleportToMine(access, npc, EssenceMineTeleporter.Sedridor)
    }

    private suspend fun Dialogue.otherTeleporters(npc: Npc) {
        chatPlayer(quiz, "Who else knows the teleport to the Rune Essence Mine?")
        player.rmKnowOthers = true
        chatNpc(
            neutral,
            "Apart from myself, there's also Aubury in Varrock, Wizard Cromperty in East " +
                "Ardougne, Brimstail in the Tree Gnome Stronghold and Wizard Distentor in " +
                "Yanille's Wizards' Guild.",
        )
        val choice =
            choice3(
                "Can you teleport me to the Rune Essence Mine?",
                1,
                "Could you tell me about the old Wizards' Tower?",
                2,
                "Thanks for the information.",
                3,
            )
        when (choice) {
            1 -> teleportFromDialogue(npc)
            2 -> {
                oldTowerStory()
                afterQuestOptions(npc, leaveOption = "Nothing thanks, I'm just looking around.")
            }
            3 -> {
                chatPlayer(happy, "Thanks for the information.")
                chatNpc(happy, "My pleasure.")
            }
        }
    }

    private suspend fun Dialogue.oldTowerStory() {
        chatPlayer(quiz, "Could you tell me about the old Wizards' Tower?")
        chatNpc(
            neutral,
            "Of course. The first Wizards' Tower was built at the same time the Order of Wizards " +
                "was founded. It was at the dawn of the Fifth Age, when the secrets of " +
                "runecrafting were rediscovered.",
        )
        chatNpc(
            neutral,
            "For years, the tower was a hub of magical research. Wizards of all races and " +
                "religions were welcomed into our order.",
        )
        chatNpc(
            sad,
            "Alas, that openness is what ultimately led to disaster. The wizards who served " +
                "Zamorak, the evil god of chaos, tried to claim our magical discoveries in his " +
                "name.",
        )
        chatNpc(
            angry,
            "They failed, but in retaliation, they burnt the entire tower to the ground. Years " +
                "of work was destroyed.",
        )
        chatNpc(
            neutral,
            "The tower was soon rebuilt of course, but even now we are still trying to regain " +
                "knowledge that was lost.",
        )
        chatNpc(
            neutral,
            "That's why I spend my time down here, in fact. This basement is all that is left of " +
                "the old tower, and I believe there are still some secrets to discover here.",
        )
        chatNpc(
            happy,
            "Of course, one secret I am no longer looking for is the teleportation incantation " +
                "to the Rune Essence Mine. We have you to thank for that.",
        )
    }

    private fun Dialogue.removeBankedCopies(obj: String) {
        val banked = access.bank.count(obj)
        if (banked > 0) {
            access.invDel(access.bank, obj, banked)
        }
    }
}
