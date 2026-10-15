package org.rsmod.content.quest.area.falador.blackknightsfortress

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MesAnimType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.BLACK_KNIGHTS
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.CABBAGE
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.CAPTAIN
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.CAT
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.DOSSIER
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.DRAYNOR_CABBAGE
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.GRELDO
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.GUARDS
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.STAGE_OVERHEARD
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.STAGE_SABOTAGED
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortressQuest.Companion.WITCH
import org.rsmod.content.quest.area.falador.blackknightsfortress.npcs.guardsEntrance
import org.rsmod.content.quest.area.falador.blackknightsfortress.npcs.meetingWarning
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Black Knights' Fortress: the guards' entrance that only lets uniformed guards in, the
 * meeting room the guard warns about, the secret walls, the grill over the witch's room and the
 * hole above her cauldron. Also the dossier Sir Amik hands out, which self-destructs when read.
 */
class BlackKnightsFortress
@Inject
constructor(
    private val bkf: BlackKnightsFortressQuest,
    private val passages: GenericPassageScript,
    private val search: NpcSearch,
    private val aiInteractions: AiPlayerInteractions,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    private val guardDoor = locType(GUARD_DOOR)
    private val meetingDoor = locType(MEETING_DOOR)
    private val sturdyDoor = locType(STURDY_DOOR)
    private val secretWall = locType(SECRET_WALL)

    override fun ScriptContext.startup() {
        onOpLoc1(LARGE_DOOR_LEFT) { mes("You can't open this door.") }
        onOpLoc1(LARGE_DOOR_RIGHT) { mes("You can't open this door.") }
        onOpLoc1(GUARD_DOOR) { guardsEntrance(it.loc) }
        onOpLoc1(MEETING_DOOR) { meetingRoom(it.loc) }
        onOpLoc1(STURDY_DOOR) { sturdyDoor(it.loc) }
        onOpLoc1(SECRET_WALL) { pushWall(it.loc) }
        onOpLoc1(GRILL) { listen(it.loc) }
        onOpLocU(HOLE, CABBAGE) { dropCabbage(it.loc) }
        onOpLocU(HOLE, DRAYNOR_CABBAGE) { dropDraynorCabbage() }
        onOpLocU(HOLE) { whyWouldI() }
        onOpHeld1(DOSSIER) { readDossier() }
    }

    private fun locType(name: String): ObjectServerType =
        ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $name")

    private suspend fun ProtectedAccess.guardsEntrance(door: BoundLocInfo) {
        arriveDelay()
        val outside = coords.z <= door.coords.z
        if (outside && !bkf.isDisguised(player)) {
            startGuardDialogue(door.coords) { guardsEntrance(bkf) }
            return
        }
        with(passages) { walkThrough(door, guardDoor) }
    }

    private suspend fun ProtectedAccess.meetingRoom(door: BoundLocInfo) {
        arriveDelay()
        val inHall = coords.x < door.coords.x
        if (inHall) {
            var goIn = false
            startGuardDialogue(door.coords) { goIn = meetingWarning() }
            if (!goIn) {
                return
            }
            knightsAttack(MEETING_ROOM)
        }
        with(passages) { walkThrough(door, meetingDoor) }
    }

    private suspend fun ProtectedAccess.sturdyDoor(door: BoundLocInfo) {
        arriveDelay()
        if (coords.x > door.coords.x) {
            knightsAttack(door.coords)
        }
        with(passages) { walkThrough(door, sturdyDoor) }
    }

    private suspend fun ProtectedAccess.pushWall(wall: BoundLocInfo) {
        arriveDelay()
        mes("You push against the wall. You find a secret passage.")
        with(passages) { walkThrough(wall, secretWall) }
    }

    private suspend fun ProtectedAccess.startGuardDialogue(
        near: CoordGrid,
        conversation: suspend Dialogue.() -> Unit,
    ) {
        val guard = GUARDS.firstNotNullOfOrNull { search.find(near, it, GUARD_RADIUS, HuntVis.Off) }
        if (guard != null) startDialogue(guard, conversation = conversation) else startDialogue(conversation)
    }

    private fun ProtectedAccess.knightsAttack(center: CoordGrid) {
        val knights =
            BLACK_KNIGHTS.flatMap { type ->
                search.findAll(center, type, KNIGHT_RADIUS, HuntVis.Off).filter { it.coords.level == center.level }
            }
        knights.firstOrNull()?.say("Die, intruder!")
        for (knight in knights) {
            knight.opPlayer2(player, aiInteractions)
        }
    }

    /* The grill over the witch's room */

    private suspend fun ProtectedAccess.listen(grill: BoundLocInfo) {
        arriveDelay()
        faceLoc(grill)
        when (bkf.stage(player)) {
            STAGE_STARTED -> {
                anim(LISTEN_SEQ)
                startDialogue { secretWeapon() }
                resetAnim()
                bkf.advance(this)
            }
            in STAGE_SABOTAGED..Int.MAX_VALUE -> {
                anim(LISTEN_SEQ)
                startDialogue { ruinedPotion() }
                resetAnim()
            }
            else -> mes("I can't hear much right now.")
        }
    }

    private suspend fun Dialogue.secretWeapon() {
        captain(quiz, "So... how's the secret weapon coming along?")
        witch(happy, "The invincibility potion is almost ready...")
        witch(happy, "It's taken me FIVE YEARS, but it's almost ready.")
        witch(neutral, "Greldo the Goblin here is just going to fetch the last ingredient for me.")
        witch(neutral, "It's a specially grown cabbage grown by my cousin Helda who lives in Draynor Manor.")
        witch(neutral, "The soil there is slightly magical and it gives the cabbages slight magical properties....")
        witch(shocked, "...not to merntion the trees!")
        witch(angry, "Now remember Greldo, only a Draynor Manor cabbage will do! Don't get lazy and bring any old cabbage, THAT would ENTIRELY wreck the potion!")
        chatNpcSpecific("Greldo", GRELDO, neutral, "Yeth, Mithreth.")
    }

    private suspend fun Dialogue.ruinedPotion() {
        witch(angry, "It's RUINED! All that work, FIVE years down the drain, who was it! I'll find them and when I do....")
        captain(worried, "Erm.. maybe we can save some of it?")
        witch(angry, "SAVE some of it? Are you mad!? That cabbage was a normal cabbage, it spoilt the whole thing and now I have to start all over again!")
        captain(worried, "No need to... get upset... you're scaring the cat.")
        witch(angry, "'The CAT'? She's not 'THE CAT'!")
        witch(happy, "She's Maemi... aren't you dear....")
        chatNpcSpecific("Black Cat", CAT, happy, "Purrr....")
    }

    /* The hole above the witch's cauldron */

    private suspend fun ProtectedAccess.dropCabbage(hole: BoundLocInfo) {
        if (bkf.stage(player) != STAGE_OVERHEARD) {
            whyWouldI()
            return
        }
        faceLoc(hole)
        invDel(inv, CABBAGE)
        bkf.advance(this)
        bkf.showSabotagedCauldron(player, false)
        anim(THROW_SEQ)
        spotanim(THROWN_SPOT)
        player.midiJingle(RUINED_POTION_JINGLE)
        delay(1)
        spotanimMap(worldRepo, FALLING_SPOT, hole.coords)
        startDialogue {
            witch(angry, "Where has Greldo got to with that magic cabbage!")
            captain(quiz, "What's that noise?")
            witch(happy, "Hopefully Greldo with the cabbage... yes, look here it co....NOOOOOoooo!")
        }
        bkf.showSabotagedCauldron(player, true)
        spotanimMap(worldRepo, BUBBLES_SPOT, CAULDRON)
        startDialogue {
            witch(angry, "My potion!")
            captain(worried, "Oh boy, this doesn't look good!")
            chatNpcSpecific("Black Cat", CAT, neutral, "Meow!")
            chatPlayer(happy, "Looks like my work here is done. Seems like that's successfully sabotaged their little secret weapon plan.")
        }
    }

    private suspend fun ProtectedAccess.dropDraynorCabbage() {
        if (bkf.stage(player) != STAGE_OVERHEARD) {
            whyWouldI()
            return
        }
        mesbox("This is the wrong sort of cabbage!")
        startDialogue { chatPlayer(confused, "I'm not supposed to be HELPING the witch you know...") }
    }

    private suspend fun ProtectedAccess.whyWouldI() {
        startDialogue { chatPlayer(confused, "Why exactly would I want to do that?") }
    }

    /* The dossier */

    private suspend fun ProtectedAccess.readDossier() {
        anim(READ_SEQ)
        startDialogue { dossierCountdown() }
        invDel(inv, DOSSIER)
        anim(EXPLODE_SEQ)
        spotanim(EXPLODE_SPOT)
    }

    private suspend fun Dialogue.witch(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("Witch", WITCH, mesanim, text)

    private suspend fun Dialogue.captain(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("Black Knight Captain", CAPTAIN, mesanim, text)

    internal companion object {
        const val LARGE_DOOR_LEFT = "loc.inaclefayeunopenabledoorl"
        const val LARGE_DOOR_RIGHT = "loc.inaclefayeunopenabledoorr"
        const val GUARD_DOOR = "loc.bkfortressdoor1"
        const val MEETING_DOOR = "loc.bkfortressdoor2"
        const val STURDY_DOOR = "loc.bkfortressdoor3"
        const val SECRET_WALL = "loc.bksecretdoor"
        const val GRILL = "loc.witchgrill"
        const val HOLE = "loc.blackknighthole"

        val MEETING_ROOM = CoordGrid(3025, 3515, 0)
        val CAULDRON = CoordGrid(3031, 3507, 0)

        const val GUARD_RADIUS = 10
        const val KNIGHT_RADIUS = 5

        const val LISTEN_SEQ = "seq.bkf_bent_to_listen_at_door_idle"
        const val THROW_SEQ = "seq.bkf_throw_cabbage"
        const val READ_SEQ = "seq.reading_dossier"
        const val EXPLODE_SEQ = "seq.dossier_explosion"
        const val THROWN_SPOT = "spotanim.bkfortress_cabbage_thrown"
        const val FALLING_SPOT = "spotanim.bkfortress_cabbage_travel"
        const val BUBBLES_SPOT = "spotanim.bkfortress_cauldron_cabbage_bubbles"
        const val EXPLODE_SPOT = "spotanim.dossier_explosion_spotanim"

        /** Js5 archive 11 group of "Ruined Potion (Black Knights' Fortress)". */
        const val RUINED_POTION_JINGLE = 5
    }
}

internal suspend fun Dialogue.dossierCountdown() {
    for (count in 3 downTo 1) {
        mesbox("Infiltrate fortress... sabotage secret weapon... self destruct in $count...")
    }
}
