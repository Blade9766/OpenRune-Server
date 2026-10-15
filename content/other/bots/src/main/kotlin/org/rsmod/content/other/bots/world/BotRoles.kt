package org.rsmod.content.other.bots.world

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import org.rsmod.api.player.stat.baseRunecraftingLvl
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.player.stat.woodcuttingLvl
import org.rsmod.api.table.runecrafting.RunecraftingAltarsRow
import org.rsmod.content.other.bots.core.BotDestination
import org.rsmod.content.other.bots.core.BotRoleType
import org.rsmod.content.other.bots.core.EssenceSource
import org.rsmod.content.other.bots.core.GatherStatus
import org.rsmod.content.skills.mining.configs.MiningRocks
import org.rsmod.content.skills.mining.scripts.Mining
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting.Companion.treeLevelReq
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting.Companion.treeLogs
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.map.CoordGrid

/** Skill-specific behaviour. New skills plug in here and in [BotRoleBehaviour.of]. */
interface BotRoleBehaviour {
    fun requirementProblem(player: Player): String?

    /** Items that stay in the inventory when banking (tools, supplies). */
    fun keep(itemId: Int): Boolean

    fun needsSupplies(player: Player): Boolean = false

    /** Item ids withdrawn from the bank as supplies, best first. */
    fun supplyIds(): List<Int> = emptyList()

    fun isAt(player: Player, destination: BotDestination): Boolean = false

    /** Takes one step out of an instance-like area (altar, essence mine); true while escaping. */
    fun escape(player: Player, destination: BotDestination): Boolean = false

    fun gather(player: Player, target: BotDestination): GatherStatus

    companion object {
        fun of(site: BotSite, role: BotRoleType, essence: EssenceSource, world: BotWorldServices): BotRoleBehaviour =
            when (role) {
                BotRoleType.Woodcutting -> WoodcuttingBehaviour(site, world)
                BotRoleType.Mining -> MiningBehaviour(site, world)
                BotRoleType.Runecrafting -> RunecraftingBehaviour(site, essence, world)
            }
    }
}

/** Shared loop for "click the nearest suitable loc, let the skill script run, repeat". */
abstract class LocGatheringBehaviour(
    protected val site: BotSite,
    protected val world: BotWorldServices,
    private val contentGroup: String,
) : BotRoleBehaviour {
    private val contentId by lazy { contentGroup.asRSCM(RSCMType.CONTENT) }
    protected val productIds: Set<Int> by lazy {
        site.products.mapNotNull { runCatching { it.asRSCM(RSCMType.OBJ) }.getOrNull() }.toSet()
    }

    abstract fun product(type: ObjectServerType): Int?

    abstract fun levelRequired(type: ObjectServerType): Int

    abstract fun level(player: Player): Int

    open fun missingTool(player: Player, type: ObjectServerType): String? = null

    override fun keep(itemId: Int): Boolean = isTool(itemId)

    abstract fun isTool(itemId: Int): Boolean

    override fun gather(player: Player, target: BotDestination): GatherStatus {
        if (player.inv.isFull()) {
            return GatherStatus.InventoryFull
        }
        if (player.interaction != null) {
            return GatherStatus.Working(0)
        }
        val candidates =
            world.findLocs(site.resource.center(), site.resource.radius) { type ->
                type.contentGroup == contentId && product(type) in productIds
            }
        if (candidates.isEmpty()) {
            return GatherStatus.NoResource
        }
        val usable = candidates.filter { (_, type) -> levelRequired(type) <= level(player) }
        if (usable.isEmpty()) {
            val lowest = candidates.minOf { (_, type) -> levelRequired(type) }
            return GatherStatus.Failed("needs level $lowest to gather here")
        }
        val (loc, type) = usable.minBy { (loc, _) -> loc.coords.chebyshevDistance(player.coords) }
        missingTool(player, type)?.let { return GatherStatus.Failed(it) }
        world.opLoc(player, loc, type, InteractionOp.Op1)
        return GatherStatus.Working(0)
    }

    override fun isAt(player: Player, destination: BotDestination): Boolean =
        destination == BotDestination.Resource && site.resource.contains(player.coords)
}

