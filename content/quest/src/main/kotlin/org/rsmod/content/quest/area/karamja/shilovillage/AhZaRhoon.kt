package org.rsmod.content.quest.area.karamja.shilovillage

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.fadeFromBlack
import org.rsmod.content.quest.area.ardougne.fadeToBlack
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BONE_KEY
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BONE_SHARD
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.CHISEL
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.CRUMPLED_SCROLL
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.ROPE
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.SPADE
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_DUG_MOUND
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_ENTERED_TEMPLE
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_FOUND_MOUND
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_LEFT_TEMPLE
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_LIT_MOUND
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_ROPED_MOUND
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_SEARCHED_MOUND
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STONE_PLAQUE
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.TATTERED_SCROLL
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.ZADIMUS_CORPSE
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The buried temple of Ah Za Rhoon east of Shilo Village.
 *
 * Above ground, the mound of earth is dug out with a spade into a fissure, a light dropped into it
 * shows the long fall and a rope makes it climbable. The mound, fissure and roped fissure are
 * separate locs; each shows the form matching the interacting player's progress for a short while.
 *
 * Below, the ruins hold the stone-plaque, the tattered scroll behind loose rocks, the crumpled
 * scroll in some sacks and Zadimus' corpse on the gallows. The way out is a crude raft made from a
 * smashed table, which rides the underground river out through the waterfall, or the treacherous
 * path along the waterfall rocks.
 */
