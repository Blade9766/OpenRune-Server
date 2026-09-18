package org.rsmod.content.quest.area.desert.touristtrap.npcs

import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.fadeFromBlack
import org.rsmod.content.quest.area.ardougne.fadeToBlack
import org.rsmod.content.quest.area.desert.touristtrap.MiningCampSecurity
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapCoords
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_CARRIED
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_IN_A_BARREL
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_IN_MINE
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.ANA_ON_WAGON
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.CART_DRIVER
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.SOUND_CART_LOOP
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_ANA_ON_WAGON
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_ESCAPED
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_RETRIEVED_ANA_SURFACE
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_SAVED_ANA
import org.rsmod.content.quest.area.desert.touristtrap.ttAnaLocation
import org.rsmod.content.quest.area.desert.touristtrap.ttCartReady
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The mine cart driver in the middle of the compound and his wooden cart, the only thing that
 * leaves the camp without being searched.
 *
 * With Ana's barrel loaded, the driver has to be charmed with the right run of cart puns and
 * then panicked into leaving (or bribed); after that the player can hide on the cart and ride out
 * through the gates with her.
 */
class MineCartDriver
@Inject
constructor(
    private val quest: TouristTrapQuest,
    private val security: MiningCampSecurity,
    private val npcSearch: NpcSearch,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(CART_DRIVER) { startDialogue(it.npc) { driver() } }
        onOpLoc1(WAGON) {
            mesbox("A sturdy looking cart for carrying barrels of rocks out of the mining camp.")
            mesbox("The cart driver seems to be busy inspecting the cart for defects.")
        }
        onOpLoc2(WAGON) { searchWagon() }
        onOpLocU(WAGON, ANA_IN_A_BARREL) { loadAna() }
    }

    private suspend fun Dialogue.driver() {
        val location = player.ttAnaLocation
        val stage = quest.stage(player)
        when {
            player.ttCartReady ->
                chatNpc(neutral, "Hurry up, get in the cart or I'll go without you!")
            location == ANA_ON_WAGON -> {
                mesbox(
                    "The cart driver seems to be fastidiously cleaning his cart. It doesn't look " +
                        "as if he wants to be disturbed.",
                )
                val option = choice3("Hello!", 1, "Nice cart.", 2, "Pssst...", 3)
                when (option) {
                    1 -> hello()
                    2 -> niceCart()
                    else -> psst()
                }
            }
            ANA_IN_A_BARREL in player.inv ->
                chatNpc(
                    quiz,
                    "What're you doing carrying that big barrel around? Put it in the back of the " +
                        "cart like all the others!",
                )
            stage in STAGE_ESCAPED until STAGE_SAVED_ANA && location == ANA_IN_MINE ->
                chatPlayer(
                    worried,
                    "I should find Ana before attempting to escape again. She must have been " +
                        "caught by the guards.",
                )
            else -> chatNpc(angry, "Don't trouble me, can't you see I'm busy?")
        }
    }

    private suspend fun Dialogue.hello() {
        chatPlayer(happy, "Hello!")
        chatNpc(angry, "Can't you see I'm busy? Now get out of here!")
        val option = choice3("Oh, okay, sorry.", 1, "Nice cart.", 2, "Pssst...", 3)
        when (option) {
            1 -> okaySorry()
            2 -> niceCart()
            else -> psst()
        }
    }

    private suspend fun Dialogue.okaySorry() {
        chatPlayer(neutral, "Oh, okay, sorry.")
        chatNpc(angry, "Look just leave me alone!")
        access.mes("The cart driver goes back to his work.")
    }

    private suspend fun Dialogue.niceCart() {
        chatPlayer(happy, "Nice cart.")
        mesbox("The cart driver looks around at you and tries to weigh you up.")
        chatNpc(quiz, "Hmmm.")
        mesbox("He tuts to himself and starts checking the wheels.")
        npc?.say("Tut !")
        access.mes("The cart driver chuckles to himself.")
        val option =
            choice3(
                "I wonder if you could help me?",
                1,
                "One wagon wheel says to the other, 'I'll see you around'.",
                2,
                "Can I help you at all?",
                3,
            )
        when (option) {
            1 -> wonderIfYouCouldHelp()
            2 -> wagonWheel()
            else -> canIHelp()
        }
    }

    private suspend fun Dialogue.wagonWheel() {
        chatPlayer(laugh, "One wagon wheel says to the other, 'I'll see you around'.")
        chatNpc(happy, "<col=000080>-- The cart driver smirks a little. --</col>")
        mesbox("He starts checking the steering on the cart.")
        if (!choice2("'One good turn deserves another'", true, "Can you get me the heck out of here please?", false)) {
            heckOut()
            return
        }
        chatPlayer(laugh, "'One good turn deserves another.'")
        mesbox("The cart driver smiles a bit and then turns to you.")
        chatNpc(happy, "Are you trying to get me fired?")
        val option = choice3("No", 1, "Yes", 2, "Fired... no, shot perhaps!", 3)
        when (option) {
            1 -> {
                chatPlayer(neutral, "No")
                chatNpc(
                    angry,
                    "It certainly sounds like it, now leave me alone. If you bug me again, I'm " +
                        "gonna call the guards.",
                )
                access.mes("The cart driver goes back to his work.")
            }
            2 -> {
                chatPlayer(neutral, "Yes")
                chatNpc(
                    quiz,
                    "And why would you want to do a crazy thing like that for? I ought to teach " +
                        "you a lesson! Guards! Guards!",
                )
                callTheGuards()
            }
            else -> shotPerhaps()
        }
    }

    private suspend fun Dialogue.shotPerhaps() {
        chatPlayer(laugh, "Fired... no, shot perhaps!")
        chatNpc(
            laugh,
            "Ha ha ha! You're funny! <col=000080>-- The cart driver checks that guards aren't " +
                "watching. --</col> What're you in fer?",
        )
        val option =
            choice3(
                "Oh, I'm not supposed to be here at all actually.",
                1,
                "I'm in for murder, so you'd better get me out of here!",
                2,
                "In for a penny in for a pound.",
                3,
            )
        when (option) {
            1 -> {
                chatPlayer(neutral, "Oh, I'm not supposed to be here at all actually.")
                chatNpc(
                    laugh,
                    "Hmmm, interesting... let me guess. You're completely innocent... like all the " +
                        "other inmates in here. Ha ha ha!",
                )
                access.mes("The Cart driver goes back to his work.")
            }
            2 -> {
                chatPlayer(shifty, "I'm in for murder, so you'd better get me out of here!")
                chatNpc(
                    confused,
                    "Hmm, well, I wonder what the guards are gonna say about that! Guards! Guards!",
                )
                callTheGuards()
            }
            else -> pennyAndPound()
        }
    }

    private suspend fun Dialogue.pennyAndPound() {
        chatPlayer(laugh, "In for a penny in for a pound.")
        mesbox("The cart driver laughs at your pun...")
        chatNpc(laugh, "Ha ha ha, oh stop it!")
        mesbox("The cart driver seems much happier now.")
        chatNpc(happy, "What can I do for you anyway?")
        val option =
            choice3(
                "Can you smuggle me out on your cart?",
                1,
                "Can you smuggle my friend Ana out on your cart?",
                2,
                "Well, you see, it's like this...",
                3,
            )
        when (option) {
            1 -> {
                chatPlayer(quiz, "Can you smuggle me out on your cart?")
                mesbox("The cart driver points at a nearby guard.")
                chatNpc(laugh, "Ask that man over there if it's okay and I'll consider it! Ha ha ha!")
                mesbox("The cart driver goes back to his work, laughing to himself.")
            }
            2 -> {
                chatPlayer(quiz, "Can you smuggle my friend out on your cart?")
                chatNpc(
                    laugh,
                    "As long as your friend is a barrel full of rocks. I don't think it would be a " +
                        "problem at all! Ha ha ha!",
                )
            }
            else -> itsLikeThis()
        }
    }

    private suspend fun Dialogue.itsLikeThis() {
        chatPlayer(shifty, "Well, you see, it's like this...")
        chatNpc(confused, "Yeah!")
        val riot =
            choice2(
                "Prison riot in ten minutes, get your cart out of here!",
                true,
                "There's ten gold in it for you if you leave now - no questions asked.",
                false,
            )
        if (!riot) {
            bribe()
            return
        }
        chatPlayer(shocked, "Prison riot in ten minutes, get your cart out of here!")
        mesbox("The cart driver seems visibly shaken...")
        chatNpc(shocked, "Oh, right..yes... yess, okay...")
        mesbox("The cart driver quickly starts preparing the cart.")
        if (choice2("Good luck!", true, "You can't leave me here, I'll get killed!", false)) {
            chatPlayer(confused, "Good luck!")
            chatNpc(confused, "Yeah, you too!")
            mesbox(
                "The cart sets off at a hectic pace. The guards at the gate get suspicious and " +
                    "search the cart. They find Ana in the Barrel and take her back into the mine.",
            )
            quest.moveAna(player, ANA_IN_MINE)
            with(security) { access.throwInCell(null) }
            return
        }
        chatPlayer(worried, "You can't leave me here, I'll get killed!")
        chatNpc(confused, "Oh, right... Okay, you'd better jump in the cart then! Quickly!")
        player.ttCartReady = true
    }

    private suspend fun Dialogue.bribe() {
        chatPlayer(shifty, "There's ten gold in it for you if you leave now no questions asked.")
        chatNpc(
            laugh,
            "If you're going to bribe me, at least make it worth my while. Now, let's say 100 " +
                "Gold pieces should we? Ha ha ha!",
        )
        if (!choice2("A hundred it is!", true, "Forget it!", false)) {
            chatPlayer(neutral, "Forget it!")
            chatNpc(angry, "Okay, fair enough! But don't bother me anymore.")
            return
        }
        chatPlayer(neutral, "A hundred it is.")
        chatNpc(happy, "Great!")
        if (!access.invTakeFee(BRIBE)) {
            chatNpc(angry, "You little cheat, trying to trick me! I'll show you! Guards! Guards!")
            access.mes("You quickly slope away and hide from the guards.")
            return
        }
        chatNpc(happy, "Okay, get in the back of the cart then!")
        player.ttCartReady = true
    }

    private suspend fun Dialogue.heckOut() {
        chatPlayer(quiz, "Can you get me the heck out of here please?")
        chatNpc(angry, "No way, and if you bug me again, I'm gonna call the guards.")
        access.mes("The cart driver goes back to his work.")
    }

    private suspend fun Dialogue.wonderIfYouCouldHelp() {
        chatPlayer(quiz, "I wonder if you could help me?")
        chatNpc(
            neutral,
            "Sorry friend, I'm busy, go bug the guards, I'm sure they'll give ya the time of day.",
        )
        npc?.say("He, he, he, ha!")
        if (choice2("Can I help you at all?", true, "Can you get me the heck out of here please?", false)) {
            canIHelp()
        } else {
            heckOut()
        }
    }

    private suspend fun Dialogue.canIHelp() {
        chatPlayer(quiz, "Can I help you at all?")
        chatNpc(angry, "I'm quite capable thanks... Now get lost before I call the guards.")
        if (choice2("Can you get me the heck out of here please?", true, "I could help, I know a lot about carts.", false)) {
            heckOut()
            return
        }
        chatPlayer(happy, "I could help, I know a lot about carts.")
        chatNpc(quiz, "Are you saying I don't know anything about carts?")
        chatNpc(
            verymad,
            "Why you cheeky little.... <col=000080>-- The cart driver seems mortally offended -- " +
                "-- his temper explodes as he calls the guards.--</col> Guards! Guards!",
        )
        callTheGuards()
    }

    private suspend fun Dialogue.psst() {
        var hiss = "Pssst..."
        while (true) {
            chatPlayer(shifty, hiss)
            if (hiss == LOUD_PSST) {
                break
            }
            mesbox("The cart driver completely ignores you.")
            hiss = choice3("Psssst...", "Psssst...", "Psssssst...", "Psssssst...", LOUD_PSST, LOUD_PSST)
        }
        mesbox("The cart driver turns around quickly to face you.")
        chatNpc(angry, "What! Can't you see I'm busy?")
        val option = choice3("Oh, okay, sorry.", 1, "Shhshhh!", 2, "I wonder if you could help me?", 3)
        when (option) {
            1 -> okaySorry()
            2 -> {
                chatPlayer(shifty, "Shhshhh!")
                chatNpc(angry, "Shush yourself!")
                access.mes("The cart driver goes back to his work.")
            }
            else -> wonderIfYouCouldHelp()
        }
    }

    /** Half the time the player slips away before anyone comes; otherwise it is the cell. */
    private suspend fun Dialogue.callTheGuards() {
        access.ifClose()
        if (access.random.randomBoolean()) {
            access.mes("You quickly slope away and hide from the guards.")
            return
        }
        access.mes("Some guards notice you and come over.")
        val guard = security.guardNear(player.coords, GUARD_REACH) ?: return
        guard.say("Hey, what are you doin'!")
        with(security) { access.throwInCell(guard) }
    }

    private suspend fun ProtectedAccess.searchWagon() {
        if (player.ttCartReady) {
            mesbox("There is space on the cart for you get on, would you like to try?")
            var option = 0
            startDialogue {
                option = choice3("Yes, I'll get on.", 1, "No, I've got other plans.", 2, "Attract mine cart drivers attention.", 3)
            }
            when (option) {
                1 -> rideOut()
                2 -> mes("You decide not to get onto the cart.")
                3 -> {
                    val driver = npcSearch.find(coords, CART_DRIVER, DRIVER_REACH, HuntVis.Off) ?: return
                    driver.say("Ahem.")
                    startDialogue(driver) {
                        chatNpc(neutral, "Hurry up, get in the cart or I'll go without you!")
                    }
                }
            }
            return
        }
        when {
            player.ttAnaLocation == ANA_ON_WAGON ->
                mesbox("You can see the barrel with Ana in it on the cart already.")
            ANA_IN_A_BARREL in inv ->
                mesbox("There should be enough space for Ana (in the barrel) to go on here.")
            else -> {
                mes("You search the mine cart.")
                mes("This looks like a mine cart which takes barrels out of the encampment to Al Kharid.")
            }
        }
    }

    private suspend fun ProtectedAccess.loadAna() {
        if (invDel(inv, ANA_IN_A_BARREL).failure) {
            return
        }
        anim(LOAD_SEQ)
        quest.moveAna(player, ANA_ON_WAGON)
        quest.advanceFrom(this, STAGE_RETRIEVED_ANA_SURFACE, STAGE_ANA_ON_WAGON)
        mesbox(
            "You place Ana (In the barrel) carefully on the cart. This was the last barrel to go " +
                "on the cart, but the cart driver doesn't seem to be in any rush to get going. And " +
                "the desert heat will soon get to Ana.",
        )
    }

    /** The ride out through the gates: the player hides on the cart and Ana's barrel comes too. */
    private suspend fun ProtectedAccess.rideOut() {
        mesbox("You decide to climb onto the cart.")
        ifClose()
        anim(CLIMB_SEQ)
        delay(1)
        soundSynth(SOUND_CART_LOOP)
        fadeToBlack()
        player.ttCartReady = false
        telejump(TouristTrapCoords.WAGON_DROP_OFF, TeleportType.Exempt)
        if (player.ttAnaLocation == ANA_ON_WAGON && invAdd(inv, ANA_IN_A_BARREL).success) {
            quest.moveAna(player, ANA_CARRIED)
            quest.advanceTo(this, STAGE_ESCAPED)
        }
        delay(1)
        fadeFromBlack()
        mesbox(
            "As soon as you get on the cart, it starts to move. Before too long you are past the " +
                "gates. You jump off the cart taking Ana with you.",
        )
    }

    private companion object {
        const val WAGON = "loc.tourtrap_qip_multi_flatback_cart"
        const val LOUD_PSST = "Pssssssssttt!!!"
        const val BRIBE = 100
        const val GUARD_REACH = 6
        const val DRIVER_REACH = 6
        const val LOAD_SEQ = "seq.human_pickuptable"
        const val CLIMB_SEQ = "seq.human_reachforladder"
    }
}