class WoodcuttingBehaviour(site: BotSite, world: BotWorldServices) :
    LocGatheringBehaviour(site, world, "content.tree") {
    override fun product(type: ObjectServerType): Int? = runCatching { type.treeLogs.id }.getOrNull()

    override fun levelRequired(type: ObjectServerType): Int = type.treeLevelReq

    override fun level(player: Player): Int = player.woodcuttingLvl

    override fun missingTool(player: Player, type: ObjectServerType): String? =
        if (Woodcutting.findAxe(player, type) == null) "no usable axe" else null

    override fun requirementProblem(player: Player): String? {
        val carried = (player.inv.objs.asSequence() + player.worn.objs.asSequence()).filterNotNull()
        return if (carried.none { isTool(it.id) }) "no axe" else null
    }

    override fun isTool(itemId: Int): Boolean = nameOf(itemId).endsWith("_axe")
}

class MiningBehaviour(site: BotSite, world: BotWorldServices) :
    LocGatheringBehaviour(site, world, "content.rock") {
    override fun product(type: ObjectServerType): Int? = MiningRocks.forLoc(type)?.oreItem?.id

    override fun levelRequired(type: ObjectServerType): Int = MiningRocks.forLoc(type)?.level ?: Int.MAX_VALUE

    override fun level(player: Player): Int = player.miningLvl

    override fun requirementProblem(player: Player): String? =
        if (Mining.findPickaxe(player) == null) "no usable pickaxe" else null

    override fun isTool(itemId: Int): Boolean = nameOf(itemId).endsWith("pickaxe")
}

/**
 * Crafts at the site's altar. With [EssenceSource.Mine] it also fetches essence through the
 * essence-mine teleport NPC (Aubury), mines it, and leaves through the mine's portal.
 */
