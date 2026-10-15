package org.rsmod.content.generic.locs.webs

import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Spider webs that block corridors (the Wilderness levers, the Mage Arena entrances, the
 * Varrock sewers...). Any bladed weapon the player carries can slash them; how often it works
 * depends on the blade, using the odds Mod Ash gave: a Wilderness sword or a +100 slash bonus
 * always cuts, a knife half the time, and anything with a slash bonus at least a fifth of the
 * time. A cut web grows back after a minute.
 */
class WebSlash @Inject constructor(private val locRepo: LocRepository, private val random: GameRandom) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(BIG_WEB) { slash(it.loc, BIG_WEB_SLASHED) }
        onOpLoc1(WALL_WEB) { slash(it.loc, WALL_WEB_SLASHED) }
    }

    private suspend fun ProtectedAccess.slash(web: BoundLocInfo, slashed: String) {
        arriveDelay()
        faceLoc(web)
        val chance = player.bestBladeChance()
        if (chance == null) {
            mes("Only a sharp blade can cut through this sticky web.")
            return
        }
        anim(SLASH_ANIM)
        delay(SLASH_TICKS)
        if (random.of(PERCENT) < chance) {
            // A plain replacement would leave the map web's collision in place; the intact web
            // has to be deleted for its blocking to go, then the torn one is spawned in its stead.
            locRepo.del(web, WEB_REGROW_TICKS)
            locRepo.add(web.coords, slashed, WEB_REGROW_TICKS, web.angle, web.shape)
            mes("You slash the web apart.")
        } else {
            mes("You fail to cut through it.")
        }
    }

    /** The success chance (out of 100) of the best blade the player has, or null without one. */
    private fun Player.bestBladeChance(): Int? {
        val candidates = buildList {
            righthand?.let(::add)
            addAll(inv.filterNotNull { true })
        }
        return candidates.mapNotNull { it.bladeChance() }.maxOrNull()
    }

    private fun InvObj.bladeChance(): Int? {
        val type = getInvObj(this)
        if (type.isType(KNIFE)) {
            return KNIFE_CHANCE
        }
        if (WILDERNESS_SWORDS.any { type.isType(it) }) {
            return PERCENT
        }
        val slash = type.paramOrNull(params.attack_slash) ?: 0
        return when {
            slash >= SURE_SLASH_BONUS -> PERCENT
            slash >= GOOD_SLASH_BONUS -> slash.coerceAtLeast(GOOD_BLADE_MIN_CHANCE)
            slash > 0 -> WEAK_BLADE_CHANCE
            else -> null
        }
    }

    private companion object {
        const val BIG_WEB = "loc.bigweb_slashable"
        const val BIG_WEB_SLASHED = "loc.bigweb_slashed"
        const val WALL_WEB = "loc.webwall"
        const val WALL_WEB_SLASHED = "loc.sliced_web"
        const val KNIFE = "obj.knife"
        val WILDERNESS_SWORDS =
            listOf(
                "obj.wilderness_sword_easy",
                "obj.wilderness_sword_medium",
                "obj.wilderness_sword_hard",
                "obj.wilderness_sword_elite",
            )

        const val SLASH_ANIM = "seq.human_sword_slash"
        const val SLASH_TICKS = 2
        const val WEB_REGROW_TICKS = 100

        const val PERCENT = 100
        const val KNIFE_CHANCE = 50
        const val SURE_SLASH_BONUS = 100
        const val GOOD_SLASH_BONUS = 20
        const val GOOD_BLADE_MIN_CHANCE = 50
        const val WEAK_BLADE_CHANCE = 20
    }
}
