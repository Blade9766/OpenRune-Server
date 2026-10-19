package org.rsmod.content.other.bots.world

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import java.util.IdentityHashMap
import kotlin.random.Random
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.player.isInCombat as isPlayerInCombat
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.baseRunecraftingLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.player.stat.woodcuttingLvl
import org.rsmod.api.table.runecrafting.RunecraftingAltarsRow
import org.rsmod.content.other.bots.core.BotDestination
import org.rsmod.content.other.bots.core.BotRoleType
import org.rsmod.content.other.bots.core.BotTargeting
import org.rsmod.content.other.bots.core.EssenceSource
import org.rsmod.content.other.bots.core.GatherStatus
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.skills.mining.configs.MiningRocks
import org.rsmod.content.skills.mining.scripts.Mining
import org.rsmod.content.skills.smithing.util.SmithingData
import org.rsmod.content.skills.smithing.util.SmithingProductMeta
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting.Companion.treeLevelReq
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting.Companion.treeLogs
import org.rsmod.content.slayer.core.SlayerTaskManager
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionNpcOp
import org.rsmod.game.interact.InteractionObjOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.map.CoordGrid

/** Skill-specific behaviour. New skills plug in here and in [BotRoleBehaviour.of]. */
interface BotRoleBehaviour {
    /** What the bot is working on and why, for `::bot trace`. */
    val focus: String?
        get() = null

    fun requirementProblem(player: Player): String?

    /** Sets up character state the role relies on, before the bot logs in. */
    fun prepare(player: Player) {}

    /** Items that stay in the inventory when banking (tools, supplies). */
    fun keep(itemId: Int): Boolean

    fun needsSupplies(player: Player): Boolean = false

    /** Item ids withdrawn from the bank as supplies, best first. */
    fun supplyIds(): List<Int> = emptyList()

    /** How many of a supply to carry when the inventory has [free] slots. */
    fun supplyAmount(free: Int): Int = free

    fun isAt(player: Player, destination: BotDestination): Boolean = false

    fun canWorkWithoutSupplies(player: Player): Boolean = false

    fun restsWithoutSupplies(): Boolean = false

    /** True when the bot must visit the site's supply source before working (a slayer master). */
    fun needsSourceVisit(player: Player): Boolean = false

    /** Where the bot works right now, when that is not the site's own resource area (slayer hunts). */
    fun resourceArea(player: Player): SiteArea? = null

    fun resourceRoute(player: Player): List<SiteTile>? = null

    /** Takes one step out of an instance-like area (altar, essence mine); true while escaping. */
    fun escape(player: Player, destination: BotDestination): Boolean = false

    fun gather(player: Player, target: BotDestination): GatherStatus

    /** Answers a modal the role's work opens (a make interface); false lets the bot close it. */
    fun answerModal(player: Player): Boolean = false

