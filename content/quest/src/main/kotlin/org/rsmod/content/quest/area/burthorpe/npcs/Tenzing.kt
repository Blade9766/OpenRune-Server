package org.rsmod.content.quest.area.burthorpe.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.BREAD
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.CLIMBING_BOOTS
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.COINS
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.SECRET_WAY_MAP
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.SPIKED_BOOTS
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.SUPPLY_COUNT
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.TENZING
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.TROUT
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpMapDrawn
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpMapHanded
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpPathScouted
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpTenzingAsked
import org.rsmod.content.quest.area.burthorpe.deathplateau.owns
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Tenzing the Sherpa, in his hut on the mountain path west of Burthorpe. During Death Plateau
 * he trades the secret way for his winter supplies and his boots back with new spikes; after it
 * he sells climbing boots, which Troll Stronghold needs for the rocks.
 */
class Tenzing
@Inject
constructor(private val quest: DeathPlateauQuest, private val objRepo: ObjRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(TENZING) { startDialogue(it.npc) { tenzing() } }
    }

    private suspend fun Dialogue.tenzing() {
        when {
            quest.isComplete(player) -> sherpa()
            player.dpMapDrawn -> mapDrawn()
            player.dpTenzingAsked -> waitingForSupplies()
            else -> firstMeeting()
        }
    }

    private suspend fun Dialogue.firstMeeting() {
        chatPlayer(happy, "Hello!")
        chatNpc(neutral, "Hello. How can I help?")
        if (!quest.isStarted(player)) {
            return
        }
        chatPlayer(
            neutral,
            "I'm helping the Imperial Guard. They need to find a way to sneak up Death Plateau to " +
                "destroy the troll camp! Saba seemed to think you'd be able to help.",
        )
        chatNpc(quiz, "Ah...Saba is still alive and kicking?")
        chatPlayer(neutral, "Yeh, he seemed very bitter.")
        chatNpc(laugh, "That's Saba alright!")
        chatNpc(
            neutral,
            "I do know of a secret way up to Death Plateau, the Imperial Guard would be able to " +
                "use it at night and not be seen until it was too late!",
        )
        chatNpc(neutral, "I'd be happy to show you it if you do something for me first.")
        chatPlayer(neutral, "Name it.")
        chatNpc(
            neutral,
            "I don't get into town much and I'm getting low on supplies. I need ten loaves of " +
                "bread and ten cooked trout, that should see me through the winter.",
        )
        chatPlayer(quiz, "Anything else?")
        chatNpc(
            neutral,
            "Yes. My climbing boots need to have new spikes, so can you take them to Dunstan in " +
                "Burthorpe? He always puts my spikes on for me.",
        )
        val accept =
            choice2("OK, I'll get those for you.", true, "I'll find the secret way for myself.", false)
        if (!accept) {
            chatPlayer(neutral, "I'll find the secret way for myself.")
            chatNpc(bored, "Hmph.")
            return
        }
        chatPlayer(happy, "OK, I'll get those for you.")
        chatNpc(happy, "Thank you traveller!")
        player.dpTenzingAsked = true
        access.invAddOrDrop(objRepo, CLIMBING_BOOTS)
        access.mes("Tenzing has given you his climbing boots.")
        objbox(CLIMBING_BOOTS, "Tenzing has given you his climbing boots.")
    }

    private suspend fun Dialogue.waitingForSupplies() {
        chatPlayer(happy, "Hello!")
        val hasSpiked = SPIKED_BOOTS in player.inv
        if (!hasSpiked && !access.owns(CLIMBING_BOOTS) && !access.owns(SPIKED_BOOTS)) {
            chatNpc(quiz, "Have you brought me the items I asked for?")
            chatPlayer(sad, "I've lost the climbing boots.")
            chatNpc(angry, "These are expensive, do not lose another pair!")
            access.invAddOrDrop(objRepo, CLIMBING_BOOTS)
            objbox(CLIMBING_BOOTS, "Tenzing has given you some climbing boots.")
            return
        }
        if (!hasSpiked && CLIMBING_BOOTS in player.inv) {
            chatNpc(quiz, "Has Dunstan added spikes to my climbing boots yet?")
            chatPlayer(neutral, "No, not yet.")
            chatNpc(
                neutral,
                "Well, when he has, bring the boots to me with ten loaves of bread and ten cooked " +
                    "trout. I have to prepare for the winter, after all.",
            )
            return
        }
        chatNpc(quiz, "Have you brought me the items I asked for?")
        val missing = buildList {
            if (!hasSpiked) add("Spiked boots.")
            if (access.invTotal(access.inv, BREAD) < SUPPLY_COUNT) add("Ten loaves of bread.")
            if (access.invTotal(access.inv, TROUT) < SUPPLY_COUNT) add("Ten cooked trout.")
        }
        if (missing.isNotEmpty()) {
            chatPlayer(sad, "I don't have the: " + missing.joinToString(" "))
            return
        }
        access.invDel(access.inv, SPIKED_BOOTS)
        access.invDel(access.inv, BREAD, SUPPLY_COUNT)
        access.invDel(access.inv, TROUT, SUPPLY_COUNT)
        objbox(SPIKED_BOOTS, "You give Tenzing the Spiked boots.")
        doubleobjbox(TROUT, BREAD, "You give Tenzing the loaves of bread and the cooked trout.")
        chatNpc(happy, "Thank you very much traveller. I'm now ready for the winter!")
        chatPlayer(quiz, "You said you would show me the secret way to Death Plateau?")
        chatNpc(
            happy,
            "Yes, of course! I drew up a map in case I ever needed to use it again.",
        )
        player.dpMapDrawn = true
        access.invAddOrDrop(objRepo, SECRET_WAY_MAP)
        objbox(SECRET_WAY_MAP, "Tenzing has given you a map of the secret way!")
        access.mes("Tenzing has given you a map of the secret way")
        chatNpc(
            neutral,
            "I don't think the Trolls have found the secret way yet, if they had I would've been " +
                "attacked by now.",
        )
        chatPlayer(
            neutral,
            "OK thanks but I think I'd better check the path. I don't want to send the Imperial " +
                "Guards to their death!",
        )
        chatNpc(happy, "You are wise for one so young.")
    }

    private suspend fun Dialogue.mapDrawn() {
        chatPlayer(happy, "Hello Tenzing!")
        chatNpc(neutral, "Hello again traveller. What can I do for you?")
        if (!player.dpMapHanded && !access.owns(SECRET_WAY_MAP)) {
            chatPlayer(sad, "I've lost the secret way map.")
            chatNpc(neutral, "Never mind. I'll quickly draw you another one.")
            access.invAddOrDrop(objRepo, SECRET_WAY_MAP)
            objbox(SECRET_WAY_MAP, "Tenzing has given you a map of the secret way!")
            access.mes("Tenzing has given you a map of the secret way")
            return
        }
        if (choice2("I'm lost!", true, "Nothing, thanks.", false)) {
            chatPlayer(confused, "I'm lost!")
            chatNpc(neutral, "To get back to Burthorpe follow the path going east.")
            if (!player.dpPathScouted) {
                chatNpc(
                    neutral,
                    "I thought you were going to investigate the secret way to Death Plateau? " +
                        "Use the back door to my hut, hop over the stile and follow that path.",
                )
            }
            chatPlayer(happy, "Oh yes, of course! Thanks!")
        } else {
            chatPlayer(neutral, "Nothing, thanks.")
            chatNpc(neutral, "Go in peace traveller.")
        }
    }

    /* After Death Plateau */

    private suspend fun Dialogue.sherpa() {
        chatPlayer(happy, "Hello Tenzing!")
        chatNpc(neutral, "Hello again traveller. What can I do for you?")
        while (true) {
            when (
                choice5(
                    "Can I buy some Climbing boots?",
                    1,
                    "What does a Sherpa do?",
                    2,
                    "How did you find out about the secret way?",
                    3,
                    "Nice place you have here.",
                    4,
                    "Nothing, thanks!",
                    5,
                )
            ) {
                1 -> buyBoots()
                2 -> {
                    chatPlayer(quiz, "What does a Sherpa do?")
                    chatNpc(
                        neutral,
                        "We are expert guides that take adventurers, such as yourself, on " +
                            "mountaineering expeditions.",
                    )
                }
                3 -> {
                    chatPlayer(quiz, "How did you find out about the secret way?")
                    chatNpc(
                        neutral,
                        "I used to take adventurers up Death Plateau and further north before the " +
                            "trolls came. I know these mountains well.",
                    )
                }
                4 -> {
                    chatPlayer(happy, "Nice place you have here.")
                    chatNpc(
                        happy,
                        "Thanks, I built it myself! I'm usually self sufficient but I can't earn " +
                            "any money with the trolls camped on Death Plateau.",
                    )
                    chatPlayer(
                        happy,
                        "Now that the Imperial Guard know about the secret way they will be able " +
                            "to destroy the trolls!",
                    )
                    chatNpc(neutral, "Let us hope so traveller!")
                }
                else -> {
                    chatPlayer(neutral, "Nothing, thanks!")
                    return
                }
            }
            chatNpc(neutral, "Was there anything else?")
        }
    }

    private suspend fun Dialogue.buyBoots() {
        chatPlayer(quiz, "Can I buy some Climbing boots?")
        chatNpc(neutral, "Sure, I'll sell you some in your size for 12 gold.")
        if (!choice2("OK, sounds good.", true, "No, thanks.", false)) {
            chatPlayer(neutral, "No, thanks.")
            return
        }
        chatPlayer(happy, "OK, sounds good.")
        if (access.invDel(access.inv, COINS, BOOTS_PRICE).failure) {
            chatPlayer(sad, "I don't have the money on me.")
            return
        }
        access.invAddOrDrop(objRepo, CLIMBING_BOOTS)
        objbox(CLIMBING_BOOTS, "Tenzing has given you some Climbing boots.")
    }

    private companion object {
        const val BOOTS_PRICE = 12
    }
}
