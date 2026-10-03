package org.rsmod.content.quest.area.ardougne.clocktower

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpObj3
import org.rsmod.game.obj.Obj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Taking the four cogs from the dungeon floor and fitting them to the clock's spindles. Each
 * level of the tower, and the cellar, has one spindle of every colour; only the broken one on each
 * level takes a cog.
 */
class ClockTowerCogs
@Inject
constructor(
    private val clockTower: ClockTowerQuest,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (cog in Cog.entries) {
            onOpObj3(objType(cog.obj)) { take(it.obj, cog) }
            onOpLocU(cog.spindle) { useOnSpindle(it.objType.internalName, spindle = cog, broken = true) }
            onOpLocU(cog.fittedSpindle) { useOnSpindle(it.objType.internalName, spindle = cog, broken = false) }
        }
    }

    private suspend fun ProtectedAccess.take(obj: Obj, cog: Cog) {
        if (clockTower.isComplete(player)) {
            mes("You have no need for another cog now the clock is fixed.")
            return
        }
        if (Cog.entries.any { it.obj in inv }) {
            mes("The cogs are too heavy to carry more than one at a time.")
            return
        }
        if (inv.isFull()) {
            mes(Constants.dm_take_invspace)
            return
        }
        if (cog == Cog.BLACK && !coolBlackCog()) {
            return
        }
        if (coords != obj.coords) {
            delay(1)
            anim(TAKE_SEQ)
        }
        soundSynth(TAKE_SOUND)
        if (!objRepo.del(obj)) {
            mes(Constants.dm_take_taken)
            return
        }
        invAdd(inv, cog.obj)
    }

    private suspend fun ProtectedAccess.coolBlackCog(): Boolean {
        if (COLD_GLOVES.any { it in player.worn }) {
            return true
        }
        val water = WATER.entries.firstOrNull { it.key in inv }
        if (water == null) {
            mesbox("The cog is red hot from the flames. You cannot pick it up.")
            return false
        }
        invReplace(inv, water.key, 1, water.value)
        soundSynth(WATER_SOUND)
        mesbox("You pour water over the cog. It quickly cools down enough to take.")
        return true
    }

    private suspend fun ProtectedAccess.useOnSpindle(obj: String, spindle: Cog, broken: Boolean) {
        val cog = Cog.ofObj(obj)
        if (cog == null) {
            mes(Constants.dm_default)
            return
        }
        arriveDelay()
        when {
            cog != spindle -> mes("The ${cog.label} cog doesn't fit on this spindle.")
            !broken -> mes("This spindle already has a cog on it.")
            !clockTower.isActive(player) -> mes(Constants.dm_default)
            clockTower.isPlaced(player, cog) -> mes("You've already fitted a cog to this spindle.")
            else -> fit(cog)
        }
    }

    private fun ProtectedAccess.fit(cog: Cog) {
        if (invDel(inv, cog.obj).failure) {
            return
        }
        anim(FIT_SEQ)
        soundSynth(FIT_SOUND)
        clockTower.place(player, cog)
        mes("The cog fits perfectly.")
    }

    private fun objType(obj: String): ItemServerType =
        checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))) { "Missing obj: $obj" }

    private companion object {
        const val TAKE_SEQ = "seq.human_pickuptable"
        const val FIT_SEQ = "seq.human_pickuptable"
        const val TAKE_SOUND = "synth.pick2"
        const val FIT_SOUND = "synth.pick2"
        const val WATER_SOUND = "synth.liquid"

        val COLD_GLOVES = listOf("obj.ice_gloves", "obj.smithing_uniform_gloves_ice")

        val WATER = linkedMapOf("obj.bucket_water" to "obj.bucket_empty", "obj.jug_water" to "obj.jug_empty")
    }
}
