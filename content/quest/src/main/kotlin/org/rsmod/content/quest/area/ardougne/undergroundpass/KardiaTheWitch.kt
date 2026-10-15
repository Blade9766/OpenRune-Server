package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.DOLL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.IBAN_BOOK
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KARDIA
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.KARDIA_CAT
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_OPEN_CHEST
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_PICKUP_FLOOR
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_SEARCH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_CHEST_OPEN
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_PICK
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_DOLL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_DWARVES
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Kardia, the witch who made Iban, and her cat.
 *
 * She will not open her door to anyone and lashes out at whoever tries it. The cat, who has
 * wandered off to the north, is the way in: left on her doorstep with a knock, it brings her out,
 * and while she fusses over it inside the chest with her doll in it is there for the taking.
 */
@Singleton
class KardiaTheWitch
@Inject
constructor(
    private val quest: UndergroundPassQuest,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val passages: GenericPassageScript,
) : PluginScript() {

    private val openChestType by lazy { locType(CHEST_OPEN) }

    override fun ScriptContext.startup() {
        for (witch in listOf(KARDIA, UndergroundPassQuest.visibleTwin(KARDIA))) {
            onOpNpc1(witch) { mes("Kardia doesn't seem interested in talking.") }
        }
        for (cat in listOf(KARDIA_CAT, UndergroundPassQuest.visibleTwin(KARDIA_CAT))) {
            onOpNpc1(cat) { pickUpCat(it.npc) }
            onOpNpc3(cat) { mes("The cat stares back at you, unimpressed.") }
        }
        onOpLoc1(WINDOW) { lookThroughWindow() }
        onOpLocU(DOOR, CAT_ITEM) { leaveCatAtDoor() }
        onOpLoc1(DOOR) { openDoor(it.loc, it.type) }
        onOpLoc1(CHEST_SHUT) { openChest(it.loc) }
        onOpLoc1(CHEST_OPEN) { searchChest() }
    }

    private fun locType(name: String): ObjectServerType =
        ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC)) ?: error("Missing loc: $name")

    private fun ProtectedAccess.kardia(): Npc? =
        npcRepo.findAll(ZoneKey.from(coords), 1).firstOrNull {
            it.isType(KARDIA) || it.isType(UndergroundPassQuest.visibleTwin(KARDIA))
        }

    private suspend fun ProtectedAccess.pickUpCat(cat: Npc) {
        if (quest.stage(player) < STAGE_DWARVES) {
            mes("It's not very nice to squeeze a cat into a satchel.")
            mes("...if you don't need to.")
            return
        }
        if (inv.contains(CAT_ITEM)) {
            mes("It's not very nice to squeeze a cat into a satchel.")
            mes("... Two is just plain cruel!")
            return
        }
        if (inv.freeSpace() == 0) {
            mes("You don't have enough inventory space to hold that item.")
            return
        }
        soundSynth(SOUND_PICK)
        npcRepo.despawn(cat, CAT_RETURN_TICKS)
        invAdd(inv, CAT_ITEM)
        delay(1)
        anim(SEQ_SEARCH)
    }

    private suspend fun ProtectedAccess.lookThroughWindow() {
        arriveDelay()
        mes("Inside you see a witch, she appears to be looking for something.")
        if (player.gaveCat == 0) {
            kardia()?.say("Here kitty, kitty!")
        }
    }

    /** The cat is left by the door with a knock, and the player ducks out of sight. */
    private suspend fun ProtectedAccess.leaveCatAtDoor() {
        arriveDelay()
        if (player.gaveCat == 1) {
            mesbox("The witch is busy playing with her other cat...")
            return
        }
        mes("... You place the cat by the door.")
        anim(SEQ_PICKUP_FLOOR)
        delay(2)
        if (invDel(inv, CAT_ITEM).failure) {
            return
        }
        mes("You knock the door and hide around the corner.")
        playerWalk(UpassCoords.WITCH_HIDING_SPOT)
        delay(HIDE_TICKS)
        UndergroundPassQuest.setVarBit(player, "varbit.upass_gavecat", 1)
        mes("The witch takes the cat inside.")
    }

    private suspend fun ProtectedAccess.openDoor(door: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        val leaving = coords.x < door.coords.x
        if (player.gaveCat == 1 || leaving) {
            mes("You open the door...")
            with(passages) { walkThrough(door, type) }
            if (!leaving) {
                delay(1)
                mes("The Witch is busy talking to the cat.")
            }
            return
        }
        mes("You reach to open the door...")
        if (kardia() == null) {
            return
        }
        mesbox("<col=8B0000>Get away... Far away from here!</col>")
        takeInstantHit(HitType.Typeless, stat("stat.hitpoints") * 25 / 100 + 1)
    }

    private suspend fun ProtectedAccess.openChest(chest: BoundLocInfo) {
        arriveDelay()
        anim(SEQ_OPEN_CHEST)
        soundSynth(SOUND_CHEST_OPEN)
        delay(1)
        searchChest()
        locRepo.change(chest, openChestType, CHEST_TICKS)
    }

    private suspend fun ProtectedAccess.searchChest() {
        mesbox("You search the chest...")
        delay(2)
        val stage = quest.stage(player)
        if (stage >= STAGE_DOLL) {
            mesbox("... but the doll's gone.")
            if (inv.contains(DOLL)) {
                startDialogue { chatPlayer(neutral, "This doll's giving me the creeps. I don't want another one!") }
            } else {
                startDialogue {
                    chatPlayer(angry, "Oh great, I've lost the doll of Iban.")
                    chatPlayer(neutral, "Now, who in this blasted cave might have found it for me?")
                }
            }
            return
        }
        if (stage != STAGE_DWARVES) {
            mesbox("The chest is full of the witch's potions and papers. Nothing in it means anything to you.")
            return
        }
        mesbox("Inside you find a book, a wooden doll and two potions.")
        quest.advanceTo(this, STAGE_DOLL)
        player.dollTaken = true
        for (item in CHEST_CONTENTS) {
            invAddOrDrop(objRepo, item)
        }
    }

    private companion object {
        const val WINDOW = "loc.upass_witchwindow"
        const val DOOR = "loc.cavewitch_door"
        const val CHEST_SHUT = "loc.cavewitchchest"
        const val CHEST_OPEN = "loc.cavewitchchestopen"
        const val CAT_ITEM = "obj.cavewitchcat"
        const val CHEST_TICKS = 20
        const val CAT_RETURN_TICKS = 100
        const val HIDE_TICKS = 5

        val CHEST_CONTENTS = listOf(DOLL, IBAN_BOOK, "obj.3dose2attack", "obj.3dosestatrestore")
    }
}