class AhZaRhoon
@Inject
constructor(
    private val shilo: ShiloVillageQuest,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (mound in MOUND_FORMS) {
            onOpLoc1(mound) { lookAtMound(it.loc) }
            onOpLoc2(mound) { searchMound(it.loc) }
            onOpLocU(mound) { useOnMound(it.loc, it.objType.internalName) }
        }

        onOpLoc2(CAVE_IN) { wriggleThroughCaveIn() }
        onOpLoc2(LOOSE_ROCKS) { searchLooseRocks() }
        onOpLoc2(SACKS) { searchSacks() }
        onOpLoc1(GALLOWS) { lookAtGallows() }
        onOpLoc2(GALLOWS) { searchGallows() }
        onOpLoc1(STRANGE_STONE) { lookAtStone(investigate = false) }
        onOpLoc2(STRANGE_STONE) { lookAtStone(investigate = true) }
        onOpLocU(STRANGE_STONE) { useOnStone(it.objType.internalName) }
        onOpLoc1(SMASHED_TABLE) { mesbox("This table might be useful with some adjustment.") }
        onOpLoc2(SMASHED_TABLE) { craftTable() }
        onOpLoc1(WATERFALL_ROCKS) {
            mesbox("There is a huge cascading waterfall in front of you, the roar of the water is quite loud. To the east you can see what looks like a trecherous pathway, it might lead outside.")
        }
        onOpLoc2(WATERFALL_ROCKS) { followWaterfallPath() }
    }

    /* The mound of earth */

    private suspend fun ProtectedAccess.lookAtMound(loc: BoundLocInfo) {
        arriveDelay()
        val stage = shilo.stage(player)
        when {
            shilo.isComplete(player) || stage >= STAGE_ROPED_MOUND -> {
                mesbox("You see a small fissure in the granite that you might just be able to crawl through. You can see that a rope is attached nearby.")
                showMound(loc, FISSURE_WITH_ROPE)
            }
            stage >= STAGE_DUG_MOUND -> {
                mesbox("You see a small fissure in the granite that you might just be able to crawl through. Beyond the fissure is a long fall.")
                showMound(loc, FISSURE)
            }
            stage >= STAGE_STARTED_SEARCH -> {
                shilo.advanceTo(this, STAGE_FOUND_MOUND)
                mesbox("It looks as if something is buried here.")
                showMound(loc, MOUND)
            }
            else -> mesbox("It just looks like some bumpy ground.")
        }
    }

    private suspend fun ProtectedAccess.searchMound(loc: BoundLocInfo) {
        arriveDelay()
        val stage = shilo.stage(player)
        when {
            shilo.isComplete(player) || stage >= STAGE_ROPED_MOUND -> {
                mesbox("You see a small fissure in the granite that you might just be able to crawl through. You can see that a rope is attached nearby, would you like to climb down?")
                enterFissure(loc)
            }
            stage >= STAGE_DUG_MOUND -> {
                mesbox("You see a small fissure in the granite that you might just be able to crawl through. Beyond the fissure is a long fall. Do you want to try to crawl through the fissure?")
                enterFissure(loc)
            }
            stage >= STAGE_STARTED_SEARCH -> {
                shilo.advanceTo(this, STAGE_SEARCHED_MOUND)
                mesbox("It looks as if something is buried here. You may need some tools to excavate further.")
                showMound(loc, MOUND)
            }
            else -> mesbox("It just looks like some bumpy ground.")
        }
    }

    private suspend fun ProtectedAccess.enterFissure(loc: BoundLocInfo) {
        var enter = false
        startDialogue {
            enter = choice2("Yes, I'll give it a go!", true, "No thanks, I'm having second thoughts.", false, title = "Climb into the fissure?")
        }
        if (!enter) {
            mesbox("You think better of attempting to squeeze into the fissure.")
            say("It looks very dangerous, and dark... Scary!")
            return
        }
        val stage = shilo.stage(player)
        if (!shilo.isComplete(player) && stage == STAGE_DUG_MOUND) {
            mesbox("The fissure looks very dark, you're not sure what lies beyond.")
            return
        }
        if (!shilo.isComplete(player) && stage == STAGE_LIT_MOUND) {
            mesbox("It looks like there is a steep drop just after the fissure. It may be dangerous to try to climb down without any sort of support.")
            return
        }
        if (stat(AGILITY) < FISSURE_AGILITY) {
            mes("You need a level $FISSURE_AGILITY agility to attempt this.")
            return
        }
        mes("You start to contort your body...")
        faceSquare(loc.coords)
        anim(CRAWL_SEQ)
        soundSynth(SQUEEZE_SOUND)
        delay(1)
        mes("With some difficulty you manage to push yourself through the small crack in the rock.")
        anim(PICKUP_SEQ)
        delay(1)
        soundSynth(ROPE_CLIMB_SOUND)
        mes("You cleverly use the rope to slowly lower yourself to the floor.")
        statAdvance(AGILITY, FISSURE_XP)
        telejump(ShiloCoords.TEMPLE_LANDING, TeleportType.Exempt)
        if (stage == STAGE_ROPED_MOUND) {
            shilo.advanceTo(this, STAGE_ENTERED_TEMPLE)
        }
    }

    private suspend fun ProtectedAccess.useOnMound(loc: BoundLocInfo, obj: String) {
        arriveDelay()
        val stage = shilo.stage(player)
        when (obj) {
            SPADE -> digMound(loc, stage)
            ROPE -> {
                when {
                    shilo.isComplete(player) || stage >= STAGE_ROPED_MOUND -> mes("There is already a rope attached!")
                    stage < STAGE_DUG_MOUND -> mes("Nothing interesting happens.")
                    stage == STAGE_DUG_MOUND -> mes("It's too dark to clearly see where to fix that.")
                    else -> {
                        faceSquare(loc.coords)
                        anim(PICKUP_SEQ)
                        soundSynth(TIE_ROPE_SOUND)
                        delay(1)
                        invDel(inv, ROPE)
                        shilo.advanceTo(this, STAGE_ROPED_MOUND)
                        objbox(ROPE, "You see where to attach the rope very clearly. You secure the rope well.")
                        showMound(loc, FISSURE_WITH_ROPE)
                    }
                }
            }
            in LIGHT_SOURCES -> dropLight(loc, obj, stage)
            else -> mes("Nothing interesting happens.")
        }
    }

    private suspend fun ProtectedAccess.digMound(loc: BoundLocInfo, stage: Int) {
        faceSquare(loc.coords)
        when {
            shilo.isComplete(player) || stage >= STAGE_DUG_MOUND -> {
                anim(DIG_SEQ)
                soundSynth(DIG_SOUND)
                delay(2)
                anim(STUMBLE_SEQ)
                delay(1)
                mesbox("You have already excavated this area. Your spade clangs against the granite.")
            }
            stage >= STAGE_STARTED_SEARCH -> {
                mes("You start digging...")
                anim(DIG_LONG_SEQ)
                soundSynth(DIG_SOUND)
                delay(2)
                mes("You dig a small hole and almost immediately hit granite.")
                delay(2)
                mes("You excavate the hole a bit more...")
                soundSynth(DIG_SOUND)
                delay(2)
                mes("And see that there is a small fissure...")
                delay(2)
                resetAnim()
                shilo.advanceTo(this, STAGE_DUG_MOUND)
                mes("You might just be able to crawl through it...")
                showMound(loc, FISSURE)
            }
            else -> mesbox("You start digging... But without knowing what you're digging for... You decide to give up.")
        }
    }

    private suspend fun ProtectedAccess.dropLight(loc: BoundLocInfo, light: String, stage: Int) {
        if (stage < STAGE_DUG_MOUND && !shilo.isComplete(player)) {
            mes("Nothing interesting happens.")
            return
        }
        if (shilo.isComplete(player) || stage >= STAGE_LIT_MOUND) {
            mes("You have already seen inside the fissure.")
            return
        }
        var sure = false
        startDialogue { sure = choice2("Yes", true, "No", false, title = "Are you sure? You won't get that item back.") }
        if (!sure) {
            return
        }
        val name = (ServerCacheManager.getItem(light.asRSCM(RSCMType.OBJ))?.name ?: "light").lowercase()
        faceSquare(loc.coords)
        anim(PICKUP_SEQ)
        delay(1)
        invDel(inv, light)
        shilo.advanceTo(this, STAGE_LIT_MOUND)
        objbox(
            light,
            "You drop the $name into the fissure and see that there is quite a large drop after you get through the hole. There is a good anchor point nearby onto which you could tie a rope.",
        )
        mesbox("The $name burns out.")
    }

    /** The mound shows [form] to the world for a while; the change is the last thing the script does. */
    private fun showMound(loc: BoundLocInfo, form: String) {
        if (loc.id == form.asRSCM(RSCMType.LOC)) {
            return
        }
        locRepo.change(loc, form, MOUND_FORM_TICKS)
    }

    /* The ruins */

    private suspend fun ProtectedAccess.wriggleThroughCaveIn() {
        arriveDelay()
        mesbox("You see that there is a narrow gap through into the darkness. You could try to wriggle through and see where it takes you.")
        var wriggle = false
        startDialogue { wriggle = choice2("Yes, I'll wriggle through.", true, "No, I'll stay here.", false, title = "Wriggle through the rubble?") }
        if (!wriggle) {
            mes("You decide to stay where you are.")
            return
        }
        anim(CRAWL_SEQ)
        soundSynth(SQUEEZE_SOUND)
        delay(1)
        val dest = if (player.coords.z > CAVE_IN_DIVIDE_Z) ShiloCoords.CAVE_IN_SOUTH else ShiloCoords.CAVE_IN_NORTH
        telejump(dest, TeleportType.Exempt)
        mes("You manage to wriggle through the rubble.")
    }

    private suspend fun ProtectedAccess.searchLooseRocks() {
        arriveDelay()
        if (shilo.isComplete(player)) {
            mesbox("You notice nothing significant about this.")
            return
        }
        if (owns(TATTERED_SCROLL)) {
            mesbox("You see nothing here but an empty bookcase behind some rocks.")
            return
        }
        mesbox("You can see that there is something hidden behind some rocks. Do you want to have a look? It looks a bit dangerous as the ceiling doesn't look at all safe.")
        var move = false
        startDialogue {
            move =
                choice2(
                    "Yes, I'll carefully move the rocks to see what's behind them.", true,
                    "No, I'll leave them alone, I don't like the look of that ceiling.", false,
                )
        }
        if (!move) {
            mesbox("You decide to leave the rocks well alone. The ceiling does look a little unsafe.")
            return
        }
        mesbox("You start to slowly move the rocks to one side.")
        anim(PICKUP_SEQ)
        delay(1)
        if (!statRandom(AGILITY, ROLL_LOW, ROLL_HIGH, invisibleBoost = 0)) {
            soundSynth(ROCKFALL_SOUND)
            queueHit(delay = 1, type = HitType.Typeless, damage = player.baseHitpointsLvl / 10 + 1)
            mesbox("You accidentally knock some rocks and the ceiling starts to cave in. Some rocks fall on you.")
            return
        }
        invAddOrDrop(objRepo, TATTERED_SCROLL)
        statAdvance(AGILITY, LOOSE_ROCKS_XP)
        objbox(
            TATTERED_SCROLL,
            "You carefully manage to remove enough rocks to see a book shelf. You gingerly remove a delicate scroll from the shelf and place it in your inventory.",
        )
    }

    private suspend fun ProtectedAccess.searchSacks() {
        arriveDelay()
        if (shilo.isComplete(player)) {
            mesbox("You notice nothing significant about this.")
            return
        }
        anim(PICKUP_SEQ)
        if (owns(CRUMPLED_SCROLL)) {
            mes("You find nothing in the sacks.")
            return
        }
        invAddOrDrop(objRepo, CRUMPLED_SCROLL)
        objbox(CRUMPLED_SCROLL, "You find a tattered but very ornate scroll, which you place carefully in your inventory.")
    }

    private suspend fun ProtectedAccess.lookAtGallows() {
        if (shilo.isComplete(player)) {
            mesbox("You notice nothing significant about this.")
            return
        }
        mesbox("You take a look at the gallows. It's pretty eerie looking.")
        mesbox("A grisly sight meets your eyes. A human corpse hangs from the noose. His hands have been tied behind his back.")
    }

    private suspend fun ProtectedAccess.searchGallows() {
        arriveDelay()
        if (shilo.isComplete(player)) {
            mesbox("You notice nothing significant about this.")
            return
        }
        mes("You search the gallows...")
        delay(2)
        if (owns(ZADIMUS_CORPSE) || owns(BONE_SHARD) || owns(BONE_KEY)) {
            mesbox("The gallows look pretty eerie. You search but find nothing.")
            return
        }
        mesbox("You find a human corpse hanging in the noose. It looks as if the corpse can be removed easily. Would you like to remove the corpse from the noose?")
        var remove = false
        startDialogue {
            remove = choice2("I don't think so, it might animate and attack me!", false, "Yes, I may find something else on the corpse.", true)
        }
        if (!remove) {
            mesbox("You move away from the corpse quietly and slowly... ...you have an eerie feeling about this!")
            say("** Gulp! **")
            return
        }
        anim(PICKUP_TABLE_SEQ)
        objbox(ZADIMUS_CORPSE, "You gently support the frame of the skeleton and lift the skull through the noose.")
        invAddOrDrop(objRepo, ZADIMUS_CORPSE)
        objbox(ZADIMUS_CORPSE, "You find an old sack and place the skeleton in it. Maybe Trufitus can give you some tips on what to do with it.")
        mesbox("You sense that there is a spirit that needs to be put to rest.")
    }

    private suspend fun ProtectedAccess.lookAtStone(investigate: Boolean) {
        if (shilo.isComplete(player)) {
            mesbox("You notice nothing significant about this stone.")
            return
        }
        if (!investigate) {
            mesbox("This stone seems to have strange markings on it.")
            return
        }
        mesbox("This stone seems to have strange markings on it. Maybe Trufitus can decipher them? The stone is too heavy to carry but the letters stand proud on a plaque. Maybe you could seperate the plaque from the rock?")
    }

    private suspend fun ProtectedAccess.useOnStone(obj: String) {
        arriveDelay()
        if (shilo.isComplete(player)) {
            mesbox("You notice nothing significant about this.")
            return
        }
        val craftingLevel =
            when (obj) {
                CHISEL -> CHISEL_PLAQUE_LEVEL
                SPADE -> SPADE_PLAQUE_LEVEL
                else -> {
                    mes("Nothing interesting happens.")
                    return
                }
            }
        if (stat(CRAFTING) < craftingLevel) {
            mesbox("You need a crafting skill of at least $craftingLevel to complete this task with this tool.")
            return
        }
        if (owns(STONE_PLAQUE)) {
            mesbox("It looks as if something has been cut from this stone.")
            return
        }
        anim(if (obj == CHISEL) CHISEL_SEQ else PICKUP_SEQ)
        delay(1)
        invAddOrDrop(objRepo, STONE_PLAQUE)
        objbox(STONE_PLAQUE, "You cleanly cut the plaque of letters away from the rock. You place it carefully into your inventory.")
    }

    /* Leaving the temple */

    private suspend fun ProtectedAccess.craftTable() {
        arriveDelay()
        if (shilo.tableWoodUsed.get(player)) {
            mesbox("There isn't enough wood left in this table to make anything!")
            return
        }
        mesbox("You may be able to turn this dilapidated table into something that could help you to get out of this place. What would you like to try and turn this table into?")
        var choice = 0
        startDialogue { choice = choice3("A ladder", 1, "A crude raft", 2, "A pole vault", 3) }
        when (choice) {
            1, 3 -> {
                if (statRandom(CRAFTING, ROLL_LOW, ROLL_HIGH, invisibleBoost = 0)) {
                    mesbox("Your experience in crafting tells you that there isn't enough wood to complete this task.")
                    return
                }
                anim(CRAFT_TABLE_SEQ)
                mesbox("You happily start hacking away at the table but realise that you won't have enough wood to properly finish the item off!")
                say("Oops! Not enough wood left to do anything else with the table!")
            }
            2 -> rideRaft()
        }
    }

    private suspend fun ProtectedAccess.rideRaft() {
        mesbox("You see that this table already looks very sea worthy it takes virtually no time at all to help fix it into a crude raft.")
        shilo.tableWoodUsed.set(player, true)
        statAdvance(CRAFTING, RAFT_XP)
        mes("You place it carefully on the water!")
        soundSynth(SPLASH_SOUND)
        locRepo.add(ShiloCoords.RAFT_LAUNCH, RAFT, RAFT_STEP_TICKS + 1, LocAngle.West, LocShape.CentrepieceStraight)
        telejump(ShiloCoords.RAFT_LAUNCH, TeleportType.Exempt)
        delay(2)
        mes("You board the raft!")
        delay(1)
        mes("You push off!")
        say("Weeeeeeee!")
        for ((index, spot) in ShiloCoords.RAFT_ROUTE.withIndex()) {
            val angle = if (index < 2) LocAngle.West else LocAngle.North
            locRepo.add(spot, RAFT, RAFT_STEP_TICKS + 1, angle, LocShape.CentrepieceStraight)
            telejump(spot, TeleportType.Exempt)
            when (index) {
                2 -> say("Weeeeeeee!")
                ShiloCoords.RAFT_ROUTE.size - 2 -> {
                    mes("You come to a huge waterfall...")
                    say("* Uh oh! *")
                    camMoveTo(spot.translateX(-CAMERA_BACKOFF), CAMERA_HEIGHT, CAMERA_RATE, CAMERA_RATE)
                    camLookAt(spot.translateX(CAMERA_BACKOFF), CAMERA_LOOK_HEIGHT, CAMERA_RATE, CAMERA_RATE)
                }
                ShiloCoords.RAFT_ROUTE.lastIndex -> mes("...And plough through it!")
            }
            delay(RAFT_STEP_TICKS)
        }
        soundSynth(RIVER_SOUND)
        fadeToBlack()
        camReset()
        telejump(ShiloCoords.WATERFALL_OUTSIDE, TeleportType.Exempt)
        leftTemple()
        delay(1)
        fadeFromBlack()
        mes("The raft soon breaks up.")
        spotanim(SPLASH_SPOTANIM)
        soundSynth(SPLASH_SOUND)
        anim(SWIM_SEQ)
        exactMove(
            start = ShiloCoords.WATERFALL_OUTSIDE,
            end = ShiloCoords.FALLS_SOUTH_BANK,
            delay1 = 0,
            delay2 = SWIM_TICKS * CLIENT_CYCLES_PER_TICK,
            dir = constants.em_face_south,
            teleportType = TeleportType.Exempt,
        )
        delay(SWIM_TICKS)
        resetAnim()
    }

    private suspend fun ProtectedAccess.followWaterfallPath() {
        arriveDelay()
        mesbox("You see a huge waterfall blocking your path. The path leads on through the cascading waterfall, but it looks quite dangerous. Would you like to try and follow the path?")
        var follow = false
        startDialogue { follow = choice2("Yes, I'll follow the path.", true, "No, I'll look for another exit.", false, title = "Follow the path?") }
        if (!follow) {
            mesbox("You decide to have another look around. And see if you can find a better way to get out.")
            return
        }
        val south = player.coords.z < ShiloCoords.WATERFALL_ROCKS_MIDDLE_Z
        mes("You start carefully edging along the slippery path...")
        anim(if (south) SIDESTEP_LEFT_SEQ else SIDESTEP_SEQ)
        exactMove(
            start = player.coords,
            end = player.coords.translateX(SIDESTEP_TILES),
            delay1 = 0,
            delay2 = SIDESTEP_TICKS * CLIENT_CYCLES_PER_TICK,
            dir = constants.em_face_east,
            teleportType = TeleportType.Exempt,
        )
        delay(SIDESTEP_TICKS)
        val bank = if (south) ShiloCoords.FALLS_SOUTH_BANK else ShiloCoords.FALLS_NORTH_BANK
        if (!statRandom(AGILITY, ROLL_LOW, ROLL_HIGH, invisibleBoost = 0)) {
            mes("You fall!")
            anim(SIDESTEP_FALL_SEQ)
            soundSynth(SPLASH_SOUND)
            say("Arrgghhhh!")
            delay(1)
            mes("You're pumelled as the thrashing water throws you against the rocks.")
            fadeToBlack()
            telejump(ShiloCoords.WATERFALL_OUTSIDE, TeleportType.Exempt)
            leftTemple()
            delay(1)
            fadeFromBlack()
            spotanim(SPLASH_SPOTANIM)
            anim(SWIM_SEQ)
            exactMove(
                start = ShiloCoords.WATERFALL_OUTSIDE,
                end = bank,
                delay1 = 0,
                delay2 = SWIM_TICKS * CLIENT_CYCLES_PER_TICK,
                dir = if (south) constants.em_face_south else constants.em_face_north,
                teleportType = TeleportType.Exempt,
            )
            delay(SWIM_TICKS)
            resetAnim()
            queueHit(delay = 1, type = HitType.Typeless, damage = player.baseHitpointsLvl * FALL_PERCENT / 100 + 1)
            return
        }
        mes("You climb your way out of the cavern into the heat of the Jungle.")
        statAdvance(AGILITY, WATERFALL_XP)
        resetAnim()
        fadeToBlack()
        telejump(bank, TeleportType.Exempt)
        leftTemple()
        delay(1)
        fadeFromBlack()
    }

    private fun ProtectedAccess.leftTemple() {
        if (shilo.stage(player) == STAGE_ENTERED_TEMPLE) {
            shilo.advanceTo(this, STAGE_LEFT_TEMPLE)
        }
    }

    private companion object {
        const val STAGE_STARTED_SEARCH = ShiloVillageQuest.STAGE_STARTED

        const val MOUND = "loc.ahzarhoon_entrance"
        const val FISSURE = "loc.ahzarhoon_entrance_fissure"
        const val FISSURE_WITH_ROPE = "loc.ahzarhoon_entrance_fissure_withrope"
        val MOUND_FORMS = listOf(MOUND, FISSURE, FISSURE_WITH_ROPE)
        const val MOUND_FORM_TICKS = 50

        const val CAVE_IN = "loc.secretrubble"
        const val LOOSE_ROCKS = "loc.secretrubblebook"
        const val SACKS = "loc.zqsacks"
        const val GALLOWS = "loc.zqgallows"
        const val STRANGE_STONE = "loc.zqsecretstone"
        const val SMASHED_TABLE = "loc.zqtableraft"
        const val WATERFALL_ROCKS = "loc.zqwaterfallrocks"
        const val RAFT = "loc.zqlograft"
        const val CAVE_IN_DIVIDE_Z = 9344

        const val AGILITY = "stat.agility"
        const val CRAFTING = "stat.crafting"
        const val FISSURE_AGILITY = 32
        const val FISSURE_XP = 3.0
        const val LOOSE_ROCKS_XP = 1.5
        const val WATERFALL_XP = 0.5
        const val RAFT_XP = 3.0
        const val CHISEL_PLAQUE_LEVEL = 9
        const val SPADE_PLAQUE_LEVEL = 29
        const val ROLL_LOW = 125
        const val ROLL_HIGH = 250
        const val FALL_PERCENT = 30

        const val RAFT_STEP_TICKS = 2
        const val SIDESTEP_TILES = 2
        const val SIDESTEP_TICKS = 2
        const val SWIM_TICKS = 3
        const val CLIENT_CYCLES_PER_TICK = 30
        const val CAMERA_BACKOFF = 7
        const val CAMERA_HEIGHT = 550
        const val CAMERA_LOOK_HEIGHT = 20
        const val CAMERA_RATE = 100

        const val DIG_SEQ = "seq.human_dig"
        const val DIG_LONG_SEQ = "seq.human_dig_long"
        const val STUMBLE_SEQ = "seq.human_stumble_back"
        const val PICKUP_SEQ = "seq.human_pickupfloor"
        const val PICKUP_TABLE_SEQ = "seq.human_pickuptable"
        const val CRAWL_SEQ = "seq.human_crawling"
        const val CHISEL_SEQ = "seq.human_cutting"
        const val CRAFT_TABLE_SEQ = "seq.human_pickuptable"
        const val SWIM_SEQ = "seq.human_swim"
        const val SIDESTEP_SEQ = "seq.human_into_sidestep"
        const val SIDESTEP_LEFT_SEQ = "seq.human_into_sidestepl"
        const val SIDESTEP_FALL_SEQ = "seq.human_sidestep_fall"
        const val SPLASH_SPOTANIM = "spotanim.watersplash"

        const val DIG_SOUND = "synth.digspade"
        const val SQUEEZE_SOUND = "synth.squeeze_thru_crack"
        const val ROPE_CLIMB_SOUND = "synth.ropeclimb"
        const val TIE_ROPE_SOUND = "synth.cf_tierope"
        const val SPLASH_SOUND = "synth.watersplash"
        const val RIVER_SOUND = "synth.splash_and_river"
        const val ROCKFALL_SOUND = "synth.fall_land"

        val LIGHT_SOURCES =
            setOf(
                "obj.lit_candle",
                "obj.lit_black_candle",
                "obj.torch_lit",
                "obj.candle_lantern_lit",
                "obj.candle_lantern_black_lit",
                "obj.wint_torch",
            )
    }
}
