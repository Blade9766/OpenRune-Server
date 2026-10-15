package org.rsmod.content.quest.area.burthorpe.heroesquest

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.ARMBAND
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.BLACKARM_ARMBAND
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.BLACKARM_BRIEFED
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.BLACKARM_LOOTED
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.CANDLESTICK
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.PHOENIX_ARMBAND
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.PHOENIX_BRIEFED
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.PHOENIX_KILLED_GRIP
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.STAGE_STARTED

/*
 * The gang leaders' side of the Master Thieves' armband, called from the Shield of Arrav scripts
 * that own Katrine's and Straven's Talk-to.
 */

/** Katrine replaces a lost armband straight after her greeting; true if she did. */
internal suspend fun Dialogue.katrineReplacesArmband(heroes: HeroesQuest): Boolean {
    if (!heroes.blackArmAt(player, BLACKARM_ARMBAND) || heroes.owns(access, ARMBAND)) {
        return false
    }
    chatPlayer(sad, "I have lost my master thief's armband...")
    chatNpc(neutral, "Lucky I 'ave a spare ain't it? Don't lose it again.")
    access.invAdd(access.inv, ARMBAND)
    return true
}

/** The extra line Katrine's menu offers a Black Arm player working on the armband, if any. */
internal fun Dialogue.katrineArmbandOption(heroes: HeroesQuest): String? {
    val stage = heroes.stage(player)
    return when {
        !heroes.isBlackArm(player) -> null
        stage in BLACKARM_BRIEFED..BLACKARM_LOOTED && access.inv.count(CANDLESTICK) > 0 ->
            "I have a candlestick now!"
        stage == STAGE_STARTED || stage in BLACKARM_BRIEFED..BLACKARM_LOOTED ->
            "Is there any way I can get the rank of master thief?"
        else -> null
    }
}

internal suspend fun Dialogue.katrineArmband(heroes: HeroesQuest) {
    val stage = heroes.stage(player)
    if (stage != STAGE_STARTED && access.inv.count(CANDLESTICK) > 0) {
        chatPlayer(happy, "I have a candlestick now!")
        if (stage != BLACKARM_LOOTED) {
            chatNpc(neutral, "Good for you.")
            chatNpc(
                neutral,
                "I'll be giving a master thieves armband to the one who retrieved that. I know " +
                    "it wasn't you.",
            )
            return
        }
        chatNpc(shocked, "Wow.... is... it REALLY it?")
        chatNpc(happy, "This really is a FINE bit of thievery.")
        chatNpc(happy, "Us thieves have been trying to get hold of this one for a while!")
        chatNpc(
            happy,
            "You wanted to be ranked as a master thief didn't you? Well, I guess this just about " +
                "ranks as good enough!",
        )
        access.invDel(access.inv, CANDLESTICK)
        access.invAdd(access.inv, ARMBAND)
        heroes.setStage(access, BLACKARM_ARMBAND)
        objbox(ARMBAND, "Katrine gives you a master thief armband.")
        return
    }
    chatPlayer(quiz, "Is there any way I can get the rank of master thief?")
    chatNpc(neutral, "Master thief? Ain't we the ambitious one!")
    chatNpc(neutral, "Well, you're gonna have to do something pretty amazing.")
    chatPlayer(quiz, "Anything you can suggest?")
    chatNpc(
        neutral,
        "Well, some of the MOST coveted prizes in thiefdom right now are in the pirate town of " +
            "Brimhaven on Karamja.",
    )
    chatNpc(neutral, "The pirate leader Scarface Pete has a pair of extremely valuable candlesticks.")
    chatNpc(neutral, "His security is VERY good.")
    chatNpc(
        neutral,
        "We, of course, have gang members in a town like Brimhaven who may be able to help you.",
    )
    chatNpc(
        neutral,
        "Visit our hideout in the alleyway on Palm Street. To get in you will need to tell them " +
            "the secret password 'four leafed clover'.",
    )
    if (stage == STAGE_STARTED) {
        heroes.setStage(access, BLACKARM_BRIEFED)
    }
}

/**
 * Straven's armband lines for a Phoenix member: a spare for a lost armband, the heist briefing,
 * a reminder, or the candlestick hand-in. True if he handled the conversation.
 */
internal suspend fun Dialogue.stravenArmband(heroes: HeroesQuest): Boolean {
    if (!heroes.isPhoenix(player)) {
        return false
    }
    val stage = heroes.stage(player)
    if (heroes.phoenixAt(player, PHOENIX_ARMBAND) && !heroes.owns(access, ARMBAND)) {
        chatPlayer(sad, "I'm afraid I've lost my Master Thief armband.")
        chatNpc(neutral, "Lucky for you I have a spare. Don't lose it again!")
        access.invAdd(access.inv, ARMBAND)
        return true
    }
    if (stage != STAGE_STARTED && stage !in PHOENIX_BRIEFED..PHOENIX_KILLED_GRIP) {
        return false
    }
    if (stage != STAGE_STARTED && access.inv.count(CANDLESTICK) > 0) {
        chatPlayer(happy, "I have retrieved a candlestick!")
        if (stage != PHOENIX_KILLED_GRIP) {
            chatNpc(
                neutral,
                "Well, in all honesty, you didn't actually retrieve it yourself, did you? You " +
                    "don't qualify as a master thief unless you actually steal it yourself, you " +
                    "know...",
            )
            return true
        }
        chatNpc(neutral, "Hmmm. Not bad, not bad. Let's see it, make sure it's genuine.")
        access.invDel(access.inv, CANDLESTICK)
        objbox(CANDLESTICK, "You hand Straven the candlestick.")
        chatPlayer(quiz, "So is this enough to get me a Master Thief armband?")
        chatNpc(neutral, "Hmm...")
        chatNpc(neutral, "I dunno...")
        chatNpc(happy, "Aww, go on then. I suppose I'm in a generous mood today.")
        access.invAdd(access.inv, ARMBAND)
        heroes.setStage(access, PHOENIX_ARMBAND)
        objbox(ARMBAND, "Straven hands you a Master Thief armband.")
        return true
    }
    if (stage == STAGE_STARTED) {
        chatPlayer(quiz, "How would I go about getting a Master Thief armband?")
        chatNpc(neutral, "Ooh... tricky stuff. Took me YEARS to get that rank.")
        chatNpc(
            neutral,
            "Well, what some of the more aspiring thieves in our gang are working on right now " +
                "is to steal some very valuable candlesticks from Scarface Pete - the pirate " +
                "leader on Karamja.",
        )
        heroes.setStage(access, PHOENIX_BRIEFED)
    } else {
        chatPlayer(quiz, "What am I supposed to be doing again?")
        chatNpc(
            angry,
            "You told me you wanted to get a Master thief's armband! Now pay attention.",
        )
        chatNpc(
            neutral,
            "Some of the more aspiring thieves in our gang are working on stealing some very " +
                "valuable candlesticks from Scarface Pete - the pirate leader on Karamja.",
        )
    }
    chatNpc(
        neutral,
        "His security is excellent, and the target very valuable so that might be enough to get " +
            "you the rank.",
    )
    chatNpc(neutral, "Go talk to our man Alfonse, the waiter in the Shrimp and Parrot.")
    chatNpc(neutral, "Use the secret word 'gherkin' to show you're one of us.")
    return true
}
