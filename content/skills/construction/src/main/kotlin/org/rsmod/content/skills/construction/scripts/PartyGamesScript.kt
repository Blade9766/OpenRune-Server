package org.rsmod.content.skills.construction.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.interfaces.emotes.PlayEmote
import org.rsmod.content.skills.construction.data.Games
import org.rsmod.content.skills.construction.house.ActiveHouse
import org.rsmod.content.skills.construction.house.HouseGames
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The games room's game space: Jacky Jester, the treasure hunt and hangman.
 *
 * Activating the jester starts a round; each player who activates him is told an emote to copy, and
 * playing it from the emotes tab earns them the next one. The first to copy [Games.JESTER_ROUNDS]
 * wins.
 *
 * The treasure hunt's fairy hides on a clear tile somewhere in the house and every player there is
 * given a treasure stone. Feeling it says how hot the trail is and whether it is warmer or colder
 * than last time; feeling it next to the fairy finds her and wins.
 *
 * Activating hangman stands the hangman in place of his chest with a word from the game's list.
 * Guess-letter takes one letter, or up to five of the letters still missing as a guess at the word -
 * if any of them is wrong none are shown. Every miss adds a piece of armour, and the tenth ends the
 * game; whoever fills in the last letter wins. Reset deals a new word and Banish puts the chest back.
 */
class PartyGamesScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val games: HouseGames,
    private val locRepo: LocRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(Games.JESTER) { startJester(it.loc) }
        onOpLoc1(Games.JESTER_PLAYING) { askJester() }
        onEvent<PlayEmote> { copied(player, seq.id) }

        onOpLoc1(Games.TREASURE_HUNT) { startHunt(it.loc) }
        onOpLoc1(Games.TREASURE_HUNT_OPEN) { mes("The treasure fairy is hiding somewhere in the house.") }
        onOpHeld1(Games.TREASURE_STONE) { feel(it.slot) }

        onOpLoc1(Games.HANGMAN) { startHangman(it.loc) }
        for ((stage, npc) in Games.HANGMAN_STAGES.withIndex()) {
            onOpNpc3(npc) { banishHangman(it.npc) }
            if (stage == Games.HANGMAN_STAGES.lastIndex) {
                onOpNpc1(npc) { resetHangman(it.npc) }
            } else {
                onOpNpc1(npc) { guess(it.npc) }
            }
        }
    }

    private fun ProtectedAccess.house(): ActiveHouse? = registry.houseAt(player.coords)

    // ----------------------------------------------------------------------------- jester

    private fun ProtectedAccess.startJester(loc: BoundLocInfo) {
        val house = house() ?: return
        swap(loc, Games.JESTER_PLAYING)
        games.of(house.owner).jester = HouseGames.JesterRound(loc.coords, loc.angle, loc.shape)
        mes("Jacky Jester springs out of his box! Copy his emotes to win.")
        askJester()
    }

    private fun ProtectedAccess.askJester() {
        val house = house() ?: return
        val round = games.of(house.owner).jester ?: return
        val emote = round.asked.getOrPut(player) { random.of(Games.JESTER_EMOTES.size) }
        round.copied.putIfAbsent(player, 0)
        mes("Jacky Jester performs the ${Games.JESTER_EMOTES[emote].first} emote. Copy him!")
    }

    private fun copied(player: Player, seq: Int) {
        val house = registry.houseAt(player.coords) ?: return
        val round = games.of(house.owner).jester ?: return
        val asked = round.asked[player] ?: return
        val (_, seqs) = Games.JESTER_EMOTES[asked]
        if (seqs.none { it.asRSCM(RSCMType.SEQ) == seq }) {
            return
        }
        val count = (round.copied[player] ?: 0) + 1
        round.copied[player] = count
        if (count < Games.JESTER_ROUNDS) {
            val next = (asked + 1 + (0 until Games.JESTER_EMOTES.size - 1).random()) % Games.JESTER_EMOTES.size
            round.asked[player] = next
            player.mes("Well copied! ($count/${Games.JESTER_ROUNDS}) Now: ${Games.JESTER_EMOTES[next].first}.")
            return
        }
        games.of(house.owner).jester = null
        restore(round.coords, Games.JESTER_PLAYING, Games.JESTER, round.angle, round.shape)
        games.win(house, player, "jester game", round.copied.keys)
    }

    // ------------------------------------------------------------------------ treasure hunt

    private fun ProtectedAccess.startHunt(loc: BoundLocInfo) {
        val house = house() ?: return
        val room = games.of(house.owner)
        if (room.hunt != null) {
            mes("A treasure hunt is already under way.")
            return
        }
        val tile = registry.hidingTiles(house).firstOrNull() ?: return
        val fairy = games.spawn(Games.TREASURE_FAIRY, tile)
        val hunt = HouseGames.TreasureHunt(fairy, loc.coords, loc.angle, loc.shape)
        room.hunt = hunt
        swap(loc, Games.TREASURE_HUNT_OPEN)
        for (other in games.inside(house)) {
            if (other.invAdd(other.inv, Games.TREASURE_STONE, 1).success) {
                hunt.players += other
                other.mes("The treasure fairy flies off to hide. Feel your treasure stone to track her down!")
            }
        }
    }

    private fun ProtectedAccess.feel(slot: Int) {
        val house = house()
        val hunt = house?.let { games.of(it.owner).hunt }
        if (house == null || hunt == null) {
            invDel(inv, Games.TREASURE_STONE, 1, slot = slot)
            mes("The stone crumbles to dust.")
            return
        }
        hunt.players += player
        val distance = distanceTo(hunt.fairy)
        if (distance <= Games.FAIRY_FOUND_RANGE) {
            endHunt(house, hunt)
            games.win(house, player, "treasure hunt", hunt.players)
            return
        }
        val last = hunt.lastDistance.put(player, distance)
        val trend =
            when {
                last == null -> ""
                distance < last -> " It's warmer than before."
                distance > last -> " It's colder than before."
                else -> " It feels the same as before."
            }
        mes("The stone feels ${warmth(distance)}.$trend")
    }

    private fun ProtectedAccess.distanceTo(npc: Npc): Int {
        val flat = player.coords.chebyshevDistance(npc.coords)
        return if (player.coords.level == npc.coords.level) flat else flat + OTHER_FLOOR_DISTANCE
    }

    private fun warmth(distance: Int): String =
        when {
            distance <= HOT -> "hot"
            distance <= WARM -> "warm"
            distance <= COOL -> "cool"
            else -> "cold"
        }

    private fun endHunt(house: ActiveHouse, hunt: HouseGames.TreasureHunt) {
        games.of(house.owner).hunt = null
        games.remove(hunt.fairy)
        restore(hunt.coords, Games.TREASURE_HUNT_OPEN, Games.TREASURE_HUNT, hunt.angle, hunt.shape)
        for (player in hunt.players) {
            while (player.invDel(player.inv, Games.TREASURE_STONE, 1).success) {
                continue
            }
        }
    }

    // ----------------------------------------------------------------------------- hangman

    private fun ProtectedAccess.startHangman(loc: BoundLocInfo) {
        val house = house() ?: return
        val room = games.of(house.owner)
        if (room.hangman != null) {
            mes("A game of hangman is already being played.")
            return
        }
        val stand = games.stand(loc, Games.HANGMAN, Games.HANGMAN_STAGES.first())
        val round = HouseGames.HangmanRound(Games.HANGMAN_WORDS.random(), stand)
        room.hangman = round
        stand.npc.say(round.shown)
        mes("The word has ${round.word.length} letters: ${round.shown}")
    }

    private suspend fun ProtectedAccess.guess(npc: Npc) {
        val house = house() ?: return
        val round = games.of(house.owner).hangman?.takeIf { it.stand.npc === npc } ?: return
        val input = stringDialog("Guess a letter, or up to ${Games.HANGMAN_GUESS_LETTERS} letters of the word:")
        val letters = input.uppercase().filter { it in 'A'..'Z' }.toSet() - round.guessed
        if (letters.isEmpty()) {
            mes("You need to guess a letter that hasn't been tried.")
            return
        }
        if (letters.size > Games.HANGMAN_GUESS_LETTERS) {
            mes("You can only guess up to ${Games.HANGMAN_GUESS_LETTERS} letters at once.")
            return
        }
        if (games.of(house.owner).hangman !== round) {
            return
        }
        round.players += player
        if (letters.all { it in round.word }) {
            round.guessed += letters
        } else {
            if (letters.size == 1) {
                round.guessed += letters
            }
            round.wrong++
            npc.transmog(type(Games.HANGMAN_STAGES[round.wrong.coerceAtMost(Games.HANGMAN_STAGES.lastIndex)]), Int.MAX_VALUE)
        }
        npc.say(round.shown)
        if (round.solved) {
            games.of(house.owner).hangman = null
            games.win(house, player, "game of hangman", round.players)
            games.sit(round.stand)
            return
        }
        if (round.wrong >= Games.HANGMAN_STAGES.lastIndex) {
            npc.say(round.word)
            for (other in round.players) {
                other.mes("The hangman is complete! The word was ${round.word}.")
            }
        }
    }

    private fun ProtectedAccess.resetHangman(npc: Npc) {
        val house = house() ?: return
        val room = games.of(house.owner)
        val round = room.hangman?.takeIf { it.stand.npc === npc } ?: return
        val fresh = HouseGames.HangmanRound(Games.HANGMAN_WORDS.random(), round.stand)
        room.hangman = fresh
        npc.resetTransmog()
        npc.say(fresh.shown)
        mes("A new word: ${fresh.shown}")
    }

    private fun ProtectedAccess.banishHangman(npc: Npc) {
        val house = house() ?: return
        val room = games.of(house.owner)
        val round = room.hangman?.takeIf { it.stand.npc === npc } ?: return
        room.hangman = null
        games.sit(round.stand)
    }

    // ------------------------------------------------------------------------------ shared

    private fun swap(loc: BoundLocInfo, into: String) {
        locRepo.del(loc, Int.MAX_VALUE)
        locRepo.add(loc.coords, into, Int.MAX_VALUE, loc.angle, loc.shape)
    }

    /** Puts [into] back where [from] stands in for it, if [from] is still there. */
    private fun restore(coords: CoordGrid, from: String, into: String, angle: LocAngle, shape: LocShape) {
        val id = from.asRSCM(RSCMType.LOC)
        locRepo.findAll(coords).firstOrNull { it.id == id }?.let { locRepo.del(it, Int.MAX_VALUE) }
        locRepo.add(coords, into, Int.MAX_VALUE, angle, shape)
    }

    private fun type(npc: String) = checkNotNull(ServerCacheManager.getNpc(npc.asRSCM(RSCMType.NPC))) { npc }

    private companion object {
        const val HOT = 3
        const val WARM = 8
        const val COOL = 16

        /** A fairy on another floor feels this much further away than she is across the map. */
        const val OTHER_FLOOR_DISTANCE = 32
    }
}
