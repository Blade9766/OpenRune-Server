package org.rsmod.content.quest.area.tirannwn.mourningsend

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.PlayerObjTakeRedirectHook
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.ELENA_AGREED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.ELENA_SIEVE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.NAPHTHA_APPLE_MIX
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.ROTTEN_APPLE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.SIEVE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_FOOD_TASK
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_REVEALED
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_SIEVE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_STORES_DONE
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.TOXIC_NAPHTHA
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.TOXIC_POWDER
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Player
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid

/**
 * Elena's part in Mourning's End Part I, asked by her East Ardougne script before anything else
 * she has to say: she agrees, reluctantly, to help (`varbit.mourning_elena` 1), tests the rotten
 * apple from behind the Mourner Headquarters and hands over a sieve with the method (2, and the
 * stage moves on). The analysis happens there and then, as in the game, so there is nothing to
 * wait for. She replaces a lost sieve during the quest, and afterwards for anyone carrying a
 * naphtha apple mix that needs straining; otherwise her usual conversation is left alone.
 *
 * Only the apple that grew behind the Headquarters will do: picking one up from those two spots
 * while she is waiting for it marks the player as carrying it ([HeadquartersAppleHook]), and that
 * mark, not the apple itself, is what she checks.
 */
@Singleton
class ElenaResearch @Inject constructor(private val mourning: MourningsEndQuest) {

    fun offers(player: Player): Boolean {
        val stage = mourning.stage(player)
        if (stage in STAGE_FOOD_TASK..STAGE_REVEALED) {
            return true
        }
        return mourning.isComplete(player) && !player.ownsAnywhere(SIEVE) && player.ownsAnywhere(NAPHTHA_APPLE_MIX)
    }

    suspend fun Dialogue.elena() {
        val stage = mourning.stage(player)
        if (mourning.isComplete(player)) {
            afterQuest()
            return
        }
        if (player.wearsDisguise()) {
            disguisedGreeting()
        }
        when {
            stage == STAGE_FOOD_TASK && player.elenaState == 0 -> askForHelp()
            stage == STAGE_FOOD_TASK -> appleSample()
            stage == STAGE_SIEVE -> reminder()
            stage == STAGE_STORES_DONE -> {
                chatPlayer(neutral, "It's all done.")
                chatNpc(quiz, "Have you found out what the mourners are up to yet?")
                chatPlayer(neutral, "I'm on my way back there now.")
                chatNpc(neutral, "Well, let me know when you find out.")
            }
            else -> {
                chatPlayer(neutral, "They're searching for an old temple deep beneath the city. It holds some great power.")
                chatNpc(worried, "Well, let me know when you find out more.")
            }
        }
    }

    private suspend fun Dialogue.disguisedGreeting() {
        chatNpc(angry, "How dare you come into my house, Mourner! Get out!")
        chatPlayer(worried, "Elena, it's me... ${player.displayName}.")
        chatNpc(shocked, "${player.displayName}? I didn't recognise you in all that mourner gear!")
    }

    private suspend fun Dialogue.askForHelp() {
        chatNpc(quiz, "How's it going? Have you found out any more about the plague?")
        chatPlayer(neutral, "It's a long story. The king was lying about where his loyalties lie.")
        chatPlayer(neutral, "He told me he faked the plague to protect everyone from King Tyras, who had been corrupted by the Dark Lord. I went through the Underground Pass and helped Lord Iorwerth kill Tyras.")
        chatPlayer(neutral, "Then an elf called Arianwyn opened Iorwerth's letter to the king. Both of them serve the Dark Lord, and Lathas wants Camelot for himself.")
        chatPlayer(neutral, "The mourners are Iorwerth's elves. Something under West Ardougne matters to them, and I'm earning their trust to find out what.")
        chatNpc(shocked, "That's quite something. If there's anything you need, just ask.")
        chatPlayer(neutral, "Actually, I need a poison made from rotten apples.")
        chatNpc(confused, "Rotten apples? I doubt apples alone would do much.")
        chatPlayer(neutral, "I put a rotten apple in the mourners' stew once, and they came down with something very like the plague.")
        chatNpc(neutral, "Then it was more likely a toxin from the mould than the apple itself. What do you need it for?")
        chatPlayer(worried, "To spoil a large food supply, so the mourners will trust me.")
        chatNpc(angry, "That's awful! I can't help you with that.")
        chatPlayer(neutral, "If I don't win their trust, the people of West Ardougne will suffer far worse. Please, trust me.")
        MourningsEndQuest.setVarBit(player, "varbit.mourning_elena", ELENA_AGREED)
        chatNpc(sad, "You had better be right, and I'll be making sure nobody dies. Bring me a sample of that rotten apple and I'll see about something to counteract it.")
        handOverApple()
    }

    private suspend fun Dialogue.appleSample() {
        chatPlayer(neutral, "I'm back.")
        chatNpc(quiz, "Do you have that sample of rotten apple for me?")
        handOverApple()
    }

