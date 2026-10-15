package org.rsmod.content.quest.area.karamja.legendsquest

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BLESSED_BOWL
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BLESSED_BOWL_PURE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.SHAMANS_TOME
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_ACCEPTED_RESCUE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_DEFEATED_NEZIKCHENED_FIRE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_FOUND_ENTRANCE
import org.rsmod.content.quest.area.karamja.legendsquest.npcs.Ungadulu
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The shaman's cave under the three mossy rocks in the north-west of the Kharazi Jungle, where
 * Ungadulu is trapped inside a flaming octagram. Its crate, table, bed and desk hide the
 * shaman's notes; the bookcase hides a crevice out to the east; and the supernatural flames burn
 * anyone who touches them until pure sacred water puts them out, or until Ungadulu's own spell
 * lets the player walk through them.
 */
class ShamanCave
@Inject
constructor(
    private val legends: LegendsQuest,
    private val ungadulu: Ungadulu,
    private val support: LegendsSupport,
    private val locRepo: LocRepository,
    private val world: WorldRepository,
) : PluginScript() {

    private val smokepuff = SpotanimType(SMOKEPUFF.asRSCM(RSCMType.SPOTANIM))

    override fun ScriptContext.startup() {
        for (rock in ENTRANCE_ROCKS) {
            onOpLoc1(rock) { searchRocks() }
        }
        onOpLoc1(ENTRANCE_LEFT) { leaveCave() }
        onOpLoc1(ENTRANCE_RIGHT) { leaveCave() }

        onOpLoc1(TABLE) { mesbox("A crudely constructed makeshift table made from various pieces of wood. You see a piece of screwed up paper on the table top.") }
        onOpLoc2(TABLE) { searchFor(NOTE_TWO, "You start searching the table...", "You find a scrap of paper with what looks like nonsense written on it.", "You cannot find anything else in here.") }
        onOpLoc1(CRATE) { mesbox("It looks like a rickety old crate, perhaps placed in this recess to hide it from prying eyes.") }
        onOpLoc2(CRATE) { searchFor(NOTE_ONE, "You search the crate.", "After some time you find a scrumpled up piece of paper. It looks like rubbish.", "You can't find anything else here.") }
        onOpLoc2(BED) { searchBed() }
        onOpLoc1(DESK) { mes("It's a very old rickety desk made of bamboo.") }
        onOpLoc2(DESK) { searchDesk() }
        onOpLoc1(BOOKCASE) { searchBookcase() }
        onOpLoc1(CREVICE) { mes("It looks like a crevice, you could possibly squeeze through.") }
        onOpLoc2(CREVICE) { squeezeBack() }

        onOpHeld1(NOTE_ONE) { readNoteOne() }
        onOpHeld1(NOTE_TWO) { readNoteTwo() }
        onOpHeld1(NOTE_THREE) { readNoteThree() }
        onOpHeld1(SHAMANS_TOME) { readTome() }

        for (wall in FIRE_WALLS) {
            onOpLoc1(wall) { touchFlames(it.vis) }
            onOpLoc2(wall) { investigateFlames(it.vis) }
            onOpLocU(wall) { useOnFlames(it.vis, it.objType.internalName) }
        }
    }

    private suspend fun ProtectedAccess.searchRocks() {
        arriveDelay()
        val stage = legends.stage(player)
        if (stage < STAGE_FOUND_ENTRANCE) {
            mes("You search the rocks but you see nothing significant...")
        }
        if (stage == STAGE_ACCEPTED_RESCUE) {
            delay(2)
            mes("...at first.")
            legends.setStage(this, STAGE_FOUND_ENTRANCE)
        }
        if (legends.stage(player) < STAGE_FOUND_ENTRANCE) {
            return
        }
        mesbox(
            "You see that there is a small crevice that you may be able to crawl though. Would you " +
                "like to try to crawl through, it looks quite an enclosed area?",
        )
        val crawl =
            choice2(
                "Yes, I'll crawl through, I'm very athletic.", true,
                "No, I'm pretty scared of enclosed areas.", false,
                title = "Crawl into hole?",
            )
        ifClose()
        if (!crawl) {
            mesbox(
                "You decide against forcing yourself into the tiny crevice. And realise that you have " +
                    "much better things to do. Like visit inns and mine ore.",
            )
            return
        }
        if (player.agilityLvl < REQUIRED_AGILITY) {
            mesbox("You need an Agility level of at least 50 to even attempt this feat.")
            return
        }
        mes("You try to crawl through...")
        mes("You contort your body to fit the crevice.")
        anim(CRAWL_SEQ)
        soundSynth(SQUEEZE_SOUND)
        delay(1)
        if (!statRandom(AGILITY, CREVICE_LOW, CREVICE_HIGH, 0)) {
            mes("You get cramped in the tiny space and start to suffocate.")
            delay(1)
            mes("You wriggle and wriggle but you cannot get out...")
            delay(1)
            mes("Eventually you manage to break free.")
            delay(1)
            mes("But you scrape yourself very badly as you force your way out.")
            delay(1)
            mes("And you're totally exhausted from the ordeal.")
            delay(1)
            hurt(5)
            return
        }
        mes("You adroitely squeeze serpent like into the crevice.")
        delay(1)
        teleport(LegendsCoords.SHAMAN_CAVE_LANDING)
        player.legendsEnteredCavern = true
        mes("You find a small narrow tunnel that goes for some distance.")
        delay(1)
        mes("After some time, you find a small cave opening... and walk through.")
    }

    private suspend fun ProtectedAccess.leaveCave() {
        arriveDelay()
        delay(1)
        mes("You crawl back out from the cavern...")
        anim(CRAWL_SEQ)
        delay(2)
        teleport(LegendsCoords.SHAMAN_CAVE_EXIT)
    }

    private suspend fun ProtectedAccess.searchFor(note: String, searching: String, found: String, nothing: String) {
        arriveDelay()
        mes(searching)
        anim(SEARCH_SEQ)
        delay(1)
        if (!legends.isComplete(player) && inv.count(note) == 0 && invAdd(inv, note, 1).success) {
            objbox(note, found)
            return
        }
        mes(nothing)
    }

    private suspend fun ProtectedAccess.searchBed() {
        arriveDelay()
        mes("You search the flea infested rags...")
        anim(SEARCH_SEQ)
        delay(1)
        if (!legends.isComplete(player) && inv.count(NOTE_THREE) == 0 && invAdd(inv, NOTE_THREE, 1).success) {
            mes("You find a scrap of paper with spidery writing on it.")
            return
        }
        mes("You cannot find anything else in here.")
    }

    private suspend fun ProtectedAccess.searchDesk() {
        arriveDelay()
        mes("You give the desk a good search.")
        anim(SEARCH_SEQ)
        delay(1)
        if (!legends.isComplete(player) && inv.count(SHAMANS_TOME) == 0 && invAdd(inv, SHAMANS_TOME, 1).success) {
            mesbox("You find an interesting tome. It looks heavy and very unique.")
            return
        }
        mes("You find nothing else of interest here.")
    }

    private suspend fun ProtectedAccess.readNoteOne() {
        mesbox("You try your best to decode the writing.<br>This is what you make out...")
        objbox(NOTE_ONE, "Daily notes of Ungadulu...<br>Day 1... I have prepared the incantations and will invoke the spirits of my ancestors and pay them homage.")
        objbox(NOTE_ONE, "Though I feel a strange presence in these caves, it is with the heart of the lion that I fight my fears and mark the magical octagram.")
        objbox(NOTE_ONE, "Day 2... What have I done? My spirit is overthrown by feelings of fear and evil, I am not myself these days and feel helpless and weak. From my teachings...")
        mesbox("The writing trails off at this point.")
    }

    private suspend fun ProtectedAccess.readNoteTwo() {
        mesbox("You try your best to decode the writing.<br>This is what you make out...")
        objbox(NOTE_TWO, "I fear that the spirit of an ancient one resides within me and uses me... I am too weak to cast the curse myself and fight the beast within.")
        objbox(NOTE_TWO, "Day 3 ...my last hope is that someone will read this and aid me... I am undone and I fear....")
        mesbox("The writing trails off at this point.")
    }

    private suspend fun ProtectedAccess.readNoteThree() {
        mesbox("You try your best to decode the writing.<br>This is what you make out...")
        objbox(NOTE_THREE, "Day 4 ... These days come so fleetingly, I have no idea how long I have been here now...<br>Day 5... A wizened charm will release me, but never magic that would harm...")
    }

    private suspend fun ProtectedAccess.readTome() {
        mesbox("You read the ancient shaman's tome. It is written in a strange sort of language but you manage a rough translation.")
        mesbox(
            "...scattered are my hopes that I will ever be released from this flaming Octagram, it " +
                "is the only thing which will contain this beast within. Although its grip over me " +
                "is weakened with magic, it is hopeless to know if a saviour would guess this. I am doomed...",
        )
    }

    private suspend fun ProtectedAccess.searchBookcase() {
        arriveDelay()
        mesbox(
            "You search the bookcase, it looks fairly old... After a while you notice that there is " +
                "a small crevice in the back. You might just be able to force your way through if you " +
                "were in any way athletic.",
        )
        val squeeze =
            choice2("Yes please!", true, "No thanks!", false, title = "Would you like to squeeze into this crevice?")
        if (!squeeze) {
            mes("You decide not to squeeze yourself into that ridiculously small crevice.")
            return
        }
        ifClose()
        if (coords != LegendsCoords.BOOKCASE_FRONT) {
            teleport(LegendsCoords.BOOKCASE_FRONT, TeleportType.Exempt)
            delay(1)
        }
        anim(PICKUP_FLOOR_SEQ)
        delay(1)
        crawl(LegendsCoords.BOOKCASE_CREVICE, constants.em_face_east)
        if (!statRandom(AGILITY, CREVICE_LOW, CREVICE_HIGH, 0)) {
            mes("You get stuck as you try to squeeze into the crevice.")
            delay(5)
            mes("It takes you a while to get back out again.")
            crawl(LegendsCoords.BOOKCASE_FRONT, constants.em_face_west)
            return
        }
        mes("You successfully squeeze through the crevice into a small tunnel.")
        telejump(LegendsCoords.CREVICE_TUNNEL)
        anim(CRAWL_SEQ)
        delay(2)
        playerWalk(LegendsCoords.CREVICE_TUNNEL_STEP)
    }

    private suspend fun ProtectedAccess.squeezeBack() {
        arriveDelay()
        mesbox("You search the the crevice. It looks as if you might be able to squeeze through, would you like to try?")
        if (!choice2("Yes please!", true, "No thanks.", false, title = "Squeeze through incredibly tight crevice?")) {
            mes("You decide not to try and squeeze your way through the crevice.")
            return
        }
        ifClose()
        mes("You squeeze your way through the crevice.")
        anim(CRAWL_SEQ)
        soundSynth(SQUEEZE_SOUND)
        delay(1)
        teleport(LegendsCoords.CREVICE_BACK)
    }

    private suspend fun ProtectedAccess.crawl(dest: CoordGrid, dir: Int) {
        anim(CRAWL_SEQ)
        exactMove(coords, dest, delay1 = 6, delay2 = 20 + 6, dir = dir, teleportType = TeleportType.Exempt)
        delay(1)
    }

    private suspend fun ProtectedAccess.touchFlames(wall: BoundLocInfo) {
        arriveDelay()
        if (legends.stage(player) >= STAGE_DEFEATED_NEZIKCHENED_FIRE) {
            walkThroughFlames(wall)
            return
        }
        mes("You approach the supernatural flames.")
        delay(2)
        mes("They give off an incredibly intense heat.")
        if (random.of(9) < 3) {
            mes("You get too close and the intense heat burns you!")
            say("Owwwww!")
            hurt(4)
        } else {
            say("Whew!")
            mes("The heat is intense and just before you burn yourself you pull yourself away")
            mes("from the flames.")
        }
    }

    private suspend fun ProtectedAccess.investigateFlames(wall: BoundLocInfo) {
        arriveDelay()
        if (legends.stage(player) >= STAGE_DEFEATED_NEZIKCHENED_FIRE) {
            walkThroughFlames(wall)
            return
        }
        mesbox(
            "You look closely at the flames. They seem to form a straight wall. Something about them " +
                "looks very strange: they look completely supernatural. For example, they seem to " +
                "come from straight out of the ground.",
        )
        say("Mmmm, pretty!")
        if (LegendsCoords.inOctagram(coords)) {
            val leap =
                choice2(
                    "Leap out of the flaming octagram.", true,
                    "Attract the shaman's attention.", false,
                    title = "What would you like to do?",
                )
            if (leap) {
                leapOut()
                return
            }
        }
        mesbox("You see a white clad figure in the midst of the flames...")
        mesbox("You see the white robed figure inside the flaming octagram gesturing to you.")
        val shaman = support.findNpc(coords, Ungadulu.UNGADULU, SHAMAN_REACH)
        if (shaman == null || !shaman.isType(Ungadulu.UNGADULU) || shaman.visType.id != shaman.type.id) {
            mes("The shaman isn't interested in you at the moment.")
            return
        }
        with(ungadulu) { comeNoCloser(shaman) }
    }

    private suspend fun ProtectedAccess.leapOut() {
        ifClose()
        mes("This is quite dangerous, but you find a suitable location to jump.")
        playerWalk(LegendsCoords.OCTAGRAM_JUMP_FROM)
        mes("You take a run up...")
        delay(2)
        val clean = statRandom(AGILITY, LEAP_LOW, LEAP_HIGH, 0)
        mes(if (clean) "You sail over the top of the flame wall..." else "But get severely burned as you jump across the flames...")
        anim(JUMP_SEQ)
        delay(1)
        teleport(LegendsCoords.OCTAGRAM_JUMP_TO)
        val percent = if (clean) 10 else 50
        hurt(stat("stat.hitpoints") * percent / 100)
        for (stat in listOf("stat.attack", "stat.strength", "stat.defence")) {
            statSub(stat, 0, percent)
        }
        mes(if (clean) "Getting only slightly singed as you go." else "You feel unwell...")
    }

    private suspend fun ProtectedAccess.walkThroughFlames(wall: BoundLocInfo) {
        mes("You feel completely fine to walk through these flames.")
        mes("The magic of Ungadulu's spell protects you from the flames.")
        teleport(acrossFlames(wall), TeleportType.Exempt)
        delay(1)
    }

    private fun ProtectedAccess.acrossFlames(wall: BoundLocInfo): CoordGrid {
        val loc = wall.coords
        if (wall.id == FIRE_STRAIGHT.asRSCM(RSCMType.LOC)) {
            return when (wall.angle) {
                LocAngle.North -> if (coords.z > loc.z) loc.translateZ(-1) else loc.translateZ(1)
                LocAngle.South -> if (coords.z >= loc.z) loc.translateZ(-1) else loc.translateZ(1)
                LocAngle.East -> if (coords.x > loc.x) loc.translateX(-1) else loc.translateX(1)
                else -> if (coords.x >= loc.x) loc.translateX(-1) else loc.translateX(1)
            }
        }
        return when (wall.angle) {
            LocAngle.North, LocAngle.South ->
                if (coords.x > loc.x || coords.z > loc.z) loc.translate(-1, -1) else loc.translate(1, 1)
            else ->
                if (coords.x < loc.x || coords.z > loc.z) loc.translate(1, -1) else loc.translate(-1, 1)
        }
    }

    private suspend fun ProtectedAccess.useOnFlames(wall: BoundLocInfo, obj: String) {
        arriveDelay()
        if (obj == BLESSED_BOWL_PURE) {
            douse(wall)
            return
        }
        val emptied = EVAPORATING[obj]
        if (emptied == null) {
            mes("Nothing interesting happens.")
            return
        }
        mes("The water evaporates in a cloud of steam...")
        anim(SEARCH_SEQ)
        world.spotanimMap(smokepuff, wall.coords, height = SMOKE_HEIGHT)
        delay(1)
        invDel(inv, obj, 1)
        invAdd(inv, emptied, 1)
        mes("...before it gets anywhere near the flames.")
    }

    private fun ProtectedAccess.douse(wall: BoundLocInfo) {
        anim(SEARCH_SEQ)
        world.spotanimMap(smokepuff, wall.coords, height = SMOKE_HEIGHT)
        soundSynth(DOUSE_SOUND)
        mes("You splash some pure water on the flames.")
        val uses = player.legendsBowlUses
        if (uses >= BOWL_USES - 1) {
            invDel(inv, BLESSED_BOWL_PURE, 1)
            invAdd(inv, BLESSED_BOWL, 1)
            player.legendsBowlUses = 0
            mes("The pure water in the golden bowl has run out...")
        } else {
            player.legendsBowlUses = uses + 1
        }
        val dest = acrossFlames(wall)
        teleport(dest, TeleportType.Exempt)
        val shape = if (wall.id == FIRE_STRAIGHT.asRSCM(RSCMType.LOC)) LocShape.WallStraight else LocShape.WallDiagonal
        val info = locRepo.findExact(wall.coords, shape) ?: return
        locRepo.del(info, DOUSED_TICKS)
    }

    private companion object {
        val ENTRANCE_ROCKS = listOf("loc.lgshamancaverock1", "loc.lgshamancaverock2", "loc.lgshamancaverock3")
        const val ENTRANCE_LEFT = "loc.shaman_entrance_cavel"
        const val ENTRANCE_RIGHT = "loc.shaman_entrance_caver"
        const val TABLE = "loc.shaman_small_table"
        const val CRATE = "loc.shaman_crate"
        const val BED = "loc.shaman_poor_bed"
        const val DESK = "loc.shaman_desk"
        const val BOOKCASE = "loc.shaman_bookcase"
        const val CREVICE = "loc.shaman_cave_crevice"
        const val FIRE_STRAIGHT = "loc.lqfirewall_straight"
        val FIRE_WALLS = listOf(FIRE_STRAIGHT, "loc.lqfirewall_diagonal")

        const val NOTE_ONE = "obj.scrawled_note1"
        const val NOTE_TWO = "obj.scrawled_note2"
        const val NOTE_THREE = "obj.scrawled_note3"

        val EVAPORATING =
            mapOf(
                "obj.goldbowlbless_water" to BLESSED_BOWL,
                "obj.goldbowl_pure" to "obj.goldbowl_empty",
                "obj.bucket_water" to "obj.bucket_empty",
                "obj.vial_water" to "obj.vial_empty",
                "obj.jug_water" to "obj.jug_empty",
                "obj.bowl_water" to "obj.bowl_empty",
            )

        const val AGILITY = "stat.agility"
        const val REQUIRED_AGILITY = 50
        const val CREVICE_LOW = 125
        const val CREVICE_HIGH = 250
        const val LEAP_LOW = 50
        const val LEAP_HIGH = 250
        const val SHAMAN_REACH = 10
        const val BOWL_USES = 10
        const val DOUSED_TICKS = 2
        const val SMOKE_HEIGHT = 124

        const val CRAWL_SEQ = "seq.human_crawling"
        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val PICKUP_FLOOR_SEQ = "seq.human_pickupfloor"
        const val JUMP_SEQ = "seq.human_spot_jump"
        const val SMOKEPUFF = "spotanim.smokepuff"
        const val SQUEEZE_SOUND = "synth.squeeze_thru_crack"
        const val DOUSE_SOUND = "synth.watersplash"
    }
}
