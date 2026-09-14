package org.rsmod.content.quest.area.lighthouse.horrorfromthedeep

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.Melee
import org.rsmod.api.bosses.dsl.Ranged
import org.rsmod.api.bosses.dsl.WithinMeleeRange
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.CASKET
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.DAGANNOTH
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.JOSSIK_INJURED
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.MOTHER
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.STAGE_DAGANNOTH_SLAIN
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.STAGE_LIGHT_FIXED
import org.rsmod.content.quest.manager.QuestInstances
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitType
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The caves beneath the Lighthouse.
 *
 * During the quest the foyer ladder takes the player into a private copy of the caves, where the
 * injured Jossik lies by the ladder. Talking to him brings a dagannoth out of the pool; once it is
 * dead, talking to him again brings its mother. After the quest the ladder leads to the real
 * caves and their dagannoth.
 *
 * The mother cycles through six colours every [COLOUR_TICKS] cycles, each a transmog of the same
 * npc, and only takes damage from the matching style: air, water, earth or fire spells, melee or
 * ranged. Npc queues are keyed by the visible type, so her death hook is registered on every
 * colour. Melee and ranged are shut off with the npc's style immunity varns; the elemental check
 * reads the spell obj carried on the hit. She throws a pair of spines in her current colour at
 * any distance, and mixes in bites while her target is next to her and not praying against melee.
 */
