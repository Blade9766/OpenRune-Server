package org.rsmod.content.quest.area.tirannwn.mourningsend.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BROKEN_DEVICE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.FIXED_DEVICE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.GNOME_KEY
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.LETTER
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_ADMITTED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_BRIEFED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_DEVICE_FIXED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_FOOD_TASK
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_REVEALED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_STORES_DONE
import org.rsmod.content.quest.area.tirannwn.mourningsend.ownsAnywhere
import org.rsmod.content.quest.area.tirannwn.mourningsend.swap
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Essyllt, the head mourner, in his office beneath the Headquarters. He takes the mourner letter
 * and sets the sheep task, replaces a lost device or key, gives the food-supply task once all four
 * flocks are dyed, and tells of the mines once two stores are spoiled. Each step moves the stage
 * only when its condition is met, so asking him early never skips anything, and the letter is
 * swapped for the device and key in one transaction.
 */
class Essyllt @Inject constructor(private val mourning: MourningsEndQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        for (type in listOf(ESSYLLT, ESSYLLT_VIS)) {
            onOpNpc1(type) { startDialogue(it.npc) { essyllt() } }
        }
    }

    private suspend fun Dialogue.essyllt() {
        val stage = mourning.stage(player)
        when {
            stage < STAGE_BRIEFED && !mourning.isComplete(player) -> {
                chatNpc(angry, "I don't know you. Get out of my office.")
            }
            stage == STAGE_BRIEFED -> newRecruit()
            stage in STAGE_ADMITTED..STAGE_DEVICE_FIXED -> sheepTask()
            stage in STAGE_FOOD_TASK until STAGE_STORES_DONE -> epidemic()
            stage == STAGE_STORES_DONE -> reveal()
            else -> {
                chatPlayer(quiz, "Do you have the key to the mines yet?")
                chatNpc(neutral, "The guard is taking his time, but we should have it soon.")
                chatPlayer(neutral, "Alright, I'll report in again later.")
            }
        }
    }

    private suspend fun Dialogue.newRecruit() {
        chatPlayer(neutral, "Hello, I'm...")
        chatNpc(neutral, "Ah... I take it you're one of the new recruits?")
        chatPlayer(neutral, "Well, I'm...")
        chatNpc(neutral, "I'm Essyllt, and I'm in charge here. Come on then, let's see your paperwork.")
        if (!player.inv.contains(LETTER)) {
            chatPlayer(worried, "I don't seem to have it with me.")
            chatNpc(angry, "No papers, no assignment. Come back when you have your letter of recommendation.")
            return
        }
        if (player.inv.freeSpace() < 1) {
            chatNpc(neutral, "You'll want your hands free for what I'm about to give you. Make some room in your pack first.")
            return
        }
        if (!access.swap(listOf(LETTER to 1), listOf(BROKEN_DEVICE to 1, GNOME_KEY to 1))) {
            return
        }
        mourning.advanceTo(access, STAGE_ADMITTED)
        objbox(LETTER, 400, "You hand over the letter of recommendation.")
        chatNpc(neutral, "This all seems to be in order. Welcome to the Death Guard. Now, your first assignment.")
        chatNpc(neutral, "As you'll know, part of our work here is keeping the people believing in the plague.")
        chatPlayer(quiz, "Why?")
        chatNpc(confused, "They didn't tell you? Hmm... then I'd better fill you in.")
        chatNpc(neutral, "Lord Iorwerth sent us to take hold of this city so we could reach the caves beneath it.")
        chatPlayer(quiz, "What's so important about the caves?")
        chatNpc(neutral, "That stays classified until you've shown your commitment to the Death Guard.")
        chatPlayer(neutral, "Oh, fair enough.")
        chatNpc(neutral, "To keep us hidden, Lord Iorwerth made an alliance with King Lathas in the east. The fool believes we'll help him rid himself of the Knights of Camelot once we're done here.")
        chatNpc(laugh, "I suspect he'll be disappointed.")
        chatNpc(neutral, "The plague does two jobs for us. Our mourner garb lets us move about the city unquestioned, and anyone we take to dig in the caves below can simply be said to have caught the plague.")
        chatPlayer(quiz, "Doesn't anyone suspect?")
        chatNpc(neutral, "There have been close calls. A girl from East Ardougne came asking too many questions, so we locked her away. Some adventurer broke her out.")
        chatNpc(laugh, "Luckily they were foolish enough that we used them to deal with King Tyras. It all worked out rather well.")
        chatNpc(neutral, "That's why the people must keep believing. One way we manage it is by convincing old Farmer Brumty that his sheep have the plague.")
        chatNpc(laugh, "The halfwit thinks any sheep that's an odd colour must be sick. A little dye goes a long way.")
        chatPlayer(shocked, "You dyed them?")
        chatNpc(neutral, "You don't think sheep come in those ridiculous colours naturally, do you? The trouble is that the dye washes out, so someone has to dye them again. That someone is you.")
        chatPlayer(laugh, "So you want me to give a load of old sheep a blue rinse?")
        chatNpc(neutral, "Not quite. They need to be red, yellow, green and blue, and each flock must stay the colour it already is or the farmer may notice. Above all, nobody must see you do it.")
        chatPlayer(quiz, "That's trickier. How did you manage it before?")
        chatNpc(neutral, "We have a gnomish device that fires fat parcels of dye which burst on impact. Unfortunately we've run out of parcels, and the device is broken.")
        chatPlayer(quiz, "Can I take a look at it?")
        chatNpc(neutral, "Of course. There's a gnome inventor here too. He isn't being very helpful about fixing it, but you can try him. He's in the next room; here's the key, and the device.")
        doubleobjbox(BROKEN_DEVICE, GNOME_KEY, "Essyllt hands you a strange object and a tarnished key.")
    }

    private suspend fun Dialogue.sheepTask() {
        chatNpc(quiz, "Have you finished with those sheep yet?")
        if (mourning.stage(player) == STAGE_DEVICE_FIXED && mourning.allSheepDone(player)) {
            foodTask()
            return
        }
        val lostDevice = !access.ownsAnywhere(BROKEN_DEVICE) && !access.ownsAnywhere(FIXED_DEVICE)
        val lostKey = !access.ownsAnywhere(GNOME_KEY)
        when {
            lostDevice && lostKey -> {
                chatPlayer(worried, "No, I'm sorry. I lost the gnomish device and the key.")
                chatNpc(neutral, "It's lucky we have a whole stack of broken devices; they always break the same way. If you need another, there's a chest full of them over there.")
                chatNpc(neutral, "I keep copies of the key on my desk as well.")
                replace(listOf(BROKEN_DEVICE, GNOME_KEY), "Essyllt hands you a strange object and a tarnished key.")
            }
            lostDevice -> {
                chatPlayer(worried, "Not yet. I lost the gnomish device.")
                chatNpc(neutral, "It's lucky we have a whole stack of broken ones; they always break the same way. If you need another, there's a chest full of them over there.")
                replace(listOf(BROKEN_DEVICE), "Essyllt hands you a strange object.")
            }
            lostKey -> {
                chatPlayer(worried, "I'm sorry, but I lost the key.")
                chatNpc(neutral, "I keep copies of the key on my desk, so I can replace that.")
                replace(listOf(GNOME_KEY), "Essyllt hands you a tarnished key.")
            }
            else -> {
                chatPlayer(neutral, "Not yet.")
                chatNpc(neutral, "Then get on with it. Red, yellow, green and blue, each flock its own colour, and don't let anyone see you.")
            }
        }
    }

    private suspend fun Dialogue.replace(items: List<String>, text: String) {
        if (player.inv.freeSpace() < items.size) {
            chatNpc(neutral, "Make some room in your pack and I'll give you another.")
            return
        }
        if (!access.swap(emptyList(), items.map { it to 1 })) {
            return
        }
        if (items.size == 2) doubleobjbox(items[0], items[1], text) else objbox(items[0], text)
    }

    private suspend fun Dialogue.foodTask() {
        chatPlayer(neutral, "Yes, it's done. What next?")
        chatNpc(happy, "I like your enthusiasm. I was going to give this next job to someone else.")
        chatNpc(neutral, "It's been a while since anyone fell ill with the plague. We need people falling ill again.")
        chatPlayer(confused, "How? The plague doesn't exist.")
        chatNpc(neutral, "Some joker put something in our stew recently, and everyone who ate it came down with something very like the plague. Find out what it was, and make more of it.")
        chatNpc(neutral, "Done properly it won't kill anyone, and it will refill our supply of cheap labour. Anyone who 'catches the plague' goes to the mines.")
        chatNpc(neutral, "With the walls up nobody grows food inside the city. It all comes from three supply points.")
        chatPlayer(neutral, "So I need to find out who spoiled your stew and with what, work out how to make it, and make plenty.")
        mourning.advanceTo(access, STAGE_FOOD_TASK)
        chatNpc(neutral, "And don't forget to use it on the food supply. Two of the three supply points should be enough.")
        chatPlayer(quiz, "How am I supposed to do all that?")
        chatNpc(neutral, "You seem resourceful. I'm no biologist.")
        chatPlayer(neutral, "I know a biologist nearby.")
        chatNpc(quiz, "Someone trustworthy, I hope?")
        chatPlayer(neutral, "Oh yes, definitely.")
        chatNpc(neutral, "Very well. Do it, then report back to me.")
    }

    private suspend fun Dialogue.epidemic() {
        chatNpc(quiz, "Back already? How is the epidemic going?")
        chatPlayer(confused, "The epidemic? Oh, you mean the poisoning?")
        chatNpc(angry, "Subtle as a brick... Yes, that. How is it going?")
        val explain = menu("Could you explain it again?" to true, "I'm working on it." to false)
        if (!explain) {
            chatPlayer(neutral, "I'm working on it.")
            chatNpc(neutral, "Two of the three food stores. Don't come back until it's done.")
            return
        }
        chatPlayer(quiz, "Could you explain it again?")
        chatNpc(neutral, "People need to look as if they have the plague. It keeps the lie alive and gives us an excuse to take more of them to the mines.")
        chatNpc(neutral, "Find out what spoiled our stew, make more of it, and use it on two of the three food stores in the city.")
    }

    private suspend fun Dialogue.reveal() {
        chatNpc(quiz, "Back already? How is the epidemic going?")
        chatPlayer(neutral, "It's all done.")
        chatNpc(happy, "Excellent. Give it a few days and the slave pens will be full again.")
        chatPlayer(quiz, "Have I proven my commitment now?")
        chatNpc(neutral, "You have. Would you like to hear about the mines?")
        chatPlayer(neutral, "Yes please.")
        chatNpc(neutral, "Long ago Seren had a great temple built deep underground. It guards a power that is key to Lord Iorwerth's plans, and we believe it lies beneath this city.")
        chatNpc(neutral, "Our slaves have dug into caverns full of beasts, which has slowed us down, but there are signs we are close.")
        mourning.advanceTo(access, STAGE_REVEALED)
        chatNpc(neutral, "I have a new task for you down in the mines, but one of the guards has taken the mine key to be copied. Report back to me regularly.")
        chatPlayer(neutral, "Will do.")
        chatNpc(neutral, "Now be on your way.")
        chatPlayer(shifty, "I'd better report this to Arianwyn.")
        chatNpc(quiz, "What was that?")
        chatPlayer(worried, "Oh, nothing.")
        chatNpc(neutral, "Hmm, fair enough.")
    }

    companion object {
        const val ESSYLLT = "npc.mourner_hideout_head_mourner"
        const val ESSYLLT_VIS = "npc.mourner_hideout_head_mourner_vis"
    }
}
