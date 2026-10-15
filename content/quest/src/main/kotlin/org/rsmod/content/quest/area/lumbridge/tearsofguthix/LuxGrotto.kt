package org.rsmod.content.quest.area.lumbridge.tearsofguthix

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.player.stat.firemakingLvl
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.script.onApNpc1
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.CRAFTING_REQ
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.EMPTY_LANTERN
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.FIREMAKING_REQ
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.LIT_LANTERN
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.MAGIC_STONE
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.MINING_REQ
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.STONE_BOWL
import org.rsmod.content.quest.area.lumbridge.tearsofguthix.TearsOfGuthixQuest.Companion.UNLIT_LANTERN
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Chasm of Tears side of the quest: the sapphire lantern, the light creatures that carry a
 * lantern-bearer across the chasm, and the magic stone on the far side that becomes Juna's bowl.
 */
class LuxGrotto @Inject constructor(private val quest: TearsOfGuthixQuest) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(LIGHT_CREATURE) { attract(it.npc) }
        onApNpc1(LIGHT_CREATURE) {
            if (isWithinApRange(it.npc, CREATURE_REACH)) {
                attract(it.npc)
            }
        }
        onOpNpcU(LIGHT_CREATURE, LIT_LANTERN) { attract(it.npc) }
        for (rocks in MAGICAL_ROCKS) {
            onOpLoc1(rocks) { mine(it.vis) }
        }
        onOpHeldU(CHISEL, MAGIC_STONE) { carveBowl() }
        for ((lantern, result) in SAPPHIRE_FITTINGS) {
            onOpHeldU(SAPPHIRE, lantern) { fitSapphire(lantern, result) }
        }
        onOpHeldU(LENS, EMPTY_LANTERN) { fitLens(EMPTY_LANTERN, "obj.bullseye_lantern_empty") }
        onOpHeldU(LENS, UNLIT_LANTERN) { fitLens(UNLIT_LANTERN, "obj.bullseye_lantern_unlit") }
        onOpHeldU(TINDERBOX, UNLIT_LANTERN) { light() }
        onOpHeldU(TINDERBOX, EMPTY_LANTERN) { mes("The lantern has no oil in it.") }
        onOpHeld1(LIT_LANTERN) { extinguish() }
    }

    internal suspend fun ProtectedAccess.attract(creature: Npc) {
        if (quest.stage(player) == 0 || LIT_LANTERN !in inv) {
            mes(constants.dm_default)
            return
        }
        val north = coords.z >= CHASM_MIDDLE_Z
        val landing = if (north) SOUTH_LANDING else NORTH_LANDING
        faceEntitySquare(creature)
        mes("The light-creature is attracted to your beam and carries you across the chasm.")
        anim(FLOAT_UP_SEQ)
        delay(1)
        anim(FLOAT_ACROSS_SEQ)
        exactMove(
            start = coords,
            end = landing,
            delay1 = 0,
            delay2 = CROSS_TICKS * CLIENT_CYCLES_PER_TICK,
            dir = if (north) constants.em_face_south else constants.em_face_north,
            teleportType = TeleportType.Exempt,
        )
        delay(CROSS_TICKS)
        anim(FLOAT_DOWN_SEQ)
        delay(1)
    }

    internal suspend fun ProtectedAccess.mine(rocks: BoundLocInfo) {
        arriveDelay()
        if (quest.isComplete(player)) {
            mes(constants.dm_default)
            return
        }
        if (player.miningLvl < MINING_REQ) {
            mes("You need a Mining level of $MINING_REQ to mine this rock.")
            return
        }
        val pickaxe = bestPickaxe()
        if (pickaxe == null) {
            mes(
                "You need a pickaxe to mine this rock. You do not have a pickaxe which you have the " +
                    "Mining level to use.",
            )
            return
        }
        if (inv.isFull()) {
            mes("Your inventory is too full to hold any more stone.")
            return
        }
        faceLoc(rocks)
        mes("You swing your pick at the rock.")
        anim(pickaxeAnim(pickaxe))
        delay(MINE_TICKS)
        if (invAdd(inv, MAGIC_STONE).success) {
            mes("You manage to mine some stone.")
        }
    }

    internal fun ProtectedAccess.carveBowl() {
        if (quest.stage(player) != STAGE_STARTED) {
            mes(constants.dm_default)
            return
        }
        anim(CARVE_SEQ)
        if (invReplace(inv, MAGIC_STONE, 1, STONE_BOWL).success) {
            mes("You make a stone bowl.")
        }
    }

    private fun ProtectedAccess.fitSapphire(lantern: String, result: String) {
        if (player.craftingLvl < CRAFTING_REQ) {
            mes("You need a Crafting level of $CRAFTING_REQ to fit a sapphire into the lantern.")
            return
        }
        if (invDel(inv, SAPPHIRE).failure) {
            return
        }
        invReplace(inv, lantern, 1, result)
        mes("You fit the sapphire into the lantern.")
        if (lantern != LANTERN_FRAME && inv.freeSpace() > 0) {
            invAdd(inv, LENS)
            mes("You keep the old lens.")
        }
    }

    private fun ProtectedAccess.fitLens(lantern: String, result: String) {
        if (invDel(inv, LENS).failure) {
            return
        }
        invReplace(inv, lantern, 1, result)
        invAdd(inv, SAPPHIRE)
        mes("You fit the lens into the lantern and take out the sapphire.")
    }

    private fun ProtectedAccess.light() {
        if (player.firemakingLvl < FIREMAKING_REQ) {
            mes("You need a Firemaking level of $FIREMAKING_REQ to light the lantern.")
            return
        }
        if (invReplace(inv, UNLIT_LANTERN, 1, LIT_LANTERN).success) {
            mes("You light the lantern.")
        }
    }

    private fun ProtectedAccess.extinguish() {
        if (invReplace(inv, LIT_LANTERN, 1, UNLIT_LANTERN).success) {
            mes("You extinguish the lantern.")
        }
    }

    private fun ProtectedAccess.bestPickaxe(): ItemServerType? {
        val carried = inv.filterNotNull { true } + listOfNotNull(player.righthand)
        return carried
            .map { getInvObj(it) }
            .filter {
                it.isContentType(PICKAXE_CONTENT) &&
                    player.miningLvl >= (it.paramOrNull(params.levelrequire) ?: 1)
            }
            .maxByOrNull { it.paramOrNull(params.levelrequire) ?: 1 }
    }

    private fun pickaxeAnim(pickaxe: ItemServerType): String {
        val seq = pickaxe.paramOrNull(params.skill_anim) ?: return DEFAULT_MINE_SEQ
        return RSCM.getReverseMapping(RSCMType.SEQ, seq.id)
    }

    companion object {
        const val LIGHT_CREATURE = "npc.tog_light_creature"
        const val CREATURE_REACH = 3
        val MAGICAL_ROCKS =
            listOf("loc.tog_blue_stone_rocks1", "loc.tog_blue_stone_rocks2", "loc.tog_blue_stone_rocks3")

        const val CHISEL = "obj.chisel"
        const val SAPPHIRE = "obj.sapphire"
        const val LENS = "obj.bullseye_lantern_lens"
        const val TINDERBOX = "obj.tinderbox"
        const val LANTERN_FRAME = "obj.bullseye_lantern_nolens"
        val SAPPHIRE_FITTINGS =
            mapOf(
                LANTERN_FRAME to EMPTY_LANTERN,
                "obj.bullseye_lantern_empty" to EMPTY_LANTERN,
                "obj.bullseye_lantern_unlit" to UNLIT_LANTERN,
            )

        const val PICKAXE_CONTENT = "content.mining_pickaxe"
        const val DEFAULT_MINE_SEQ = "seq.human_mining_bronze_pickaxe"
        const val CARVE_SEQ = "seq.human_crafting"
        const val FLOAT_UP_SEQ = "seq.tog_player_float_up"
        const val FLOAT_ACROSS_SEQ = "seq.tog_player_float_across"
        const val FLOAT_DOWN_SEQ = "seq.tog_player_float_down"
        const val MINE_TICKS = 9
        const val CROSS_TICKS = 8
        const val CLIENT_CYCLES_PER_TICK = 30

        /** The ledge north of the chasm and the magic stone cave south of it. */
        const val CHASM_MIDDLE_Z = 9515
        val NORTH_LANDING = CoordGrid(3229, 9527, 2)
        val SOUTH_LANDING = CoordGrid(3228, 9501, 2)
    }
}
