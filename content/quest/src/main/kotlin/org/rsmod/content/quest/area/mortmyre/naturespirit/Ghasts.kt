package org.rsmod.content.quest.area.mortmyre.naturespirit

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.FUNGUS
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.GHAST_INVISIBLE
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.GHAST_VISIBLE
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.PEAR
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.POUCH
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.POUCH_EMPTY
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.ROTTEN_FOOD
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_ALL_GHASTS
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_FIRST_GHAST
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_POUCH_GIVEN
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_SECOND_GHAST
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STEM
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The ghasts of Mort Myre and the druid pouch that exposes them.
 *
 * An invisible ghast drifts up to a player and, 7 times in 10, misses entirely; otherwise it rots
 * a piece of food, or feeds on the player for 1-3 damage when they carry none, then drifts off
 * again. A player whose druid pouch holds a charge is safe: the pouch fires at the ghast, spending
 * the charge, and the ghast turns solid and bound to that player until it dies or loses interest.
 * The pouch can also be used on a ghast directly. The solid ghast is a separate npc so that it
 * fights, drops and dies like any other monster.
 */
@Singleton
class Ghasts
@Inject
constructor(
    private val natureSpirit: NatureSpiritQuest,
    private val npcRepo: NpcRepository,
    private val world: WorldRepository,
    private val aiInteractions: AiPlayerInteractions,
    private val hitModifier: PlayerHitModifier,
) : PluginScript() {

    private val invisibleType = npcType(GHAST_INVISIBLE)
    private val visibleType = npcType(GHAST_VISIBLE)
    private val aggressiveHunt = ServerCacheManager.getHunt(AGGRESSIVE_MELEE_HUNT)

    private val owners = HashMap<Npc, PlayerUid>()

    override fun ScriptContext.startup() {
        invisibleType.huntMode = AGGRESSIVE_MELEE_HUNT
        onEvent<NpcStateEvents.Create> {
            if (npc.id == invisibleType.id) {
                aggressiveHunt?.let(npc::setHuntMode)
            }
        }
        onEvent<NpcStateEvents.Delete> { owners.remove(npc) }
        onAiOpPlayer2(invisibleType) { haunt(it.target) }
        onAiApPlayer2(invisibleType) { haunt(it.target) }
        onOpNpcU(GHAST_INVISIBLE) { usePouchOnGhast(it.npc, it.objType.internalName) }
        onOpHeld4(POUCH_EMPTY) { fill() }
        onOpHeld4(POUCH) { fill() }
        for (pouch in listOf(POUCH_EMPTY, POUCH)) {
            for (item in BLOSSOMS.keys) {
                onOpHeldU(pouch, item) { fill() }
            }
        }
    }

    fun ownerOf(ghast: Npc): PlayerUid? = owners[ghast]

    private fun StandardNpcAccess.haunt(target: Player) {
        if (!target.isValidTarget() || !MortMyreCoords.inDecayArea(target.coords)) {
            resetMode()
            return
        }
        if (actionDelay > mapClock) {
            return
        }
        actionDelay = mapClock + ATTACK_RATE
        if (target.pouchCharges() > 0) {
            reveal(npc, target)
            return
        }
        anim(ATTACK_SEQ)
        world.soundArea(npc, ATTACK_SOUND)
        if (random.of(ATTACK_ROLL) < MISS_CHANCE) {
            driftAway()
            return
        }
        val foodSlot = target.inv.indices.filter { target.inv[it]?.let { obj -> getInvObj(obj).isFood() } == true }.randomOrNull()
        if (foodSlot != null) {
            val food = target.inv[foodSlot] ?: return
            target.invDel(target.inv, getInvObj(food).internalName, count = 1, slot = foodSlot)
            target.invAdd(target.inv, ROTTEN_FOOD)
            target.mes("You feel something attacking your backpack, and smell a terrible stench.")
        } else {
            target.finishNpcHit(npc, delay = 1, type = HitType.Typeless, damage = random.of(1, MAX_FEED), modifier = hitModifier)
            target.mes("You feel something attack you!")
        }
        driftAway()
    }

    private fun StandardNpcAccess.driftAway() {
        resetMode()
        walk(random.of(coords, DRIFT_RADIUS))
    }

    private fun reveal(ghast: Npc, target: Player) {
        if (!ghast.isSlotAssigned) {
            return
        }
        target.spendPouchCharge()
        target.mes("The druid pouch makes the ghast visible.")
        world.spotanimMap(SpotanimType(IMPACT_SPOTANIM.asRSCM(RSCMType.SPOTANIM)), ghast.coords)
        world.soundArea(ghast, APPEAR_SOUND)
        val coords = ghast.coords
        npcRepo.del(ghast, VISIBLE_LIFETIME + RESPAWN_TICKS)
        val solid = Npc(visibleType, coords)
        npcRepo.add(solid, VISIBLE_LIFETIME)
        owners[solid] = target.uid
        solid.opPlayer2(target, aiInteractions)
    }

    private suspend fun ProtectedAccess.usePouchOnGhast(ghast: Npc, obj: String) {
        if (obj != POUCH && obj != POUCH_EMPTY) {
            mes("Nothing interesting happens.")
            return
        }
        if (!toldAboutPouch()) {
            objbox(obj, "You've not been told how to use this item yet.")
            return
        }
        if (player.pouchCharges() <= 0) {
            mes("You have nothing left in your druid pouch!")
            return
        }
        reveal(ghast, player)
    }

    /** Takes three blossomed items, pears first, then stems, then fungi. */
    private suspend fun ProtectedAccess.fill() {
        if (!toldAboutPouch()) {
            objbox(POUCH_EMPTY, "You've not been told how to use this item yet.")
            return
        }
        val chosen = ArrayList<String>(ITEMS_PER_FILL)
        for (item in BLOSSOMS.keys) {
            repeat(inv.count(item)) {
                if (chosen.size < ITEMS_PER_FILL) {
                    chosen += item
                }
            }
        }
        if (chosen.size < ITEMS_PER_FILL) {
            mes("You need at least three blossomed items to add something to the druid pouch.")
            return
        }
        for (item in chosen) {
            invDel(inv, item)
        }
        val charges = chosen.sumOf { BLOSSOMS.getValue(it) }
        if (POUCH_EMPTY in player.inv) {
            invDel(inv, POUCH_EMPTY)
        }
        invAdd(inv, POUCH, charges)
    }

    private fun ProtectedAccess.toldAboutPouch(): Boolean =
        natureSpirit.isComplete(player) || natureSpirit.stage(player) >= STAGE_POUCH_GIVEN

    private fun Player.pouchCharges(): Int = inv.count(POUCH)

    private fun Player.spendPouchCharge() {
        invDel(inv, POUCH, 1)
        if (inv.count(POUCH) == 0) {
            invAdd(inv, POUCH_EMPTY)
        }
    }

    private fun dev.openrune.types.ItemServerType.isFood(): Boolean =
        interfaceOptions.any { it == "Eat" } && id !in ROT_PROOF

    private fun npcType(name: String): NpcServerType =
        ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $name")

    private companion object {
        /** `.data/gamevals/stalk.rscm` `aggressive_melee`: hunts everyone, whatever their level. */
        const val AGGRESSIVE_MELEE_HUNT = 6

        const val ATTACK_RATE = 4
        const val ATTACK_ROLL = 10
        const val MISS_CHANCE = 7
        const val MAX_FEED = 3
        const val DRIFT_RADIUS = 4

        /** How long a solid ghast lingers, and how soon after it the invisible one returns. */
        const val VISIBLE_LIFETIME = 200
        const val RESPAWN_TICKS = 40

        const val ITEMS_PER_FILL = 3

        const val ATTACK_SEQ = "seq.inv_ghast_attack"
        const val ATTACK_SOUND = "synth.ghast_attack"
        const val APPEAR_SOUND = "synth.ghast_appear"
        const val IMPACT_SPOTANIM = "spotanim.druidpouch_impact"

        /** Charges each blossomed item adds, in the order a fill takes them. */
        val BLOSSOMS = linkedMapOf(PEAR to 3, STEM to 2, FUNGUS to 1)

        val ROT_PROOF = setOf(PEAR.asRSCM(RSCMType.OBJ))
    }
}

