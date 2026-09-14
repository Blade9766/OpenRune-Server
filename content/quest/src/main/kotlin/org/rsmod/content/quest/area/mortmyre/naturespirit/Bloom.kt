package org.rsmod.content.quest.area.mortmyre.naturespirit

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpWorn1
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.BLESSED_SICKLE
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.DRUIDIC_SPELL
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.FUNGUS
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.PEAR
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_BLESSED
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STEM
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.USED_SPELL
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Bloom: the dead growth of Mort Myre flowers around the caster. Rotting logs sprout fungi,
 * rotting branches bud and small bushes bear golden pears, each of which can be picked once
 * before it withers back.
 *
 * Filliman's druidic spell is a single free cast that only works on logs, and only once Drezel
 * has blessed the player. A blessed silver sickle casts it as often as the player has prayer to
 * spend, 1-6 points a time.
 */
class Bloom
@Inject
constructor(
    private val natureSpirit: NatureSpiritQuest,
    private val locRepo: LocRepository,
    private val world: WorldRepository,
) : PluginScript() {

    private class Growth(val dead: String, val bloomed: String, val harvest: String, val spotanim: String, val sound: String) {
        val deadId: Int = dead.asRSCM(RSCMType.LOC)
    }

    override fun ScriptContext.startup() {
        onOpHeld1(DRUIDIC_SPELL) { castDruidicSpell() }
        onOpHeld1(BLESSED_SICKLE) { castWithSickle() }
        onOpWorn1(BLESSED_SICKLE) { castWithSickle() }
        for (growth in GROWTHS) {
            onOpLoc2(growth.bloomed) { harvest(it.loc, growth) }
        }
    }

    private suspend fun ProtectedAccess.castDruidicSpell() {
        if (natureSpirit.stage(player) < STAGE_BLESSED && !natureSpirit.isComplete(player)) {
            objbox(DRUIDIC_SPELL, "You've not been told how to use this item yet.")
            return
        }
        castAnimation()
        mes("You cast the spell in the swamp.")
        val bloomed = bloomAround(coords, GROWTHS.filter { it.harvest == FUNGUS })
        if (bloomed == 0) {
            mes("There is no suitable material to be affected in this area.")
            return
        }
        invReplace(inv, DRUIDIC_SPELL, 1, USED_SPELL)
    }

    private suspend fun ProtectedAccess.castWithSickle() {
        if (player.stat(PRAYER) <= 0) {
            mes("You need some Prayer points to cast Bloom.")
            return
        }
        castAnimation()
        statSub(PRAYER, constant = random.of(1, MAX_PRAYER_COST).coerceAtMost(player.stat(PRAYER)), percent = 0)
        bloomAround(coords, GROWTHS)
    }

    private suspend fun ProtectedAccess.castAnimation() {
        anim(BLOOM_SEQ)
        spotanim(PLAYER_SPOTANIM)
        spotanimMap(world, AREA_SPOTANIM, coords)
        delay(1)
    }

    /** Blooms every dead growth on the tiles around [centre]; returns how many bloomed. */
    private fun ProtectedAccess.bloomAround(centre: CoordGrid, growths: List<Growth>): Int {
        var bloomed = 0
        for (dx in -1..1) {
            for (dz in -1..1) {
                val tile = centre.translate(dx, dz)
                for (loc in locRepo.findAll(tile).toList()) {
                    val growth = growths.firstOrNull { loc.id == it.deadId } ?: continue
                    locRepo.add(loc.coords, growth.bloomed, BLOOM_DURATION, loc.angle, loc.shape)
                    spotanimMap(world, growth.spotanim, tile)
                    soundSynth(growth.sound)
                    bloomed++
                }
            }
        }
        return bloomed
    }

    private suspend fun ProtectedAccess.harvest(loc: BoundLocInfo, growth: Growth) {
        arriveDelay()
        if (inv.isFull()) {
            mes("You don't have enough inventory space.")
            return
        }
        faceLoc(loc)
        anim(PICK_SEQ)
        soundSynth(PICK_SOUND)
        invAdd(inv, growth.harvest)
        locRepo.change(loc, growth.dead, Int.MAX_VALUE)
    }

    private companion object {
        const val PRAYER = "stat.prayer"
        const val MAX_PRAYER_COST = 6

        /** A bloomed growth withers back if nobody picks it. */
        const val BLOOM_DURATION = 100

        const val BLOOM_SEQ = "seq.druidicspirit_human_bloom"
        const val PICK_SEQ = "seq.human_pickuptable"
        const val PLAYER_SPOTANIM = "spotanim.druidicspirit_bloom_player_spotanim"
        const val AREA_SPOTANIM = "spotanim.druidicspirit_bloom_spotanim"
        const val PICK_SOUND = "synth.pick2"

        val GROWTHS =
            listOf(
                Growth("loc.log_druidicspirit", "loc.log_druidicspirit2", FUNGUS, "spotanim.mushroom_growing", "synth.bloom_mushroom"),
                Growth("loc.branch_druidicspirit", "loc.branch_druidicspirit2", STEM, "spotanim.leaves_growing", "synth.bloom_branch"),
                Growth("loc.peartree_druidicspirit", "loc.peartree_druidicspirit2", PEAR, "spotanim.pears_growing", "synth.bloom_pears"),
            )
    }
}