class RunecraftingBehaviour(
    private val site: BotSite,
    private val essence: EssenceSource,
    private val world: BotWorldServices,
) : BotRoleBehaviour {
    private val altar: RunecraftingAltarsRow? by lazy {
        RunecraftingAltarsRow.all().firstOrNull { it.altarObject.internalName == site.altarLoc }
    }

    private val essenceIds: List<Int> by lazy {
        altar?.rune?.input?.map { it.id }?.sortedDescending() ?: emptyList()
    }

    private val mineRockIds: Set<Int> by lazy {
        listOf("obj.blankrune", "obj.blankrune_high")
            .mapNotNull { runCatching { it.asRSCM(RSCMType.OBJ) }.getOrNull() }
            .toSet()
    }

    override fun requirementProblem(player: Player): String? {
        val altar = altar ?: return "site has no valid altar_loc"
        val tiara = altar.tiara?.item?.internalName ?: return "altar has no tiara"
        if (tiara !in player.worn) {
            return "must wear $tiara"
        }
        val level = altar.rune.statReq.first().t1
        if (player.baseRunecraftingLvl < level) {
            return "needs Runecrafting level $level"
        }
        val carried = (player.inv.objs.asSequence() + player.worn.objs.asSequence()).filterNotNull()
        if (essence == EssenceSource.Mine && carried.none { isPickaxe(it.id) }) {
            return "needs a pickaxe to mine essence"
        }
        if (essence == EssenceSource.Mine && !world.hasCompletedQuest(player, RUNE_MYSTERIES)) {
            return "needs Rune Mysteries for the essence mine teleport; use essence bank"
        }
        return null
    }

    override fun keep(itemId: Int): Boolean =
        itemId in essenceIds || isPickaxe(itemId) || nameOf(itemId).endsWith("_talisman")

    override fun needsSupplies(player: Player): Boolean = essenceCount(player) == 0

    override fun supplyIds(): List<Int> = essenceIds

    override fun isAt(player: Player, destination: BotDestination): Boolean =
        when (destination) {
            BotDestination.Resource -> site.resource.contains(player.coords) || insideAltar(player)
            BotDestination.SupplySource ->
                insideEssenceMine(player) || site.supplyArea?.contains(player.coords) == true
            else -> false
        }

    override fun escape(player: Player, destination: BotDestination): Boolean {
        if (insideAltar(player) && destination != BotDestination.Resource) {
            if (player.interaction == null) {
                val portal = altar?.exitPortal?.id ?: return false
                clickNearest(player, player.coords, ESCAPE_RADIUS) { it.id == portal }
            }
            return true
        }
        if (insideEssenceMine(player) && destination != BotDestination.SupplySource) {
            if (player.interaction == null) {
                val portal = runCatching { MINE_PORTAL.asRSCM(RSCMType.LOC) }.getOrNull() ?: return false
                clickNearest(player, player.coords, ESCAPE_RADIUS) { it.id == portal }
            }
            return true
        }
        return false
    }

    override fun gather(player: Player, target: BotDestination): GatherStatus =
        if (target == BotDestination.SupplySource) mineEssence(player) else craft(player)

    private fun craft(player: Player): GatherStatus {
        val altar = altar ?: return GatherStatus.Failed("site has no valid altar_loc")
        if (player.interaction != null) {
            return GatherStatus.Working(0)
        }
        if (needsSupplies(player)) {
            return GatherStatus.NeedsSupplies
        }
        if (insideAltar(player)) {
            val altarId = altar.altarObject.id
            val clicked = clickNearest(player, player.coords, ESCAPE_RADIUS) { it.id == altarId }
            return if (clicked) GatherStatus.Working(0) else GatherStatus.Failed("altar not found")
        }
        val ruins = altar.ruins.map { it.id }.toSet()
        val clicked =
            clickNearest(player, site.resource.center(), site.resource.radius + 4) { type ->
                type.id in ruins || type.multiDefault in ruins || type.multiLoc.any { (it and 0xFFFF) in ruins }
            }
        return if (clicked) GatherStatus.Working(0) else GatherStatus.Failed("ruins not found")
    }

    private fun mineEssence(player: Player): GatherStatus {
        if (player.interaction != null) {
            return GatherStatus.Working(0)
        }
        if (!insideEssenceMine(player)) {
            val npcName = site.supplyNpc.ifBlank { return GatherStatus.Failed("site has no supply_npc") }
            val npcId = runCatching { npcName.asRSCM(RSCMType.NPC) }.getOrNull()
                ?: return GatherStatus.Failed("unknown npc $npcName")
            val area = site.supplyArea ?: return GatherStatus.Failed("site has no supply_area")
            val npc = world.findNpc(area.center(), area.radius + 6, npcId)
                ?: return GatherStatus.NoResource
            world.opNpc(player, npc, InteractionOp.Op4)
            return GatherStatus.Working(0)
        }
        if (player.inv.isFull()) {
            return GatherStatus.InventoryFull
        }
        val clicked =
            clickNearest(player, player.coords, ESSENCE_MINE_RADIUS) { type ->
                MiningRocks.forLoc(type)?.oreItem?.id in mineRockIds
            }
        return if (clicked) GatherStatus.Working(0) else GatherStatus.NoResource
    }

    private fun clickNearest(
        player: Player,
        center: CoordGrid,
        radius: Int,
        filter: (ObjectServerType) -> Boolean,
    ): Boolean {
        val (loc, type) =
            world.findLocs(center, radius, filter).minByOrNull { (loc, _) ->
                loc.coords.chebyshevDistance(player.coords)
            } ?: return false
        return world.opLoc(player, loc, type, InteractionOp.Op1)
    }

    private fun essenceCount(player: Player): Int =
        player.inv.objs.sumOf { if (it != null && it.id in essenceIds) it.count else 0 }

    private fun insideAltar(player: Player): Boolean {
        val entrance = altar?.entrance ?: return false
        return player.coords.level == entrance.level && player.coords.chebyshevDistance(entrance) <= ALTAR_RADIUS
    }

    private fun insideEssenceMine(player: Player): Boolean =
        player.coords.chebyshevDistance(ESSENCE_MINE) <= ESSENCE_MINE_RADIUS

    private fun isPickaxe(itemId: Int): Boolean = nameOf(itemId).endsWith("pickaxe")

    private companion object {
        private const val ALTAR_RADIUS = 30
        private const val ESCAPE_RADIUS = 30
        private const val ESSENCE_MINE_RADIUS = 60
        private const val MINE_PORTAL = "loc.blankrunestone_exit_portal"
        private const val RUNE_MYSTERIES = "quest_runemysteries"
        private val ESSENCE_MINE = CoordGrid(2910, 4832)
    }
}

private fun nameOf(itemId: Int): String = ServerCacheManager.getItem(itemId)?.internalName.orEmpty()
