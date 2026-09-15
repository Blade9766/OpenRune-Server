package org.rsmod.content.quest.area.karamja.shilovillage

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.content.quest.area.ardougne.fadeFromBlack
import org.rsmod.content.quest.area.ardougne.fadeToBlack
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BERVIRIUS_NOTES
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.CHARCOAL
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.LOCATING_CRYSTAL
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.PAPYRUS
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.RASHILIYIA
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.RASHILIYIA_CORPSE
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_CORPSE_RETRIEVED
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_ENTERED_BERVIRIUS
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_ENTERED_TEMPLE
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STAGE_LEFT_TEMPLE
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.SWORD_POMMEL
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Cairn Isle and the Tomb of Bervirius.
 *
 * The isle is reached over the climbing rocks on the Karamja shore and a rickety bridge that can
 * pitch a clumsy player into the river. The well-stacked rocks on its north side hide a crawl-way
 * into the tomb, which only a player who has read about Bervirius thinks to look for. His dolmen
 * holds the sword pommel, the locating crystal and the writings copied into Bervirius' notes, and
 * it is where Rashiliyia's remains are laid to rest at the end of the quest.
 */
class TombOfBervirius
@Inject
constructor(
    private val shilo: ShiloVillageQuest,
    private val undead: ShiloUndead,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(CLIMBING_ROCKS) { climbRocks(it.loc) }
        onPlayerTimer(BRIDGE_TIMER) { balanceOnBridge() }
        onOpLoc1(STACKED_ROCKS) { mesbox("These rocks look like they have been stacked uniformly.") }
        onOpLoc2(STACKED_ROCKS) { searchStackedRocks() }
        onOpLoc1(DOLMEN) { inspectDolmen(search = false) }
        onOpLoc2(DOLMEN) { inspectDolmen(search = true) }
        onOpLocU(DOLMEN) { useOnDolmen(it.objType.internalName) }
        onOpLoc1(HANDHOLDS) { climbOut(it.loc) }
        onOpNpc1(RASHILIYIA) { mes("Rashiliyia doesn't seem interested in talking to you.") }
    }

    /* Cairn Isle */

    private suspend fun ProtectedAccess.climbRocks(rocks: BoundLocInfo) {
        arriveDelay()
        if (player.coords.x == ShiloCoords.CLIMBING_ROCKS_X) {
            mes("You can't do that from here.")
            return
        }
        val fromEast = player.coords.x > ShiloCoords.CLIMBING_ROCKS_X
        val eastFoot = CoordGrid(ShiloCoords.CLIMBING_ROCKS_X + CLIMB_SPAN / 2, ShiloCoords.BRIDGE_Z, 0)
        val westFoot = CoordGrid(ShiloCoords.CLIMBING_ROCKS_X - CLIMB_SPAN / 2, ShiloCoords.BRIDGE_Z, 0)
        statAdvance(AGILITY, ROCKS_XP)
        if (!fromEast) {
            startClimbFrom(westFoot)
            anim(CLIMB_DOWN_SEQ)
            climbAcross(eastFoot, constants.em_face_east)
            if (!statRandom(AGILITY, ROLL_LOW, ROLL_HIGH, invisibleBoost = 0)) {
                slip()
            }
            return
        }
        if (stat(AGILITY) < ROCKS_AGILITY) {
            mes("You need an Agility level of at least $ROCKS_AGILITY to climb these rocks.")
            return
        }
        startClimbFrom(eastFoot)
        faceSquare(rocks.coords)
        anim(CLIMB_SEQ)
        val top = CoordGrid(ShiloCoords.CLIMBING_ROCKS_X, ShiloCoords.BRIDGE_Z, 0)
        climbAcross(top, constants.em_face_west)
        if (!statRandom(AGILITY, ROLL_LOW, ROLL_HIGH, invisibleBoost = 0)) {
            anim(CLIMB_FALL_SEQ)
            climbAcross(eastFoot, constants.em_face_west)
            slip()
            return
        }
        anim(CLIMB_SEQ)
        climbAcross(westFoot, constants.em_face_west)
        timer(BRIDGE_TIMER, BRIDGE_FIRST_CHECK)
    }

    private suspend fun ProtectedAccess.startClimbFrom(foot: CoordGrid) {
        if (player.coords != foot) {
            telejump(foot, TeleportType.Exempt)
            delay(1)
        }
    }

    private suspend fun ProtectedAccess.climbAcross(dest: CoordGrid, facing: Int) {
        exactMove(
            start = player.coords,
            end = dest,
            delay1 = 0,
            delay2 = CLIMB_TICKS * CLIENT_CYCLES_PER_TICK,
            dir = facing,
            teleportType = TeleportType.Exempt,
        )
        delay(CLIMB_TICKS)
        resetAnim()
    }

    private suspend fun ProtectedAccess.slip() {
        mes("You fall and hurt yourself.")
        say("Ouch")
        val damage = (player.hitpoints * SLIP_PERCENT / 100 + 1) * 2
        queueHit(delay = 1, type = HitType.Typeless, damage = damage.coerceAtMost(player.hitpoints))
    }

    private suspend fun ProtectedAccess.balanceOnBridge() {
        if (!ShiloCoords.nearCairnCrossing(player.coords)) {
            return
        }
        val onBridge = player.coords.z == ShiloCoords.BRIDGE_Z && player.coords.x in ShiloCoords.BRIDGE_TILES
        if (!onBridge) {
            timer(BRIDGE_TIMER, BRIDGE_CHECK)
            return
        }
        if (statRandom(AGILITY, ROLL_LOW, ROLL_HIGH, invisibleBoost = 0)) {
            mes("You manage to keep your balance on the bridge.")
            statAdvance(AGILITY, BRIDGE_XP)
            timer(BRIDGE_TIMER, BRIDGE_RECHECK)
            return
        }
        stopAction()
        mes("You fall!")
        anim(STUMBLE_SEQ)
        delay(1)
        say("Ahhhhhhhhhh!")
        anim(FALLING_SEQ)
        exactMove(
            start = player.coords,
            end = ShiloCoords.BRIDGE_SPLASHDOWN,
            delay1 = 0,
            delay2 = CLIENT_CYCLES_PER_TICK,
            dir = constants.em_face_south,
            teleportType = TeleportType.Exempt,
        )
        delay(1)
        say("Choke!")
        spotanim(SPLASH_SPOTANIM)
        soundSynth(SPLASH_SOUND)
        anim(SWIM_SEQ)
        exactMove(
            start = ShiloCoords.BRIDGE_SPLASHDOWN,
            end = ShiloCoords.BRIDGE_SHORE,
            delay1 = 0,
            delay2 = SWIM_TICKS * CLIENT_CYCLES_PER_TICK,
            dir = constants.em_face_south,
            teleportType = TeleportType.Exempt,
        )
        say("Gulp!")
        delay(SWIM_TICKS)
        say("Cough!")
        resetAnim()
        mes("You just manage to drag your pitiful frame onto the river bank.")
        mes("Though you nearly drowned in the river!")
        val damage = (player.hitpoints / DROWN_DIVISOR) * DROWN_HITS
        if (damage > 0) {
            queueHit(delay = 1, type = HitType.Typeless, damage = damage.coerceAtMost(player.hitpoints - 1))
        }
    }

    private suspend fun ProtectedAccess.searchStackedRocks() {
        arriveDelay()
        val allowed = shilo.readTatteredScroll.get(player) || shilo.stage(player) >= STAGE_ENTERED_BERVIRIUS || shilo.isComplete(player)
        if (!allowed) {
            mesbox("You find nothing of significance. And it does look quite scary.")
            return
        }
        mesbox("You investigate the rocks and find a dank, narrow crawl-way. Do you want to crawl into this dank, dark, narrow, possibly dangerous hole?")
        var crawl = false
        startDialogue {
            crawl = choice2("Yes please, I can think of nothing nicer!", true, "No way could you get me to go in there!", false, title = "Crawl into hole?")
        }
        if (!crawl) {
            mes("You decide that the surface is the place for you!")
            return
        }
        if (stat(AGILITY) < CRAWL_AGILITY) {
            mes("You need an Agility level of at least $CRAWL_AGILITY to squeeze in there.")
            return
        }
        mesbox("You contort your body and prepare to squirm worm like into the hole.")
        anim(CRAWL_SEQ)
        soundSynth(SQUEEZE_SOUND)
        delay(1)
        if (!statRandom(AGILITY, ROLL_LOW, ROLL_HIGH, invisibleBoost = 0)) {
            mes("You manage to get yourself stuck.")
            delay(2)
            mes("You have to wrench yourself free to get out.")
            delay(2)
            mes("You manage to pull yourself out, but are hurt in the process.")
            resetAnim()
            mes("Maybe you'll have better luck next time?")
            queueHit(delay = 1, type = HitType.Typeless, damage = STUCK_DAMAGE.coerceAtMost(player.hitpoints))
            return
        }
        mesbox("You struggle through the narrow crevice in the rocks.")
        if (shilo.stage(player) in STAGE_ENTERED_TEMPLE..STAGE_LEFT_TEMPLE) {
            shilo.advanceTo(this, STAGE_ENTERED_BERVIRIUS)
        }
        telejump(ShiloCoords.BERVIRIUS_LANDING, TeleportType.Exempt)
        resetAnim()
        mesbox("And drop to your feet into a narrow underground corridor.")
    }

    /* The tomb */

    private suspend fun ProtectedAccess.inspectDolmen(search: Boolean) {
        arriveDelay()
        if (shilo.isComplete(player)) {
            mesbox("There is nothing new about this dolmen.")
            return
        }
        val pommel = ownsSwordPommel()
        val crystal = owns(LOCATING_CRYSTAL)
        val onTop =
            when {
                pommel && crystal -> "You can see nothing on the dolmen."
                pommel || crystal -> "You can see an item on the dolmen."
                else -> "You can see that there are some items on the dolmen."
            }
        mesbox("The dolmen is intricately decorated with the symbol of two crossed palm trees. It might be the family crest? $onTop")
        if (!search) {
            return
        }
        anim(PICKUP_TABLE_SEQ)
        if (!pommel) {
            invAddOrDrop(objRepo, SWORD_POMMEL)
            objbox(SWORD_POMMEL, "You find a rusty sword with an ivory pommel. You take the Ivory pommel and place it in your inventory.")
        }
        if (!crystal) {
            invAddOrDrop(objRepo, LOCATING_CRYSTAL)
            objbox(LOCATING_CRYSTAL, "You find a Crystal Sphere. You take the crystal and place it carefully in your inventory.")
        }
        if (owns(BERVIRIUS_NOTES)) {
            return
        }
        if (!shilo.copiedDolmenNotes.get(player)) {
            shilo.copiedDolmenNotes.set(player, true)
            invAddOrDrop(objRepo, BERVIRIUS_NOTES)
            objbox(
                BERVIRIUS_NOTES,
                "You find some writing on the dolmen, you grab some nearby scraps of delicate paper together and copy the text as best you can and collect them together as a scroll.",
            )
            return
        }
        if (!player.inv.contains(PAPYRUS) || !owns(CHARCOAL)) {
            mesbox("You find some writing on the dolmen. You'll need some papyrus and charcoal to make new notes from this dolmen.")
            return
        }
        mesbox("Do you want to take some notes from this dolmen using your papyrus and charcoal?")
        var copy = false
        startDialogue { copy = choice2("Yes, I'll make some notes.", true, "No, I don't want to make any notes.", false) }
        if (!copy) {
            mesbox("You decide not to take any notes from the dolmen.")
            return
        }
        invDel(inv, PAPYRUS)
        invAddOrDrop(objRepo, BERVIRIUS_NOTES)
        objbox(
            BERVIRIUS_NOTES,
            "You find some writing on the dolmen, you take some of your papyrus and using the charcoal copy the text as best you can and collect them together as a scroll.",
        )
    }

    private suspend fun ProtectedAccess.climbOut(handholds: BoundLocInfo) {
        arriveDelay()
        mes("You attempt to climb the granite rock.")
        faceSquare(handholds.coords)
        anim(CLIMB_SEQ)
        delay(1)
        if (!statRandom(AGILITY, ROLL_LOW, ROLL_HIGH, invisibleBoost = 0)) {
            mes("You fall!")
            anim(CLIMB_FALL_SEQ)
            say("Arrggghhhhhh!")
            delay(1)
            resetAnim()
            queueHit(delay = 1, type = HitType.Typeless, damage = random.of(1, HANDHOLD_FALL_MAX).coerceAtMost(player.hitpoints))
            return
        }
        mes("You manage to climb back out again!")
        delay(1)
        fadeToBlack()
        telejump(ShiloCoords.BERVIRIUS_EXIT, TeleportType.Exempt)
        delay(1)
        fadeFromBlack()
        resetAnim()
        walk(ShiloCoords.BERVIRIUS_EXIT.translate(1, 0))
    }

    private suspend fun ProtectedAccess.useOnDolmen(obj: String) {
        arriveDelay()
        if (obj != RASHILIYIA_CORPSE) {
            mes("Nothing interesting happens.")
            return
        }
        if (shilo.stage(player) != STAGE_CORPSE_RETRIEVED) {
            mes("Nothing interesting happens.")
            return
        }
        anim(PICKUP_TABLE_SEQ)
        objbox(RASHILIYIA_CORPSE, "You carefully place Rashiliyias remains on the dolmen. You feel a strange vibration in the air.")
        invDel(inv, RASHILIYIA_CORPSE)
        val spot = mapFindSquareLineOfWalk(player.coords, 1, 1) ?: player.coords
        soundSynth(SPIRIT_SOUND)
        val queen = undead.spawnApparition(RASHILIYIA, spot, QUEEN_DURATION, player)
        delay(1)
        if (queen != null) {
            startDialogue(queen) {
                chatNpc(
                    happy,
                    "You have my gratitude for releasing my spirit. I have suffered a vengeful and evil existence. I was tricked by Zamorak. He returned my son to me as an undead creature.",
                )
                chatNpc(
                    happy,
                    "My hatred and bitterness corrupted me. I tried to destroy all life... now I am released. And am grateful to contemplate eternal rest...",
                )
            }
            mes("Without warning the spirit of Rashiliyia disappears.")
            undead.dismissApparition(queen)
        }
        shilo.quest.completeQuest(this)
        shilo.syncVars(player)
    }

    private companion object {
        const val CLIMBING_ROCKS = "loc.zqclimbingrocks"
        const val STACKED_ROCKS = "loc.zqrocks"
        const val DOLMEN = "loc.zqdolmen"
        const val HANDHOLDS = "loc.zqhandholds"
        const val BRIDGE_TIMER = "timer.shilo_cairn_bridge"

        const val AGILITY = "stat.agility"
        const val ROCKS_AGILITY = 15
        const val CRAWL_AGILITY = 32
        const val ROCKS_XP = 1.0
        const val BRIDGE_XP = 10.0
        const val ROLL_LOW = 125
        const val ROLL_HIGH = 250

        const val CLIMB_SPAN = 4
        const val CLIMB_TICKS = 2
        const val SWIM_TICKS = 3
        const val CLIENT_CYCLES_PER_TICK = 30
        const val SLIP_PERCENT = 5
        const val DROWN_DIVISOR = 11
        const val DROWN_HITS = 3
        const val STUCK_DAMAGE = 3
        const val HANDHOLD_FALL_MAX = 10
        const val BRIDGE_FIRST_CHECK = 3
        const val BRIDGE_CHECK = 3
        const val BRIDGE_RECHECK = 20
        const val QUEEN_DURATION = 100

        const val CLIMB_SEQ = "seq.human_climbing"
        const val CLIMB_DOWN_SEQ = "seq.human_climbing_down"
        const val CLIMB_FALL_SEQ = "seq.human_climbing_fall"
        const val STUMBLE_SEQ = "seq.human_walk_logbalance_stumble"
        const val FALLING_SEQ = "seq.human_falling"
        const val SWIM_SEQ = "seq.human_swim"
        const val CRAWL_SEQ = "seq.human_crawling"
        const val PICKUP_TABLE_SEQ = "seq.human_pickuptable"
        const val SPLASH_SPOTANIM = "spotanim.watersplash"

        const val SPLASH_SOUND = "synth.watersplash"
        const val SQUEEZE_SOUND = "synth.squeeze_thru_crack"
        const val SPIRIT_SOUND = "synth.ghost_disappear"
    }
}
