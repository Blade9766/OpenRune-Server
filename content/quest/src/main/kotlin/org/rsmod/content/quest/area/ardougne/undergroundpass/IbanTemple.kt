package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.DISCIPLE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.DOLL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.IBAN
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.IBANS_STAFF
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_IBAN_ATTACK
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_STUNNED
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_COLLAPSE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_DOOR_OPEN
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_IBAN_LIGHTNING
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_STUNNED
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SPOT_IBAN_BOLT
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SPOT_ROCKFALL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SPOT_STUNNED
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_DOLL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_DOLL_READY
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_IBAN_DEAD
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.ZAMORAK_BOTTOM
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.ZAMORAK_TOP
import org.rsmod.content.quest.area.wilderness.magearena.walkable
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Iban's temple and the end of him.
 *
 * Only followers of Zamorak get through the doors: Zamorak monk robes and nothing else worn. Iban
 * sits on his throne at the far end of the hall and rains bolts down on the floor between the
 * doors and the Well of the Damned, which stun and throw back anyone they hit. The finished doll,
 * thrown into the well, is the end of him, and the temple comes down after.
 */
@Singleton
class IbanTemple
@Inject
constructor(
    private val quest: UndergroundPassQuest,
    private val npcRepo: NpcRepository,
    private val worldRepo: WorldRepository,
    private val objRepo: ObjRepository,
    private val launcher: ProtectedAccessLauncher,
    private val random: GameRandom,
    private val collision: CollisionFlagMap,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    private val boltSpotanim by lazy { SpotanimType(SPOT_IBAN_BOLT.asRSCM(RSCMType.SPOTANIM)) }

    override fun ScriptContext.startup() {
        for (door in TEMPLE_DOORS) {
            onOpLoc1(door) { useTempleDoor(it.loc) }
        }
        for (monk in listOf(DISCIPLE, UndergroundPassQuest.visibleTwin(DISCIPLE))) {
            onOpNpc1(monk) { talkToDisciple(it.npc) }
        }
        onOpLoc1(WELL_OF_THE_DAMNED) { lookIntoWell() }
        onOpLocU(WELL_OF_THE_DAMNED, DOLL) { castDollIn() }
        onPlayerSoftTimer(TEMPLE_TIMER) { templeTick(player) }
    }

    private suspend fun ProtectedAccess.talkToDisciple(monk: Npc) {
        if (!dressedAsDisciple(strict = false)) {
            startDialogue(monk) {
                chatPlayer(happy, "Hi.")
                chatNpc(angry, "An impostor... Die, scum!")
            }
            monk.facePlayer(player)
            monk.opPlayer2(player, aiInteractions)
            return
        }
        startDialogue(monk) { discipleChatter() }
    }

    private suspend fun Dialogue.discipleChatter() {
        chatPlayer(happy, "Hi.")
        when (random.of(0, 3)) {
            0 -> {
                chatNpc(neutral, "Hail the great one, my lord Iban. I die for you again and again.")
                chatPlayer(confused, "Is that possible ?")
                chatNpc(neutral, "Under Iban anything is possible. Death is only the beginning.")
            }
            1 -> chatNpc(neutral, "Iban is our father, our guide. Soon he will rule all life.")
            2 -> {
                chatNpc(neutral, "Som Molica Aniul Demonte.")
                chatPlayer(confused, "Pardon ?")
            }
            else -> chatNpc(neutral, "Succumb to the might of Iban, the ruler of this world.")
        }
    }

    private suspend fun ProtectedAccess.useTempleDoor(door: BoundLocInfo) {
        arriveDelay()
        if (coords.x <= door.coords.x) {
            leaveTemple(door)
            return
        }
        if (!dressedAsDisciple(strict = true)) {
            mes("The door refuses to open...")
            delay(1)
            mes("Only followers of Zamorak may enter.")
            return
        }
        if (quest.stage(player) >= STAGE_COMPLETE) {
            mes("The temple is in ruins...")
            delay(2)
            mes("...You cannot enter.")
            return
        }
        mes("You pull open the large doors...")
        soundSynth(SOUND_DOOR_OPEN)
        climbOver(CoordGrid(door.coords.x - 1, coords.z, coords.level), SEQ_WALK, ticks = 1)
        softTimer(TEMPLE_TIMER, 1)
        player.templeEntered = true
        UndergroundPassQuest.setVarBit(player, "varbit.upass_seen_temple", 1)
        if (!dollComplete() || !inv.contains(DOLL) || quest.stage(player) >= STAGE_IBAN_DEAD) {
            return
        }
        if (quest.stage(player) == STAGE_DOLL) {
            quest.advanceTo(this, STAGE_DOLL_READY)
        }
        val iban = iban() ?: return
        mes("Iban seems to sense danger.")
        mes("Iban: Who dares to bring the witch's magic into my temple?")
        mes("His eyes fixate on you as he raises his arm...")
        ibanSays(iban, "An imposter dares desecrate this sacred place!")
        delay(1)
        ibanSays(iban, "...Home to the only true child of Zamorak.")
        delay(1)
        ibanSays(iban, "Join the damned, mortal!")
    }

    private suspend fun ProtectedAccess.leaveTemple(door: BoundLocInfo) {
        mes("You pull open the large doors.")
        soundSynth(SOUND_DOOR_OPEN)
        delay(1)
        mes("...And walk out of the temple.")
        climbOver(CoordGrid(door.coords.x + 2, coords.z, coords.level), SEQ_WALK, ticks = 2)
        clearSoftTimer(TEMPLE_TIMER)
    }

    private suspend fun ProtectedAccess.lookIntoWell() {
        arriveDelay()
        mesbox(
            "The well goes down further than the temple is tall, and the sound coming up out of " +
                "it is a great many voices, all of them Iban's.",
        )
    }

    private suspend fun ProtectedAccess.castDollIn() {
        arriveDelay()
        val iban = iban()
        if (!dollComplete() || quest.stage(player) > STAGE_DOLL_READY || iban == null) {
            if (quest.stage(player) >= STAGE_IBAN_DEAD || iban == null) {
                mes("Iban is already dead...")
            } else {
                mes("The doll is still incomplete.")
            }
            return
        }
        faceSquare(UpassCoords.WELL_OF_THE_DAMNED)
        anim(SEQ_THROW)
        mes("You throw the doll of Iban into the pit...")
        if (invDel(inv, DOLL).failure) {
            return
        }
        delay(1)
        ibanSays(iban, "What's happening? It's dark here...so dark!")
        delay(1)
        ibanSays(iban, "I'm falling into the dark, what have you done?")
        mes("Iban falls to his knees clutching his throat...")
        delay(1)
        soundSynth(SOUND_IBAN_LIGHTNING)
        ibanSays(iban, "Noooooooo!")
        mes("Iban slumps motionless to the floor...")
        clearSoftTimer(TEMPLE_TIMER)
        quest.advanceTo(this, STAGE_IBAN_DEAD)
        delay(1)
        mes("A roar comes from the pit of the damned.")
        mes("The infamous Iban has finally gone to rest.")
        delay(1)
        invAddOrDrop(objRepo, IBANS_STAFF)
        invAddOrDrop(objRepo, DEATH_RUNE, DEATH_RUNES)
        invAddOrDrop(objRepo, FIRE_RUNE, FIRE_RUNES)
        mes("Amongst Ibans remains you find his staff and some runes.")
        delay(2)
        soundSynth(SOUND_COLLAPSE)
        spotanimMap(worldRepo, SPOT_ROCKFALL, UpassCoords.TEMPLE_ROCKFALLS[0])
        spotanimMap(worldRepo, SPOT_ROCKFALL, UpassCoords.TEMPLE_ROCKFALLS[1], delay = ROCKFALL_DELAY)
        mes("Suddenly around you rocks crash to the floor")
        mes("as the ground begins to shake...")
        delay(1)
        mes("...The temple walls begin to collapse in...")
        spotanimMap(worldRepo, SPOT_ROCKFALL, UpassCoords.TEMPLE_ROCKFALLS[2], delay = ROCKFALL_DELAY)
        spotanimMap(worldRepo, SPOT_ROCKFALL, UpassCoords.TEMPLE_ROCKFALLS[3], delay = ROCKFALL_DELAY)
        delay(1)
        mes("...And you're thrown from the temple platform.")
        camReset()
        telejump(UpassCoords.TEMPLE_ESCAPE_LANDING, TeleportType.Exempt)
    }

    /**
     * Every tick in the hall, Iban may send a rain of bolts down on the floor between the doors
     * and the well. A bolt landing on the player's tile stuns them and knocks them back a step.
     */
    private fun templeTick(player: Player) {
        if (!inTemple(player.coords) || quest.stage(player) >= STAGE_IBAN_DEAD) {
            player.clearSoftTimer(TEMPLE_TIMER)
            return
        }
        if (random.of(0, BOLT_ONE_IN - 1) != 0) {
            return
        }
        val iban = findIban(player.coords)
        if (iban != null && random.of(0, 1) == 0) {
            iban.anim(SEQ_IBAN_ATTACK)
            iban.say(IBAN_TAUNTS[random.of(0, IBAN_TAUNTS.size - 1)])
        }
        var hit = false
        repeat(BOLTS_PER_TICK) {
            val tile =
                UpassCoords.TEMPLE_BOLT_ORIGIN.translate(
                    random.of(0, UpassCoords.TEMPLE_BOLT_WIDTH - 1),
                    random.of(0, UpassCoords.TEMPLE_BOLT_LENGTH - 1),
                )
            if (!collision.walkable(tile)) {
                return@repeat
            }
            worldRepo.spotanimMap(boltSpotanim, tile)
            if (tile == player.coords) {
                hit = true
            }
        }
        if (hit) {
            launcher.launch(player) { struckByBolt() }
        }
    }

    private fun ProtectedAccess.struckByBolt() {
        takeInstantHit(HitType.Typeless, random.of(BOLT_MIN, BOLT_MAX))
        val back = coords.translate(1, 0)
        if (collision.walkable(back)) {
            telejump(back, TeleportType.Exempt)
        }
        anim(SEQ_STUNNED)
        spotanim(SPOT_STUNNED, height = STUN_HEIGHT)
        soundSynth(SOUND_STUNNED)
    }

    private fun ProtectedAccess.ibanSays(iban: Npc, line: String) {
        mes("Iban: $line")
        iban.say(line)
    }

    private fun ProtectedAccess.iban(): Npc? = findIban(coords)

    private fun findIban(near: CoordGrid): Npc? =
        npcRepo.findAll(ZoneKey.from(near), ZONE_RADIUS).firstOrNull {
            it.isType(IBAN) || it.isType(UndergroundPassQuest.visibleTwin(IBAN))
        }

    private fun ProtectedAccess.dollComplete(): Boolean =
        player.ashesOnDoll == 1 &&
            player.venomOnDoll == 1 &&
            player.shadowOnDoll == 1 &&
            player.doveOnDoll == 1

    /**
     * Monk robes top and bottom. The doors, stricter than the disciples, also refuse anyone wearing
     * anything else at all.
     */
    private fun ProtectedAccess.dressedAsDisciple(strict: Boolean): Boolean {
        val worn = player.worn
        if (!worn.contains(ZAMORAK_TOP) || !worn.contains(ZAMORAK_BOTTOM)) {
            return false
        }
        return !strict || worn.occupiedSpace() == ROBE_PIECES
    }

    private fun inTemple(coords: CoordGrid): Boolean =
        coords.level == TEMPLE_LEVEL &&
            coords.x in TEMPLE_MIN_X..TEMPLE_MAX_X &&
            coords.z in TEMPLE_MIN_Z..TEMPLE_MAX_Z

    private companion object {
        val TEMPLE_DOORS =
            arrayOf("loc.upass_templedoor_closed_left", "loc.upass_templedoor_closed_right")
        const val WELL_OF_THE_DAMNED = "loc.cave_temple_altar"

        const val TEMPLE_TIMER = "timer.upass_temple"
        const val SEQ_THROW = "seq.human_throw_arrow1"
        const val SEQ_WALK = "seq.human_walk_f"
        const val DEATH_RUNE = "obj.deathrune"
        const val FIRE_RUNE = "obj.firerune"
        const val DEATH_RUNES = 15
        const val FIRE_RUNES = 30
        const val ROCKFALL_DELAY = 20
        const val ROBE_PIECES = 2

        const val BOLT_ONE_IN = 4
        const val BOLTS_PER_TICK = 25
        const val BOLT_MIN = 5
        const val BOLT_MAX = 10
        const val STUN_HEIGHT = 124
        const val ZONE_RADIUS = 2

        const val TEMPLE_LEVEL = 1
        const val TEMPLE_MIN_X = 2126
        const val TEMPLE_MAX_X = 2143
        const val TEMPLE_MIN_Z = 4637
        const val TEMPLE_MAX_Z = 4658

        val IBAN_TAUNTS =
            listOf(
                "Begone from my temple!",
                "Fool!",
                "I'll swallow your soul!",
                "You belong in the slave pits!",
                "You will die, frail mortal.",
                "You dare to defy me?!?",
                "Who dares desecrate my temple!",
                "I am the great Iban, I cannot die!",
            )
    }
}
