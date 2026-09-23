package org.rsmod.content.quest.area.karamja.legendsquest

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.CRYSTAL_CHUNK
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.CRYSTAL_HUNK
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.CRYSTAL_LUMP
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.HEART_CRYSTAL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.HEART_CRYSTAL_GLOWING
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_COLLECTED_TOTEM
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_DEFEATED_NEZIKCHENED_FINAL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_DEFEATED_NEZIKCHENED_FIRE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_DEFEATED_NEZIKCHENED_WATER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_HEART_IN_RECESS
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_RECEIVED_DAGGER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_SPAWNED_NEZIKCHENED_FINAL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_SUMMONED_NEZIKCHENED
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Nezikchened, the demon behind every misfortune of the Kharazi tribe, and the three dead heroes
 * who guard the source of the sacred water.
 *
 * The demon is fought three times, each time bound to the player who drew him out: when the Book
 * of Binding casts him out of Ungadulu, when the Holy Force spell unmasks him as Echned Zekin at
 * the source, and when the player tries to replace his corrupted totem pole. Nobody else can
 * touch him, and he cannot be hurt at all outside those three moments.
 *
 * San Tojalon, Irvig Senay and Ranalph Devere each guard a piece of the heart crystal in the
 * Viyeldi caves. Killing Viyeldi leaves them at the demon's command, and he raises them one after
 * another at the totem pole before he fights the player himself.
 */