class DagannothCaves
@Inject
constructor(
    private val horror: HorrorFromTheDeepQuest,
    private val instances: QuestInstances,
    private val instanceManager: InstanceManager,
    private val bossDeps: BossDeps,
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
    private val worldQueues: WorldQueueList,
    private val aiInteractions: AiPlayerInteractions,
    private val mapClock: MapClock,
) : PluginScript() {

    private val dagannothType = ServerCacheManager.getNpc(DAGANNOTH.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $DAGANNOTH")
    private val motherType = ServerCacheManager.getNpc(MOTHER.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $MOTHER")

    private val spellsByElement: Map<Element, List<ItemServerType>> =
        Element.entries.associateWith { element -> element.spells.map { ServerCacheManager.getItem(it.asRSCM(RSCMType.OBJ)) ?: error("Missing spell obj: $it") } }

    private val motherSpec =
        boss(MOTHER) {
            stats(attackRate = ATTACK_RATE, aggressionRadius = AGGRESSION_RADIUS)

            val bite =
                ability("bite") {
                    anim(ATTACK_SEQ)
                    sound(ATTACK_SOUND)
                    hit {
                        damage(0..MELEE_MAX).roll()
                        type(Melee)
                    }
                }

            for (colour in Colour.entries) {
                val spines =
                    ability("spines_${colour.phase}") {
                        anim(RANGED_SEQ)
                        sound(SPINES_SOUND)
                        projectile {
                            spotanim = colour.spines
                            hit {
                                damage(0..RANGED_MAX).roll()
                                type(Ranged)
                            }
                        }
                        projectile {
                            spotanim = colour.spines
                            config = SECOND_SPINE
                            hit {
                                damage(0..RANGED_MAX).roll()
                                type(Ranged)
                            }
                        }
                    }
                phase(colour.phase) {
                    weightedSelectorRandom {
                        +random(bite, weight = 1, requires = Condition.And(WithinMeleeRange, Condition.Not(Condition.TargetPraying(Melee))))
                        +random(spines, weight = 1)
                    }
                }
            }
        }

    override fun ScriptContext.startup() {
        BossCombat.register(this, motherSpec, bossDeps, onModifyHit = { resistWrongElement() })
        onNpcQueue(dagannothType, "queue.death") { dagannothDies() }
        for (colour in Colour.entries) {
            val form = ServerCacheManager.getNpc(colour.npc.asRSCM(RSCMType.NPC)) ?: error("Missing npc: ${colour.npc}")
            onNpcQueue(form, "queue.death") { motherDies() }
        }
        onOpNpc1(JOSSIK_INJURED) { talkToJossik(it.npc) }
        onOpLoc1(FOYER_LADDER_PREQUEST) { climbDown() }
        onOpLoc1(FOYER_LADDER_POSTQUEST) { climbDown() }
        onOpLoc2(FOYER_LADDER_POSTQUEST) { peek() }
        onOpLoc1(CAVE_LADDER) { climbUp() }
    }

    /* Ladders */

    private suspend fun ProtectedAccess.climbDown() {
        arriveDelay()
        anim(CLIMB_DOWN_SEQ)
        delay(1)
        if (horror.isComplete(player) || !LighthouseCoords.inCavesCopy(coords)) {
            telejump(LighthouseCoords.toReal(LighthouseCoords.CAVE_LANDING), TeleportType.Exempt)
            return
        }
        if (horror.stage(player) < STAGE_LIGHT_FIXED) {
            mes("You can't see a way down.")
            return
        }
        with(instances) {
            enterCopy(
                KEY,
                LighthouseCoords.CAVE_LANDING,
                LighthouseCoords.FOYER_LANDING,
                listOf(InstanceNpc(JOSSIK_INJURED, LighthouseCoords.JOSSIK_INJURED)),
                bossName = BOSS_NAME,
            )
        } ?: return
        soundSynth(CHITTER_SOUND)
    }

    private suspend fun ProtectedAccess.climbUp() {
        arriveDelay()
        anim(CLIMB_UP_SEQ)
        delay(1)
        val exit = with(instances) { leaveCopy() }
        val dest =
            when {
                exit != null -> exit
                LighthouseCoords.inCavesCopy(coords) -> LighthouseCoords.FOYER_LANDING
                else -> LighthouseCoords.toReal(LighthouseCoords.FOYER_LANDING)
            }
        telejump(dest, TeleportType.Exempt)
    }

    private fun ProtectedAccess.peek() {
        mes("You peer down the ladder. The cave below is crawling with dagannoth.")
    }

    /* Jossik */

    private suspend fun ProtectedAccess.talkToJossik(jossik: Npc) {
        val visit = currentVisit() ?: return
        if (instances.npcsIn(visit).any { it.isSlotAssigned && (it.type == dagannothType || it.type == motherType) }) {
            startDialogue(jossik) { chatNpc(worried, "Don't just stand there talking! Do something!") }
            return
        }
        when (horror.stage(player)) {
            STAGE_LIGHT_FIXED -> {
                startDialogue(jossik) { firstMeeting() }
                emerge(visit, DAGANNOTH)
            }
            STAGE_DAGANNOTH_SLAIN -> {
                startDialogue(jossik) { itWasABaby() }
                emerge(visit, MOTHER)
            }
            else -> startDialogue(jossik) { chatNpc(sad, "*cough* Please... get me out of here...") }
        }
    }

    private suspend fun Dialogue.firstMeeting() {
        chatNpc(sad, "*cough* Please... help me. I think my leg is broken, and those things will be back any moment!")
        chatPlayer(quiz, "You must be Jossik. What things?")
        chatNpc(worried, "I don't know! I've never seen anything like them.")
        chatNpc(sad, "I came looking for some trace of my uncle Silas, who vanished from this lighthouse months ago. I worked out the secret of that strange wall and opened it, but when I came down here...")
        chatNpc(worried, "Something attacked me. I don't know what they are, only that they're strong. They left me too hurt to climb out, and I've been waiting for them to finish me ever since.")
        chatPlayer(neutral, "It's alright, I'm here now. Larrissa was frantic about you and sent me to find you.")
        chatPlayer(neutral, "I'll go back up and tell her you're alive, and then we'll work out how to get you out of here.")
        chatNpc(shocked, "NO! Don't leave me! Look, they're coming back! Do something!")
    }

    private suspend fun Dialogue.itWasABaby() {
        chatPlayer(happy, "Right, the creature is dead. Let's get you out of here.")
        chatNpc(worried, "No... you don't understand...")
        chatNpc(worried, "That wasn't the thing that attacked me...")
        chatNpc(shocked, "That was one of its young...")
    }

    private suspend fun ProtectedAccess.emerge(visit: QuestInstances.Visit, type: String) {
        val beast = instances.spawn(visit, type, LighthouseCoords.DAGANNOTH_EMERGE)
        beast.facePlayer(player)
        soundSynth(CHITTER_SOUND)
        for (seq in EMERGE_SEQS) {
            beast.anim(seq)
            delay(EMERGE_STEP_TICKS)
        }
        if (!beast.isSlotAssigned) {
            return
        }
        beast.mode = null
        if (type == MOTHER) {
            beast.apRangeOverride = SPINES_RANGE
            applyColour(beast, Colour.White)
            scheduleColourChange(beast, player)
        }
        beast.opPlayer2(player, aiInteractions)
    }

    private fun ProtectedAccess.currentVisit(): QuestInstances.Visit? {
        val session = instanceManager.sessionForPlayer(player) ?: return null
        val enter = instanceManager.resolveCoord(session, LighthouseCoords.CAVE_LANDING) ?: return null
        return QuestInstances.Visit(session, enter, LighthouseCoords.CAVE_LANDING)
    }

    /* The young dagannoth */

    private suspend fun StandardNpcAccess.dagannothDies() {
        val hero = findHero(playerList)
        anim(DEATH_SEQ)
        hero?.soundSynth(DEATH_SOUND)
        delay(DEATH_TICKS)
        npcRepo.del(npc, Int.MAX_VALUE)
        if (hero != null) {
            launchWhenFree(hero) { dagannothSlain() }
        }
    }

    private suspend fun ProtectedAccess.dagannothSlain() {
        if (horror.stage(player) == STAGE_LIGHT_FIXED) {
            horror.advanceTo(this, STAGE_DAGANNOTH_SLAIN)
        }
        val visit = currentVisit() ?: return
        val jossik = instances.npcsIn(visit).firstOrNull { it.isSlotAssigned && it.type.id == JOSSIK_INJURED.asRSCM(RSCMType.NPC) } ?: return
        startDialogue(jossik) { itWasABaby() }
        emerge(visit, MOTHER)
    }

    /* The mother */

    private fun scheduleColourChange(mother: Npc, player: Player) {
        val uid = player.uid
        worldQueues.add(COLOUR_TICKS) {
            if (!mother.isSlotAssigned || mother.hitpoints <= 0) {
                return@add
            }
            val next = Colour.entries[(colourOf(mother).ordinal + 1) % Colour.entries.size]
            applyColour(mother, next)
            mother.say(next.cry)
            uid.resolve(playerList)?.let { watcher ->
                watcher.mes("The Dagannoth changes to ${next.phase}...")
                watcher.soundSynth(CHANGE_SOUND)
            }
            scheduleColourChange(mother, player)
        }
    }

    private fun applyColour(mother: Npc, colour: Colour) {
        if (colour == Colour.White) {
            mother.resetTransmog()
        } else {
            val type = ServerCacheManager.getNpc(colour.npc.asRSCM(RSCMType.NPC)) ?: return
            mother.transmog(type, Int.MAX_VALUE)
        }
        mother.vars[IMMUNE_MELEE] = if (colour.style == HitType.Melee) 0 else 1
        mother.vars[IMMUNE_RANGED] = if (colour.style == HitType.Ranged) 0 else 1
        mother.vars[IMMUNE_MAGIC] = if (colour.style == HitType.Magic) 0 else 1
        bossDeps.encounterRegistry.of(mother).transitionTo(colour.phase, mapClock.cycle)
    }

    private fun colourOf(mother: Npc): Colour = Colour.entries.firstOrNull { it.npc.asRSCM(RSCMType.NPC) == mother.visType.id } ?: Colour.White

    private fun NpcHitEvents.Modify.resistWrongElement() {
        if (hit.type != HitType.Magic) {
            return
        }
        val element = colourOf(npc).element ?: return
        if (spellsByElement.getValue(element).none(hit::isSecondaryObj)) {
            hit.damage = 0
        }
    }

    private suspend fun StandardNpcAccess.motherDies() {
        val hero = findHero(playerList)
        anim(DEATH_SEQ)
        hero?.soundSynth(DEATH_SOUND)
        delay(DEATH_TICKS)
        npcRepo.del(npc, Int.MAX_VALUE)
        if (hero != null) {
            launchWhenFree(hero) { motherSlain() }
        }
    }

    private suspend fun ProtectedAccess.motherSlain() {
        if (horror.isComplete(player)) {
            return
        }
        if (invAdd(inv, CASKET).failure) {
            mes("You have no room for the rusty casket. Jossik will look after it for you.")
        }
        horror.quest.completeQuest(this)
        val jossikId = JOSSIK_INJURED.asRSCM(RSCMType.NPC)
        val jossik = currentVisit()?.let { visit -> instances.npcsIn(visit).firstOrNull { it.isSlotAssigned && it.type.id == jossikId } }
        if (jossik != null) {
            startDialogue(jossik) { motherSlainDialogue() }
        } else {
            startDialogue { motherSlainDialogue() }
        }
    }

    private suspend fun Dialogue.motherSlainDialogue() {
        chatPlayer(happy, "Okay, it's dead! Let's get out of here!")
        chatNpcSpecific(JOSSIK_NAME, JOSSIK_INJURED, worried, "Yes, and quickly. The mother may be dead, but her young are not.")
        chatNpcSpecific(JOSSIK_NAME, JOSSIK_INJURED, neutral, "Come up to my library. I might be able to help with that casket you found.")
        chatNpcSpecific(JOSSIK_NAME, JOSSIK_INJURED, quiz, "Bring it to me there. It looks oddly familiar...")
    }

    private fun launchWhenFree(player: Player, block: suspend ProtectedAccess.() -> Unit) {
        // Hits from spines fired before the kill are strong queues and would close the dialogue.
        if (player.queueList.strongQueues == 0 && launcher.launch(player, block = block)) {
            return
        }
        val uid = player.uid
        worldQueues.add(1) {
            val target = uid.resolve(playerList) ?: return@add
            launchWhenFree(target, block)
        }
    }

    private enum class Element(val spells: List<String>) {
        Air(listOf("obj.01_wind_strike", "obj.17_wind_bolt", "obj.41_wind_blast", "obj.62_wind_wave", "obj.81_wind_surge")),
        Water(listOf("obj.05_water_strike", "obj.23_water_bolt", "obj.47_water_blast", "obj.65_water_wave", "obj.85_water_surge")),
        Earth(listOf("obj.09_earth_strike", "obj.29_earth_bolt", "obj.53_earth_blast", "obj.70_earth_wave", "obj.90_earth_surge")),
        Fire(listOf("obj.13_fire_strike", "obj.35_fire_bolt", "obj.59_fire_blast", "obj.75_fire_wave", "obj.95_fire_surge")),
    }

    /** The mother's colours in the order she cycles through them. */
    private enum class Colour(
        val phase: String,
        val npc: String,
        val style: HitType,
        val element: Element?,
        val spines: String,
        val cry: String,
    ) {
        White("white", MOTHER, HitType.Magic, Element.Air, "spotanim.horror_spines_travel_air", "Tktktktktktkt"),
        Blue("blue", "npc.horror_dagganoth_water", HitType.Magic, Element.Water, "spotanim.horror_spines_travel_water", "Krrrrrrk"),
        Orange("orange", "npc.horror_dagganoth_melee", HitType.Melee, null, "spotanim.horror_spines_travel_melee", "Chkhkhkhkhk"),
        Brown("brown", "npc.horror_dagganoth_earth", HitType.Magic, Element.Earth, "spotanim.horror_spines_travel_earth", "Krrrrrrssssssss"),
        Red("red", "npc.horror_dagganoth_fire", HitType.Magic, Element.Fire, "spotanim.horror_spines_travel_fire", "Sssssrrrkkkkk"),
        Green("green", "npc.horror_dagganoth_ranged", HitType.Ranged, null, "spotanim.horror_spines_travel_ranged", "Krkrkrkrkrkrkrkr"),
    }

    private companion object {
        const val KEY = "horrorfromthedeep_caves"
        const val BOSS_NAME = "Dagannoth mother"
        const val JOSSIK_NAME = "Jossik"

        const val FOYER_LADDER_PREQUEST = "loc.horror_ladder_top2_prequest"
        const val FOYER_LADDER_POSTQUEST = "loc.horror_ladder_top2_postquest"
        const val CAVE_LADDER = "loc.horror_ladder_base2"

        const val ATTACK_RATE = 4
        const val AGGRESSION_RADIUS = 10
        const val MELEE_MAX = 9
        const val RANGED_MAX = 12
        const val SPINES_RANGE = 7
        val SECOND_SPINE = ProjectileConfig(startDelay = 66)
        const val COLOUR_TICKS = 30
        const val EMERGE_STEP_TICKS = 2
        const val DEATH_TICKS = 3

        const val IMMUNE_MELEE = "varn.immune_melee"
        const val IMMUNE_RANGED = "varn.immune_ranged"
        const val IMMUNE_MAGIC = "varn.immune_magic"

        const val CLIMB_DOWN_SEQ = "seq.human_pickupfloor"
        const val CLIMB_UP_SEQ = "seq.human_reachforladder"
        const val ATTACK_SEQ = "seq.horror_dagannoth_attack"
        const val RANGED_SEQ = "seq.horror_dagannoth_rangeattack"
        const val DEATH_SEQ = "seq.horror_dagannoth_death"
        val EMERGE_SEQS =
            listOf("seq.horror_dagannoth_emerge_a", "seq.horror_dagannoth_emerge_b", "seq.horror_dagannoth_emerge_c")

        const val ATTACK_SOUND = "synth.dagganoth_attack"
        const val SPINES_SOUND = "synth.dagganoth_spines"
        const val DEATH_SOUND = "synth.dagganoth_death"
        const val CHANGE_SOUND = "synth.dagganoth_changes"
        const val CHITTER_SOUND = "synth.dagganoth_chitters"
    }
}
