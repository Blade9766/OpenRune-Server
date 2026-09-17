package org.rsmod.content.quest.area.draynor.porcineofinterest.npcs

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.REINFORCED_GOGGLES
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.SLAYER_REWARD_POINTS
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.SPRIA
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.STAGE_AMBUSHED
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.STAGE_BOUNTY
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.STAGE_SLAIN
import org.rsmod.content.quest.area.draynor.porcineofinterest.addSlayerRewardPoints
import org.rsmod.content.quest.area.draynor.porcineofinterest.carries
import org.rsmod.content.slayer.SlayerInterfaces
import org.rsmod.content.slayer.core.SlayerTaskManager
import org.rsmod.content.slayer.dialogue.SlayerMasters
import org.rsmod.content.slayer.dialogue.SlayerMasters.spriaStart
import org.rsmod.content.slayer.dialogue.StandardSlayerDialogue.requestAssignment
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Spria, the slayer master who drags the player out of the Sourhog Cave and afterwards sets up
 * shop in Draynor Village.
 *
 * Her world spawn is `npc.slayer_master_9`, the multinpc that shows the quest version of her
 * (`npc.porcine_spria`, Talk-to only) until the quest ends and the working slayer master
 * (`npc.slayer_master_9_active`) afterwards. Op events on a multinpc always arrive under the base
 * type, so every op is registered here and the finished-quest cases are handed to the slayer
 * plugin that owns them.
 */
