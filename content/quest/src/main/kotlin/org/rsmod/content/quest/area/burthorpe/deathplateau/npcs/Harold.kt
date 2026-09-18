package org.rsmod.content.quest.area.burthorpe.deathplateau.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.ASGARNIAN_ALE
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.BLURBERRY_SPECIAL
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.COINS
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.COMBINATION
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.HAROLD
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.IOU
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.PREMADE_BLURBERRY_SPECIAL
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.STAGE_BUY_DRINK
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.STAGE_COMBINATION
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.STAGE_FIND_HAROLD
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.STAGE_GAMBLE
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.STAGE_HAROLD_REFUSED
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.STAGE_IOU
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpDiceHarold
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpDiceStake
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpHaroldDrunk
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpHaroldPurse
import org.rsmod.content.quest.area.burthorpe.deathplateau.owns
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Harold, last night's guard, drinking and gambling his shame away in his room above the Toad
 * and Chicken, and the dice game (interface `death_dice`) that wins the combination off him.
 *
 * Harold rolls his two dice first; the player then clicks "Roll Dice!" for theirs and the higher
 * total takes both stakes, Harold keeping them on a draw. His purse starts at [STARTING_PURSE]
 * when he gets his ale, grows with every stake he wins and shrinks with every one he pays out.
 * Once a win of [IOU_MINIMUM_BET] or more is bigger than what is left in it, he pays what he has
 * and writes the rest on an IOU - on the back of the combination. A Blurberry special leaves him
 * too drunk to count, so the player wins every game.
 */
