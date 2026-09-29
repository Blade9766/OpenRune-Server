package org.rsmod.content.skills.hunter.netting

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import jakarta.inject.Inject
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.hunter.rumours.RumourTracker
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class NetScript
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val spawner: ImplingSpawner,
    private val xpMods: XpModifiers,
    private val invisibleLevels: InvisibleLevels,
    private val rumours: RumourTracker,
) : PluginScript() {
    private enum class CatchTool {
        Net,
        MagicNet,
        Barehanded,
    }

    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { spawner.tick() }

        for (butterfly in Butterfly.entries) {
            onOpNpc1(butterfly.npc) { catchButterfly(it.npc, butterfly) }
            onOpHeld4(butterfly.jar) { release(butterfly) }
        }
        for (impling in Impling.entries) {
            impling.npcs.forEach { npc -> onOpNpc1(npc) { catchImpling(it.npc, impling, maze = false) } }
            impling.mazeNpcs.forEach { npc -> onOpNpc1(npc) { catchImpling(it.npc, impling, maze = true) } }
            onOpHeld3(impling.jar) { lootJar(impling) }
        }
    }

    private suspend fun ProtectedAccess.catchButterfly(npc: Npc, butterfly: Butterfly) {
        val tool = catchTool(butterfly.level, butterfly.displayName) ?: return
        val uid = npc.uid
        swing(tool)
        if (!isCatchable(npc, uid)) {
            return
        }
        val better = tool != CatchTool.Net
        val low = if (better) butterfly.bonusLow else butterfly.low
        val high = if (better) butterfly.bonusHigh else butterfly.high
        if (!statRandom(STAT, low, high, invisibleLevels)) {
            mes("You fail to catch the ${butterfly.displayName}.")
            return
        }
        npcRepo.despawn(npc, BUTTERFLY_RESPAWN)
        statAdvance(STAT, butterfly.xp * xpMods.get(player, STAT))
        if (inv.contains(EMPTY_BUTTERFLY_JAR)) {
            invDel(inv, EMPTY_BUTTERFLY_JAR)
            invAdd(inv, butterfly.jar)
            mes("You manage to catch the ${butterfly.displayName} and put it in a jar.")
        } else {
            applyEffect(butterfly.effect)
            mes("You catch the ${butterfly.displayName} and it flutters away.")
        }
        rumours.onCatch(player, butterfly.name)
    }

    private suspend fun ProtectedAccess.catchImpling(npc: Npc, impling: Impling, maze: Boolean) {
        val tool = catchTool(impling.level, "${impling.displayName} impling") ?: return
        val hasJar = inv.contains(EMPTY_IMPLING_JAR)
        if (maze && !hasJar) {
            mes("You need an impling jar to catch implings in Puro-Puro.")
            return
        }
        val uid = npc.uid
        swing(tool)
        if (!isCatchable(npc, uid)) {
            return
        }
        if (!rollImpling(tool)) {
            npc.anim("seq.ii_impling_dodge", delay = 0, priority = 0)
            mes("You fail to catch the impling.")
            return
        }
        when {
            maze -> npcRepo.despawn(npc, mazeRespawn(impling))
            !spawner.onCaught(npc) -> npcRepo.despawn(npc, WORLD_RESPAWN)
        }
        val xp = if (maze) impling.puroXp else impling.worldXp
        statAdvance(STAT, xp * xpMods.get(player, STAT))
        if (hasJar) {
            invDel(inv, EMPTY_IMPLING_JAR)
            invAdd(inv, impling.jar)
            mes("You manage to catch the impling and squeeze it into a jar.")
        } else {
            giveLoot(impling)
            mes("You manage to catch the impling and acquire some loot.")
        }
    }

    private fun ProtectedAccess.catchTool(level: Int, name: String): CatchTool? {
        if (player.hunterLvl < level) {
            mes("You need a Hunter level of $level to catch a $name.")
            return null
        }
        val weapon = player.righthand
        return when {
            weapon.isType(MAGIC_NET) -> CatchTool.MagicNet
            weapon.isType(NET) -> CatchTool.Net
            player.hunterLvl >= level + BAREHANDED_LEVELS -> CatchTool.Barehanded
            else -> {
                mes("You need to be wielding a butterfly net to catch that.")
                null
            }
        }
    }

    private suspend fun ProtectedAccess.swing(tool: CatchTool) {
        val seq = if (tool == CatchTool.Barehanded) BAREHANDED_SEQ else NET_SEQ
        anim(seq)
        delay(1)
    }

    private fun isCatchable(npc: Npc, uid: NpcUid): Boolean =
        npc.isSlotAssigned && npc.isVisible && npc.uid == uid

    private fun ProtectedAccess.rollImpling(tool: CatchTool): Boolean {
        val level = (player.hunterLvl + invisibleLevels.get(player, STAT)).coerceIn(1, 99)
        var chance = IMPLING_LOW + (IMPLING_HIGH - IMPLING_LOW) * (level - 1) / 98.0
        if (tool != CatchTool.Net) {
            chance += IMPLING_BONUS
        }
        return random.randomDouble() < chance
    }

    private fun mazeRespawn(impling: Impling): Int =
        if (impling == Impling.Earth || impling == Impling.Essence) 50 else 7

    private suspend fun ProtectedAccess.lootJar(impling: Impling) {
        if (!inv.contains(impling.jar)) {
            return
        }
        invDel(inv, impling.jar)
        giveLoot(impling)
        if (random.of(JAR_BREAK_CHANCE) == 0) {
            mes("You break the jar as you try and open it. You throw the shattered remains away.")
        } else {
            invAddOrDrop(objRepo, EMPTY_IMPLING_JAR)
        }
    }

    private fun ProtectedAccess.giveLoot(impling: Impling) {
        val table = implingJarLoot[impling.jar] ?: return
        when (val result = table.roll(player, ArgMap()).flatten()) {
            is RollResult.Nothing -> Unit
            is RollResult.Single -> giveDrop(result.result)
            is RollResult.ListOf -> result.results.forEach { giveDrop(it) }
        }
    }

    private fun ProtectedAccess.giveDrop(drop: DropRollItem) {
        if (drop.isNothing || !drop.condition(player)) {
            return
        }
        val obj = drop.transformObj(player) ?: drop.obj
        invAddOrDrop(objRepo, obj, drop.rollCount(random))
    }

    private suspend fun ProtectedAccess.release(butterfly: Butterfly) {
        if (!inv.contains(butterfly.jar)) {
            return
        }
        invDel(inv, butterfly.jar)
        invAdd(inv, EMPTY_BUTTERFLY_JAR)
        anim("seq.hunting_butterfly_out_jar")
        applyEffect(butterfly.effect)
        mes("You release the ${butterfly.displayName}.")
        delay(1)
    }

    private fun ProtectedAccess.applyEffect(effect: ButterflyEffect) {
        when (effect) {
            ButterflyEffect.Attack -> statBoost("stat.attack", 4, 15)
            ButterflyEffect.Defence -> statBoost("stat.defence", 4, 15)
            ButterflyEffect.Strength -> statBoost("stat.strength", 4, 15)
            ButterflyEffect.Hitpoints -> statHeal("stat.hitpoints", 15, 0)
            ButterflyEffect.Prayer -> statHeal("stat.prayer", 22, 0)
            ButterflyEffect.Restore -> {
                for (stat in restorableStats) {
                    statHeal(stat, 6, 20)
                }
                statHeal("stat.hitpoints", 8, 0)
            }
        }
    }

    private companion object {
        const val STAT = "stat.hunter"
        const val NET = "obj.hunting_butterfly_net"
        const val MAGIC_NET = "obj.ii_magic_butterfly_net"
        const val EMPTY_BUTTERFLY_JAR = "obj.butterfly_jar"
        const val EMPTY_IMPLING_JAR = "obj.ii_impling_jar"
        const val NET_SEQ = "seq.human_butterflynet_swing"
        const val BAREHANDED_SEQ = "seq.hunting_impling_catch"
        const val BAREHANDED_LEVELS = 10
        const val BUTTERFLY_RESPAWN = 8
        const val WORLD_RESPAWN = 100
        const val JAR_BREAK_CHANCE = 10
        const val IMPLING_LOW = 0.12
        const val IMPLING_HIGH = 0.98
        const val IMPLING_BONUS = 0.08

        val restorableStats: List<String> by lazy {
            ServerCacheManager.getStats().keys
                .map { RSCM.getReverseMapping(RSCMType.STAT, it) }
                .filter { it != "stat.prayer" && it != "stat.hitpoints" }
        }
    }
}
