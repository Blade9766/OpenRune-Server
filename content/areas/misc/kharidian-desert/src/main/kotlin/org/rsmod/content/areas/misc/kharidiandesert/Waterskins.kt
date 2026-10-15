package org.rsmod.content.areas.misc.kharidiandesert

import dev.openrune.ServerCacheManager
import dev.openrune.definition.constants.ConstantProvider
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Keeping waterskins topped up: at any sink, well, pump or fountain, from a container of water,
 * or one dose at a time by slicing open a Kharidian cactus.
 *
 * Cactus cutting is a straight Woodcutting roll, ~12% at level 1 to ~99% at 99 (Mod Ash), worth
 * 10 xp on a success and 0.4 on a miss; a cut cactus is dry for a minute.
 */
class Waterskins @Inject constructor(private val locRepo: LocRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        for (source in waterSources()) {
            for (skin in REFILLABLE) {
                onOpLocU(source, skin) { fillAtSource() }
            }
        }
        for ((container, pour) in CONTAINERS) {
            for (skin in REFILLABLE) {
                onOpHeldU(container, skin) { pourInto(it.secondSlot, pour) }
            }
        }
        for ((healthy, dry) in CACTI) {
            onOpLoc1(healthy) {
                val blade = bestBlade()
                if (blade == null) {
                    mes("You need a knife or other slashing weapon to cut the cactus...")
                } else {
                    cutCactus(it.loc, dry, knife = blade.id == knifeId)
                }
            }
            onOpLocU(healthy) { event ->
                if (isBlade(event.objType)) {
                    cutCactus(event.loc, dry, knife = event.objType.id == knifeId)
                } else {
                    mes("Nothing interesting happens.")
                }
            }
            onOpLocU(dry) { mes("This cactus is totally dry and has no fluid.") }
        }
    }

    /** Every loc the cache names as a place to draw water. */
    private fun waterSources(): List<String> {
        val locs = ConstantProvider.mappings[RSCMType.LOC.prefix].orEmpty()
        val byId = sortedMapOf<Int, String>()
        for ((name, id) in locs) {
            val type = ServerCacheManager.getObject(id) ?: continue
            if (type.name in WATER_SOURCE_NAMES) {
                byId.putIfAbsent(id, name)
            }
        }
        return byId.values.toList()
    }

    private suspend fun ProtectedAccess.fillAtSource() {
        arriveDelay()
        while (true) {
            val slot = emptiestSkinSlot() ?: return
            anim(FILL_SEQ)
            soundSynth(LIQUID_SOUND)
            invReplaceSlot(inv, slot, 1, doseType(MAX_DOSES))
            mes("You fill the waterskin.")
            delay(FILL_TICKS)
        }
    }

    private fun ProtectedAccess.pourInto(skinSlot: Int, pour: Pour) {
        val skin = inv[skinSlot] ?: return
        val doses = doseIds.indexOf(skin.id)
        if (doses < 0 || doses >= MAX_DOSES) {
            mes("The waterskin is already full.")
            return
        }
        val filled = (doses + pour.doses).coerceAtMost(MAX_DOSES)
        if (invReplace(inv, pour.full, 1, pour.empty).failure) {
            return
        }
        invReplaceSlot(inv, skinSlot, 1, doseType(filled))
        soundSynth(LIQUID_SOUND)
        mes("You fill the waterskin with the water from the ${pour.name}.")
    }

    /**
     * Slices a healthy cactus open. The roll is on the visible Woodcutting level alone; the
     * blade makes no difference beyond having one.
     */
    private suspend fun ProtectedAccess.cutCactus(cactus: BoundLocInfo, dry: String, knife: Boolean) {
        arriveDelay()
        faceLoc(cactus)
        anim(if (knife) KNIFE_SLASH_SEQ else SWORD_SLASH_SEQ)
        soundSynth(SLASH_SOUND)
        delay(CUT_TICKS)
        if (!statRandom("stat.woodcutting", CUT_LOW, CUT_HIGH, 0)) {
            mes("You fail to cut the cactus correctly and it gives no water this time.")
            statAdvance("stat.woodcutting", MISS_XP)
        } else {
            val slot = emptiestSkinSlot()
            if (slot == null) {
                mes("You have no empty waterskins to put the water in.")
            } else {
                val doses = doseIds.indexOf(inv[slot]!!.id)
                invReplaceSlot(inv, slot, 1, doseType(doses + 1))
                soundSynth(LIQUID_SOUND)
                mes("You top up your skin with water from the cactus.")
            }
            statAdvance("stat.woodcutting", CUT_XP)
        }
        // Swapping the loc ends this script, so the dried cactus is the last thing it does.
        locRepo.change(cactus, dry, DRY_TICKS)
    }

    /** The slot of the waterskin with the least water in it that is not already full. */
    private fun ProtectedAccess.emptiestSkinSlot(): Int? {
        for (dose in 0 until MAX_DOSES) {
            val slot = inv.indexOfFirst { it?.id == doseIds[dose] }
            if (slot >= 0) {
                return slot
            }
        }
        return null
    }

    private fun ProtectedAccess.bestBlade(): ItemServerType? {
        val candidates = buildList<InvObj> {
            player.righthand?.let(::add)
            addAll(inv.filterNotNull { true })
        }
        return candidates.map { getInvObj(it) }.firstOrNull(::isBlade)
    }

    private fun isBlade(type: ItemServerType): Boolean =
        type.id == knifeId || (type.paramOrNull(params.attack_slash) ?: 0) > 0

    data class Pour(val full: String, val empty: String, val name: String, val doses: Int)

    companion object {
        /** Waterskins indexed by how many doses they hold. */
        val DOSES =
            listOf(
                "obj.water_skin0",
                "obj.water_skin1",
                "obj.water_skin2",
                "obj.water_skin3",
                "obj.water_skin4",
            )
        const val EMPTY = "obj.water_skin0"
        private const val MAX_DOSES = 4

        private val REFILLABLE = DOSES.dropLast(1)

        private val doseIds: List<Int> by lazy { DOSES.map { it.asRSCM(RSCMType.OBJ) } }
        private val knifeId: Int by lazy { KNIFE.asRSCM(RSCMType.OBJ) }

        private fun doseType(doses: Int): ItemServerType =
            ServerCacheManager.getItem(doseIds[doses]) ?: error("Missing obj: ${DOSES[doses]}")

        private val WATER_SOURCE_NAMES =
            setOf("Sink", "Well", "Water pump", "Waterpump", "Fountain", "Water trough")

        private val CONTAINERS =
            listOf(
                "obj.bucket_water" to Pour("obj.bucket_water", "obj.bucket_empty", "bucket", 4),
                "obj.bowl_water" to Pour("obj.bowl_water", "obj.bowl_empty", "bowl", 3),
                "obj.jug_water" to Pour("obj.jug_water", "obj.jug_empty", "jug", 2),
                "obj.vial_water" to Pour("obj.vial_water", "obj.vial_empty", "vial", 1),
            )

        private val CACTI =
            listOf(
                "loc.desert_cactus_full" to "loc.desert_cactus_empty",
                "loc.desert_cactus_full_alt" to "loc.desert_cactus_empty_alt",
            )

        private const val KNIFE = "obj.knife"
        private const val KNIFE_SLASH_SEQ = "seq.human_knife_slash"
        private const val SWORD_SLASH_SEQ = "seq.human_sword_slash"
        private const val FILL_SEQ = "seq.human_pickuptable"
        private const val SLASH_SOUND = "synth.hacksword_slash"
        private const val LIQUID_SOUND = "synth.liquid"

        private const val FILL_TICKS = 2
        private const val CUT_TICKS = 2
        private const val CUT_LOW = 30
        private const val CUT_HIGH = 252
        private const val CUT_XP = 10.0
        private const val MISS_XP = 0.4
        private const val DRY_TICKS = 100
    }
}