    companion object {
        fun of(site: BotSite, role: BotRoleType, essence: EssenceSource, world: BotWorldServices): BotRoleBehaviour =
            when (role) {
                BotRoleType.Woodcutting -> WoodcuttingBehaviour(site, world)
                BotRoleType.Mining ->
                    if (site.supplyNpc.isNotBlank()) EssenceMiningBehaviour(site, world) else MiningBehaviour(site, world)
                BotRoleType.Runecrafting -> RunecraftingBehaviour(site, essence, world)
                BotRoleType.Fishing -> FishingBehaviour(site, world)
                BotRoleType.Thieving -> StallThievingBehaviour(site, world)
                BotRoleType.Combat -> CombatBehaviour(site, world)
                BotRoleType.Slayer -> SlayerBehaviour(site, world)
                BotRoleType.Smithing -> SmithingBehaviour(site, world)
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

    override var focus: String? = null
        protected set

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
        val claims = world.claims(player, site.resource.center(), site.resource.radius)
        val (loc, type) =
            BotTargeting.pick(
                usable,
                Random,
                distance = { (loc, _) -> loc.coords.chebyshevDistance(player.coords) },
                crowd = { (loc, _) -> claims.on(loc) },
            )!!
        missingTool(player, type)?.let { return GatherStatus.Failed(it) }
        world.opLoc(player, loc, type, InteractionOp.Op1)
        focus = describe(type.name, loc.coords, claims.on(loc), usable.size)
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

    private val mine = EssenceMine(site, world)

    override fun prepare(player: Player) {
        if (essence == EssenceSource.Mine) {
            mine.prepare(player)
        }
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
        if (essence == EssenceSource.Mine && Mining.findPickaxe(player) == null) {
            return "needs a pickaxe it has the Mining level to use"
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
                mine.inside(player) || site.supplyArea?.contains(player.coords) == true
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
        return destination != BotDestination.SupplySource && mine.leave(player)
    }

    override fun gather(player: Player, target: BotDestination): GatherStatus =
        if (target == BotDestination.SupplySource) mine.mine(player) else craft(player)

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

    private fun clickNearest(
        player: Player,
        center: CoordGrid,
        radius: Int,
        filter: (ObjectServerType) -> Boolean,
    ): Boolean = world.clickNearestLoc(player, center, radius, filter)

    private fun essenceCount(player: Player): Int =
        player.inv.objs.sumOf { if (it != null && it.id in essenceIds) it.count else 0 }

    private fun insideAltar(player: Player): Boolean {
        val entrance = altar?.entrance ?: return false
        return player.coords.level == entrance.level && player.coords.chebyshevDistance(entrance) <= ALTAR_RADIUS
    }

    private fun isPickaxe(itemId: Int): Boolean = nameOf(itemId).endsWith("pickaxe")

    private companion object {
        private const val ALTAR_RADIUS = 30
        private const val ESCAPE_RADIUS = 30
    }
}

/**
 * The rune essence mine, reached through the site's [BotSite.supplyNpc] teleport (Aubury) and left
 * through its exit portal. Shared by essence miners and mine-fed runecrafters.
 */
class EssenceMine(private val site: BotSite, private val world: BotWorldServices) {
    private val rockProducts: Set<Int> by lazy { resolveIds(listOf("obj.blankrune", "obj.blankrune_high"), RSCMType.OBJ) }

    fun prepare(player: Player) {
        Quest.get(RUNE_MYSTERIES)?.let { if (!it.isQuestCompleted(player)) it.jumpToStage(player, it.maxSteps) }
    }

    fun inside(player: Player): Boolean = player.coords.chebyshevDistance(MINE_CENTER) <= MINE_RADIUS

    /** Clicks the exit portal; true while the bot is still inside the mine. */
    fun leave(player: Player): Boolean {
        if (!inside(player)) {
            return false
        }
        if (player.interaction == null) {
            val portal = runCatching { MINE_PORTAL.asRSCM(RSCMType.LOC) }.getOrNull() ?: return false
            world.clickNearestLoc(player, player.coords, PORTAL_RADIUS) { it.id == portal }
        }
        return true
    }

    /** Teleports in through the supply npc, then mines essence until the inventory is full. */
    fun mine(player: Player): GatherStatus {
        if (player.interaction != null) {
            return GatherStatus.Working(0)
        }
        if (!inside(player)) {
            val npcName = site.supplyNpc.ifBlank { return GatherStatus.Failed("site has no supply_npc") }
            val npcId = runCatching { npcName.asRSCM(RSCMType.NPC) }.getOrNull()
                ?: return GatherStatus.Failed("unknown npc $npcName")
            val area = site.supplyArea ?: site.resource
            val npc = world.findNpc(area.center(), area.radius + 6, npcId) ?: return GatherStatus.NoResource
            world.opNpc(player, npc, InteractionOp.Op4)
            return GatherStatus.Working(0)
        }
        if (player.inv.isFull()) {
            return GatherStatus.InventoryFull
        }
        val clicked =
            world.clickNearestLoc(player, player.coords, MINE_RADIUS) { type ->
                MiningRocks.forLoc(type)?.oreItem?.id in rockProducts
            }
        return if (clicked) GatherStatus.Working(0) else GatherStatus.NoResource
    }

    private companion object {
        private const val MINE_RADIUS = 60
        private const val PORTAL_RADIUS = 30
        private const val MINE_PORTAL = "loc.blankrunestone_exit_portal"
        private const val RUNE_MYSTERIES = "quest_runemysteries"
        private val MINE_CENTER = CoordGrid(2910, 4832)
    }
}

/**
 * Mines essence to sell: the site's [BotSite.resource] surrounds the teleport npc, and working
 * there means teleporting into the essence mine and mining until the inventory is full.
 */
class EssenceMiningBehaviour(private val site: BotSite, world: BotWorldServices) : BotRoleBehaviour {
    private val mine = EssenceMine(site, world)

    override fun prepare(player: Player) = mine.prepare(player)

    override fun requirementProblem(player: Player): String? =
        if (Mining.findPickaxe(player) == null) "no usable pickaxe" else null

    override fun keep(itemId: Int): Boolean = nameOf(itemId).endsWith("pickaxe")

    override fun isAt(player: Player, destination: BotDestination): Boolean =
        destination == BotDestination.Resource && (mine.inside(player) || site.resource.contains(player.coords))

    override fun escape(player: Player, destination: BotDestination): Boolean =
        destination != BotDestination.Resource && mine.leave(player)

    override fun gather(player: Player, target: BotDestination): GatherStatus = mine.mine(player)
}

private fun BotWorldServices.clickNearestLoc(
    player: Player,
    center: CoordGrid,
    radius: Int,
    filter: (ObjectServerType) -> Boolean,
): Boolean {
    val (loc, type) =
        findLocs(center, radius, filter).minByOrNull { (loc, _) -> loc.coords.chebyshevDistance(player.coords) }
            ?: return false
    return opLoc(player, loc, type, InteractionOp.Op1)
}

/** A site's [BotSite.targets]: exact `npc.`/`loc.` types, `content.` groups and `name:` names. */
private class SiteTargets(names: List<String>, exactType: RSCMType) {
    private val exact: Set<Int> = resolveIds(names.filterNot { it.startsWith(CONTENT) || it.startsWith(NAME) }, exactType)
    private val groups: Set<Int> = resolveIds(names.filter { it.startsWith(CONTENT) }, RSCMType.CONTENT)
    private val displayNames: Set<String> = names.filter { it.startsWith(NAME) }.map { it.removePrefix(NAME).lowercase() }.toSet()

    fun matches(typeId: Int, contentGroup: Int, name: String): Boolean =
        typeId in exact || contentGroup in groups || name.lowercase() in displayNames

    private companion object {
        private const val CONTENT = "content."
        private const val NAME = "name:"
    }
}

private fun resolveIds(names: List<String>, type: RSCMType): Set<Int> =
    names.mapNotNull { runCatching { it.asRSCM(type) }.getOrNull() }.toSet()

private fun Player.carriesAny(ids: Set<Int>): Boolean =
    inv.objs.any { it != null && it.id in ids } || worn.objs.any { it != null && it.id in ids }

private fun Player.isActing(): Boolean = interaction != null || weakQueueList.isNotEmpty

private fun interactionOp(op: Int): InteractionOp =
    when (op) {
        2 -> InteractionOp.Op2
        3 -> InteractionOp.Op3
        4 -> InteractionOp.Op4
        5 -> InteractionOp.Op5
        else -> InteractionOp.Op1
    }

/**
 * Clicks the nearest target npc in the resource area (fishing spots). Fishing keeps going through
 * a weak queue rather than the interaction, so a queued catch counts as still working.
 */
class FishingBehaviour(private val site: BotSite, private val world: BotWorldServices) : BotRoleBehaviour {
    private val targets by lazy { SiteTargets(site.targets, RSCMType.NPC) }

    override var focus: String? = null
        private set
    private val toolIds by lazy { resolveIds(site.tools, RSCMType.OBJ) }
    private val supplyIds by lazy { resolveIds(site.supplies, RSCMType.OBJ) }

    override fun requirementProblem(player: Player): String? =
        if (toolIds.isNotEmpty() && !player.carriesAny(toolIds)) "needs one of ${site.tools}" else null

    override fun keep(itemId: Int): Boolean = itemId in toolIds || itemId in supplyIds

    override fun needsSupplies(player: Player): Boolean =
        supplyIds.isNotEmpty() && !player.carriesAny(supplyIds)

    override fun supplyIds(): List<Int> = supplyIds.toList()

    override fun supplyAmount(free: Int): Int = if (site.supplyAmount > 0) site.supplyAmount else free

    override fun isAt(player: Player, destination: BotDestination): Boolean =
        destination == BotDestination.Resource && site.resource.contains(player.coords)

    override fun gather(player: Player, target: BotDestination): GatherStatus {
        if (player.inv.isFull()) {
            return GatherStatus.InventoryFull
        }
        if (needsSupplies(player)) {
            return GatherStatus.NeedsSupplies
        }
        if (player.isActing()) {
            return GatherStatus.Working(0)
        }
        val spot =
            world
                .findNpcs(site.resource.center(), site.resource.radius) { targets.matches(it.type.id, it.type.contentGroup, it.type.name) }
                .minByOrNull { it.coords.chebyshevDistance(player.coords) }
                ?: return GatherStatus.NoResource
        if (!world.opNpc(player, spot, interactionOp(site.targetOp))) {
            return GatherStatus.NoResource
        }
        focus = "${spot.type.name} at ${spot.coords.x},${spot.coords.z}: the nearest spot"
        return GatherStatus.Working(0)
    }
}

/** Steals from the nearest stocked stall loc in the resource area; emptied stalls are skipped. */
class StallThievingBehaviour(private val site: BotSite, private val world: BotWorldServices) : BotRoleBehaviour {
    private val targets by lazy { SiteTargets(site.targets, RSCMType.LOC) }

    override var focus: String? = null
        private set

    override fun requirementProblem(player: Player): String? = null

    override fun keep(itemId: Int): Boolean = false

    override fun isAt(player: Player, destination: BotDestination): Boolean =
        destination == BotDestination.Resource && site.resource.contains(player.coords)

    override fun gather(player: Player, target: BotDestination): GatherStatus {
        if (player.inv.isFull()) {
            return GatherStatus.InventoryFull
        }
        if (player.isActing()) {
            return GatherStatus.Working(0)
        }
        val stalls = world.findLocs(site.resource.center(), site.resource.radius) { targets.matches(it.id, it.contentGroup, it.name) }
        val claims = world.claims(player, site.resource.center(), site.resource.radius)
        val (loc, type) =
            BotTargeting.pick(
                stalls,
                Random,
                distance = { (loc, _) -> loc.coords.chebyshevDistance(player.coords) },
                crowd = { (loc, _) -> claims.on(loc) },
            ) ?: return GatherStatus.NoResource
        if (!world.opLoc(player, loc, type, interactionOp(site.targetOp))) {
            return GatherStatus.NoResource
        }
        focus = describe(type.name, loc.coords, claims.on(loc), stalls.size)
        return GatherStatus.Working(0)
    }
}

/**
 * Fights target npcs in the resource area. Below [EAT_PERCENT] hitpoints it eats, and with no food
 * left it heads back to the bank to restock. Between fights it picks up the site's products.
 */
open class CombatBehaviour(protected val site: BotSite, protected val world: BotWorldServices) : BotRoleBehaviour {
    private val targets by lazy { SiteTargets(site.targets, RSCMType.NPC) }
    private val toolIds by lazy { resolveIds(site.tools, RSCMType.OBJ) }
    private val foodIds by lazy { resolveIds(site.supplies, RSCMType.OBJ) }
    private val siteLootIds by lazy { resolveIds(site.products, RSCMType.OBJ) }

    /** Clicks per drop or npc that never led anywhere; unreachable ones are skipped. */
    private val attempts = IdentityHashMap<Any, Int>()
    private var decisions = 0
    private var lastTarget: Any? = null
    private var engaged = false
    private var targetCombatStamp = 0

    override var focus: String? = null
        protected set

    /** Where the bot fights right now. */
    protected open fun fightArea(player: Player): SiteArea = site.resource

    protected open fun isTarget(player: Player, npc: Npc): Boolean =
        targets.matches(npc.type.id, npc.type.contentGroup, npc.type.name)

    protected open fun lootIds(player: Player): Set<Int> = siteLootIds

    protected open fun attackOp(npc: Npc): InteractionOp = interactionOp(site.targetOp)

    override fun requirementProblem(player: Player): String? =
        if (toolIds.isNotEmpty() && !player.carriesAny(toolIds)) "needs one of ${site.tools}" else null

    override fun keep(itemId: Int): Boolean = itemId in toolIds || itemId in foodIds

    override fun needsSupplies(player: Player): Boolean = foodIds.isNotEmpty() && !player.carriesAny(foodIds)

    override fun supplyIds(): List<Int> = foodIds.toList()

    override fun supplyAmount(free: Int): Int = if (site.supplyAmount > 0) minOf(site.supplyAmount, free) else free

    /** Food is a comfort, not a requirement: without it the bot rests until it is nearly healed. */
    override fun canWorkWithoutSupplies(player: Player): Boolean =
        player.hitpoints * 100 >= player.baseHitpointsLvl * RESTED_PERCENT

    override fun restsWithoutSupplies(): Boolean = true

    override fun isAt(player: Player, destination: BotDestination): Boolean =
        destination == BotDestination.Resource && fightArea(player).contains(player.coords)

    override fun gather(player: Player, target: BotDestination): GatherStatus {
        if (foodIds.isNotEmpty() && player.hitpoints * 100 < player.baseHitpointsLvl * EAT_PERCENT) {
            val food = player.inv.indices.firstOrNull { player.inv[it]?.id in foodIds } ?: return GatherStatus.NeedsSupplies
            world.eat(player, food)
            return GatherStatus.Working(0)
        }
        if (player.inv.isFull()) {
            return GatherStatus.InventoryFull
        }
        if (player.isActing()) {
            if (player.interaction is InteractionObjOp) {
                return GatherStatus.Working(0)
            }
            val fighting = (player.interaction as? InteractionNpcOp)?.target
            if (fighting != null && (fighting.isInCombat() || fighting.vars[LAST_COMBAT] != targetCombatStamp)) {
                engaged = true
                attempts.remove(fighting)
            }
            if (!engaged) {
                retaliate(player, fighting)?.let { return it }
            }
            return GatherStatus.Working(0, progressed = fighting != null)
        }
        lastTarget?.let { if (!engaged) attempts[it] = (attempts[it] ?: 0) + 1 }
        lastTarget = null
        engaged = false
        if (++decisions % ATTEMPTS_RESET == 0) {
            attempts.clear()
        }
        val area = fightArea(player)
        val loot = lootIds(player)
        val drop =
            world
                .findObjs(player, area.center(), area.radius) { it.type in loot && reachable(it) }
                .minByOrNull { it.coords.chebyshevDistance(player.coords) }
        if (drop != null) {
            lastTarget = drop
            world.pickUp(player, drop)
            focus = "picking up ${nameOf(drop.type)} at ${drop.coords.x},${drop.coords.z}"
            return GatherStatus.Working(0)
        }
        retaliate(player, current = null)?.let { return it }
        val claims = world.claims(player, area.center(), area.radius)
        val free =
            world.findNpcs(area.center(), area.radius) {
                isTarget(player, it) && !it.isInCombat() && !claims.taken(it) && reachable(it)
            }
        val npc =
            BotTargeting.pick(free, Random, distance = { it.coords.chebyshevDistance(player.coords) }, crowd = { 0 })
        if (npc == null) {
            attempts.clear()
            return GatherStatus.NoResource
        }
        return attack(player, npc, "the nearest of ${free.size} free targets")
    }

    /**
     * Fights back against whatever is hitting the bot before picking a new target, as a player
     * would; [current] is the npc it is already walking to, kept if that one is the attacker. Drops
     * are grabbed first, or aggressive monsters would keep the bot from ever looting.
     */
    private fun retaliate(player: Player, current: Npc?): GatherStatus? {
        if (!player.isPlayerInCombat()) {
            return null
        }
        val attackers = world.attackers(player)
        if (attackers.isEmpty() || (current != null && current in attackers)) {
            return null
        }
        val attacker = attackers.minBy { it.coords.chebyshevDistance(player.coords) }
        return attack(player, attacker, "it attacked first")
    }

    private fun attack(player: Player, npc: Npc, why: String): GatherStatus {
        lastTarget = npc
        engaged = false
        targetCombatStamp = npc.vars[LAST_COMBAT]
        if (!world.opNpc(player, npc, attackOp(npc))) {
            return GatherStatus.NoResource
        }
        focus = "attacking ${npc.type.name} at ${npc.coords.x},${npc.coords.z}: $why"
        return GatherStatus.Working(0)
    }

    private fun reachable(target: Any): Boolean = (attempts[target] ?: 0) < MAX_ATTEMPTS

    private companion object {
        private const val LAST_COMBAT = "varn.lastcombat"
        private const val MAX_ATTEMPTS = 3
        private const val ATTEMPTS_RESET = 500
        private const val EAT_PERCENT = 50
        private const val RESTED_PERCENT = 90
    }
}

/**
 * Combat on slayer tasks from the site's master ([BotSite.supplyNpc], visited through the supply
 * route). A bot with no task, or a finished one, goes to the master; a task the site has no
 * [SlayerHunt] for is handed back and re-rolled, at most [MAX_REROLLS] times in a row.
 */
class SlayerBehaviour(site: BotSite, world: BotWorldServices) : CombatBehaviour(site, world) {
    private var rerolls = 0

    private fun taskId(player: Player): Int = player.vars[TASK_VARP]

    private fun hunt(player: Player): SlayerHunt? {
        val taskId = taskId(player)
        if (taskId == 0 || player.vars[COUNT_VARP] <= 0) {
            return null
        }
        val name = SlayerTaskManager.slayerTargets.firstOrNull { it.id == taskId }?.nameLowercase ?: return null
        return site.hunts.firstOrNull { it.task.equals(name, ignoreCase = true) }
    }

    override fun prepare(player: Player) {
        Quest.get(MASTER_QUEST)?.let { if (!it.isQuestCompleted(player)) it.jumpToStage(player, it.maxSteps) }
    }

    override fun needsSourceVisit(player: Player): Boolean = hunt(player) == null

    override fun resourceArea(player: Player): SiteArea? = hunt(player)?.resource

    override fun resourceRoute(player: Player): List<SiteTile>? = hunt(player)?.resourceRoute

    override fun fightArea(player: Player): SiteArea = hunt(player)?.resource ?: site.resource

    override fun isTarget(player: Player, npc: Npc): Boolean =
        taskId(player) != 0 && SlayerTaskManager.isTaskNpcType(npc.type, taskId(player))

    override fun lootIds(player: Player): Set<Int> = resolveIds(hunt(player)?.products.orEmpty(), RSCMType.OBJ)

    override fun attackOp(npc: Npc): InteractionOp {
        val index = (0 until 5).firstOrNull { npc.type.actions.getOpOrNull(it).equals("Attack", ignoreCase = true) }
        return interactionOp((index ?: 1) + 1)
    }

    override fun isAt(player: Player, destination: BotDestination): Boolean =
        when (destination) {
            BotDestination.SupplySource -> site.supplyArea?.contains(player.coords) == true
            else -> super.isAt(player, destination)
        }

    override fun gather(player: Player, target: BotDestination): GatherStatus {
        if (target == BotDestination.SupplySource) {
            return askForTask(player)
        }
        if (hunt(player) == null) {
            return GatherStatus.Done
        }
        return super.gather(player, target)
    }

    private fun askForTask(player: Player): GatherStatus {
        if (hunt(player) != null) {
            rerolls = 0
            return GatherStatus.Done
        }
        if (player.isAccessProtected || player.isDelayed) {
            return GatherStatus.Working(0, progressed = true)
        }
        if (rerolls++ >= MAX_REROLLS) {
            rerolls = 0
            return GatherStatus.Failed("no task with a hunt at ${site.key} after $MAX_REROLLS tries")
        }
        val handBack = taskId(player) != 0 && player.vars[COUNT_VARP] > 0
        world.launch(player) {
            if (handBack) {
                SlayerTaskManager.resetTask(this)
            }
            SlayerTaskManager.assignTask(this, site.supplyNpc)
        }
        return GatherStatus.Working(0, progressed = true)
    }

    private companion object {
        private const val TASK_VARP = "varp.slayer_target"
        private const val COUNT_VARP = "varp.slayer_count"
        private const val MASTER_QUEST = "quest_porcineofinterest"
        private const val MAX_REROLLS = 100
    }
}

/**
 * Smiths the site's first product at the nearest anvil in the resource area: it clicks the anvil
 * with the product's bars in its inventory and presses the product's button on the smithing
 * interface, which already offers every bar carried, then withdraws more bars once they run out.
 */
class SmithingBehaviour(private val site: BotSite, private val world: BotWorldServices) : BotRoleBehaviour {
    private val product: SmithingProductMeta? by lazy {
        val wanted = site.products.firstOrNull()
        SmithingData.productMeta.firstOrNull { it.product.internalName == wanted }
    }
    private val button: String? by lazy {
        val product = product ?: return@lazy null
        SmithingData.smithingButtonComponents.firstOrNull { SmithingData.typeForChild(it, product.bar) == product.name }
    }
    private val barId: Int? by lazy { product?.bar?.output?.id }
    private val toolIds by lazy { resolveIds(site.tools.ifEmpty { listOf(HAMMER) }, RSCMType.OBJ) }
    private val anvilCategory by lazy { runCatching { SmithingData.ANVIL_CATEGORY.asRSCM(RSCMType.CATEGORY) }.getOrNull() }

    override var focus: String? = null
        private set

    override fun requirementProblem(player: Player): String? {
        val product = product ?: return "site product is not smithable"
        if (button == null) {
            return "no smithing button makes ${product.name}"
        }
        if (!player.carriesAny(toolIds)) {
            return "needs a hammer"
        }
        val level = maxOf(product.level, product.bar.statReq.first().t1)
        return if (player.smithingLvl < level) "needs Smithing level $level" else null
    }

    override fun keep(itemId: Int): Boolean = itemId in toolIds || itemId == barId

    override fun needsSupplies(player: Player): Boolean = barsHeld(player) < (product?.barCount ?: 1)

    override fun supplyIds(): List<Int> = listOfNotNull(barId)

    override fun isAt(player: Player, destination: BotDestination): Boolean =
        destination == BotDestination.Resource && site.resource.contains(player.coords)

    override fun answerModal(player: Player): Boolean {
        val button = button ?: return false
        if (!player.ui.containsModal(SmithingData.SMITHING_INTERFACE) || player.isModalButtonProtected) {
            return false
        }
        return world.clickModalButton(player, button)
    }

    override fun gather(player: Player, target: BotDestination): GatherStatus {
        if (needsSupplies(player)) {
            return GatherStatus.NeedsSupplies
        }
        if (player.isActing() || player.queueList.isNotEmpty) {
            return GatherStatus.Working(0)
        }
        val category = anvilCategory ?: return GatherStatus.Failed("unknown anvil category")
        val anvils = world.findLocs(site.resource.center(), site.resource.radius) { it.category == category }
        val claims = world.claims(player, site.resource.center(), site.resource.radius)
        val (loc, type) =
            BotTargeting.pick(
                anvils,
                Random,
                distance = { (loc, _) -> loc.coords.chebyshevDistance(player.coords) },
                crowd = { (loc, _) -> claims.on(loc) },
            ) ?: return GatherStatus.NoResource
        if (!world.opLoc(player, loc, type, InteractionOp.Op1)) {
            return GatherStatus.NoResource
        }
        focus = "${product?.name}: ${describe(type.name, loc.coords, claims.on(loc), anvils.size)}"
        return GatherStatus.Working(0)
    }

    private fun barsHeld(player: Player): Int = player.inv.objs.sumOf { if (it != null && it.id == barId) it.count else 0 }

    private companion object {
        private const val HAMMER = "obj.hammer"
    }
}

private fun nameOf(itemId: Int): String = ServerCacheManager.getItem(itemId)?.internalName.orEmpty()

private fun describe(name: String, at: CoordGrid, others: Int, choices: Int): String {
    val crowd = if (others == 0) "nobody else on it" else "$others other player(s) on it"
    return "$name at ${at.x},${at.z}: $crowd, best of $choices"
}