    private suspend fun Dialogue.handOverApple() {
        if (player.hqApple == 0 || !player.inv.contains(ROTTEN_APPLE)) {
            if (player.inv.contains(ROTTEN_APPLE)) {
                chatPlayer(neutral, "I have this rotten apple.")
                chatNpc(confused, "That's not the one you told me about. I need an apple from where the mourners' stew was spoiled, behind their headquarters.")
            } else {
                chatPlayer(neutral, "Not yet. I'll be back shortly with a rotten apple for you.")
            }
            return
        }
        chatPlayer(happy, "I have one right here.")
        if (access.invDel(access.inv, ROTTEN_APPLE, 1).failure) {
            return
        }
        MourningsEndQuest.setVarBit(player, "varbit.mourning_hq_apple", 0)
        objbox(ROTTEN_APPLE, "You hand Elena the rotten apple.")
        chatNpc(sad, "Ick... Alright then, let's get started.")
        mesbox("Elena starts some tests on the apple.")
        chatNpc(neutral, "Right... let's see. I've isolated the toxin. It comes from the mould, and it won't kill anyone, so I won't need anything to counteract it.")
        chatNpc(quiz, "How big is this food store?")
        chatPlayer(neutral, "Two of the three supply points in West Ardougne.")
        chatNpc(worried, "That's over half the city's food! You'll need a lot of the toxin, and it will have to be refined so nobody notices rotten apples in their bread.")
        chatPlayer(quiz, "Can't you make it?")
        chatNpc(neutral, "I don't have the equipment for that sort of quantity. I can tell you how, though.")
        chatNpc(neutral, "Crush plenty of rotten apples, then dissolve the toxin out with a solvent that evaporates easily. Naphtha would be perfect.")
        chatPlayer(neutral, "I've made naphtha before, with the Chemist in Rimmington.")
        chatNpc(neutral, "Then mix the naphtha with the crushed apples, strain out the solids, and heat what's left until the naphtha evaporates. Not over a naked flame, mind. Naphtha is highly flammable.")
        chatPlayer(sad, "Why is nothing ever easy?")
        if (!giveSieve()) {
            chatNpc(neutral, "You'll need a sieve for the straining. Make some room in your pack and I'll give you one.")
            return
        }
        MourningsEndQuest.setVarBit(player, "varbit.mourning_elena", ELENA_SIEVE)
        mourning.advanceTo(access, STAGE_SIEVE)
        chatNpc(neutral, "Sorry. You'll need this for the straining.")
        objbox(SIEVE, "Elena hands you a large sieve.")
        chatNpc(neutral, "Try the orchard just north of the city. Nobody has tended it since the blight, so there should be plenty of rotten apples.")
    }

    private suspend fun Dialogue.reminder() {
        chatPlayer(neutral, "Hey Elena.")
        if (player.elenaState >= ELENA_SIEVE && !access.ownsAnywhere(SIEVE) && !holdsToxin()) {
            replaceSieve()
            return
        }
        if (player.inv.contains(TOXIC_POWDER)) {
            chatPlayer(neutral, "I've made a batch, but I haven't used it yet.")
            chatNpc(worried, "Well, be careful. That toxin is nasty stuff!")
            return
        }
        chatNpc(quiz, "So how's the poisoning going?")
        chatPlayer(quiz, "Could you remind me what I need to do?")
        chatNpc(neutral, "Get rotten apples from the orchard north of the city and crush them. Mix them with naphtha, which the Chemist in Rimmington can help with, strain it through the sieve, then heat it, but not over a naked flame.")
        chatPlayer(neutral, "Okay, thanks Elena.")
        chatNpc(happy, "No problem.")
    }

    private suspend fun Dialogue.afterQuest() {
        val asks = menu("Do you have another sieve? I lost the last one." to true, "Just saying hello." to false)
        if (!asks) {
            chatPlayer(happy, "Just saying hello.")
            chatNpc(happy, "Hello yourself!")
            return
        }
        replaceSieve()
    }

    private suspend fun Dialogue.replaceSieve() {
        chatPlayer(worried, "Do you have another sieve? I lost the last one.")
        if (!giveSieve()) {
            chatNpc(neutral, "I've plenty, but you'll need to make room for one.")
            return
        }
        chatNpc(laugh, "Funny you should ask. Somebody delivered hundreds of them by mistake.")
        objbox(SIEVE, "Elena hands you a large sieve.")
    }

    private fun Dialogue.giveSieve(): Boolean =
        player.inv.freeSpace() >= 1 && access.invAdd(access.inv, SIEVE).success

    private fun Dialogue.holdsToxin(): Boolean = access.ownsAnywhere(TOXIC_NAPHTHA) || access.ownsAnywhere(TOXIC_POWDER)
}

/**
 * Takes a rotten apple from one of the two spots behind the Mourner Headquarters into the pack
 * while Elena is waiting for one, marking it as the sample she asked for.
 */
class HeadquartersAppleHook @Inject constructor(private val mourning: MourningsEndQuest) : PlayerObjTakeRedirectHook {
    private val appleId by lazy { ROTTEN_APPLE.asRSCM(RSCMType.OBJ) }

    override fun redirects(player: Player, obj: Obj, objType: ItemServerType): Boolean =
        objType.id == appleId &&
            obj.coords in HQ_APPLE_SPAWNS &&
            mourning.stage(player) == STAGE_FOOD_TASK &&
            player.inv.freeSpace() > 0

    override fun take(player: Player, obj: Obj, objType: ItemServerType): Boolean {
        if (player.invAdd(player.inv, ROTTEN_APPLE, obj.count).failure) {
            return false
        }
        MourningsEndQuest.setVarBit(player, "varbit.mourning_hq_apple", 1)
        return true
    }

    companion object {
        val HQ_APPLE_SPAWNS = setOf(CoordGrid(2535, 3333, 0), CoordGrid(2549, 3332, 0))
    }
}
