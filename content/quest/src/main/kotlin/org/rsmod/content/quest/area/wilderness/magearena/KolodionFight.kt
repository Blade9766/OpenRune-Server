package org.rsmod.content.quest.area.wilderness.magearena

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.dsl.external
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.content.quest.area.wilderness.magearena.MageArenaQuest.Companion.STAGE_DUEL
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** One of Kolodion's shapes, in the order they are fought. */
class KolodionForm(
    val npc: String,
    val line: String,
    val attackAnim: String,
    /** Ticks between attacks; the demon is the only quick one. */
    val attackRate: Int,
)

/** Which player each Kolodion in the arena belongs to. */
@Singleton
class KolodionFights {
    private val owners = HashMap<NpcUid, PlayerUid>()
    private val active = HashMap<PlayerUid, Npc>()

    fun register(npc: Npc, owner: Player) {
        owners[npc.uid] = owner.uid
        active[owner.uid] = npc
    }

    fun ownerOf(npc: Npc): PlayerUid? = owners[npc.uid]

    fun activeFor(owner: Player): Npc? {
        val npc = active[owner.uid] ?: return null
        return if (npc.isSlotAssigned && owners[npc.uid] == owner.uid) npc else null
    }

    fun forget(npc: Npc) {
        val owner = owners.remove(npc.uid) ?: return
        if (active[owner]?.uid == npc.uid) {
            active.remove(owner)
        }
    }
}

/**
 * The duel that opens the arena: Kolodion teleports the player into the arena and fights them
 * as a man, an ogre, a spider, a ghost and finally a black demon. Every form attacks with the
 * three god spells and only magic can hurt it. The forms fall in order, and if the player leaves
 * (or dies) the next visit picks up at the form they reached. Beating the demon sends the player
 * back to the cave, where Kolodion points them at the sparkling pool.
 */
