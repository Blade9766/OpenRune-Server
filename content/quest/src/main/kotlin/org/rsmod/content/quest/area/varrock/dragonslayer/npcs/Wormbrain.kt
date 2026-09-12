package org.rsmod.content.quest.area.varrock.dragonslayer.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.COINS
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.MAP_PART_LOZAR
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_BRIEFED
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.WORMBRAIN_PRICE
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Wormbrain, the goblin thief in the Port Sarim jail who ended up with Lozar's map piece. He
 * sells it for 10,000 coins; killing him drops it instead (see the Wormbrain drop table and
 * [org.rsmod.content.quest.area.varrock.dragonslayer.WormbrainAttackHook]). Since he can only be
 * fought through the bars, his drops land at the killer's feet rather than inside the cell.
 */
class Wormbrain
@Inject
constructor(
    private val dragonSlayer: DragonSlayerQuest,
    private val death: NpcDeath,
    private val playerList: PlayerList,
) : PluginScript() {

    private val wormbrainType =
        ServerCacheManager.getNpc(WORMBRAIN.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $WORMBRAIN")

    override fun ScriptContext.startup() {
        onNpcQueue(wormbrainType, "queue.death") { beatenUp() }
        onOpNpc1(WORMBRAIN) { startDialogue(it.npc) { wormbrain() } }
        // He is locked in a cell, so Talk-to can never be reached; talk through the bars instead.
        onApNpc1(WORMBRAIN) {
            if (isWithinApRange(it.npc, TALK_RANGE)) {
                startDialogue(it.npc) { wormbrain() }
            }
        }
    }

    /** Wormbrain drops whatever he has where the player stands, since the cell is locked. */
    private suspend fun StandardNpcAccess.beatenUp() {
        val hero = findHero(playerList)
        say("Ow!")
        if (hero != null && dragonSlayer.stage(hero) >= STAGE_BRIEFED && !dragonSlayer.hasMapPiece(hero, MAP_PART_LOZAR)) {
            hero.mes("Wormbrain drops a map piece on the floor.")
        }
        death.deathWithDrops(this, dropCoords = hero?.coords ?: coords)
    }

    private suspend fun Dialogue.wormbrain() {
        chatNpc(quiz, "Whut you want?")
        val wantsMap =
            dragonSlayer.stage(player) >= STAGE_BRIEFED && !dragonSlayer.hasMapPiece(player, MAP_PART_LOZAR)
        if (wantsMap) {
            when (
                choice3(
                    "I believe you've got a piece of map that I need.", 1,
                    "What are you in for?", 2,
                    "Sorry, thought this was a zoo.", 3,
                )
            ) {
                1 -> mapPiece()
                2 -> whatAreYouInFor()
                3 -> chatPlayer(laugh, "Sorry, thought this was a zoo.")
            }
        } else {
            when (
                choice2(
                    "What are you in for?", 1,
                    "Sorry, thought this was a zoo.", 2,
                )
            ) {
                1 -> whatAreYouInFor()
                2 -> chatPlayer(laugh, "Sorry, thought this was a zoo.")
            }
        }
    }

    private suspend fun Dialogue.whatAreYouInFor() {
        chatPlayer(quiz, "What are you in for?")
        chatNpc(confused, "Me not sure. Me pick some stuff up and take it away.")
        chatPlayer(quiz, "Well, did the stuff belong to you?")
        chatNpc(confused, "Umm... no.")
        chatPlayer(neutral, "Well, that would be why, then.")
        chatNpc(neutral, "Oh, right.")
    }

    private suspend fun Dialogue.mapPiece() {
        chatPlayer(neutral, "I believe you've got a piece of map that I need.")
        chatNpc(quiz, "So? Why should me be giving it to you? What you do for Wormbrain?")
        while (true) {
            when (
                choice4(
                    "I'm not going to do anything for you. Forget it.", 1,
                    "I'll let you live. I could just kill you.", 2,
                    "I suppose I could pay you for the map piece...", 3,
                    "Where did you get the map piece from?", 4,
                )
            ) {
                1 -> {
                    chatPlayer(angry, "I'm not going to do anything for you. Forget it.")
                    chatNpc(angry, "Be dat way, then.")
                    return
                }
                2 -> {
                    chatPlayer(angry, "I'll let you live. I could just kill you.")
                    chatNpc(laugh, "Ha! Me in here and you out dere. You not get map piece.")
                }
                3 -> {
                    haggle()
                    return
                }
                4 -> {
                    chatPlayer(quiz, "Where did you get the map piece from?")
                    chatNpc(laugh, "We rob house of stupid wizard. She very old, not put up much fight at all. Hahaha!")
                    chatPlayer(worried, "Uh... hahaha.")
                    chatNpc(neutral, "Her house full of pictures of a city on island, and old pictures of people. Me not recognise island.")
                    chatNpc(neutral, "Me find map piece. Me not know what it is, but it in locked box so me figure it important.")
                    chatNpc(sad, "But by the time me get box open, other goblins gone. Then me not run fast enough and guards catch me.")
                    chatNpc(quiz, "But now you want map piece, so must be special! What you do for me to get it?")
                }
            }
        }
    }

    private suspend fun Dialogue.haggle() {
        chatPlayer(quiz, "I suppose I could pay you for the map piece. Say, 500 coins?")
        chatNpc(angry, "Me not stoopid, it worth at least 10,000 coins!")
        when (
            choice2(
                "You must be joking! Forget it.", 1,
                "Alright then, 10,000 it is.", 2,
            )
        ) {
            1 -> {
                chatPlayer(shocked, "You must be joking! Forget it.")
                chatNpc(angry, "Fine, you not get map piece.")
            }
            2 -> {
                chatPlayer(neutral, "Alright then, 10,000 coins it is.")
                if (player.inv.count(COINS) < WORMBRAIN_PRICE) {
                    chatPlayer(sad, "Oops, I don't have enough on me.")
                    chatNpc(bored, "Comes back when you has enough.")
                    return
                }
                if (access.invAdd(access.inv, MAP_PART_LOZAR).failure) {
                    chatPlayer(sad, "Hold on, I don't have anywhere to put it.")
                    chatNpc(bored, "Comes back when you has room, then.")
                    return
                }
                access.invDel(access.inv, COINS, WORMBRAIN_PRICE)
                objbox(MAP_PART_LOZAR, "You buy the map piece from Wormbrain.")
                chatNpc(laugh, "Fank you very much! Now me can bribe da guards, hehehe.")
            }
        }
    }

    private companion object {
        const val WORMBRAIN = "npc.wormbrain"

        /** Far enough to talk across the corridor and through the cell door. */
        const val TALK_RANGE = 3
    }
}
