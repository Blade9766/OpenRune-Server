package org.rsmod.content.skills.construction.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.worn.RangedAmmoValidation
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.construction.data.Games
import org.rsmod.content.skills.construction.house.ActiveHouse
import org.rsmod.content.skills.construction.house.HouseGames
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The games room's ranging games: hoop and stick, dartboard and archery target.
 *
 * Each go is one shot, ten to a player, and everyone who has taken a shot is on the same round until
 * they have all shot out; then the highest score wins. A shot climbs the target's scoring tiers one
 * Ranged roll at a time, with the chances the wiki charts, and any score trains Ranged. The hoop
 * needs nothing, the dartboard a thrown weapon wielded and the archery target a bow or crossbow with
 * its ammunition - the thrown weapon or ammunition is used up. The `poh_ranging` scoreboard shows the
 * first four players.
 */
class RangingGameScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val games: HouseGames,
    private val eventBus: EventBus,
    private val xpMods: XpModifiers,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (game in Games.RangingGame.entries) {
            onOpLoc1(game.loc) { shoot(game) }
        }
    }

    private suspend fun ProtectedAccess.shoot(game: Games.RangingGame) {
        val house = registry.houseAt(player.coords) ?: return
        val room = games.of(house.owner)
        room.ranging?.let { current ->
            dropLeavers(house, current)
            if (current.scores.isNotEmpty() && current.scores.values.all { it.shots >= Games.SHOTS }) {
                finish(house, current)
            }
        }
        val current = room.ranging?.takeIf { it.scores.isNotEmpty() }
        if (current != null && current.game != game) {
            mes("A game at the ${current.game.label.lowercase()} is under way. Wait for it to finish.")
            return
        }
        val round = current ?: HouseGames.RangingRound(game).also { room.ranging = it }
        val score = round.scores[player]
        if (score != null && score.shots >= Games.SHOTS) {
            mes("You have taken all your shots. Wait for the others to finish.")
            return
        }
        val seq = fire(game) ?: return
        val entry = score ?: HouseGames.Score().also { round.scores[player] = it }
        if (entry.shots == 0) {
            player.ifOpenOverlay(SCOREBOARD, eventBus)
        }
        anim(seq)
        delay(SHOT_TICKS)
        // The house may have been rebuilt meanwhile, into a new region.
        val now = registry.houseAt(player.coords) ?: return
        entry.shots++
        val points = roll(game)
        if (points > 0) {
            entry.points += points
            statAdvance(RANGED, game.xp * xpMods.get(player, RANGED))
            mes("You score $points.")
        } else {
            mes("You miss.")
        }
        dropLeavers(now, round)
        showScores(round)
        if (round.scores.isNotEmpty() && round.scores.values.all { it.shots >= Games.SHOTS }) {
            finish(now, round)
        }
    }

    /** Takes out of [round] anyone no longer in the house, so the rest aren't kept waiting for them. */
    private fun dropLeavers(house: ActiveHouse, round: HouseGames.RangingRound) {
        val inside = games.inside(house).toSet()
        for (leaver in round.scores.keys.filter { it !in inside }) {
            round.scores.remove(leaver)
            leaver.ifCloseOverlay(SCOREBOARD, eventBus)
        }
    }

    /** Checks and uses up what [game] needs for one shot, returning the throwing seq, or null if it can't be taken. */
    private fun ProtectedAccess.fire(game: Games.RangingGame): String? =
        when (game) {
            Games.RangingGame.HOOP -> HOOP_SEQ
            Games.RangingGame.DARTBOARD -> throwDart()
            Games.RangingGame.ARCHERY -> shootArrow()
        }

    private fun ProtectedAccess.throwDart(): String? {
        val weapon = wielded()
        if (weapon == null || !weapon.isCategoryType(THROWN)) {
            mes("You need to be wielding something to throw at the dartboard.")
            return null
        }
        if (invDel(player.worn, objName(weapon), 1, slot = Wearpos.RightHand.slot).failure) {
            return null
        }
        return DART_SEQ
    }

    private fun ProtectedAccess.shootArrow(): String? {
        val weapon = wielded()
        val crossbow = weapon?.isCategoryType(CROSSBOW) == true
        if (weapon == null || !(crossbow || weapon.isCategoryType(BOW) || weapon.isCategoryType(CHARGE_BOW))) {
            mes("You need to be wielding a bow or crossbow to shoot at the target.")
            return null
        }
        if (RangedAmmoValidation.requiresAmmo(weapon)) {
            val ammo = player.worn[Wearpos.Quiver.slot]?.let { ServerCacheManager.getItem(it.id) }
            if (ammo == null || !RangedAmmoValidation.isUsable(weapon, ammo)) {
                mes("You have no ammunition you can fire from that.")
                return null
            }
            if (invDel(player.worn, objName(ammo), 1, slot = Wearpos.Quiver.slot).failure) {
                return null
            }
        }
        return if (crossbow) CROSSBOW_SEQ else BOW_SEQ
    }

    private fun ProtectedAccess.wielded(): ItemServerType? =
        player.worn[Wearpos.RightHand.slot]?.let { ServerCacheManager.getItem(it.id) }

    /** Climbs the target's tiers while each roll succeeds; the last tier reached is the score. */
    private fun ProtectedAccess.roll(game: Games.RangingGame): Int {
        var points = 0
        for (tier in game.tiers) {
            if (!statRandom(RANGED, tier.low, tier.high, invisibleBoost = 0)) {
                break
            }
            points = tier.points
        }
        return points
    }

    private fun showScores(round: HouseGames.RangingRound, winner: Player? = null) {
        val board = round.scores.entries.take(Games.SCOREBOARD_ROWS)
        for (viewer in round.scores.keys) {
            for (row in 0 until Games.SCOREBOARD_ROWS) {
                val entry = board.getOrNull(row)
                val player = entry?.key
                val score = entry?.value
                viewer.ifSetText(column(PLAYER, row), player?.displayName ?: EMPTY)
                viewer.ifSetText(column(SHOTS, row), score?.shots?.toString() ?: EMPTY)
                viewer.ifSetText(column(SCORE, row), score?.points?.toString() ?: EMPTY)
                viewer.ifSetText(column(WINNER, row), if (player != null && player === winner) WINNER_MARK else "")
            }
        }
    }

    private fun finish(house: ActiveHouse, round: HouseGames.RangingRound) {
        val room = games.of(house.owner)
        // A shot landing after its round was already over must not end the one after it.
        if (room.ranging !== round) {
            return
        }
        room.ranging = null
        val best = round.scores.values.maxOf { it.points }
        val leaders = round.scores.filterValues { it.points == best }.keys
        val winner = leaders.singleOrNull()
        showScores(round, winner)
        for ((player, score) in round.scores) {
            player.mes("The game is over. You scored ${score.points}.")
            player.ifCloseOverlay(SCOREBOARD, eventBus)
        }
        if (winner == null) {
            round.scores.keys.forEach { it.mes("It's a draw!") }
            return
        }
        games.win(house, winner, "${round.game.label} game", round.scores.keys)
    }

    private fun column(name: String, row: Int): String = "component.poh_ranging:poh_ranging_$name${row + 1}"

    private fun objName(type: ItemServerType): String = RSCM.getReverseMapping(RSCMType.OBJ, type.id)

    private companion object {
        const val RANGED = "stat.ranged"
        const val SCOREBOARD = "interface.poh_ranging"
        const val PLAYER = "player"
        const val SHOTS = "shots"
        const val SCORE = "score"
        const val WINNER = "winner"
        const val WINNER_MARK = "*"
        const val EMPTY = "-"
        const val SHOT_TICKS = 2

        const val THROWN = "category.throwing_weapon"
        const val BOW = "category.bow"
        const val CHARGE_BOW = "category.chargebow"
        const val CROSSBOW = "category.crossbow"

        const val HOOP_SEQ = "seq.human_throw_hoop1"
        const val DART_SEQ = "seq.ii_human_dart_throw"
        const val BOW_SEQ = "seq.human_bow"
        const val CROSSBOW_SEQ = "seq.human_crossbow"
    }
}
