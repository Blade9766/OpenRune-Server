package org.rsmod.content.other.castlewars

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.mechanics.toxins.Toxin.cureAllToxins
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpPlayerU
import org.rsmod.api.script.onOpWorn1
import org.rsmod.api.script.onOpWorn2
import org.rsmod.content.other.consumables.potion.PotionEffectService
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Bandages, explosive potions, Castlewars brews, the team cloaks and Lanthus's manual. */
internal class CastleWarsItemScript
@Inject
constructor(
    private val game: CastleWarsGame,
    private val potions: PotionEffectService,
    private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1(BANDAGES) { bandage(player, self = true) }
        onOpPlayerU(checkNotNull(ServerCacheManager.getItem(BANDAGES.asRSCM(RSCMType.OBJ)))) {
            bandageOther(it.target)
        }
        onOpHeld5(EXPLOSIVE) { dropExplosive(it.slot) }
        CastleWars.BREWS.forEachIndexed { index, brew ->
            onOpHeld1(brew) { drinkBrew(it.slot, CastleWars.BREWS.getOrNull(index + 1)) }
        }
        for (team in Team.entries) {
            onOpWorn1(team.cloak) { removeCloak(team) }
            onOpWorn2(team.cloak) { surrender() }
        }
        onOpHeld1(MANUAL) { readManual() }
        onOpHeld1(CRATE) { openCrate(it.slot) }
    }

    /* Bandages */

    private suspend fun ProtectedAccess.bandageOther(target: Player) {
        val team = game.playingTeamOf(player) ?: return
        if (game.playingTeamOf(target) != team) {
            mes("You can only bandage members of your own team.")
            return
        }
        bandage(target, self = false)
    }

    /**
     * A bandage heals a tenth of the patient's Hitpoints level (half as much again with an
     * empowered Castle Wars bracelet), capped at 18 off the dedicated worlds, restores 30% run
     * energy and cures poison.
     */
    private suspend fun ProtectedAccess.bandage(target: Player, self: Boolean) {
        if (!game.isPlaying(player)) {
            mes("You can only use bandages in a game of Castle Wars.")
            return
        }
        invDel(inv, BANDAGES, 1)
        anim("seq.human_eat")
        soundSynth(EAT_SOUND)
        val percent = if (player.vars["varbit.castlewars_bracelet_active"] == 1) BRACELET_PERCENT else BASE_PERCENT
        val amount = (target.baseHitpointsLvl * percent / 100).coerceIn(1, MAX_HEAL)
        target.statHeal("stat.hitpoints", amount, 0)
        target.cureAllToxins()
        target.runEnergy = (target.runEnergy + constants.run_max_energy * RUN_PERCENT / 100).coerceAtMost(constants.run_max_energy)
        UpdateRun.energy(target, target.runEnergy)
        if (!self) {
            target.mes("${player.displayName} bandages your wounds.")
            mes("You bandage ${target.displayName}'s wounds.")
        }
        delay(1)
    }

    /* Explosive potions */

    private fun ProtectedAccess.dropExplosive(slot: Int) {
        invDel(inv, EXPLOSIVE, 1, slot = slot)
        spotanim("spotanim.explodingvial")
        soundSynth("synth.exploding_vial")
        say("Ow!")
        val damage = (player.baseHitpointsLvl * EXPLOSIVE_PERCENT / 100).coerceAtLeast(1)
        val nonLethal = damage.coerceAtMost((player.hitpoints - 1).coerceAtLeast(0))
        if (nonLethal > 0) {
            player.queueHit(delay = 0, type = HitType.Typeless, damage = nonLethal, modifier = NoopPlayerHitModifier)
        }
        mes("The potion explodes as it hits the ground!")
    }

    /* Castlewars brew */

    /**
     * One dose gives the boosts of a super combat potion, a ranging potion and an imbued heart,
     * a super restore, a fifth of the player's run energy back and a stamina potion's reduced run
     * energy drain.
     */
    private suspend fun ProtectedAccess.drinkBrew(slot: Int, next: String?) {
        invReplace(inv, CastleWars.BREWS.first { inv[slot]?.id == it.asRSCM(RSCMType.OBJ) }, 1, next ?: "obj.vial_empty", slot)
        anim("seq.human_eat")
        soundSynth("synth.liquid")
        for (stat in RESTORABLE) {
            statHeal(stat, RESTORE_CONSTANT, RESTORE_PERCENT)
        }
        for (stat in listOf("stat.attack", "stat.strength", "stat.defence")) {
            statBoost(stat, MELEE_CONSTANT, MELEE_PERCENT)
        }
        statBoost("stat.ranged", RANGED_CONSTANT, BOOST_PERCENT)
        statBoost("stat.magic", MAGIC_CONSTANT, BOOST_PERCENT)
        player.runEnergy = (player.runEnergy + constants.run_max_energy / 5).coerceAtMost(constants.run_max_energy)
        UpdateRun.energy(player, player.runEnergy)
        potions.grantStamina(this, STAMINA_DURATION)
        mes(
            if (next == null) {
                "You drink the last of your Castlewars brew."
            } else {
                "You drink some of your Castlewars brew."
            },
        )
        delay(BREW_DELAY)
    }

    /* Team cloaks */

    private fun ProtectedAccess.removeCloak(team: Team) {
        if (game.isParticipant(player)) {
            mes("You can't remove your team's colours.")
            return
        }
        invDel(player.worn, team.cloak, 1)
        rebuildAppearance()
    }

    /**
     * A player on less than 15% of their Hitpoints may surrender, which takes them straight back
     * to their team's respawn room fully healed.
     */
    private fun ProtectedAccess.surrender() {
        val team = game.playingTeamOf(player)
        if (team == null) {
            mes("You can only surrender during a game of Castle Wars.")
            return
        }
        if (player.hitpoints * 100 >= player.baseHitpointsLvl * SURRENDER_PERCENT) {
            mes("You can only surrender when your Hitpoints are low.")
            return
        }
        val flag = game.carriedFlag(player)
        if (flag != null) {
            game.removeBanner(player)
            game.flagDropped(flag, coords)
        }
        telejump(game.scatter(team.spawnRoom), TeleportType.Exempt)
        statRestore("stat.hitpoints")
        statRestore("stat.prayer")
        player.runEnergy = constants.run_max_energy
        UpdateRun.energy(player, player.runEnergy)
        mes("You surrender and are returned to your team's respawn room.")
    }

    /* Supply crate */

    /** Three equally weighted rolls on the crate table; every reward stacks. */
    private fun ProtectedAccess.openCrate(slot: Int) {
        if (inv.freeSpace() < CRATE_ROLLS - 1) {
            mes("You need at least ${CRATE_ROLLS - 1} free inventory spaces to open the crate.")
            return
        }
        invDel(inv, CRATE, 1, slot = slot)
        repeat(CRATE_ROLLS) {
            val (obj, amount) = CRATE_LOOT[random.of(maxExclusive = CRATE_LOOT.size)]
            invAdd(inv, obj, random.of(amount), strict = false)
        }
    }

    /* Manual */

    private suspend fun ProtectedAccess.readManual() {
        for (page in MANUAL_PAGES) {
            mesbox(page)
        }
    }

    private companion object {
        const val BANDAGES = "obj.castlewars_bandages"
        const val EXPLOSIVE = "obj.castlewars_explosives_potion"
        const val MANUAL = "obj.castlewars_manual"
        const val CRATE = "obj.castlewars_crate"
        const val CRATE_ROLLS = 3
        const val EAT_SOUND = 2393

        const val BASE_PERCENT = 10
        const val BRACELET_PERCENT = 15
        const val MAX_HEAL = 18
        const val RUN_PERCENT = 30
        const val EXPLOSIVE_PERCENT = 15
        const val SURRENDER_PERCENT = 15

        const val RESTORE_CONSTANT = 8
        const val RESTORE_PERCENT = 25
        const val MELEE_CONSTANT = 5
        const val MELEE_PERCENT = 15
        const val RANGED_CONSTANT = 4
        const val MAGIC_CONSTANT = 1
        const val BOOST_PERCENT = 10
        const val BREW_DELAY = 2
        const val STAMINA_DURATION = 200

        val CRATE_LOOT =
            listOf(
                "obj.cert_blighted_mantaray" to 15..25,
                "obj.cert_blighted_anglerfish" to 25..35,
                "obj.cert_blighted_karambwan" to 35..45,
                "obj.cert_blighted_4dose2restore" to 4..4,
                "obj.blighted_sack_icebarrage" to 10..15,
                "obj.blighted_sack_vengeance" to 20..30,
                "obj.castlewars_arrow" to 75..105,
                "obj.castlewars_bolt" to 75..105,
                "obj.rune_arrow" to 175..225,
                "obj.rune_javelin" to 100..120,
                CastleWars.TICKET to 2..2,
            )

        val RESTORABLE =
            listOf(
                "stat.attack",
                "stat.strength",
                "stat.defence",
                "stat.ranged",
                "stat.magic",
                "stat.prayer",
                "stat.agility",
                "stat.thieving",
                "stat.mining",
            )

        val MANUAL_PAGES =
            listOf(
                "Castle Wars is fought between Saradomin and Zamorak. Each team defends its own " +
                    "standard at the top of its castle while trying to steal the other's.",
                "Take the enemy standard from its stand and carry it back to the top of your " +
                    "own castle. Use Capture on your stand to score. The team with the most " +
                    "captures after twenty minutes wins.",
                "Supply tables in each castle hold barricades, rocks for the catapult, climbing " +
                    "ropes, explosive potions, pickaxes, toolkits and buckets. Bandages and " +
                    "brews wait in your respawn room.",
                "Barricades block the enemy and can be burnt or blown up. Ropes let you scale " +
                    "enemy battlements. Pickaxes and explosives clear or collapse the tunnels " +
                    "beneath the arena - anyone under a collapse is crushed.",
            )
    }
}