/** A solid ghast is bound to the player whose pouch exposed it. */
class GhastAttackHook @Inject constructor(private val ghasts: Ghasts) : NpcAttackValidateHook {
    private val visibleId = GHAST_VISIBLE.asRSCM(RSCMType.NPC)

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id != visibleId) {
            return NpcAttackValidateResult.Pass
        }
        val owner = ghasts.ownerOf(npc) ?: return NpcAttackValidateResult.Pass
        if (owner != player.uid) {
            return NpcAttackValidateResult.Deny("That ghast is bound to someone else.")
        }
        return NpcAttackValidateResult.Pass
    }
}

/** Each ghast slain during the quest counts towards the three the nature spirit asked for. */
class GhastKillHook
@Inject
constructor(
    private val natureSpirit: NatureSpiritQuest,
    private val launcher: ProtectedAccessLauncher,
    private val worldQueues: WorldQueueList,
    private val playerList: PlayerList,
    private val world: WorldRepository,
) : NpcDeathKillHook {
    private val visibleId = GHAST_VISIBLE.asRSCM(RSCMType.NPC)

    override fun onKill(context: NpcDeathKillContext) {
        if (context.npc.id != visibleId) {
            return
        }
        world.spotanimMap(SpotanimType(DEATH_SPOTANIM.asRSCM(RSCMType.SPOTANIM)), context.npc.coords)
        val hero = context.hero
        if (natureSpirit.stage(hero) !in STAGE_POUCH_GIVEN until STAGE_ALL_GHASTS) {
            return
        }
        countKill(hero.uid, ATTEMPTS)
    }

    private fun countKill(uid: PlayerUid, attempts: Int) {
        worldQueues.add(1) {
            val player = uid.resolve(playerList) ?: return@add
            val launched =
                launcher.launch(player) {
                    val next =
                        when (natureSpirit.stage(player)) {
                            in STAGE_POUCH_GIVEN until STAGE_FIRST_GHAST -> STAGE_FIRST_GHAST
                            in STAGE_FIRST_GHAST until STAGE_SECOND_GHAST -> STAGE_SECOND_GHAST
                            in STAGE_SECOND_GHAST until STAGE_ALL_GHASTS -> STAGE_ALL_GHASTS
                            else -> return@launch
                        }
                    natureSpirit.advanceTo(this, next)
                    mes(
                        when (next) {
                            STAGE_FIRST_GHAST -> "That's one Ghast, 2 more to kill."
                            STAGE_SECOND_GHAST -> "That's two Ghasts, 1 more to kill."
                            else -> "That's all three Ghasts!"
                        },
                    )
                }
            if (!launched && attempts > 0) {
                countKill(uid, attempts - 1)
            }
        }
    }

    private companion object {
        const val DEATH_SPOTANIM = "spotanim.ghast_spotdeath"
        const val ATTEMPTS = 20
    }
}
