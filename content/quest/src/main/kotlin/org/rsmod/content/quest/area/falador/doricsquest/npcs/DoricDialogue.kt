package org.rsmod.content.quest.area.falador.doricsquest.npcs

import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.quest.area.falador.doricsquest.DoricsQuest
import org.rsmod.content.quest.area.falador.doricsquest.DoricsQuest.Companion.BRONZE_PICKAXE
import org.rsmod.content.quest.area.falador.doricsquest.DoricsQuest.Companion.COPPER_ORE

/**
 * Doric's conversations, shared by talking to him and by trying his anvils. When no [Dialogue.npc]
 * is attached (the anvil), he speaks through his chathead without turning.
 */
@Singleton
class DoricDialogue
@Inject
constructor(private val dorics: DoricsQuest, private val objRepo: ObjRepository) {

    suspend fun Dialogue.talk() {
        when {
            dorics.isComplete(player) -> afterQuest()
            dorics.isStarted(player) -> materialsCheck()
            else -> beforeQuest()
        }
    }

    suspend fun Dialogue.anvilRefusal() {
        if (dorics.isStarted(player)) {
            materialsCheck()
            return
        }
        doric(angry, "Hey, who said you could use that? My anvils get enough work with my own use. I make pickaxes, and it takes a lot of hard work.")
        when (
            choice2(
                "Sorry, would it be OK if I used your anvils?", 1,
                "I didn't want to use your anvils anyway.", 2,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Sorry, would it be OK if I used your anvils?")
                doric(neutral, "If you could get me some more materials then I could let you use them.")
                when (
                    choice2(
                        "Yes, I will get you materials.", 1,
                        "No, hitting rocks is for the boring people, sorry.", 2,
                    )
                ) {
                    1 -> acceptQuest()
                    2 -> declineQuest()
                }
            }
            2 -> {
                chatPlayer(angry, "I didn't want to use your anvils anyway.")
                doric(neutral, "That is your choice.")
            }
        }
    }

    private suspend fun Dialogue.beforeQuest() {
        doric(happy, "Hello traveller, what brings you to my humble smithy?")
        when (
            choice5(
                "I wanted to use your anvils.", 1,
                "I want to use your whetstone.", 2,
                "Mind your own business, shortstuff!", 3,
                "I was just checking out the landscape.", 4,
                "What do you make here?", 5,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I wanted to use your anvils.")
                doric(neutral, "My anvils get enough work with my own use. I make pickaxes, and it takes a lot of hard work. If you could get me some more materials, then I could let you use them.")
                offerQuest()
            }
            2 -> {
                chatPlayer(neutral, "I wanted to use your whetstone.")
                doric(neutral, "The whetstone is for more advanced smithing, but I could let you use it as well as my anvils if you could get me some more materials.")
                offerQuest()
            }
            3 -> {
                chatPlayer(angry, "Mind your own business, shortstuff!")
                doric(angry, "How nice to meet someone with such pleasant manners. Do come again when you need to shout at someone smaller than you!")
            }
            4 -> landscape()
            5 -> pickaxes()
        }
    }

    private suspend fun Dialogue.offerQuest() {
        when (choice2("Yes.", 1, "No.", 2, title = "Start Doric's Quest?")) {
            1 -> acceptQuest()
            2 -> declineQuest()
        }
    }

    private suspend fun Dialogue.declineQuest() {
        chatPlayer(neutral, "No, hitting rocks is for the boring people, sorry.")
        doric(neutral, "That is your choice. Nice to meet you anyway.")
    }

    private suspend fun Dialogue.acceptQuest() {
        chatPlayer(happy, "Yes, I will get you the materials.")
        doric(neutral, "Clay is what I use more than anything, to make casts. Could you get me 6 clay, 4 copper ore, and 2 iron ore, please? I could pay a little, and let you use my anvils. Take this pickaxe with you just in case you need it.")
        dorics.start(access)
        access.invAddOrDrop(objRepo, BRONZE_PICKAXE)
        if (dorics.hasMaterials(player)) {
            coincidence()
            return
        }
        whereToFind()
    }

    private suspend fun Dialogue.coincidence() {
        chatPlayer(happy, "You know, it's funny you should require those exact things!")
        doric(confused, "What do you mean?")
        chatPlayer(happy, "I can usually fit 28 things in my backpack and in a world full of quite literally limitless possibilities, a complete coincidence has occurred!")
        doric(confused, "I don't quite understand what you're saying?")
        chatPlayer(happy, "Well, out of pure coincidence, despite definitely not knowing what you were about to request, I just so happened to have carried those exact items!")
        if (dorics.hasExactMaterials(player)) {
            chatPlayer(happy, "In fact, in the exact quantities too!")
        }
        handIn("Oh my, that is a coincidence! Pass them here, please. I can spare you some coins for your trouble, and please use my anvils any time you want.")
    }

    private suspend fun Dialogue.materialsCheck() {
        doric(quiz, "Have you got my materials yet, traveller?")
        if (dorics.hasMaterials(player)) {
            chatPlayer(happy, "I have everything you need!")
            handIn("Many thanks! Pass them here, please. I can spare you some coins for your trouble, and please use my anvils any time you want.")
            return
        }
        chatPlayer(sad, "Sorry, I don't have them all yet.")
        doric(neutral, "Not to worry, stick at it. Remember, I need 6 clay, 4 copper ore, and 2 iron ore.")
        whereToFind()
    }

    private suspend fun Dialogue.whereToFind() {
        when (
            choice2(
                "Where can I find those?", 1,
                "Certainly, I'll be right back!", 2,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Where can I find those?")
                doric(neutral, "You'll be able to find all those ores in the rocks just inside the Dwarven Mine. Head east from here and you'll find the entrance in the side of Ice Mountain.")
                if (player.miningLvl < IRON_ORE_LEVEL) {
                    chatPlayer(sad, "But I'm not a good enough miner to get iron ore.")
                    doric(neutral, "Oh well, you could practice mining until you can. Can't beat a bit of mining - it's a useful skill. Failing that, you might be able to find a more experienced adventurer to buy the iron ore off.")
                }
            }
            2 -> chatPlayer(happy, "Certainly, I'll be right back!")
        }
    }

    /**
     * The materials are taken before the box is shown, and the quest completes even if the box is
     * dismissed by logging out or walking away, so they can never be lost without the reward.
     */
    private suspend fun Dialogue.handIn(thanks: String) {
        doric(happy, thanks)
        if (!dorics.takeMaterials(access)) {
            access.mes("You no longer have all the materials Doric asked for.")
            return
        }
        try {
            objbox(COPPER_ORE, "You hand the clay, copper, and iron to Doric.")
        } finally {
            dorics.complete(access)
        }
    }

    private suspend fun Dialogue.afterQuest() {
        doric(happy, "Hello traveller, how is your metalworking coming along?")
        chatPlayer(happy, "Not too bad, Doric.")
        doric(happy, "Good, the love of metal is a thing close to my heart.")
    }

    private suspend fun Dialogue.landscape() {
        chatPlayer(neutral, "I was just checking out the landscape.")
        doric(happy, "Hope you like it. I do enjoy the solitude of my little home. If you get time, please say hi to my friends in the Dwarven Mine.")
        when (choice2("Dwarven Mine?", 1, "Will do!", 2)) {
            1 -> {
                chatPlayer(quiz, "Dwarven Mine?")
                doric(happy, "Yep, the entrance is in the side of Ice Mountain just to the east of here. They're a friendly bunch. Stop in at Nurmof's store and buy one of my pickaxes!")
            }
            2 -> chatPlayer(happy, "Will do!")
        }
    }

    private suspend fun Dialogue.pickaxes() {
        chatPlayer(quiz, "What do you make here?")
        doric(happy, "I make pickaxes. I am the best maker of pickaxes in the whole of Gielinor.")
        chatPlayer(quiz, "Do you have any to sell?")
        doric(neutral, "Sorry, but I've got a running order with Nurmof.")
        when (choice2("Who's Nurmof?", 1, "Ah, fair enough.", 2)) {
            1 -> {
                chatPlayer(quiz, "Who's Nurmof?")
                doric(neutral, "Nurmof has a store over in the Dwarven Mine. You can find the entrance on the side of Ice Mountain to the east of here.")
            }
            2 -> chatPlayer(neutral, "Ah, fair enough.")
        }
    }

    private suspend fun Dialogue.doric(mesanim: MesAnimType, text: String) {
        if (npc != null) {
            chatNpc(mesanim, text)
        } else {
            chatNpcSpecific(DORIC_NAME, DORIC, mesanim, text)
        }
    }

    companion object {
        const val DORIC = "npc.doric"
        const val DORIC_NAME = "Doric"
        const val IRON_ORE_LEVEL = 15
    }
}