class KolodionFight
@Inject
constructor(
    private val mageArena: MageArenaQuest,
    private val fights: KolodionFights,
    private val deps: BossDeps,
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
    private val aiInteractions: AiPlayerInteractions,
    private val death: NpcDeath,
    private val collision: CollisionFlagMap,
    private val search: NpcSearch,
    private val worldQueues: WorldQueueList,
) : PluginScript() {

    private val quest
        get() = mageArena.quest

    private val formTypes: List<NpcServerType> =
        FORMS.map { form ->
            ServerCacheManager.getNpc(form.npc.asRSCM(RSCMType.NPC)) ?: error("Missing npc: ${form.npc}")
        }

    private val spec =
        boss(*FORMS.map { it.npc }.toTypedArray()) {
            stats(attackRate = STANDARD_ATTACK_RATE, aggressionRadius = AGGRESSION_RADIUS)
            val cast = ability("kolodion_cast", external(CAST_HANDLER))
            phase("duel") { weightedSelectorRandom { +random(cast, weight = 1) } }
        }

    override fun ScriptContext.startup() {
        deps.extensionRegistry.register(CAST_HANDLER) { access, npc, target, _ -> castGodSpell(access, npc, target) }
        BossCombat.register(
            this,
            spec,
            deps,
            onModifyHit = {
                // Hand-to-hand combat is useless in the arena: only magic lands on Kolodion.
                if (hit.isFromPlayer && hit.type != HitType.Magic) {
                    hit.damage = 0
                }
            },
        )
        for ((index, type) in formTypes.withIndex()) {
            onNpcQueue(type, "queue.death") { formDefeated(index) }
        }
    }

    /** Kolodion's opening move: the player is sent into the arena and the current form appears. */
    suspend fun ProtectedAccess.beginDuel() {
        if (mageArena.stage(player) == 0) {
            quest.advanceQuestStage(this)
        }
        magicTeleport(MageArenaCoords.FIGHT_PLAYER)
        summonKolodion()
    }

    /** Puts the form the player has reached into the arena and sets it on them. */
    suspend fun ProtectedAccess.summonKolodion() {
        fights.activeFor(player)?.let { existing ->
            fights.forget(existing)
            npcRepo.del(existing, Int.MAX_VALUE)
        }
        val index = mageArena.kolodionForm.get(player).coerceIn(0, FORMS.lastIndex)
        spawnForm(player, index, MageArenaCoords.FIGHT_KOLODION)
    }

    private fun spawnForm(player: Player, index: Int, near: CoordGrid) {
        val form = FORMS[index]
        val type = formTypes[index]
        val tile = collision.freeFootprint(near, type.size) ?: near
        val npc = Npc(type, tile)
        npcRepo.add(npc, LINGER_TICKS)
        fights.register(npc, player)
        deps.encounter(npc).attackRateOverride = form.attackRate
        npc.spotanim(TRANSFORM_SPOTANIM)
        npc.facePlayer(player)
        npc.say(form.line)
        npc.opPlayer2(player, aiInteractions)
    }

    /**
     * Every form casts one of the god spells at random: the same max hit of 20 the battle mages
     * have, and the spell's usual side effect when it lands.
     */
    private fun castGodSpell(access: StandardNpcAccess, npc: Npc, target: Player) {
        val form = FORMS.firstOrNull { it.npc.asRSCM(RSCMType.NPC) == npc.id } ?: return
        val god = God.entries[deps.random.of(God.entries.size)]
        access.anim(form.attackAnim)
        deps.worldRepo.soundArea(npc, god.castSound, radius = SOUND_RADIUS)
        val hit = deps.accuracy.rollMagicAccuracy(npc, target, deps.random)
        val damage = if (hit) deps.random.of(0..GOD_SPELL_MAX_HIT) else 0
        target.spotanim(god.impact, delay = IMPACT_CLIENT_DELAY, height = 0)
        target.finishNpcHit(npc, HIT_DELAY, HitType.Magic, damage, deps.playerHitModifier)
        if (damage > 0) {
            applyGodSpellEffect(target, god)
        }
    }

    /**
     * A form's death queue. Kolodion drops nothing; the next shape rises where this one fell, or
     * the duel is won.
     */
    private suspend fun StandardNpcAccess.formDefeated(index: Int) {
        val owner = fights.ownerOf(npc)?.resolve(playerList) ?: findHero(playerList)
        val coords = npc.coords
        fights.forget(npc)
        death.deathNoDrops(this)
        if (owner == null) {
            return
        }
        if (index < FORMS.lastIndex) {
            mageArena.kolodionForm.set(owner, index + 1)
            spawnForm(owner, index + 1, coords)
            return
        }
        mageArena.kolodionForm.set(owner, 0)
        launcher.launch(owner) { duelWon() }
    }

    private suspend fun ProtectedAccess.duelWon() {
        if (mageArena.stage(player) == STAGE_DUEL) {
            quest.advanceQuestStage(this)
        }
        player.mes("Kolodion's final form crumbles. You have proven yourself in the arena.")
        // Kolodion paces the cave, so land beside wherever he is rather than on a fixed tile.
        val kolodion =
            search.find(
                MageArenaCoords.CAVE_KOLODION_ARRIVAL,
                MageArenaCoords.KOLODION,
                KOLODION_SEARCH_RADIUS,
                HuntVis.Off,
            )
        val arrival =
            kolodion
                ?.let {
                    collision.tilesAround(it.coords, it.type.size, MageArenaCoords.CAVE_KOLODION_ARRIVAL).firstOrNull()
                }
                ?: MageArenaCoords.CAVE_KOLODION_ARRIVAL
        val uid = player.uid
        // Auto-retaliate keeps picking a new battle mage out of the arena while the teleport
        // plays, and every one of them shouts "I can't reach that!" from the cave. Drop whatever
        // the player has latched onto at the top of each cycle until the talk begins.
        for (cycle in 1..RETALIATION_CLEAR_CYCLES) {
            worldQueues.add(cycle) { uid.resolve(playerList)?.clearInteraction() }
        }
        magicTeleport(arrival)
        player.clearInteraction()

        // The combat interaction with the form that just fell outlives this script: the engine
        // only settles it once the script ends, and settling it cancels whatever the player is
        // doing. So the winner's talk waits for a cycle and runs as a script of its own.
        worldQueues.add(CONGRATULATION_DELAY) {
            val winner = uid.resolve(playerList) ?: return@add
            winner.clearInteraction()
            launcher.launch(winner) { congratulate() }
        }
    }

    private suspend fun ProtectedAccess.congratulate() {
        val kolodion =
            search.find(player.coords, MageArenaCoords.KOLODION, KOLODION_SEARCH_RADIUS, HuntVis.Off) ?: return
        kolodion.facePlayer(player)
        startDialogue(kolodion) {
            chatNpc(happy, "Well done, young adventurer; you truly are a worthy battle mage.")
            chatPlayer(quiz, "What now?")
            chatNpc(
                neutral,
                "Step into the magic pool. It will take you to a chamber. There, you must decide " +
                    "which god you will represent in the arena.",
            )
            chatPlayer(happy, "Thanks, Kolodion.")
            chatNpc(happy, "That's what I'm here for.")
        }
    }

    companion object {
        val FORMS =
            listOf(
                KolodionForm("npc.kolhuman", "You must prove yourself... now!", "seq.human_caststrike", 7),
                KolodionForm("npc.kologre", "This is only the beginning; you can't beat me!", "seq.giant_attack", 7),
                KolodionForm("npc.kolspider", "Foolish mortal; I am unstoppable.", "seq.spider_update_attack_large", 7),
                KolodionForm("npc.kolethereal", "Now you feel it... The dark energy.", "seq.human_caststrike", 7),
                KolodionForm("npc.koldemon", "Aaaaaaaarrgghhhh! The power!", "seq.demon_attack", 4),
            )

        private const val CAST_HANDLER = "kolodion.cast"
        private const val STANDARD_ATTACK_RATE = 7
        private const val AGGRESSION_RADIUS = 12
        private const val GOD_SPELL_MAX_HIT = 20
        private const val SOUND_RADIUS = 8
        private const val IMPACT_CLIENT_DELAY = 30
        private const val HIT_DELAY = 2

        /** Five minutes alone in the arena and the shape-shifter loses interest. */
        private const val LINGER_TICKS = 500
        private const val KOLODION_SEARCH_RADIUS = 12
        private const val TRANSFORM_SPOTANIM = "spotanim.smokepuff"

        /** Long enough for the engine to let go of the duel before Kolodion speaks. */
        private const val CONGRATULATION_DELAY = 2

        /** The teleport cast plus the wait, so nothing is left targeted on the way out. */
        private const val RETALIATION_CLEAR_CYCLES = 4
    }
}

/** Nobody but the challenger may fight their Kolodion. */
class KolodionAttackHook @Inject constructor(private val fights: KolodionFights) : NpcAttackValidateHook {
    private val formIds = KolodionFight.FORMS.map { it.npc.asRSCM(RSCMType.NPC) }.toSet()

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id !in formIds) {
            return NpcAttackValidateResult.Pass
        }
        val owner = fights.ownerOf(npc) ?: return NpcAttackValidateResult.Pass
        if (owner != player.uid) {
            return NpcAttackValidateResult.Deny("Kolodion is already duelling someone else.")
        }
        return NpcAttackValidateResult.Pass
    }
}
