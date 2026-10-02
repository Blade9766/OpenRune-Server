package org.rsmod.content.quest.area.morytania.ghostsahoy.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOW_ASKED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.BOW_SIGNED
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.COINS
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.MAX_DEBT_UNITS
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.OAK_LONGBOW
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.ROBIN
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.RUNEDRAW_STAKE
import org.rsmod.content.quest.area.morytania.ghostsahoy.GhostsAhoyQuest.Companion.SIGNED_OAK_LONGBOW
import org.rsmod.content.quest.area.morytania.ghostsahoy.RuneDrawGame
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Robin, the master bowman lodging at the inn, and his game of Rune-Draw.
 *
 * Each game stakes [RUNEDRAW_STAKE] coins, taken from the player when the bag is opened. A loss
 * leaves the stake with Robin. A win returns the stake, and while Ak-Haranu's bow is wanted Robin
 * runs up a debt of the same amount instead of paying (four wins, in any order, to reach 100
 * coins); otherwise he pays out. Once he owes 100 coins he signs an oak longbow to clear it.
 *
 * The game is held server-side per player and settled exactly once: it is removed from [games]
 * before anything is paid, so repeated button packets, closing the board or logging out mid-game
 * can't settle it twice. Leaving a game unfinished forfeits the stake.
 */