class Harold
@Inject
constructor(
    private val quest: DeathPlateauQuest,
    private val passages: GenericPassageScript,
    private val objRepo: ObjRepository,
) : PluginScript() {

    private val doorType: ObjectServerType =
        ServerCacheManager.getObject(HAROLDS_DOOR.asRSCM(RSCMType.LOC))
            ?: error("Missing loc: $HAROLDS_DOOR")

    override fun ScriptContext.startup() {
        onOpNpc1(HAROLD) { startDialogue(it.npc) { harold() } }
        onOpNpcU(HAROLD) {
            if (it.objType.id == ASGARNIAN_ALE.asRSCM(RSCMType.OBJ)) {
                startDialogue(it.npc) { buyDrink(offered = true) }
            } else {
                mes("Nothing interesting happens.")
            }
        }
        onOpLoc1(HAROLDS_DOOR) { haroldsDoor(it.loc) }
        onOpHeld1(IOU) { readIou() }
        onOpHeld1(COMBINATION) { readCombination() }
        onIfModalButton(ROLL_BUTTON) { playerRoll() }
    }

    /* Harold's room */

    private suspend fun ProtectedAccess.haroldsDoor(door: BoundLocInfo) {
        arriveDelay()
        val leaving = coords.z < door.coords.z
        if (leaving) {
            with(passages) { walkThrough(door, doorType) }
            return
        }
        if (!quest.isComplete(player) && quest.stage(player) < STAGE_FIND_HAROLD) {
            startDialogue {
                chatPlayer(neutral, "I'd better not go into someone's bedroom without a reason!")
            }
            return
        }
        soundSynth(KNOCK_SOUND)
        mesbox("You knock on the door.")
        startDialogue { chatNpcSpecific("Harold", HAROLD, happy, "Come in!") }
        with(passages) { walkThrough(door, doorType) }
    }

    /* Talking to Harold */

    private suspend fun Dialogue.harold() {
        val stage = quest.stage(player)
        when {
            quest.isComplete(player) -> afterTheQuest()
            stage < STAGE_FIND_HAROLD -> {
                chatPlayer(neutral, "Hello there.")
                chatNpc(bored, "Hi.")
            }
            stage == STAGE_FIND_HAROLD || stage == STAGE_HAROLD_REFUSED -> sulking(stage)
            stage == STAGE_BUY_DRINK -> {
                chatPlayer(neutral, "Hello there.")
                chatNpc(bored, "What?")
                buyDrink(offered = false)
            }
            else -> talkative()
        }
    }

    private suspend fun Dialogue.sulking(stage: Int) {
        chatPlayer(neutral, "Hello there.")
        chatNpc(bored, if (stage == STAGE_FIND_HAROLD) "Hi." else "What?")
        val choice =
            choice2(
                "You're the guard that was on duty last night?",
                true,
                "Can I buy you a drink?",
                false,
            )
        if (!choice) {
            buyDrink(offered = false)
            return
        }
        chatPlayer(quiz, "You're the guard that was on duty last night?")
        if (stage == STAGE_HAROLD_REFUSED) {
            chatNpc(angry, "I said I don't want to talk about it!")
            return
        }
        chatNpc(bored, "Yeah.")
        chatPlayer(quiz, "Denulth said that you lost the combination to the equipment room?")
        chatNpc(angry, "I don't want to talk about it!")
        quest.advanceTo(access, STAGE_HAROLD_REFUSED)
    }

    private suspend fun Dialogue.talkative() {
        if (lostTheIou()) {
            return
        }
        val isDrunk = player.dpHaroldDrunk
        chatPlayer(neutral, "Hello there.")
        if (isDrunk) {
            chatNpc(drunk, "'Ello matey!")
        } else {
            chatNpc(neutral, "Hi.")
        }
        when (
            choice3(
                "Where were you when you last had the combination?",
                1,
                "Would you like to gamble?",
                2,
                "Can I buy you a drink?",
                3,
            )
        ) {
            1 -> lastHadTheCombination(isDrunk)
            2 -> gamble(isDrunk)
            else -> {
                if (isDrunk) {
                    chatPlayer(quiz, "Can I buy you a drink?")
                    chatNpc(drunk, "I fink I've had enough!")
                } else if (quest.stage(player) >= STAGE_IOU) {
                    buyDrink(offered = false)
                } else {
                    blurberry()
                }
            }
        }
    }

    private suspend fun Dialogue.lastHadTheCombination(isDrunk: Boolean) {
        chatPlayer(quiz, "Where were you when you last had the combination?")
        if (isDrunk) {
            chatNpc(drunk, "Hmm...!")
            chatNpc(drunk, "Er...!")
            chatNpc(drunk, "What wash the queshtion?")
            return
        }
        chatNpc(
            sad,
            "I honestly don't know! I've looked everywhere. I've searched the castle and my room!",
        )
        chatPlayer(quiz, "Have you tried looking between here and the castle?")
        chatNpc(sad, "Yeah, I tried that.")
        chatNpc(sad, "I need another beer.")
    }

    /** Asgarnian ale: what gets him talking at all, and all he wants once he is broke. */
    private suspend fun Dialogue.buyDrink(offered: Boolean) {
        if (quest.isComplete(player)) {
            afterTheQuest()
            return
        }
        val stage = quest.stage(player)
        if (stage < STAGE_FIND_HAROLD) {
            chatNpc(bored, "No thanks.")
            return
        }
        if (player.dpHaroldDrunk) {
            chatPlayer(quiz, "Can I buy you a drink?")
            chatNpc(drunk, "I fink I've had enough!")
            return
        }
        chatPlayer(quiz, "Can I buy you a drink?")
        chatNpc(happy, "Now you're talking! An Asgarnian Ale please!")
        if (ASGARNIAN_ALE !in player.inv) {
            chatPlayer(neutral, "I'll go and get you one.")
            return
        }
        access.invDel(access.inv, ASGARNIAN_ALE)
        objbox(ASGARNIAN_ALE, "You give Harold an Asgarnian Ale.")
        access.mes("You give Harold an Asgarnian Ale.")
        access.soundSynth(DRINK_SOUND)
        if (stage >= STAGE_GAMBLE) {
            chatNpc(happy, ALE_THANKS.random())
            return
        }
        chatNpc(happy, "Arrh. That hit the spot!")
        player.dpHaroldPurse = STARTING_PURSE
        quest.advanceTo(access, STAGE_GAMBLE)
        if (offered) {
            return
        }
        when (
            choice2(
                "Where were you when you last had the combination?",
                true,
                "Would you like to gamble?",
                false,
            )
        ) {
            true -> lastHadTheCombination(isDrunk = false)
            false -> gamble(isDrunk = false)
        }
    }

    private suspend fun Dialogue.blurberry() {
        chatPlayer(quiz, "Can I buy you a drink?")
        chatNpc(happy, "Sounds good! I normally drink Asgarnian Ale but you know what?")
        chatPlayer(quiz, "What?")
        chatNpc(
            happy,
            "I really fancy one of those Blurberry Specials. I never get over to the Gnome " +
                "Stronghold so I haven't had one for ages!",
        )
        val cocktail =
            listOf(BLURBERRY_SPECIAL, PREMADE_BLURBERRY_SPECIAL).firstOrNull { it in player.inv }
        if (cocktail == null) {
            chatPlayer(neutral, "I'll go and get you one.")
            return
        }
        access.invDel(access.inv, cocktail)
        objbox(cocktail, "You give Harold a Blurberry Special.")
        access.mes("You give Harold a Blurberry Special.")
        access.soundSynth(DRINK_SOUND)
        npc?.say("Wow!")
        chatNpc(drunk, "Now THAT hit the spot!")
        player.dpHaroldDrunk = true
    }

    /** Harold writes out another IOU, or the combination itself once its back has been read. */
    private suspend fun Dialogue.lostTheIou(): Boolean {
        val stage = quest.stage(player)
        if (stage < STAGE_IOU || access.owns(IOU) || access.owns(COMBINATION)) {
            return false
        }
        chatPlayer(neutral, "Hello there.")
        if (player.dpHaroldDrunk) {
            chatNpc(drunk, "*hic*")
            chatPlayer(sad, "I've lost the IOU you gave me.")
            chatNpc(drunk, "Oh dear.")
        } else {
            chatNpc(neutral, "Hi.")
            chatPlayer(sad, "I've lost the IOU you gave me.")
            chatNpc(neutral, "I'll write you another.")
        }
        if (stage >= STAGE_COMBINATION) {
            access.invAddOrDrop(objRepo, COMBINATION)
            objbox(COMBINATION, "Harold has given you the IOU, which you know is the combination.")
        } else {
            access.invAddOrDrop(objRepo, IOU)
            objbox(IOU, "Harold has given you an IOU scribbled on some paper.")
        }
        return true
    }

    private suspend fun Dialogue.afterTheQuest() {
        chatPlayer(neutral, "Hello there.")
        chatNpc(neutral, "Hi.")
        chatPlayer(quiz, "Can I buy you a drink?")
        chatNpc(happy, "Now you're talking! An Asgarnian Ale please!")
        if (ASGARNIAN_ALE !in player.inv) {
            chatPlayer(neutral, "I'll go and get you one.")
            return
        }
        access.invDel(access.inv, ASGARNIAN_ALE)
        objbox(ASGARNIAN_ALE, "You give Harold an Asgarnian ale.")
        access.soundSynth(DRINK_SOUND)
        chatNpc(happy, "*burp*")
    }

    /* The dice game */

    private suspend fun Dialogue.gamble(isDrunk: Boolean) {
        chatPlayer(quiz, "Would you like to gamble?")
        if (quest.stage(player) >= STAGE_IOU) {
            if (isDrunk) {
                chatNpc(drunk, "I'm shure I had money, not anymore!")
            } else {
                chatNpc(sad, "I've run out of money!")
                chatNpc(sad, "Oh dear. I need beer.")
            }
            return
        }
        if (isDrunk) {
            chatNpc(drunk, "Shure!")
            chatNpc(drunk, "Place your betsh pleashe!")
            chatNpc(drunk, "*giggle*")
        } else {
            chatNpc(happy, "Good. Good. I have some dice. How much do you want to offer?")
        }
        val stake = access.countDialog("Enter amount:")
        if (stake <= 0) {
            access.mes("You have to offer some money.")
            return
        }
        if (stake > access.invTotal(access.inv, COINS)) {
            access.mes("You do not have that much money!")
            return
        }
        if (stake > MAX_BET) {
            if (isDrunk) {
                chatNpc(drunk, "Eashy tiger! Max bet ish 1000 coinsh.")
            } else {
                chatNpc(shocked, "Woah! Do you think I'm made of money? Max bet is 1000 gold.")
            }
            return
        }
        if (isDrunk) {
            chatNpc(drunk, "Right...er...here goes...")
        } else {
            chatNpc(happy, "OK. I'll roll first!")
            chatNpc(
                neutral,
                "Don't forget that once I start my roll you can't back out of the bet! If you " +
                    "do you lose your stake!",
            )
        }
        access.haroldRoll(stake)
    }

    private suspend fun ProtectedAccess.haroldRoll(stake: Int) {
        if (invDel(inv, COINS, stake).failure) {
            return
        }
        player.dpDiceStake = stake
        ifOpenMainModal(DICE_INTERFACE)
        ifSetText("$DICE:death_gamble_playername", player.displayName)
        ifSetText("$DICE:death_gamble_harold_stake", stake.toString())
        ifSetText("$DICE:death_gamble_player_stake", stake.toString())
        ifSetObj("$DICE:death_gamble_harold_gold1", coinsModel(stake), GOLD_ZOOM)
        ifSetObj("$DICE:death_gamble_player_gold1", coinsModel(stake), GOLD_ZOOM)
        ifSetHide("$DICE:death_gamble_result", true)
        ifSetHide("$DICE:death_gamble_continue", true)
        ifSetHide("$DICE:death_gamble_roll", true)

        val first = random.of(1, DIE_FACES)
        val second = random.of(1, DIE_FACES)
        player.dpDiceHarold = first + second
        soundSynth(SHAKE_SOUND)
        rollDie("$DICE:death_gamble_harold_dice1", first)
        rollDie("$DICE:death_gamble_harold_dice2", second)
        delay(ROLL_TICKS)
        soundSynth(ROLL_SOUND)
        ifSetEvents(ROLL_BUTTON, 0..0, IfEvent.Op1)
        ifSetHide("$DICE:death_gamble_roll", false)
    }

    private suspend fun ProtectedAccess.playerRoll() {
        val stake = player.dpDiceStake
        if (stake <= 0) {
            return
        }
        player.dpDiceStake = 0
        ifSetHide("$DICE:death_gamble_roll", true)
        val first = random.of(1, DIE_FACES)
        val second = random.of(1, DIE_FACES)
        soundSynth(SHAKE_SOUND)
        rollDie("$DICE:death_gamble_player_dice1", first)
        rollDie("$DICE:death_gamble_player_dice2", second)
        delay(ROLL_TICKS)
        soundSynth(ROLL_SOUND)

        val isDrunk = player.dpHaroldDrunk
        val won = isDrunk || first + second > player.dpDiceHarold
        ifSetText("$DICE:death_gamble_result", if (won) "You win!" else "Harold wins!")
        ifSetHide("$DICE:death_gamble_result", false)
        ifSetHide("$DICE:death_gamble_continue", false)

        if (!won) {
            player.dpHaroldPurse = (player.dpHaroldPurse + stake).coerceAtMost(MAX_PURSE)
            mes("You give Harold his winnings.")
            pauseButton()
            return
        }
        val purse = player.dpHaroldPurse
        val bankrupt = stake >= IOU_MINIMUM_BET && purse < stake
        if (bankrupt) {
            invAddOrDrop(objRepo, COINS, stake + purse)
            invAddOrDrop(objRepo, IOU)
            player.dpHaroldPurse = 0
            quest.advanceTo(this, STAGE_IOU)
            mes("Harold has given you an IOU scribbled on some paper.")
        } else {
            invAddOrDrop(objRepo, COINS, stake * 2)
            player.dpHaroldPurse = (purse - stake).coerceAtLeast(0)
            mes("Harold has given you your winnings!")
        }
        pauseButton()
        startDialogue { afterTheWin(isDrunk, bankrupt) }
    }

    private suspend fun Dialogue.afterTheWin(isDrunk: Boolean, bankrupt: Boolean) {
        if (isDrunk) {
            chatNpcSpecific("Harold", HAROLD, drunk, DRUNK_ROLLS.random())
            mesbox("Harold is so drunk he can hardly see, let alone count!")
        }
        if (!bankrupt) {
            return
        }
        if (isDrunk) {
            chatNpcSpecific("Harold", HAROLD, drunk, "Um...not enough money.")
            chatNpcSpecific("Harold", HAROLD, drunk, "Heresh shome of it.")
            objbox(COINS_STACK, "Harold has given you some of your winnings!")
            chatNpcSpecific("Harold", HAROLD, drunk, "I owe you the resht!")
        } else {
            chatNpcSpecific("Harold", HAROLD, sad, "Oh dear, I seem to have run out of money!")
            chatNpcSpecific("Harold", HAROLD, sad, "Here's what I have.")
            objbox(COINS_STACK, "Harold has given you some of your winnings!")
            chatNpcSpecific("Harold", HAROLD, neutral, "I'll write you out an IOU for the rest.")
        }
        objbox(IOU, "Harold has given you an IOU scribbled on some paper.")
    }

    private fun ProtectedAccess.rollDie(component: String, face: Int) {
        val seq = ServerCacheManager.getAnim("seq.dice_roll_$face".asRSCM(RSCMType.SEQ))
        ifSetAnim(component, seq)
    }

    private fun coinsModel(amount: Int): String =
        when {
            amount >= 1000 -> "obj.coins_1000"
            amount >= 250 -> "obj.coins_250"
            amount >= 100 -> "obj.coins_100"
            amount >= 25 -> "obj.coins_25"
            amount >= 5 -> "obj.coins_5"
            amount >= 2 -> "obj.coins_$amount"
            else -> COINS
        }

    /* The IOU and the combination */

    private suspend fun ProtectedAccess.readIou() {
        startDialogue {
            chatPlayer(neutral, "The IOU says that Harold owes me some money.")
            chatPlayer(shocked, "Wait just a minute!")
            chatPlayer(
                angry,
                "The IOU is written on the back of the combination! The stupid guard had it in " +
                    "his back pocket all the time!",
            )
        }
        if (invReplace(inv, IOU, 1, COMBINATION).failure) {
            return
        }
        quest.advanceTo(this, STAGE_COMBINATION)
        objbox(COMBINATION, "You have found the combination!")
        mes("You have found the combination!")
    }

    private fun ProtectedAccess.readCombination() {
        ifOpenMain("interface.questjournal")
        runClientScript(SCROLL_INIT_SCRIPT)
        ifSetText("component.questjournal:title", "<col=7f0000>Combination</col>")
        for (line in 1..SCROLL_LINES) {
            ifSetText("component.questjournal:qj$line", COMBINATION_TEXT.getOrElse(line - 1) { "" })
        }
    }

    private companion object {
        const val HAROLDS_DOOR = "loc.death_harold_door"
        const val DICE_INTERFACE = "interface.death_dice"
        const val DICE = "component.death_dice"
        const val ROLL_BUTTON = "component.death_dice:death_gamble_roll_button"
        const val COINS_STACK = "obj.coins_250"

        const val MAX_BET = 1000
        const val STARTING_PURSE = 100
        const val IOU_MINIMUM_BET = 60
        const val MAX_PURSE = 2047
        const val DIE_FACES = 6
        const val GOLD_ZOOM = 445
        const val ROLL_TICKS = 3

        const val SHAKE_SOUND = "synth.dice_shake"
        const val ROLL_SOUND = "synth.dice_roll"
        const val KNOCK_SOUND = "synth.knock_knock"
        const val DRINK_SOUND = "synth.drink"

        const val SCROLL_INIT_SCRIPT = 5240
        const val SCROLL_LINES = 24

        val COMBINATION_TEXT =
            listOf(
                "",
                "Red is north of Blue.",
                "Yellow is south of Purple.",
                "Green is north of Purple.",
                "Blue is west of Yellow.",
                "Purple is east of Red.",
            )

        val ALE_THANKS = listOf("*burp*", "Mmm... Asgarnian Ale.", "Thanks!", "Arrh. That hit the spot!")

        val DRUNK_ROLLS =
            listOf(
                "Shixteen! How am I shupposhed to beat that!",
                "I didn't know you could ushe four dishe. Oh well.",
                "*hic*",
                "I sheemed to have rolled a one.",
            )
    }
}