@Singleton
class Nezikchened
@Inject
constructor(
    private val legends: LegendsQuest,
    private val support: LegendsSupport,
    private val npcRepo: NpcRepository,
    private val world: WorldRepository,
    private val worldQueues: WorldQueueList,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    enum class Fight { Fire, Water, Final }

    private data class Binding(val owner: PlayerUid, val fight: Fight)

    private val bindings = HashMap<Npc, Binding>()
    private val firebolt = SpotanimType(FIREBOLT_CASTING.asRSCM(RSCMType.SPOTANIM))

    override fun ScriptContext.startup() {
        onEvent<NpcStateEvents.Delete> { bindings.remove(npc) }
        for (hero in HEROES) {
            onOpNpc1(hero.npc) { challenge(it.npc, hero) }
        }
    }

    fun isDemon(npc: Npc): Boolean = npc.isType(DEMON)

    fun heroOf(npc: Npc): Hero? = HEROES.firstOrNull { npc.isType(it.npc) }

    fun ownerOf(npc: Npc): PlayerUid? = bindings[npc]?.owner

    fun fightOf(npc: Npc): Fight? = bindings[npc]?.fight

    /** Whether the demon can be hurt at all at the player's current stage. */
    fun isVulnerable(player: Player): Boolean =
        legends.stage(player) in VULNERABLE_STAGES

    /** Draws a new form of the demon out beside the player and sets it on them. */
    fun ProtectedAccess.summonDemon(fight: Fight, radius: Int, cry: String): Npc? {
        val demon = spawn(DEMON, fight, radius) ?: return null
        demon.say(cry)
        mes("Nezikchened: $cry")
        soundSynth(DEMON_APPROACH_SOUND)
        return demon
    }

    private fun ProtectedAccess.spawn(type: String, fight: Fight, radius: Int, attack: Boolean = true): Npc? {
        val npcType = ServerCacheManager.getNpc(type.asRSCM(RSCMType.NPC)) ?: return null
        val spot = mapFindSquareLineOfWalk(coords, 1, radius) ?: coords
        val npc = Npc(npcType, spot)
        npcRepo.add(npc, SPAWN_DURATION)
        bindings[npc] = Binding(player.uid, fight)
        npc.facePlayer(player)
        if (!attack) {
            return npc
        }
        val uid = player.uid
        worldQueues.add(1) {
            val target = support.resolve(uid) ?: return@add
            if (npc.isSlotAssigned) {
                npc.opPlayer2(target, aiInteractions)
            }
        }
        return npc
    }

    /**
     * The fight at the corrupted totem pole. If Viyeldi died by the player's hand, the dead
     * heroes he kept in check rise first, one at a time; the demon only steps in once all three
     * are cut down.
     */
    suspend fun ProtectedAccess.defendTotem() {
        ifClose()
        legends.advanceFrom(this, STAGE_COLLECTED_TOTEM, STAGE_SPAWNED_NEZIKCHENED_FINAL)
        val slain = player.legendsHeroesSlain
        if (player.legendsKilledViyeldi && slain != ALL_HEROES) {
            val demon = spawn(DEMON, Fight.Final, 2, attack = false) ?: return
            demonSpeaks(demon, "And now you try to defile my sanctuary...")
            delay(2)
            demonSpeaks(demon, "I will teach thee!")
            delay(2)
            mes("The demon starts chanting...")
            delay(2)
            demonSpeaks(demon, "Protectors of source, alive in death,")
            delay(2)
            demonSpeaks(demon, "do not rest while this Vacu draws breath!")
            delay(2)
            mes("The demon is summoning the dead heroes from the Viyeldi caves!")
            delay(2)
            npcRepo.del(demon, Int.MAX_VALUE)
            raiseNextHero()
            return
        }
        val demon = spawn(DEMON, Fight.Final, 2) ?: return
        demonSpeaks(demon, "And now you try to defile my sanctuary...")
        delay(2)
        demonSpeaks(demon, "I will teach thee!")
        delay(2)
        mes("The demon screams in rage...")
        delay(1)
        demonSpeaks(demon, "Raarrrrghhhh!")
        delay(1)
        demonSpeaks(demon, "I'll kill you myself!")
        delay(1)
        mes("You feel a great sense of loss...")
        statSub(PRAYER, 0, 75)
        delay(1)
        demonSpeaks(demon, "Your faith will help you little here.")
    }

    private fun ProtectedAccess.demonSpeaks(demon: Npc, line: String) {
        mes("Nezikchened: $line")
        demon.say(line)
    }

    private suspend fun ProtectedAccess.raiseNextHero() {
        val slain = player.legendsHeroesSlain
        val hero = HEROES.firstOrNull { slain and it.bit == 0 } ?: return
        val npc = spawn(hero.npc, Fight.Final, 1) ?: return
        mes("${hero.name}: Corrupted are we now that Viyeldi is slain...")
        npc.say("Corrupted are we now that Viyeldi is slain...")
        delay(1)
        mes("${hero.name}: Bent to this demon's will and forced to bring you pain...")
        npc.say("Bent to this demon's will and forced to bring you pain...")
    }

    private suspend fun ProtectedAccess.challenge(npc: Npc, hero: Hero) {
        if (isInCombat()) {
            mes("You're a bit busy to talk right now.")
            return
        }
        if (ownsPiece(hero)) {
            npc.say(hero.bested)
            return
        }
        npc.say(hero.challenge)
        npc.opPlayer2(player, aiInteractions)
    }

    private fun ProtectedAccess.ownsPiece(hero: Hero): Boolean =
        legends.stage(player) >= STAGE_HEART_IN_RECESS ||
            legends.owns(this, hero.piece) ||
            legends.owns(this, HEART_CRYSTAL) ||
            legends.owns(this, HEART_CRYSTAL_GLOWING) ||
            player.legendsCrystals and hero.crystalBit != 0

    /** Runs the scene that follows a kill of the demon or one of the heroes. */
    fun killed(hero: Player, npc: Npc) {
        val binding = bindings[npc]
        if (binding != null && binding.owner != hero.uid) {
            return
        }
        val deathCoords = npc.coords
        when {
            isDemon(npc) -> support.launchWhenFree(hero.uid) { demonDefeated(binding?.fight, deathCoords) }
            else -> {
                val undead = heroOf(npc) ?: return
                support.launchWhenFree(hero.uid) { heroDefeated(undead, binding?.fight) }
            }
        }
    }

    private suspend fun ProtectedAccess.demonDefeated(fight: Fight?, deathCoords: CoordGrid) {
        val stage = legends.stage(player)
        when {
            fight == Fight.Fire && stage == STAGE_SUMMONED_NEZIKCHENED -> {
                legends.setStage(this, STAGE_DEFEATED_NEZIKCHENED_FIRE)
                mes("Nezikchened: Ha ha ha... I shall return for you when the time is right.")
                mes("The demon starts an incantation...")
                delay(2)
                mes("Nezikchened: But I will leave you with a taste of my power...")
                delay(2)
                mes("As he finishes the incantation a powerful bolt of energy strikes you.")
                world.spotanimMap(firebolt, deathCoords, height = BOLT_HEIGHT)
                soundSynth(FIRE_BOLT_SOUND)
                delay(2)
                mes("Nezikchened: Haha hah ha ha ha ha....")
                delay(2)
                hurt(random.of(0, 16))
                delay(3)
                mes("The demon's body falls to the floor in a pile of ashes...")
            }
            fight == Fight.Water && stage == STAGE_RECEIVED_DAGGER -> {
                legends.setStage(this, STAGE_DEFEATED_NEZIKCHENED_WATER)
                mes("The demon seems very angry now...")
                delay(2)
                mes("Nezikchened: You would bite the hand that feeds you?")
                mes("You deliver a final devastating blow to the demon.")
                delay(2)
                mes("Nezikchened: Very well, I will ready myself for our next encounter...")
                mes("And its unearthly frame crumbles into dust.")
                delay(3)
                mes("The demon's body falls to the floor in a pile of ashes...")
            }
            fight == Fight.Final && stage == STAGE_SPAWNED_NEZIKCHENED_FINAL -> {
                legends.setStage(this, STAGE_DEFEATED_NEZIKCHENED_FINAL)
                mes("You deliver the final killing blow to the foul demon.")
                delay(2)
                mes("Nezikchened: Arrggghhhhh....")
                delay(2)
                mes("Nezikchened: I am beaten by a mere mortal...")
                delay(2)
                mes("Nezikchened: I will revenge myself upon you...")
                delay(2)
                say("Yeah, yeah, yeah!")
                delay(3)
                say("Heard it all before!")
            }
        }
    }

    private suspend fun ProtectedAccess.heroDefeated(hero: Hero, fight: Fight?) {
        if (fight == Fight.Final && LegendsCoords.inKharazi(coords)) {
            player.legendsHeroesSlain = player.legendsHeroesSlain or hero.bit
            mes("A nerve tingling scream echoes around you")
            mes("as you slay ${hero.name}.")
            delay(1)
            mes("${hero.name}: Arrghhhhh")
            delay(1)
            mes("${hero.name}: Forever must I live in this torment")
            delay(1)
            mes("${hero.name}: till this beast is slain.")
            delay(2)
            defendTotem()
            return
        }
        if (ownsPiece(hero)) {
            startDialogue { chatNpcSpecific(hero.name, hero.npc, neutral, hero.fought) }
            return
        }
        invAdd(inv, hero.piece, 1)
        mes("${hero.name}: You have proved yourself of the honour.")
        startDialogue {
            chatNpcSpecific(hero.name, hero.npc, neutral, "You have proved yourself of the honour.")
            objbox(hero.piece, hero.found)
        }
    }

    data class Hero(
        val npc: String,
        val name: String,
        val piece: String,
        val bit: Int,
        val crystalBit: Int,
        val challenge: String,
        val bested: String,
        val fought: String,
        val found: String,
    )

    companion object {
        const val DEMON = "npc.nezikchened"
        const val PRAYER = "stat.prayer"
        const val SPAWN_DURATION = 500
        const val BOLT_HEIGHT = 92

        const val FIREBOLT_CASTING = "spotanim.firebolt_casting"
        const val FIRE_BOLT_SOUND = "synth.fire_bolt_all"
        const val DEMON_APPROACH_SOUND = "synth.demon_approach"

        const val CHUNK_BIT = 1
        const val HUNK_BIT = 2
        const val LUMP_BIT = 4
        const val ALL_HEROES = 7

        val VULNERABLE_STAGES =
            setOf(STAGE_SUMMONED_NEZIKCHENED, STAGE_RECEIVED_DAGGER, STAGE_SPAWNED_NEZIKCHENED_FINAL)

        val HEROES =
            listOf(
                Hero(
                    npc = "npc.san_tojalon",
                    name = "San Tojalon",
                    piece = CRYSTAL_CHUNK,
                    bit = 1,
                    crystalBit = CHUNK_BIT,
                    challenge = "Are you ready to have your mettle tested?",
                    bested = "You have defeated me in battle, and still you search for the source?",
                    fought = "You have fought the good fight this day.",
                    found = "A chunk of crystal forms mid-air and falls to the floor. You place the crystal chunk into your inventory.",
                ),
                Hero(
                    npc = "npc.irvig_senay",
                    name = "Irvig Senay",
                    piece = CRYSTAL_HUNK,
                    bit = 2,
                    crystalBit = HUNK_BIT,
                    challenge = "Your destiny awaits, adventurer...",
                    bested = "You have bested me. Go forth, and discover the source.",
                    fought = "You fought well, adventurer.",
                    found = "A hunk of crystal forms mid-air and falls to the floor. You place the crystal hunk into your inventory.",
                ),
                Hero(
                    npc = "npc.ranalph_devere",
                    name = "Ranalph Devere",
                    piece = CRYSTAL_LUMP,
                    bit = 4,
                    crystalBit = LUMP_BIT,
                    challenge = "Your destiny awaits, adventurer...",
                    bested = "My duty is done. I hope the source brings you all that you hoped for...",
                    fought = "You fought well, adventurer.",
                    found = "A lump of crystal forms mid-air and falls to the floor. You place the crystal lump into your inventory.",
                ),
            )
    }
}

/**
 * Only the player who drew Nezikchened out may fight him, and only while one of the three fights
 * is on; the heroes the demon raised at the totem pole are bound to that player in the same way.
 */
class NezikchenedAttackHook @Inject constructor(private val nezikchened: Nezikchened) : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        val owner = nezikchened.ownerOf(npc)
        if (owner != null && owner != player.uid) {
            return NpcAttackValidateResult.Deny("It's not after you...")
        }
        if (nezikchened.isDemon(npc) && !nezikchened.isVulnerable(player)) {
            return NpcAttackValidateResult.Deny("Your attack glides straight through the demon, as if he isn't even there.")
        }
        return NpcAttackValidateResult.Pass
    }
}

class NezikchenedKillHook @Inject constructor(private val nezikchened: Nezikchened) : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        val npc = context.npc
        if (nezikchened.isDemon(npc) || nezikchened.heroOf(npc) != null) {
            nezikchened.killed(context.hero, npc)
        }
    }
}