@Singleton
class Robin
@Inject
constructor(private val ahoy: GhostsAhoyQuest, private val objRepo: ObjRepository) : PluginScript() {

    private val games = HashMap<PlayerUid, RuneDrawGame>()

    override fun ScriptContext.startup() {
        onOpNpc1(ROBIN) { startDialogue(it.npc) { robin() } }
        onIfModalButton(DRAW_BUTTON) { draw() }
        onIfModalButton(HOLD_BUTTON) { hold() }
        onIfClose(BOARD) { games.remove(player.uid) }
        onPlayerLogout { games.remove(player.uid) }
    }

    fun gameOf(player: Player): RuneDrawGame? = games[player.uid]

    private suspend fun Dialogue.robin() {
        chatNpc(neutral, "Ah, a fellow mortal! Not many of us about in this town.")
        val bow = ahoy.bowState(player)
        val asked = bow in BOW_ASKED..BOW_ASKED + MAX_DEBT_UNITS
        val options = buildList {
            if (asked) add("Will you sign an oak longbow for me?" to Topic.SIGN)
            add("Fancy a game of Rune-Draw?" to Topic.PLAY)
            add("How do you play Rune-Draw?" to Topic.RULES)
            add("Goodbye." to Topic.BYE)
        }
        when (menu(options)) {
            Topic.SIGN -> sign()
            Topic.PLAY -> offerGame()
            Topic.RULES -> rules()
            Topic.BYE -> chatPlayer(neutral, "Goodbye.")
        }
    }

    private suspend fun Dialogue.sign() {
        chatPlayer(quiz, "Will you sign an oak longbow for me? A trader at the docks is a great admirer of yours.")
        if (ahoy.robinDebt(player) < MAX_DEBT_UNITS * RUNEDRAW_STAKE) {
            chatNpc(shifty, "Sign things for strangers? I'm here for a quiet holiday, not to hand out autographs.")
            return
        }
        chatPlayer(neutral, "You owe me 100 coins from Rune-Draw. I'm sure the ghosts would love to hear how a mortal guest welches on his debts.")
        chatNpc(shocked, "Keep your voice down! Fine, fine - I'll sign your bow and we'll call it square.")
        if (!player.inv.contains(OAK_LONGBOW)) {
            chatNpc(neutral, "You'll have to bring me an oak longbow first, though. I don't carry spares.")
            return
        }
        val signed = checkNotNull(ServerCacheManager.getItem(SIGNED_OAK_LONGBOW.asRSCM()))
        val slot = player.inv.indexOfFirst { it?.id == OAK_LONGBOW.asRSCM() }
        if (access.invReplaceSlot(access.inv, slot, 1, signed).failure) {
            return
        }
        ahoy.setBowState(player, BOW_SIGNED)
        objbox(SIGNED_OAK_LONGBOW, "Robin scrawls his signature along the oak longbow.")
        chatNpc(sad, "There. Now we're even, and you'll keep quiet, won't you?")
    }

    private suspend fun Dialogue.rules() {
        chatPlayer(quiz, "How do you play Rune-Draw?")
        chatNpc(happy, "Simple! There are ten runes in a bag: air, mind, water, earth, fire, body, cosmic, chaos, nature and death.")
        chatNpc(neutral, "Air is worth one, mind two, and so on up to nature at nine. We take turns drawing, and the highest score wins.")
        chatNpc(neutral, "Draw the death rune and you've lost, whatever your score. You can hold and stop drawing whenever you like.")
        chatNpc(neutral, "Then the other player keeps drawing until they beat you, or draw death. It's $RUNEDRAW_STAKE coins a game.")
    }

    private suspend fun Dialogue.offerGame() {
        chatPlayer(happy, "Fancy a game of Rune-Draw?")
        val bow = ahoy.bowState(player)
        if (bow == BOW_ASKED + MAX_DEBT_UNITS) {
            chatNpc(sad, "I already owe you 100 coins. I'm not playing you again until we've settled that.")
            return
        }
        if (player.inv.count(COINS) < RUNEDRAW_STAKE) {
            chatNpc(neutral, "It's $RUNEDRAW_STAKE coins a game, friend, and you haven't got them. Come back when you have.")
            return
        }
        chatNpc(happy, "Always! $RUNEDRAW_STAKE coins a game. You draw first.")
        access.startGame()
    }

    private fun ProtectedAccess.startGame() {
        if (games.containsKey(player.uid)) {
            return
        }
        if (invDel(inv, COINS, RUNEDRAW_STAKE).failure) {
            return
        }
        val game = RuneDrawGame(RuneDrawGame.shuffledBag { random.of(it) })
        games[player.uid] = game
        ifOpenMainModal(BOARD)
        ifSetEvents(DRAW_BUTTON, 0..0, IfEvent.Op1)
        ifSetEvents(HOLD_BUTTON, 0..0, IfEvent.Op1)
        render(game)
    }

    fun ProtectedAccess.draw() {
        val game = games[player.uid] ?: return
        game.playerDraw()
        render(game)
        settleIfOver(game)
    }

    fun ProtectedAccess.hold() {
        val game = games[player.uid] ?: return
        game.playerHold()
        render(game)
        settleIfOver(game)
    }

    private fun ProtectedAccess.settleIfOver(game: RuneDrawGame) {
        if (!game.isOver || games.remove(player.uid) !== game) {
            return
        }
        when (game.result) {
            RuneDrawGame.Result.WON -> won()
            else -> mes("You lose the game of Rune-Draw.")
        }
    }

    private fun ProtectedAccess.won() {
        val bow = ahoy.bowState(player)
        val owing = bow in BOW_ASKED until BOW_ASKED + MAX_DEBT_UNITS
        val payout = if (owing) RUNEDRAW_STAKE else RUNEDRAW_STAKE * 2
        if (invAdd(inv, COINS, payout).failure) {
            objRepo.add(COINS, coords, COIN_DROP_TICKS, receiver = player, count = payout)
        }
        mes("You win the game of Rune-Draw!")
        if (owing) {
            ahoy.setBowState(player, bow + 1)
            mes("Robin can't pay up. He now owes you ${ahoy.robinDebt(player)} coins.")
        } else {
            mes("Robin pays you $RUNEDRAW_STAKE coins.")
        }
    }

    private fun ProtectedAccess.render(game: RuneDrawGame) {
        for (i in 0 until RuneDrawGame.BAG_SIZE) {
            slot(i + 1, game.playerRunes.getOrNull(i))
            slot(i + 1 + RuneDrawGame.BAG_SIZE, game.robinRunes.getOrNull(i))
        }
        ifSetText(SCORE_PLAYER, scoreText(game.playerRunes, game.playerScore))
        ifSetText(SCORE_ROBIN, scoreText(game.robinRunes, game.robinScore))
        ifSetText(STATUS_PLAYER, status(game.playerHeld, game.result == RuneDrawGame.Result.WON, game.isOver))
        ifSetText(STATUS_ROBIN, status(game.robinHeld, game.result == RuneDrawGame.Result.LOST, game.isOver))
        ifSetHide(DRAW_LAYER, game.isOver || game.playerHeld)
        ifSetHide(HOLD_LAYER, game.isOver || game.playerHeld)
    }

    private fun ProtectedAccess.slot(index: Int, rune: RuneDrawGame.Rune?) {
        val component = "component.ahoy_runedraw:runedraw_slot_$index"
        if (rune == null) {
            ifSetHide(component, true)
            return
        }
        ifSetHide(component, false)
        ifSetObj(component, rune.obj, RUNE_ZOOM)
    }

    private fun scoreText(runes: List<RuneDrawGame.Rune>, score: Int): String =
        if (runes.lastOrNull() == RuneDrawGame.Rune.DEATH) "DEATH" else score.toString()

    private fun status(held: Boolean, won: Boolean, over: Boolean): String =
        when {
            over && won -> "Winner!"
            over -> ""
            held -> "Holding"
            else -> ""
        }

    private enum class Topic {
        SIGN,
        PLAY,
        RULES,
        BYE,
    }

    companion object {
        const val BOARD = "interface.ahoy_runedraw"
        const val DRAW_BUTTON = "component.ahoy_runedraw:runedraw_btn_draw"
        const val HOLD_BUTTON = "component.ahoy_runedraw:runedraw_btn_hold"
        private const val DRAW_LAYER = "component.ahoy_runedraw:runedraw_layer_draw"
        private const val HOLD_LAYER = "component.ahoy_runedraw:runedraw_layer_hold"
        private const val SCORE_PLAYER = "component.ahoy_runedraw:runedraw_score_l"
        private const val SCORE_ROBIN = "component.ahoy_runedraw:runedraw_score_r"
        private const val STATUS_PLAYER = "component.ahoy_runedraw:runedraw_totalscore_l"
        private const val STATUS_ROBIN = "component.ahoy_runedraw:runedraw_totalscore_r"
        private const val RUNE_ZOOM = 1027
        private const val COIN_DROP_TICKS = 200
    }
}
