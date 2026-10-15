package org.rsmod.content.skills.construction.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.skills.construction.data.Leagues
import org.rsmod.content.skills.construction.data.Leagues.League
import org.rsmod.content.skills.construction.house.HouseAccess
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The league hall's displays.
 *
 * The owner puts a league trophy on a pedestal, a league banner on the banner stand or a whole relic
 * hunter outfit - head, top, legs and boots of one league and tier - on the outfit stand by using it
 * on the empty display. It is taken in and the house is put back together showing the cache's
 * variant of the display carrying it; Remove-trophy, Remove-banner and Remove-outfit hand it back.
 * The variants carry no Remove, so a display has to be emptied before it can be taken down.
 *
 * No league runs on this server, so the accomplishments scroll and the trophy case have no records
 * to show, and the statues are only looked at.
 */
class LeagueHallScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val houses: HouseAccess,
    private val locRepo: LocRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (number in 1..3) {
            for (style in Leagues.PEDESTAL_STYLES) {
                val base = Leagues.pedestal(number, style)
                onOpLocU(base) { placeTrophy(number, it.objType.id) }
                onOpLoc2(base) { mes("The pedestal is empty. Use a league trophy on it to put it on display.") }
                for (league in League.entries) {
                    for (metal in Leagues.METALS.indices) {
                        val shown = Leagues.pedestalWith(base, league, metal)
                        onOpLoc2(shown) { mes("The pedestal displays a ${trophyName(league, metal)}.") }
                        onOpLoc3(shown) { takeTrophy(number) }
                    }
                }
            }
        }
        for (style in Leagues.STAND_STYLES) {
            val base = Leagues.bannerStand(style)
            onOpLocU(base) { placeBanner(it.objType.id) }
            onOpLoc2(base) { mes("The banner stand is empty. Use a league banner on it to put it on display.") }
            for (league in League.entries) {
                val shown = Leagues.bannerStandWith(base, league)
                onOpLoc2(shown) { mes("The stand flies a ${itemName(league.bannerObj)}.") }
                onOpLoc3(shown) { takeBanner() }
            }
        }
        for (style in Leagues.OUTFIT_STYLES) {
            val base = Leagues.outfitStand(style)
            onOpLocU(base) { placeOutfit(it.objType.id) }
            onOpLoc2(base) { mes("The outfit stand is empty. Use a relic hunter outfit on it to put it on display.") }
            for (league in League.entries) {
                for (tier in 1..Leagues.OUTFIT_TIERS) {
                    val shown = Leagues.outfitStandWith(base, league, tier)
                    onOpLoc2(shown) { mes("The stand displays a tier $tier relic hunter outfit.") }
                    onOpLoc3(shown) { takeOutfit() }
                }
            }
        }
        for (statue in Leagues.STATUES) {
            onOpLoc1(statue) { mes("A monument to the leagues of Gielinor.") }
        }
        onOpLoc2(Leagues.GLOBE) { mes("You give the globe a spin.") }
        onOpLoc1(Leagues.SCROLL) { mes(NO_LEAGUES) }
        onOpLoc2(Leagues.SCROLL) { mes(NO_LEAGUES) }
        onOpLoc3(Leagues.SCROLL) { mes(NO_LEAGUES) }
        for ((closed, open) in Leagues.TROPHY_CASES) {
            onOpLoc2(closed) { swap(it.loc, open) }
            onOpLoc1(open) { mes("The trophy case is empty: no leagues have been held on this world yet.") }
            onOpLoc2(open) { swap(it.loc, closed) }
        }
    }

    private fun ProtectedAccess.isOwner(): Boolean {
        val house = registry.houseAt(player.coords) ?: return false
        if (house.owner !== player) {
            mes("Only the owner of this house can change its displays.")
            return false
        }
        return true
    }

    // ---------------------------------------------------------------------------- trophies

    private fun ProtectedAccess.placeTrophy(number: Int, obj: Int) {
        if (!isOwner()) {
            return
        }
        val (league, metal) =
            League.entries.firstNotNullOfOrNull { league ->
                Leagues.METALS.indices.firstOrNull { id(league.trophy(it)) == obj }?.let { league to it }
            } ?: run {
                mes("Only a league trophy can go on the pedestal.")
                return
            }
        if (player.vars[Leagues.pedestalLeague(number)] != 0) {
            mes(OCCUPIED)
            return
        }
        if (invDel(inv, league.trophy(metal), 1).failure) {
            return
        }
        set(Leagues.pedestalLeague(number), league.ordinal + 1)
        set(Leagues.pedestalTrophy(number), metal + 1)
        mes("You put the ${trophyName(league, metal)} on display.")
        houses.rebuild(this)
    }

    private fun ProtectedAccess.takeTrophy(number: Int) {
        if (!isOwner()) {
            return
        }
        val league = League.entries.getOrNull(player.vars[Leagues.pedestalLeague(number)] - 1) ?: return
        val metal = player.vars[Leagues.pedestalTrophy(number)] - 1
        if (metal !in Leagues.METALS.indices) {
            return
        }
        if (invAdd(inv, league.trophy(metal), 1).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        set(Leagues.pedestalLeague(number), 0)
        set(Leagues.pedestalTrophy(number), 0)
        houses.rebuild(this)
    }

    private fun trophyName(league: League, metal: Int): String = itemName(league.trophy(metal))

    // ----------------------------------------------------------------------------- banners

    private fun ProtectedAccess.placeBanner(obj: Int) {
        if (!isOwner()) {
            return
        }
        val league = League.entries.firstOrNull { id(it.bannerObj) == obj }
        if (league == null) {
            mes("Only a league banner can go on the banner stand.")
            return
        }
        if (player.vars[Leagues.BANNER_LEAGUE] != 0) {
            mes(OCCUPIED)
            return
        }
        if (invDel(inv, league.bannerObj, 1).failure) {
            return
        }
        set(Leagues.BANNER_LEAGUE, league.ordinal + 1)
        houses.rebuild(this)
    }

    private fun ProtectedAccess.takeBanner() {
        if (!isOwner()) {
            return
        }
        val league = League.entries.getOrNull(player.vars[Leagues.BANNER_LEAGUE] - 1) ?: return
        if (invAdd(inv, league.bannerObj, 1).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        set(Leagues.BANNER_LEAGUE, 0)
        houses.rebuild(this)
    }

    // ----------------------------------------------------------------------------- outfits

    private fun ProtectedAccess.placeOutfit(obj: Int) {
        if (!isOwner()) {
            return
        }
        val (league, tier) =
            League.entries.firstNotNullOfOrNull { league ->
                (1..Leagues.OUTFIT_TIERS).firstOrNull { tier -> league.outfit(tier).any { id(it) == obj } }?.let { league to it }
            } ?: run {
                mes("Only a relic hunter outfit can go on the outfit stand.")
                return
            }
        if (player.vars[Leagues.OUTFIT_LEAGUE] != 0) {
            mes(OCCUPIED)
            return
        }
        val pieces = league.outfit(tier)
        if (pieces.any { inv.count(it) < 1 }) {
            mes("You need the whole outfit - head, top, legs and boots - to put it on display.")
            return
        }
        for (piece in pieces) {
            invDel(inv, piece, 1)
        }
        set(Leagues.OUTFIT_LEAGUE, league.ordinal + 1)
        set(Leagues.OUTFIT_TIER, tier)
        houses.rebuild(this)
    }

    private fun ProtectedAccess.takeOutfit() {
        if (!isOwner()) {
            return
        }
        val league = League.entries.getOrNull(player.vars[Leagues.OUTFIT_LEAGUE] - 1) ?: return
        val tier = player.vars[Leagues.OUTFIT_TIER]
        if (tier !in 1..Leagues.OUTFIT_TIERS) {
            return
        }
        val pieces = league.outfit(tier)
        if (inv.freeSpace() < pieces.size) {
            mes("You need ${pieces.size} free inventory spaces to take the outfit.")
            return
        }
        for (piece in pieces) {
            invAdd(inv, piece, 1)
        }
        set(Leagues.OUTFIT_LEAGUE, 0)
        set(Leagues.OUTFIT_TIER, 0)
        houses.rebuild(this)
    }

    // ------------------------------------------------------------------------------ shared

    private fun ProtectedAccess.set(varbit: String, value: Int) = VarPlayerIntMapSetter.set(player, varbit, value)

    private fun swap(loc: BoundLocInfo, into: String) {
        locRepo.del(loc, Int.MAX_VALUE)
        locRepo.add(loc.coords, into, Int.MAX_VALUE, loc.angle, loc.shape)
    }

    private fun id(obj: String): Int = obj.asRSCM(RSCMType.OBJ)

    private fun itemName(obj: String): String =
        ServerCacheManager.getItem(id(obj))?.name?.lowercase() ?: RSCM.getReverseMapping(RSCMType.OBJ, id(obj))

    private companion object {
        const val NO_LEAGUES = "No leagues have been held on this world yet."
        const val OCCUPIED = "Something is already on display there."
    }
}
