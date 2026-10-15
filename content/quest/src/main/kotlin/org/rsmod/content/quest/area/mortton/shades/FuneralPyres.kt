package org.rsmod.content.quest.area.mortton.shades

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.SACRED_OIL
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_CREMATED
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_LOGS_ON_PYRE
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_PYRE_LOGS
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_SACRED_OIL
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.TINDERBOX
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.VIAL_EMPTY
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The funeral pyres south and west of Mort'ton.
 *
 * Sacred oil turns logs into pyre logs; pyre logs laid on a pyre, then shade remains, then a
 * tinderbox cremate the shade. Its spirit rises from the flames and a reward - a shade key, or
 * coins 21% of the time - appears on the stone stand beside the pyre for the player who lit it.
 * Choosing Build on a bare pyre while carrying everything does all three steps in one go, using
 * the best pyre logs the remains allow. A pyre left half-built is claimed by its builder and
 * clears itself after a couple of minutes.
 */
class FuneralPyres
@Inject
constructor(
    private val shades: ShadesOfMorttonQuest,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val world: WorldRepository,
    private val random: GameRandom,
) : PluginScript() {

    private val builders = HashMap<CoordGrid, PlayerUid>()
    private val pendingRemains = HashMap<CoordGrid, Remains>()

    override fun ScriptContext.startup() {
        for (wood in Wood.entries) {
            for (oil in SACRED_OIL) {
                onOpHeldU(oil, wood.log) { makePyreLogs(oil, wood) }
            }
            onOpLocU(PYRE, wood.pyreLog) { placeLogs(it.loc, wood) }
        }
        onOpLoc1(PYRE) { autoBuild(it.loc) }
        for (loc in Wood.entries.map { it.logsLoc }.distinct()) {
            onOpLoc1(loc) { mes("You need to place some shade remains on the pyre.") }
            for (remains in Remains.entries) {
                onOpLocU(loc, remains.obj) { placeRemains(it.loc, remains) }
            }
        }
        for (loc in Wood.entries.map { it.bonesLoc }.distinct()) {
            onOpLoc1(loc) { light(it.loc) }
            onOpLocU(loc, TINDERBOX) { light(it.loc) }
        }
    }

    private suspend fun ProtectedAccess.makePyreLogs(oil: String, wood: Wood) {
        val doses = SACRED_OIL.indexOf(oil) + 1
        if (doses < wood.oilDoses) {
            mes("You need at least ${wood.oilDoses} doses of sacred oil to treat these logs.")
            return
        }
        anim(OIL_SEQ)
        soundSynth(OIL_SOUND)
        val left = doses - wood.oilDoses
        invReplace(inv, oil, 1, if (left == 0) VIAL_EMPTY else SACRED_OIL[left - 1])
        invReplace(inv, wood.log, 1, wood.pyreLog)
        statAdvance("stat.firemaking", wood.oilDoses * XP_PER_DOSE)
        mes("You use the sacred oil on the logs and get pyre logs.")
        if (shades.stage(player) in STAGE_SACRED_OIL until STAGE_PYRE_LOGS) {
            shades.advanceTo(this, STAGE_PYRE_LOGS)
        }
    }

    private suspend fun ProtectedAccess.autoBuild(pyre: BoundLocInfo) {
        val remains = Remains.entries.lastOrNull { it.obj in inv }
        val wood = remains?.let { r -> Wood.entries.lastOrNull { it.pyreLog in inv && it.ordinal >= r.minWood.ordinal } }
        if (remains == null || wood == null || TINDERBOX !in inv) {
            mes("You need pyre logs, shade remains and a tinderbox to cremate a shade on this pyre.")
            return
        }
        if (!claim(pyre.coords)) {
            return
        }
        faceSquare(pyre.adjustedCentre)
        anim(PLACE_SEQ)
        delay(1)
        invDel(inv, wood.pyreLog)
        invDel(inv, remains.obj)
        mes("You put some logs on the pyre and place the shade's remains on them.")
        advanceTo(STAGE_LOGS_ON_PYRE)
        cremate(pyre, wood, remains)
    }

    private suspend fun ProtectedAccess.placeLogs(pyre: BoundLocInfo, wood: Wood) {
        if (!claim(pyre.coords)) {
            return
        }
        anim(PLACE_SEQ)
        delay(1)
        invDel(inv, wood.pyreLog)
        mes("You put some logs on the pyre.")
        advanceTo(STAGE_LOGS_ON_PYRE)
        changePyre(pyre, wood.logsLoc)
    }

    private suspend fun ProtectedAccess.placeRemains(pyre: BoundLocInfo, remains: Remains) {
        if (!owns(pyre.coords)) {
            return
        }
        val wood = Wood.entries.first { it.logsLoc == pyre.internalName }
        if (wood.ordinal < remains.minWood.ordinal) {
            mes("These logs aren't good enough to cremate these remains.")
            return
        }
        anim(PLACE_SEQ)
        delay(1)
        invDel(inv, remains.obj)
        pendingRemains[pyre.coords] = remains
        mes("You place the shade's remains on the logs.")
        changePyre(pyre, wood.bonesLoc)
    }

    private suspend fun ProtectedAccess.light(pyre: BoundLocInfo) {
        if (!owns(pyre.coords)) {
            return
        }
        val remains = pendingRemains[pyre.coords] ?: return
        val wood = Wood.entries.first { it.bonesLoc == pyre.internalName }
        cremate(pyre, wood, remains)
    }

    private suspend fun ProtectedAccess.cremate(pyre: BoundLocInfo, wood: Wood, remains: Remains) {
        if (TINDERBOX !in inv) {
            mes("You need a tinderbox to light the pyre.")
            return
        }
        if (statBase("stat.firemaking") < wood.level) {
            mesbox("You need a Firemaking level of ${wood.level} to light these pyre logs.")
            return
        }
        pendingRemains.remove(pyre.coords)
        faceSquare(pyre.adjustedCentre)
        anim(LIGHT_SEQ)
        soundSynth(TINDERBOX_SOUND)
        delay(LIGHT_TICKS)
        soundSynth(FIRE_SOUND)
        val centre = pyre.adjustedCentre
        val spirit = Npc(npcType(SPIRIT), centre)
        npcRepo.add(spirit, SPIRIT_TICKS)
        spirit.anim(SPIRIT_SEQ)
        world.soundArea(centre, SIGH_SOUND)
        mes("The shade's spirit is released and rises from the pyre.")
        statAdvance("stat.prayer", remains.prayerXp(wood))
        statAdvance("stat.firemaking", wood.burnXp)
        reward(pyre, remains)
        builders.remove(pyre.coords)
        if (shades.stage(player) in STAGE_SACRED_OIL until STAGE_CREMATED) {
            shades.advanceTo(this, STAGE_CREMATED)
        }
        // Swapping the loc ends this script, so the burning pyre is the very last step.
        val burning = locRepo.add(pyre.coords, wood.bonesLoc, BURN_TICKS, pyre.angle, pyre.shape)
        world.locAnim(burning, FIRE_SEQ)
    }

    private fun ProtectedAccess.reward(pyre: BoundLocInfo, remains: Remains) {
        val stand = STANDS.minBy { it.chebyshevDistance(pyre.adjustedCentre) }
        if (random.randomDouble() < COIN_CHANCE) {
            objRepo.add(COINS, stand, REWARD_TICKS, player, random.of(remains.coins))
        } else {
            val key = random.pick(pickKeyTier(remains))
            objRepo.add(key, stand, REWARD_TICKS, player)
        }
        mes("A reward appears on the stone stand.")
    }

    private fun pickKeyTier(remains: Remains): List<String> {
        val roll = random.randomDouble() * (1.0 - COIN_CHANCE)
        return if (remains.lowKeys.isNotEmpty() && roll < remains.lowChance) remains.lowKeys else remains.highKeys
    }

    private fun ProtectedAccess.advanceTo(stage: Int) {
        if (shades.stage(player) in STAGE_PYRE_LOGS until stage) {
            shades.advanceTo(this, stage)
        }
    }

    private fun ProtectedAccess.claim(pyre: CoordGrid): Boolean {
        val owner = builders[pyre]
        if (owner != null && owner != player.uid) {
            mes("Someone else is using this pyre.")
            return false
        }
        builders[pyre] = player.uid
        return true
    }

    private fun ProtectedAccess.owns(pyre: CoordGrid): Boolean {
        if (builders[pyre] == player.uid) {
            return true
        }
        mes("Someone else is using this pyre.")
        return false
    }

    private fun changePyre(pyre: BoundLocInfo, into: String) {
        locRepo.add(pyre.coords, into, ABANDON_TICKS, pyre.angle, pyre.shape) {
            builders.remove(pyre.coords)
            pendingRemains.remove(pyre.coords)
        }
    }

    private fun npcType(name: String) =
        requireNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { "Missing npc: $name" }

    private enum class Wood(
        val log: String,
        val pyreLog: String,
        val loc: String,
        val oilDoses: Int,
        val level: Int,
        val burnXp: Double,
    ) {
        Normal("obj.logs", "obj.logs_pyre", "logs", 2, 5, 50.0),
        Oak("obj.oak_logs", "obj.oak_logs_pyre", "oak", 2, 20, 70.0),
        Willow("obj.willow_logs", "obj.willow_logs_pyre", "willow", 3, 35, 100.0),
        Teak("obj.teak_logs", "obj.teak_logs_pyre", "teak", 3, 40, 120.0),
        ArcticPine("obj.arctic_pine_log", "obj.arctic_pine_logs_pyre", "arctic_pine", 2, 47, 158.5),
        Maple("obj.maple_logs", "obj.maple_logs_pyre", "maple", 3, 50, 175.0),
        Mahogany("obj.mahogany_logs", "obj.mahogany_logs_pyre", "mahogany", 3, 55, 210.0),
        Yew("obj.yew_logs", "obj.yew_logs_pyre", "yew", 4, 65, 255.0),
        Magic("obj.magic_logs", "obj.magic_logs_pyre", "magic", 4, 80, 404.5),
        Redwood("obj.redwood_logs", "obj.redwood_logs_pyre", "magic", 4, 95, 500.0);

        val logsLoc: String
            get() = "loc.temple_pyre_$loc"

        val bonesLoc: String
            get() = "loc.temple_pyre_bones_$loc"
    }

    /**
     * Prayer experience per [Wood] from the lowest log that can burn the remains upwards, and the
     * key tiers: [lowChance] of all rewards are a [lowKeys] key, the rest that are not coins a
     * [highKeys] key.
     */
    private enum class Remains(
        val obj: String,
        val minWood: Wood,
        val xp: List<Double>,
        val coins: IntRange,
        val lowKeys: List<String>,
        val lowChance: Double,
        val highKeys: List<String>,
    ) {
        Loar(
            "obj.shade_bones1",
            Wood.Normal,
            listOf(25.0, 33.0, 33.5, 33.7, 33.9, 34.0, 34.3, 34.5, 35.0, 35.5),
            200..300,
            emptyList(),
            0.0,
            keys("bronze", "bloodred", "brown", "crimson"),
        ),
        Phrin(
            "obj.shade_bones2",
            Wood.Normal,
            listOf(37.5, 45.5, 46.0, 46.2, 46.4, 46.5, 46.8, 47.0, 47.5, 48.0),
            400..500,
            keys("bronze", "black", "purple"),
            0.125,
            keys("steel", "bloodred", "brown", "crimson"),
        ),
        Riyl(
            "obj.shade_bones3",
            Wood.Willow,
            listOf(61.0, 61.2, 61.4, 61.5, 61.8, 62.0, 62.5, 63.0),
            600..700,
            keys("steel", "black", "purple"),
            0.125,
            keys("black", "bloodred", "brown", "crimson"),
        ),
        Asyn(
            "obj.shade_bones4",
            Wood.Yew,
            listOf(79.5, 80.0, 80.5),
            800..900,
            keys("black", "crimson", "black", "purple"),
            0.282,
            keys("silver", "bloodred", "brown"),
        ),
        Fiyr(
            "obj.shade_bones5",
            Wood.Magic,
            listOf(100.0, 100.5),
            2000..4000,
            keys("silver", "brown", "crimson", "black", "purple"),
            0.634,
            keys("gold", "bloodred"),
        ),
        Urium(
            "obj.shade_bones6",
            Wood.Redwood,
            listOf(120.5),
            2000..7000,
            emptyList(),
            0.0,
            keys("gold", "bloodred", "brown", "crimson", "black", "purple"),
        );

        fun prayerXp(wood: Wood): Double = xp[(wood.ordinal - minWood.ordinal).coerceIn(0, xp.lastIndex)]
    }

    private companion object {
        const val PYRE = "loc.temple_pyre"
        const val SPIRIT = "npc.shade_heaven"
        const val COINS = "obj.coins"

        const val COIN_CHANCE = 0.21
        const val XP_PER_DOSE = 5.0
        const val LIGHT_TICKS = 3
        const val SPIRIT_TICKS = 8
        const val REWARD_TICKS = 500
        const val ABANDON_TICKS = 200
        const val BURN_TICKS = 10

        const val OIL_SEQ = "seq.human_pickuptable"
        const val PLACE_SEQ = "seq.human_pickuptable"
        const val LIGHT_SEQ = "seq.human_createfire"
        const val SPIRIT_SEQ = "seq.shade_heaven"
        const val FIRE_SEQ = "seq.temple_pyre_fire"
        const val OIL_SOUND = "synth.oil_pour"
        const val TINDERBOX_SOUND = "synth.tinderbox_strike"
        const val FIRE_SOUND = "synth.fire_lit"
        const val SIGH_SOUND = "synth.shade_sigh"

        /** The stone stand beside each pyre that the reward appears on. */
        val STANDS =
            listOf(
                CoordGrid(3461, 3282, 0),
                CoordGrid(3461, 3294, 0),
                CoordGrid(3463, 3270, 0),
                CoordGrid(3465, 3278, 0),
                CoordGrid(3465, 3286, 0),
                CoordGrid(3465, 3290, 0),
                CoordGrid(3465, 3298, 0),
                CoordGrid(3467, 3266, 0),
                CoordGrid(3467, 3274, 0),
                CoordGrid(3469, 3282, 0),
                CoordGrid(3469, 3294, 0),
                CoordGrid(3471, 3270, 0),
                CoordGrid(3472, 3268, 0),
                CoordGrid(3476, 3264, 0),
                CoordGrid(3476, 3272, 0),
                CoordGrid(3480, 3268, 0),
                CoordGrid(3496, 3268, 0),
                CoordGrid(3500, 3264, 0),
                CoordGrid(3500, 3272, 0),
                CoordGrid(3503, 3275, 0),
                CoordGrid(3504, 3268, 0),
                CoordGrid(3507, 3271, 0),
                CoordGrid(3507, 3279, 0),
                CoordGrid(3511, 3275, 0),
            )

        fun keys(metal: String, vararg trims: String): List<String> =
            trims.map { "obj.shadekey_${metal}_$it" }
    }
}