class Spria @Inject constructor(private val porcine: PorcineOfInterestQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(SPRIA) { startDialogue(it.npc) { spria() } }
        onOpNpc3(SPRIA) {
            focusMaster()
            startDialogue(it.npc) { requestAssignment(SlayerMasters.Npc.spriaActive) }
        }
        onOpNpc4(SPRIA) { openRewards() }
        onOpNpc5(SPRIA) { openRewards() }
    }

    private fun ProtectedAccess.openRewards() {
        focusMaster()
        SlayerInterfaces.openInterface(this, ACTIVE_SPRIA)
    }

    private fun ProtectedAccess.focusMaster() {
        val master = SlayerTaskManager.findMasterByNpc(ACTIVE_SPRIA) ?: return
        VarPlayerIntMapSetter.set(player, "varbit.slayer_master_in_focus", master.masterId)
    }

    private suspend fun Dialogue.spria() {
        val stage = porcine.stage(player)
        when {
            porcine.isComplete(player) -> spriaStart()
            stage == STAGE_BOUNTY -> finishQuest()
            stage == STAGE_SLAIN -> collectTheBountyFirst()
            stage >= STAGE_AMBUSHED -> rescueTalk(stage)
            else -> stillUnpacking()
        }
    }

    private suspend fun Dialogue.stillUnpacking() {
        chatNpc(
            neutral,
            "I've only recently moved in and I'm still getting unpacked. Could you come back later?",
        )
    }

    private suspend fun Dialogue.rescueTalk(stage: Int) {
        if (stage > STAGE_AMBUSHED || porcine.spriaBriefed.get(player)) {
            chatNpc(quiz, "How goes it?")
            followUp()
            return
        }
        chatNpc(happy, "Oh, you're awake!")
        chatPlayer(confused, "Who are you? How did I end up here?")
        chatNpc(neutral, "My name's Spria and I'm a slayer master. You've had a nasty encounter with a Sourhog.")
        chatPlayer(quiz, "A Sourhog? You mean the giant pig monster?")
        chatNpc(
            neutral,
            "Precisely. You're extremely lucky that I found you. Another few hours and you'd have " +
                "been hog chow.",
        )
        chatNpc(quiz, "Were you responding to Sarah's bounty too?")
        chatPlayer(sad, "Yes... It sounded like an easy few coins...")
        chatNpc(
            neutral,
            "I thought as much. I found you on the floor of the cave, you must have been knocked " +
                "unconscious.",
        )
        chatPlayer(
            worried,
            "That thing caught me completely off-guard. Normally I can handle myself in a fight, " +
                "but its spitting attack is pretty fearsome!",
        )
        if (access.inv.isFull()) {
            chatNpc(
                neutral,
                "I have an item for you that will help you on your quest, but you don't seem to " +
                    "have enough space. Come and see me again when you've cleared some room.",
            )
            return
        }
        chatNpc(
            neutral,
            "The beast's saliva is extremely acidic and your eyes are most vulnerable. It'll cause " +
                "terrible injuries and leave you unable to attack or defend yourself very well.",
        )
        chatPlayer(quiz, "So how am I supposed to handle that?")
        chatNpc(
            happy,
            "Luckily, the Slayer skill provides us with an array of useful equipment and " +
                "techniques for dealing with... special foes.",
        )
        chatNpc(neutral, "In the case of our Sourhog, you'll be needing these.")
        access.invAdd(access.inv, REINFORCED_GOGGLES)
        objbox(REINFORCED_GOGGLES, "Spria hands you a pair of Reinforced Goggles.")
        chatNpc(
            neutral,
            "This eyewear will protect you from the spitting attack, allowing you to fight the " +
                "monster on equal terms.",
        )
        chatNpc(
            neutral,
            "Most enemies have a weakness, too. The Sourhog's thick hide provides excellent " +
                "protection against slashing and insulates it from magic damage.",
        )
        chatNpc(neutral, "You'd be best trying to pierce it with arrows or a stabbing weapon.")
        chatNpc(
            neutral,
            "Go now, the bounty is all yours to claim. I suggest you recoup your strength and " +
                "head back to fight it using your new goggles.",
        )
        chatNpc(
            neutral,
            "If you make it out alive, collect your bounty and then come see me so that we can " +
                "speak further.",
        )
        porcine.spriaBriefed.set(player, true)
        porcine.advanceTo(access, PorcineOfInterestQuest.STAGE_GOGGLES)
        followUp()
    }

    /**
     * Her five standing topics. The client menu tops out at five options, so the spare-goggles
     * request takes the place of the Draynor question - which is only of interest immediately
     * after waking up anyway - whenever the player has managed to lose the pair she gave them.
     */
    private suspend fun Dialogue.followUp() {
        while (true) {
            val lostGoggles = !hasGogglesAnywhere()
            val secondOption =
                if (lostGoggles) "Can I have another pair of Reinforced Goggles?"
                else "How did I end up back in Draynor Village?"
            val choice =
                choice5(
                    "Why are you helping me?",
                    1,
                    secondOption,
                    2,
                    "You said you were a slayer master. What's that?",
                    3,
                    "What can you tell me about the Sourhog?",
                    4,
                    "That'll be all for now.",
                    5,
                )
            when (choice) {
                1 -> whyHelp()
                2 -> if (lostGoggles) replaceGoggles() else howDidIGetHere()
                3 -> whatIsASlayerMaster()
                4 -> sourhogStory()
                else -> {
                    chatPlayer(neutral, "That'll be all for now.")
                    chatNpc(happy, "Very well. Good luck, slayer.")
                    return
                }
            }
            chatNpc(quiz, "Was there something else?")
        }
    }

    private fun Dialogue.hasGogglesAnywhere(): Boolean =
        access.carries(REINFORCED_GOGGLES) || access.bank.count(REINFORCED_GOGGLES) > 0

    private suspend fun Dialogue.whyHelp() {
        chatPlayer(quiz, "Why are you helping me?")
        chatNpc(neutral, "Well, I suppose I could have left you in there to be eaten...")
        chatNpc(
            laugh,
            "But you know what that'd make me? A monster. And as a slayer master, that's really " +
                "not a great look.",
        )
        chatNpc(
            neutral,
            "Besides, my father always taught me that knowledge is worth more than gold. I'm " +
                "happy to sacrifice a few coins in order to pass my experience on to you.",
        )
        chatPlayer(happy, "Well, I sure am glad you were there to lend a hand. Thank you!")
        chatNpc(happy, "Don't mention it.")
    }

    private suspend fun Dialogue.howDidIGetHere() {
        chatPlayer(quiz, "How did I end up back in Draynor Village?")
        chatNpc(
            neutral,
            "I carried you out and dragged you back here to my house to recover, it really wasn't " +
                "easy. You're welcome to rest here for as long as you need.",
        )
    }

    private suspend fun Dialogue.whatIsASlayerMaster() {
        chatPlayer(quiz, "You said you were a slayer master. What's that?")
        chatNpc(
            neutral,
            "There are many of us. We're experts in the ancient art of monster slaying. People " +
                "from across Gielinor come to us for tasks and advice.",
        )
        chatNpc(
            happy,
            "Well... actually, I'm fairly new to this. I've been learning from my father, Turael, " +
                "for many years. We decided it was finally time for me to continue the family " +
                "profession.",
        )
    }

    private suspend fun Dialogue.replaceGoggles() {
        chatPlayer(quiz, "Can I have another pair of Reinforced Goggles?")
        if (access.inv.isFull()) {
            chatNpc(neutral, "Not with your backpack in that state. Clear some room and ask again.")
            return
        }
        chatNpc(bored, "Sure, I have another going spare. Please try to take better care of these.")
        access.invAdd(access.inv, REINFORCED_GOGGLES)
        objbox(REINFORCED_GOGGLES, "Spria hands you another pair of Reinforced Goggles.")
    }

    private suspend fun Dialogue.sourhogStory() {
        chatPlayer(quiz, "What can you tell me about the Sourhog?")
        chatNpc(neutral, "Well, I'm not much of a history buff... But I'll tell you what I know.")
        chatNpc(
            neutral,
            "Many years back, before even I was around, there were two rival family farms not far " +
                "from where we're standing.",
        )
        chatNpc(neutral, "Every year the village would hold a 'Finest Swine in Show' prize at the annual faire.")
        chatNpc(
            neutral,
            "The same farmer would win year after year without fail. Driven mad with jealousy, " +
                "his rival decided to hire a dodgy dark wizard to give him an edge over the " +
                "competition.",
        )
        chatNpc(laugh, "The wizard attempted to alter the pig's appearance. I'm sure you can guess how well that went!")
        chatNpc(
            neutral,
            "Long story short, the mutant, what we now know as a Sourhog, went on a brief rampage " +
                "and escaped.",
        )
        chatNpc(worried, "It must have somehow managed to reproduce, because people have reported seeing them in groups.")
        chatNpc(
            neutral,
            "Up until now, so long as they were left alone, they'd keep to themselves. Rumours " +
                "are, they live in deep underground caverns, scavenging what they can find.",
        )
        chatNpc(
            worried,
            "But if they're living so close-by and attacking people on roads, then... they must be " +
                "getting bold and trying to resurface.",
        )
    }

    private suspend fun Dialogue.collectTheBountyFirst() {
        chatNpc(neutral, "'Ello, and what are you after then?")
        chatPlayer(happy, "I did it! I killed the Sourhog!")
        chatNpc(happy, "Nicely done. Did you collect your reward from Sarah?")
        chatPlayer(neutral, "No, not yet.")
        chatNpc(neutral, "Go and collect your reward and then come and speak with me.")
    }

    private suspend fun Dialogue.finishQuest() {
        chatNpc(neutral, "'Ello, and what are you after then?")
        chatPlayer(happy, "I did it! I killed the Sourhog and collected the bounty from Sarah.")
        chatNpc(happy, "Very impressive, slayer. I'm glad that the goggles served you well.")
        chatNpc(
            worried,
            "Although, these Sourhogs often live in primitive tribes. I fear that where there's " +
                "one, there's likely to be more to follow.",
        )
        chatNpc(neutral, "We may have to monitor that lair from time to time to keep their population in check.")
        chatNpc(
            neutral,
            "And fasten a pair of Reinforced Goggles into the eye holes of any slayer helm you " +
                "assemble - they will do the same job down there.",
        )
        chatNpc(
            happy,
            "You seem to have a natural talent for killing monsters. Come back whenever you " +
                "want a task and I'll find you something.",
        )
        player.addSlayerRewardPoints(SLAYER_REWARD_POINTS)
        porcine.advanceTo(access, STAGE_COMPLETE)
    }

    private companion object {
        const val ACTIVE_SPRIA = "npc.slayer_master_9_active"
    }
}
