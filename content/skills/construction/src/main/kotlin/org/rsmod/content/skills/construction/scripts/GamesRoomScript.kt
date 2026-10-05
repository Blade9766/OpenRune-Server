package org.rsmod.content.skills.construction.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import kotlin.math.max
import org.rsmod.api.combat.commons.styles.AttackStyle
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.combat.maxhit.player.PlayerMeleeMaxHit
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpcT
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.construction.data.Games
import org.rsmod.content.skills.construction.house.HouseGames
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The games room's attack stone and elemental balance.
 *
 * Set-up stands the attack stone's npc in place of the stone. Each Hit rolls up to the player's
 * melee max hit; the stone cracks through its npc's stages as the damage mounts, a chip off it now
 * and then hurts whoever hit it, and the player who did the most damage when it breaks wins. Hits
 * train the attack style's stat at a fortieth of the usual rate, and none of it Hitpoints.
 *
 * Activate stands the balance's npc in place of its orb, tipped towards some of the elements. Players
 * take turns casting the elemental combat spells on it - runes are used as normal, a bigger spell
 * tips it further, and air and earth cancel out as water and fire do - and whoever leaves it white
 * wins. The npc shows the heavier pair's colour and how far it leans. Banish puts the orb back.
 */
class GamesRoomScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val games: HouseGames,
    private val xpMods: XpModifiers,
    private val bonuses: WornBonuses,
    private val attackStyles: AttackStyles,
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (stone in Games.AttackStone.entries) {
            onOpLoc1(stone.loc) { setUp(it.loc, stone) }
            for (stage in stone.stages) {
                onOpNpc1(stage) { hit(it.npc) }
            }
        }
        for (balance in Games.Balance.entries) {
            onOpLoc1(balance.loc) { activate(it.loc, balance) }
            for (form in balance.forms) {
                onOpNpc2(form) { banish(it.npc) }
                val type = ServerCacheManager.getNpc(form.asRSCM(RSCMType.NPC)) ?: continue
                for (spell in Games.BALANCE_SPELLS) {
                    onOpNpcT(type, spell.component) { cast(it.npc, spell) }
                }
            }
        }
    }

    // ------------------------------------------------------------------------ attack stone

    private fun ProtectedAccess.setUp(loc: BoundLocInfo, stone: Games.AttackStone) {
        val house = registry.houseAt(player.coords) ?: return
        val room = games.of(house.owner)
        if (room.stone != null) {
            mes("The stone is already set up.")
            return
        }
        room.stone = HouseGames.StoneRound(stone, games.stand(loc, stone.loc, stone.npcAt(0)))
        mes("You set up the attack stone. Whoever does the most damage before it breaks wins.")
    }

    private suspend fun ProtectedAccess.hit(npc: Npc) {
        val house = registry.houseAt(player.coords) ?: return
        val round = games.of(house.owner).stone?.takeIf { it.stand.npc === npc } ?: return
        anim(PUNCH_SEQ)
        val damage = random.of(0, maxHit())
        round.damage[player] = (round.damage[player] ?: 0) + damage
        round.total += damage
        mes(if (damage == 0) "You fail to make a mark on the stone." else "You hit the stone for $damage.")
        trainStyle(damage * Games.STONE_XP_PER_DAMAGE)
        if (random.of(CHIP_CHANCE) == 0) {
            mes("A chip flies off the stone and hits you!")
            queueHit(delay = 1, type = HitType.Typeless, damage = random.of(1, CHIP_MAX_DAMAGE))
        }
        npc.transmog(type(round.stone.npcAt(round.total)), Int.MAX_VALUE)
        if (round.total < round.stone.hitpoints) {
            delay(HIT_TICKS)
            return
        }
        games.of(house.owner).stone = null
        val best = round.damage.values.max()
        val winner = round.damage.filterValues { it == best }.keys.first()
        games.win(house, winner, "attack stone game", round.damage.keys)
        games.sitAfter(house.owner, round.stand, RUBBLE_TICKS)
    }

    private fun ProtectedAccess.maxHit(): Int {
        val effectiveStrength = stat(STRENGTH) + EFFECTIVE_LEVEL_BONUS
        return max(1, PlayerMeleeMaxHit.calculateBaseDamage(effectiveStrength, bonuses.strengthBonus(player)))
    }

    private fun ProtectedAccess.trainStyle(xp: Double) {
        if (xp <= 0.0) {
            return
        }
        val stats =
            when (attackStyles.get(player)) {
                AttackStyle.AggressiveMelee -> listOf(STRENGTH)
                AttackStyle.DefensiveMelee -> listOf(DEFENCE)
                AttackStyle.ControlledMelee -> listOf(ATTACK, STRENGTH, DEFENCE)
                else -> listOf(ATTACK)
            }
        for (stat in stats) {
            statAdvance(stat, xp / stats.size * xpMods.get(player, stat))
        }
    }

    // ------------------------------------------------------------------- elemental balance

    private fun ProtectedAccess.activate(loc: BoundLocInfo, balance: Games.Balance) {
        val house = registry.houseAt(player.coords) ?: return
        val room = games.of(house.owner)
        if (room.balance != null) {
            mes("The balance is already in play.")
            return
        }
        val airEarth = balance.start * if (random.randomBoolean()) 1 else -1
        val waterFire = random.of(-balance.start, balance.start)
        val stand = games.stand(loc, balance.loc, balance.npcFor(airEarth, waterFire))
        room.balance = HouseGames.BalanceRound(balance, stand, airEarth, waterFire)
        mes("The balance tips out of true. Cast elemental spells on it to make it white again.")
    }

    private fun ProtectedAccess.banish(npc: Npc) {
        val house = registry.houseAt(player.coords) ?: return
        val room = games.of(house.owner)
        val round = room.balance?.takeIf { it.stand.npc === npc } ?: return
        room.balance = null
        games.sit(round.stand)
    }

    private suspend fun ProtectedAccess.cast(npc: Npc, balanceSpell: Games.BalanceSpell) {
        val house = registry.houseAt(player.coords) ?: return
        val round = games.of(house.owner).balance?.takeIf { it.stand.npc === npc } ?: return
        if (round.lastCaster === player && round.casters.any { it !== player }) {
            mes("It isn't your turn.")
            return
        }
        val component = balanceSpell.component.asRSCM(RSCMType.COMPONENT)
        val spell = spells.allSpells().firstOrNull { it.component.packed == component } ?: return
        if (stat(MAGIC) < spell.levelReq) {
            mes("You need a Magic level of ${spell.levelReq} to cast this spell.")
            return
        }
        if (runes.attemptCast(player, spell).isFailure()) {
            return
        }
        faceEntitySquare(npc)
        anim(if (balanceSpell.weight >= WAVE_WEIGHT) WAVE_SEQ else STRIKE_SEQ)
        statAdvance(MAGIC, spell.castXp * xpMods.get(player, MAGIC))
        round.casters += player
        round.lastCaster = player
        val weight = balanceSpell.weight
        when (balanceSpell.element) {
            Games.Element.AIR -> round.airEarth += weight
            Games.Element.EARTH -> round.airEarth -= weight
            Games.Element.WATER -> round.waterFire += weight
            Games.Element.FIRE -> round.waterFire -= weight
        }
        npc.transmog(type(round.balance.npcFor(round.airEarth, round.waterFire)), Int.MAX_VALUE)
        if (round.airEarth != 0 || round.waterFire != 0) {
            delay(CAST_TICKS)
            return
        }
        games.of(house.owner).balance = null
        games.win(house, player, "elemental balance", round.casters)
        games.sitAfter(house.owner, round.stand, RUBBLE_TICKS)
    }

    private fun type(npc: String) = checkNotNull(ServerCacheManager.getNpc(npc.asRSCM(RSCMType.NPC))) { npc }

    private companion object {
        const val ATTACK = "stat.attack"
        const val STRENGTH = "stat.strength"
        const val DEFENCE = "stat.defence"
        const val MAGIC = "stat.magic"

        const val PUNCH_SEQ = "seq.human_unarmedpunch"
        const val STRIKE_SEQ = "seq.human_caststrike"
        const val WAVE_SEQ = "seq.human_castwave"
        const val WAVE_WEIGHT = 4

        /** The effective level's flat +8, as every melee max hit adds. */
        const val EFFECTIVE_LEVEL_BONUS = 8

        const val CHIP_CHANCE = 8
        const val CHIP_MAX_DAMAGE = 3
        const val HIT_TICKS = 4
        const val CAST_TICKS = 5
        const val RUBBLE_TICKS = 3
    }
}
