package org.rsmod.content.quest.area.desert.touristtrap.npcs

import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.desert.touristtrap.MiningCampSecurity
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapCoords
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.BEDABIN_KEY
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.CAPTAIN_SIAD
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.CELL_DOOR_KEY
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.METAL_KEY
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_ESCAPED
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_GIVEN_BEDABIN_KEY
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.STAGE_RETRIEVED_PLANS
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.TECHNICAL_PLANS
import org.rsmod.content.quest.area.desert.touristtrap.TouristTrapQuest.Companion.WROUGHT_IRON_KEY
import org.rsmod.content.quest.area.desert.touristtrap.ttSeenSailingBooks
import org.rsmod.content.quest.area.desert.touristtrap.ttSiadDistracted
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Captain Siad, at his desk upstairs in the camp's northern building, and the chest of plans he
 * keeps an eye on.
 *
 * He cannot be talked out of watching the chest, only distracted: flattering his sailing (once
 * the bookcase has given it away) always works, a dragon at the window or a cry of fire only
 * sometimes. The distraction lasts until the player next reaches for the chest or the desk.
 */
class CaptainSiad
@Inject
constructor(
    private val quest: TouristTrapQuest,
    private val security: MiningCampSecurity,
    private val npcSearch: NpcSearch,
    private val locRepo: LocRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(CAPTAIN_SIAD) {
            mes("The captain looks up from his work as you address him.")
            delay(2)
            startDialogue(it.npc) { siad() }
        }
        onOpLoc1(BOOKCASE) { mesbox("The captain seems to collect lots of books!") }
        onOpLoc2(BOOKCASE) {
            player.ttSeenSailingBooks = true
            objbox(SAILING_BOOK, "You notice several books on the subject of sailing.")
        }
        onOpLoc1(CHEST) { openChest(it.loc) }
        onOpLocU(CHEST, BEDABIN_KEY) { openChest(it.loc) }
        onOpLoc2(TABLE) { searchTable(it.loc) }
    }

    private suspend fun Dialogue.siad() {
        if (ownsPlans(access)) {
            chatNpc(angry, "I don't have time to talk to you. Move along please!")
            return
        }
        chatNpc(angry, "What are you doing in here?")
        val option =
            choice5(
                "I wanted to have a chat?",
                1,
                "What's it got to do with you?",
                2,
                "Prepare to die!",
                3,
                "All the slaves have broken free!",
                4,
                "Fire! Fire!",
                5,
            )
        when (option) {
            1 -> wantedAChat()
            2 -> whatsItGotToDoWithYou()
            3 -> prepareToDie()
            4 -> slavesBrokenFree()
            else -> fire()
        }
    }

    private suspend fun Dialogue.wantedAChat() {
        chatPlayer(happy, "I wanted to have a chat?")
        chatNpc(angry, "You don't belong in here, get out!")
        val option =
            choice5(
                "But I just need two minutes of your time?",
                1,
                "Prepare to die!",
                2,
                "All the slaves have broken free!",
                3,
                "Fire! Fire!",
                4,
                "You seem to have a lot of books!",
                5,
            )
        when (option) {
            1 -> twoMinutes()
            2 -> prepareToDie()
            3 -> slavesBrokenFree()
            4 -> fire()
            else -> lotOfBooks()
        }
    }

    private suspend fun Dialogue.twoMinutes() {
        chatPlayer(neutral, "But I just need two minutes of your time?")
        chatNpc(confused, "Well, okay, but very quickly. I am a very busy person you know!")
        if (choice2("Well, er... erm, I err....", true, "Oh my, a dragon just flew straight past your window!", false)) {
            stammer()
        } else {
            dragon()
        }
    }

    private suspend fun Dialogue.lotOfBooks() {
        chatPlayer(happy, "You seem to have a lot of books!")
        chatNpc(angry, "Yes, I do. Now please get to the point?")
        val option =
            if (player.ttSeenSailingBooks) {
                choice3(
                    "How long have you been interested in books?",
                    1,
                    "I could get you some books!",
                    2,
                    "So, you're interested in sailing?",
                    3,
                )
            } else {
                choice2("How long have you been interested in books?", 1, "I could get you some books!", 2)
            }
        when (option) {
            1 -> {
                chatPlayer(quiz, "How long have you been interested in books?")
                chatNpc(angry, "Long enough to know when someone is stalling!")
                chatNpc(angry, "Please state your business or get out!")
            }
            2 -> {
                chatPlayer(happy, "I could get you some books!")
                chatNpc(happy, "Oh, really!")
                chatNpc(angry, "Sorry, not interested!")
            }
            else -> sailing()
        }
    }

    private suspend fun Dialogue.sailing() {
        chatPlayer(quiz, "So, you're interested in sailing?")
        chatNpc(happy, "Well, yes actually... It's been a passion of mine for some years...")
        if (!choice2("I could tell by the cut of your jib.", true, "Not much sailing to be done around here though?", false)) {
            chatPlayer(quiz, "Not much sailing to be done around here though?")
            chatNpc(
                angry,
                "Well of course there isn't, we're surrounded by desert. Now, why are you here " +
                    "exactly?",
            )
            if (choice2("Oh my, a dragon just flew straight past your window!", true, "Well, er... erm, I err....", false)) {
                dragon()
            } else {
                stammer()
            }
            return
        }
        chatPlayer(happy, "I could tell by the cut of your jib.")
        chatNpc(happy, "Oh yes? Really?")
        mesbox("-- The Captain looks flattered. --")
        chatNpc(happy, "Well, I was quite a catch in my day you know!")
        mesbox(
            "The Captain starts rambling on about his days as a salty sea dog. He looks quite " +
                "distracted...",
        )
        player.ttSiadDistracted = true
    }

    private suspend fun Dialogue.dragon() {
        chatPlayer(shocked, "Oh my, a dragon just flew straight past your window!")
        if (access.random.of(DRAGON_ODDS) == 0) {
            player.ttSiadDistracted = true
            mesbox(
                "The captain seems distracted with what you just said. The captain looks out of " +
                    "the window for the dragon.",
            )
            return
        }
        val who = if (access.isBodyTypeB()) "lady" else "man"
        chatNpc(
            angry,
            "Really! Where? I don't see any dragons young $who? Now, please get out of my " +
                "office, I have work to do.",
        )
        access.mes("The Captain goes back to his work.")
    }

    private suspend fun Dialogue.stammer() {
        chatPlayer(worried, "Well, er... erm, I err....")
        chatNpc(angry, "Come on, spit it out! Right that's it! Guards!")
        callTheGuards()
    }

    private suspend fun Dialogue.whatsItGotToDoWithYou() {
        chatPlayer(angry, "What's it got to do with you?")
        chatNpc(
            angry,
            "This happens to be my office. Now explain yourself before I run you through!",
        )
        explainYourself()
    }

    private suspend fun Dialogue.explainYourself() {
        val hasKey = quest.stage(player) >= STAGE_GIVEN_BEDABIN_KEY
        val option =
            if (hasKey) {
                choice3(
                    "I'm here to take your plans, hand them over now or I'll kill you!",
                    1,
                    "The guard downstairs said you were lonely.",
                    2,
                    "I need to service your chest.",
                    3,
                )
            } else {
                choice2("The guard downstairs said you were lonely.", 2, "I need to service your chest.", 3)
            }
        when (option) {
            1 -> takePlans()
            2 -> lonely()
            else -> serviceChest()
        }
    }

    private suspend fun Dialogue.takePlans() {
        chatPlayer(angry, "I'm here to take your plans, hand them over now or I'll kill you!")
        chatNpcSpecific("Guard", GUARD_HEAD, angry, "Guards! Guards!")
        callTheGuards()
    }

    private suspend fun Dialogue.lonely() {
        chatPlayer(shifty, "The guard downstairs said you were lonely.")
        chatNpc(quiz, "Well, I most certainly am not lonely!")
        chatNpc(angry, "I'm an incredibly busy man you know! Now, get to the point, what do you want?")
        if (choice2("Well, er... erm, I err....", true, "I need to service your chest.", false)) {
            stammer()
        } else {
            serviceChest()
        }
    }

    private suspend fun Dialogue.serviceChest() {
        chatPlayer(shifty, "I need to service your chest.")
        chatNpc(quiz, "You need to what?")
        chatPlayer(shifty, "I need to service your chest?")
        chatNpc(angry, "There's nothing wrong with the chest, it's fine, now get out!")
        if (quest.stage(player) >= STAGE_GIVEN_BEDABIN_KEY) {
            if (choice2("I'm here to take your plans, hand them over now or I'll kill you!", true, "Fire! Fire!", false)) {
                takePlans()
            } else {
                fire()
            }
            return
        }
        if (choice2("Fire! Fire!", true, "Oh my, a dragon just flew straight past your window!", false)) {
            fire()
        } else {
            dragon()
        }
    }

    private suspend fun Dialogue.prepareToDie() {
        chatPlayer(angry, "Prepare to die!")
        chatNpc(angry, "I'll teach you a lesson!")
    }

    private suspend fun Dialogue.slavesBrokenFree() {
        chatPlayer(shocked, "All the slaves have broken free!")
        chatNpc(
            angry,
            "Don't talk rubbish, the warning siren isn't sounding. Now state your business before " +
                "I have you thrown out.",
        )
        explainYourself()
    }

    private suspend fun Dialogue.fire() {
        chatPlayer(shocked, "Fire! Fire!")
        if (access.random.of(FIRE_ODDS) == 0) {
            player.ttSiadDistracted = true
            mesbox(
                "The captain seems distracted with what you just said. The captain looks out of " +
                    "the window to see if there is a fire.",
            )
            return
        }
        chatNpc(
            angry,
            "Where's the fire? I don't see any fire? Stop messing me around and state your " +
                "business!",
        )
        if (!choice2("It's down in the lower mines, sound the alarm!", true, "Oh yes, you're right, they must have put it out!", false)) {
            chatPlayer(neutral, "Oh yes, you're right, they must have put it out!")
            chatNpc(
                angry,
                "Good, now perhaps you can leave me in peace? After all I do have some work to do.",
            )
            if (choice2("Er, yes okay then.", true, "Well, er... erm, I err....", false)) {
                chatPlayer(neutral, "Er, yes okay then.")
                chatNpc(angry, "Good! Please remove yourself from my office.")
            } else {
                stammer()
            }
            return
        }
        chatPlayer(shocked, "It's down in the lower mines, sound the alarm!")
        chatNpc(
            angry,
            "You go and sound the alarm, I can't see anything wrong with the mine. Have you seen " +
                "the fire yourself?",
        )
        if (!choice2("Yes actually!", true, "Er, no, one of the slaves told me.", false)) {
            chatPlayer(shifty, "Er, no, one of the slaves told me.")
            chatNpc(
                angry,
                "Well... you can't believe them, they're all a bunch of convicts. Anyway, it " +
                    "doesn't look as if there is a fire down there. So I'm going to get on with " +
                    "my work. Please remove yourself from my office.",
            )
            return
        }
        chatPlayer(shifty, "Yes actually!")
        chatNpc(angry, "Well, why didn't you raise the alarm?")
        if (choice2("I don't know where the alarm is.", true, "I was so concerned for your safety that I rushed to save you.", false)) {
            chatPlayer(neutral, "I don't know where the alarm is.")
            chatNpc(
                angry,
                "That's the most ridiculous thing I've heard. Who are you? Where do you come " +
                    "from? It doesn't matter... Guards! Show this person out!",
            )
            callTheGuards()
            return
        }
        chatPlayer(happy, "I was so concerned for your safety that I rushed to save you.")
        chatNpc(
            angry,
            "Well, that's very good of you. But as you can see, I am very fine and well thanks! " +
                "Now, please leave so that I can get back to my work.",
        )
        access.mes("The Captain goes back to his desk.")
    }

    private suspend fun Dialogue.callTheGuards() {
        val guard = security.guardNear(player.coords, GUARD_REACH)
        chatNpcSpecific("Guard", GUARD_HEAD, angry, "Hey, you there!")
        chatNpcSpecific("Guard", GUARD_HEAD, angry, "You're in some serious trouble now!")
        with(security) { access.throwInCell(guard) }
    }

    private suspend fun ProtectedAccess.openChest(chest: BoundLocInfo) {
        arriveDelay()
        if (!player.ttSiadDistracted) {
            val siad = siadNear() ?: run {
                mes("This chest needs a key to unlock it.")
                return
            }
            mes("The captain spots you before you manage to open the chest...")
            delay(2)
            startDialogue(siad) { siad() }
            return
        }
        player.ttSiadDistracted = false
        if (BEDABIN_KEY !in inv) {
            mes("This chest needs a key to unlock it.")
            return
        }
        if (ownsPlans(this)) {
            mes("The chest is empty.")
            return
        }
        anim(CHEST_SEQ)
        soundSynth(CHEST_SOUND)
        invAdd(inv, TECHNICAL_PLANS)
        quest.advanceFrom(this, STAGE_GIVEN_BEDABIN_KEY, STAGE_RETRIEVED_PLANS)
        objbox(
            TECHNICAL_PLANS,
            "While the Captain's distracted, you quickly unlock the chest with the Bedabins' copy " +
                "of the key. You take out the plans.",
        )
        // Swapping the loc ends this script, so the open lid is the last thing it does.
        locRepo.change(chest, OPEN_CHEST, CHEST_OPEN_TICKS)
    }

    private suspend fun ProtectedAccess.searchTable(table: BoundLocInfo) {
        arriveDelay()
        anim(SEARCH_SEQ)
        when (table.coords) {
            TouristTrapCoords.BOWL_TABLE -> {
                if (CELL_DOOR_KEY in inv) {
                    mes("You search the table but find nothing of interest.")
                    return
                }
                invAdd(inv, CELL_DOOR_KEY)
                objbox(CELL_DOOR_KEY, "You find a cell door key.")
            }
            TouristTrapCoords.SIAD_DESK -> searchDesk()
            else -> {
                if (METAL_KEY in inv || !quest.isStarted(player)) {
                    mes("You search the table but find nothing of interest.")
                    return
                }
                invAdd(inv, METAL_KEY)
                objbox(METAL_KEY, "You find the key to the main gate.")
            }
        }
    }

    /** Only with the captain looking the other way; every key to the camp is kept in it. */
    private suspend fun ProtectedAccess.searchDesk() {
        if (!player.ttSiadDistracted) {
            val siad = siadNear() ?: return
            mes("The captain spots you before you manage to search the desk...")
            delay(2)
            startDialogue(siad) { siad() }
            return
        }
        player.ttSiadDistracted = false
        mes("You search the captains desk while he's not looking..")
        var found = false
        if (CELL_DOOR_KEY !in inv) {
            invAdd(inv, CELL_DOOR_KEY)
            objbox(CELL_DOOR_KEY, "You find a cell door key.")
            found = true
        }
        if (METAL_KEY !in inv) {
            invAdd(inv, METAL_KEY)
            objbox(METAL_KEY, "You find the key to the main gate.")
            found = true
        }
        if (quest.stage(player) >= STAGE_ESCAPED && WROUGHT_IRON_KEY !in inv) {
            invAdd(inv, WROUGHT_IRON_KEY)
            objbox(WROUGHT_IRON_KEY, "You find a wrought iron key.")
            found = true
        }
        if (!found) {
            mes("...but you find nothing of interest.")
        }
    }

    private fun ProtectedAccess.siadNear(): Npc? =
        npcSearch.find(coords, CAPTAIN_SIAD, SIAD_SIGHT, HuntVis.Off)

    private fun ownsPlans(access: ProtectedAccess): Boolean =
        TECHNICAL_PLANS in access.inv || TECHNICAL_PLANS in access.bank

    private companion object {
        const val BOOKCASE = "loc.capt_siad_bookcase"
        const val CHEST = "loc.captain_siads_chest_closed"
        const val OPEN_CHEST = "loc.captain_siads_chest_open"
        const val TABLE = "loc.tourtrap_qip_desert_table"
        const val SAILING_BOOK = "obj.tourtrap_qip_sailing_book"
        const val GUARD_HEAD = "npc.tourtrap_qip_desert_mining_guard_1"

        const val CHEST_SEQ = "seq.human_pickuptable"
        const val SEARCH_SEQ = "seq.human_pickuptable"
        const val CHEST_SOUND = "synth.chest_open"
        const val CHEST_OPEN_TICKS = 5

        /** One chance in this many that the captain looks out of the window. */
        const val DRAGON_ODDS = 10
        const val FIRE_ODDS = 4

        const val SIAD_SIGHT = 7
        const val GUARD_REACH = 10
    }
}
