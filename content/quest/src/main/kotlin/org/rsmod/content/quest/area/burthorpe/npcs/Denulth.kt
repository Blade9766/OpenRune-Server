package org.rsmod.content.quest.area.burthorpe.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.stat.baseAgilityLvl
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.CERTIFICATE
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.COMBINATION
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.DENULTH
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.SECRET_WAY_MAP
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.STAGE_ROOM_OPEN
import org.rsmod.content.quest.area.burthorpe.deathplateau.DeathPlateauQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpCertificateHanded
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpCertificateIssued
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpCombinationHanded
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpDunstanAsked
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpMapHanded
import org.rsmod.content.quest.area.burthorpe.deathplateau.dpPathScouted
import org.rsmod.content.quest.area.burthorpe.deathplateau.owns
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.AGILITY_REQ
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.STAGE_GODRIC_FREED
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.STAGE_PRISON_OPEN
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Denulth, commander of the Imperial Guard, in his tent in Burthorpe. He gives out both troll
 * quests: Death Plateau, and once that is done, the rescue of Godric in Troll Stronghold.
 */
class Denulth
@Inject
constructor(
    private val deathPlateau: DeathPlateauQuest,
    private val trollStronghold: TrollStrongholdQuest,
    private val objRepo: ObjRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(DENULTH) { startDialogue(it.npc) { denulth() } }
    }

    private suspend fun Dialogue.denulth() {
        when {
            !deathPlateau.isComplete(player) && !deathPlateau.isStarted(player) -> newcomer()
            !deathPlateau.isComplete(player) -> deathPlateauInProgress()
            trollStronghold.isComplete(player) -> afterTrollStronghold()
            trollStronghold.isStarted(player) -> rescueInProgress()
            else -> trollStrongholdOffer()
        }
    }

    /* Death Plateau */

    private suspend fun Dialogue.newcomer() {
        chatPlayer(happy, "Hello!")
        chatNpc(neutral, "Hello citizen, how can I help?")
        while (true) {
            when (
                choice3(
                    "Do you have any quests for me?",
                    1,
                    "What is this place?",
                    2,
                    "You can't, thanks.",
                    3,
                )
            ) {
                1 -> if (offerDeathPlateau()) return
                2 -> whatIsThisPlace()
                else -> {
                    chatPlayer(neutral, "You can't, thanks.")
                    return
                }
            }
            chatNpc(neutral, "Can I assist you with anything else?")
        }
    }

    /** @return `true` once the quest has been taken on and the conversation is over. */
    private suspend fun Dialogue.offerDeathPlateau(): Boolean {
        chatPlayer(quiz, "Do you have any quests for me?")
        chatNpc(sad, "I don't know if you can help us!")
        chatNpc(
            angry,
            "The trolls have taken up camp on Death Plateau! They are using it to launch raids at " +
                "night on the village. We have tried to attack the camp but the main path is " +
                "heavily guarded!",
        )
        chatPlayer(quiz, "Perhaps there is a way you can sneak up at night?")
        chatNpc(neutral, "If there is another way I do not know of it.")
        chatNpc(quiz, "Do you know of such a path?")
        val accept = choice2("Yes.", true, "No.", false, title = "Start the Death Plateau quest?")
        if (!accept) {
            chatPlayer(sad, "No, sorry.")
            chatNpc(neutral, "Never mind citizen.")
            return false
        }
        chatPlayer(neutral, "No but perhaps I could try and find one?")
        chatNpc(happy, "Citizen you would be well rewarded!")
        chatNpc(
            worried,
            "If you go up to Death Plateau be very careful as the trolls will attack you on sight!",
        )
        chatPlayer(neutral, "I'll be careful.")
        deathPlateau.advanceTo(access, STAGE_STARTED)
        chatNpc(neutral, "One other thing.")
        chatPlayer(quiz, "What's that?")
        chatNpc(neutral, "All of our equipment is kept in the castle on the hill.")
        chatNpc(
            angry,
            "The stupid guard that was on duty last night lost the combination to the lock! I " +
                "told the Prince that the Imperial Guard should've been in charge of security!",
        )
        chatPlayer(quiz, "No problem, what does the combination look like?")
        chatNpc(
            neutral,
            "The equipment room is unlocked when the stone balls are placed in the correct order " +
                "on the stone mechanism outside it. The right order is written on a piece of " +
                "paper the guard had.",
        )
        chatPlayer(confused, "A stone what...?!")
        chatNpc(
            bored,
            "Well citizen, the Prince is fond of puzzles. Why we couldn't just have a key is " +
                "beyond me!",
        )
        chatPlayer(happy, "I'll get on it right away!")
        return true
    }

    private suspend fun Dialogue.deathPlateauInProgress() {
        when {
            player.dpPathScouted || player.dpMapHanded || player.dpCombinationHanded -> {
                reportTheSecretWay()
                return
            }
            player.dpDunstanAsked && !player.dpCertificateIssued -> {
                signUpDunstansSon()
                return
            }
            player.dpCertificateIssued &&
                !player.dpCertificateHanded &&
                !access.owns(CERTIFICATE) -> {
                chatPlayer(happy, "Hello!")
                chatNpc(neutral, "Hello citizen, have you found the secret way up Death Plateau?")
                chatPlayer(sad, "I'm working on it but I've lost the certificate!")
                chatNpc(neutral, "No problem, I have a duplicate.")
                access.invAddOrDrop(objRepo, CERTIFICATE)
                objbox(CERTIFICATE, "Denulth has given you a certificate.")
                return
            }
        }
        chatPlayer(happy, "Hello!")
        chatNpc(neutral, "Hello citizen, is there anything you'd like to know?")
        while (true) {
            when (
                choice4(
                    "Can you remind me of the quest I am on?",
                    1,
                    "I thought the White Knights controlled Asgarnia?",
                    2,
                    "What is this place?",
                    3,
                    "That's all, thanks.",
                    4,
                )
            ) {
                1 -> remindMe()
                2 -> whiteKnights()
                3 -> whatIsThisPlace()
                else -> {
                    chatPlayer(neutral, "That's all, thanks.")
                    chatNpc(neutral, "God speed citizen.")
                    return
                }
            }
            chatNpc(neutral, "Is there anything else you would like to know?")
        }
    }

    private suspend fun Dialogue.remindMe() {
        chatPlayer(quiz, "Can you remind me of the quest I am on?")
        chatNpc(
            neutral,
            "You offered to see if you could find another way up Death Plateau. We could then " +
                "use it to sneak up and attack the trolls by night.",
        )
        chatPlayer(
            quiz,
            "Ah yes, and the guard had lost the combination to your equipment room in the " +
                "castle on the hill?",
        )
        chatNpc(
            neutral,
            "That's right citizen, you offered to recover the combination and unlock the door.",
        )
        if (deathPlateau.stage(player) >= STAGE_ROOM_OPEN) {
            chatPlayer(
                happy,
                "I've unlocked the equipment room so I just need to find an alternate route up " +
                    "Death Plateau!",
            )
            chatNpc(happy, "Good work citizen!")
        }
    }

    private suspend fun Dialogue.signUpDunstansSon() {
        chatPlayer(happy, "Hello!")
        chatNpc(neutral, "Hello citizen, have you found another way up Death Plateau?")
        chatPlayer(happy, "Yes there is another way up Death Plateau!")
        chatNpc(happy, "We are saved!")
        chatPlayer(neutral, "There's one thing...")
        chatNpc(quiz, "And what is that citizen?")
        chatPlayer(
            neutral,
            "There is a Sherpa who will only show me the secret way if I first get some spikes " +
                "for his climbing boots. The smith will only do this for me if you sign up his " +
                "son for the Imperial Guard!",
        )
        chatNpc(confused, "Hmm...this is very irregular.")
        chatPlayer(quiz, "Will you not do this?")
        chatNpc(
            neutral,
            "I have heard of Dunstan's son, he is a very promising young man. For the sake of " +
                "your mission we can make an exception!",
        )
        access.invAddOrDrop(objRepo, CERTIFICATE)
        player.dpCertificateIssued = true
        objbox(CERTIFICATE, "Denulth has given you a certificate.")
        chatNpc(
            neutral,
            "This certificate proves that we have accepted Dunstan's son for training in the " +
                "Imperial Guard!",
        )
        chatPlayer(happy, "Thank you Denulth, I shall be back shortly!")
    }

    /** Handing over the secret way map and the combination, in either order, then the reward. */
    private suspend fun Dialogue.reportTheSecretWay() {
        chatPlayer(happy, "Hello!")
        if (player.dpMapHanded) {
            chatNpc(neutral, "Hello citizen, I have the map of the secret way you gave me earlier.")
        } else {
            chatNpc(neutral, "Hello citizen, have you found the secret way up Death Plateau?")
            chatPlayer(
                happy,
                "Yes! There is a path that runs from a Sherpa's hut around the back of Death " +
                    "Plateau. The trolls haven't found it yet. The Sherpa made a map I can give " +
                    "you.",
            )
            if (player.dpPathScouted && access.invDel(access.inv, SECRET_WAY_MAP).success) {
                player.dpMapHanded = true
                objbox(SECRET_WAY_MAP, "You give Denulth the map of the secret way.")
                access.mes("You give Denulth the map of the secret way.")
                chatNpc(happy, "Excellent, this looks perfect. They will never see us coming.")
            } else {
                chatPlayer(sad, "I don't have the map on me.")
            }
        }
        if (player.dpCombinationHanded) {
            chatNpc(neutral, "I have the combination to the equipment room that you recovered for me.")
        } else {
            chatNpc(quiz, "Have you managed to open the equipment room?")
            if (deathPlateau.stage(player) < STAGE_ROOM_OPEN) {
                chatPlayer(sad, "Not yet.")
                return
            }
            if (access.invDel(access.inv, COMBINATION).failure) {
                chatPlayer(sad, "I have opened the door but I don't have the combination on me.")
                return
            }
            player.dpCombinationHanded = true
            chatPlayer(happy, "Yes! The door is open and here is the combination.")
            objbox(COMBINATION, "You give Denulth the combination to the equipment room.")
            access.mes("You give Denulth the combination to the equipment room.")
        }
        if (!player.dpMapHanded || !player.dpCombinationHanded) {
            return
        }
        chatNpc(happy, "Well done citizen! We will reward you by training you in attack!")
        chatNpc(
            happy,
            "I shall present you with some steel fighting claws. In addition I shall show you " +
                "the knowledge of creating the fighting claws for yourself.",
        )
        chatNpc(happy, "You are now an honorary member of the Imperial Guard!")
        deathPlateau.quest.completeQuest(access)
    }

    /* Troll Stronghold */

    private suspend fun Dialogue.trollStrongholdOffer() {
        chatPlayer(happy, "Hello!")
        chatNpc(happy, "Welcome back friend!")
        when (
            choice3(
                "How goes your fight with the trolls?",
                1,
                "I thought the White Knights controlled Asgarnia?",
                2,
                "See you about Denulth!",
                3,
            )
        ) {
            1 -> offerTrollStronghold()
            2 -> whiteKnights()
            else -> seeYouAbout()
        }
    }

    private suspend fun Dialogue.offerTrollStronghold() {
        chatPlayer(quiz, "How goes your fight with the trolls?")
        chatNpc(
            sad,
            "I'm afraid I have bad news. We made our attack as planned, but we met unexpected " +
                "resistance.",
        )
        chatPlayer(quiz, "What happened?")
        chatNpc(
            sad,
            "We were ambushed by trolls coming from the north. They captured Dunstan's son, " +
                "Godric, who we enlisted at your request; we tried to follow but we were " +
                "repelled at the foot of their stronghold.",
        )
        if (player.baseAgilityLvl < AGILITY_REQ || player.combatLevel < RECOMMENDED_COMBAT) {
            mesbox(
                "Before starting this quest, be aware that one or more of your skill levels are " +
                    "lower than what is required to fully complete it. Your combat level is " +
                    "also lower than the recommended level of 50.",
            )
        }
        val accept = choice2("Yes.", true, "No.", false, title = "Start the Troll Stronghold quest?")
        if (!accept) {
            chatPlayer(sad, "I'm sorry to hear that.")
            return
        }
        chatPlayer(quiz, "Is there anything I can do to help?")
        chatNpc(
            worried,
            "The way to the stronghold is treacherous, friend. Even if you manage to climb your " +
                "way up, there will be many trolls defending the stronghold.",
        )
        chatPlayer(angry, "I'll get Godric back!")
        trollStronghold.advanceTo(access, TrollStrongholdQuest.STAGE_STARTED)
        chatNpc(
            happy,
            "God speed friend! I would send some of my men with you, but none of them are brave " +
                "enough to follow.",
        )
    }

    private suspend fun Dialogue.rescueInProgress() {
        val stage = trollStronghold.stage(player)
        when {
            stage >= STAGE_GODRIC_FREED -> {
                chatPlayer(happy, "I have freed Godric!")
                chatNpc(happy, "Oh, what great news! You should hurry to tell Dunstan, he will be overjoyed!")
            }
            stage >= STAGE_PRISON_OPEN -> {
                chatPlayer(happy, "Hello!")
                chatNpc(happy, "Welcome back friend!")
                chatPlayer(neutral, "I've found my way into the prison.")
                chatNpc(quiz, "...and?")
                chatPlayer(neutral, "That's all.")
                chatNpc(worried, "Hurry, friend. Find a way to free Godric!")
            }
            else -> {
                chatNpc(quiz, "How are you getting on with rescuing Godric?")
                chatPlayer(sad, "I haven't found a way to climb up yet.")
                chatNpc(worried, "Hurry, friend! Who knows what they'll do with Godric?")
            }
        }
    }

    private suspend fun Dialogue.afterTrollStronghold() {
        chatPlayer(happy, "Hello!")
        chatNpc(happy, "Welcome back friend!")
        while (true) {
            when (
                choice3(
                    "How goes your fight with the trolls?",
                    1,
                    "I thought the White Knights controlled Asgarnia?",
                    2,
                    "See you about Denulth!",
                    3,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "How goes your fight with the trolls?")
                    chatNpc(
                        happy,
                        "We are busy preparing for an attack by night. Godric knows of a secret " +
                            "entrance to the stronghold. Once we destroy the stronghold Burthorpe " +
                            "will be safe! Friend, we are indebted to you!",
                    )
                    chatPlayer(happy, "Good luck!")
                    return
                }
                2 -> whiteKnights()
                else -> {
                    seeYouAbout()
                    return
                }
            }
        }
    }

    /* Shared */

    private suspend fun Dialogue.whatIsThisPlace() {
        chatPlayer(quiz, "What is this place?")
        chatNpc(happy, "Welcome to the Principality of Burthorpe!")
        chatNpc(
            neutral,
            "We are the Imperial Guard for his Royal Highness Prince Anlaf of Burthorpe.",
        )
    }

    private suspend fun Dialogue.whiteKnights() {
        chatPlayer(quiz, "I thought the White Knights controlled Asgarnia?")
        chatNpc(
            neutral,
            "You are right citizen. The White Knights have taken advantage of the old and weak " +
                "king, they control most of Asgarnia, including Falador. However they do not " +
                "control Burthorpe!",
        )
        chatNpc(happy, "We are the prince's elite troops! We keep Burthorpe secure!")
        chatNpc(
            angry,
            "The White Knights have overlooked us, until now! They are pouring money into their " +
                "war against the Black Knights, they are looking for an excuse to stop our " +
                "funding and I'm afraid they may have found it!",
        )
        chatNpc(
            angry,
            "If we can not destroy the troll camp on Death Plateau then the Imperial Guard will " +
                "be disbanded and Burthorpe will come under control of the White Knights. We " +
                "can not let this happen!",
        )
    }

    private suspend fun Dialogue.seeYouAbout() {
        chatPlayer(happy, "See you about Denulth!")
        chatNpc(happy, "God speed friend!")
    }

    private companion object {
        const val RECOMMENDED_COMBAT = 50
    }
}
