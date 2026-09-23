package org.rsmod.content.quest.area.burthorpe.heroesquest

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.BLAMISH_OIL
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.OILY_ROD
import org.rsmod.content.quest.area.burthorpe.heroesquest.HeroesQuest.Companion.SLIME

/** The lava eel option Gerrant's fishing shop offers while the Heroes' Quest is under way. */
public fun Dialogue.lavaEelOption(heroes: HeroesQuest): String? =
    if (heroes.isInProgress(player)) "I want to find out how to catch a lava eel." else null

/** Gerrant's advice on lava eels, and his jar of blamish snail slime for the fishing line. */
public suspend fun Dialogue.lavaEelAdvice(heroes: HeroesQuest) {
    chatPlayer(neutral, "I want to find out how to catch a lava eel.")
    if (heroes.owns(access, SLIME) || heroes.owns(access, BLAMISH_OIL) || heroes.owns(access, OILY_ROD)) {
        chatNpc(
            confused,
            "You'll need a lava-proof fishing line. I'm sure I already gave you what you need " +
                "for this - take an ordinary fishing rod, and then cover it with the fire-proof " +
                "Blamish Oil.",
        )
        return
    }
    chatNpc(
        neutral,
        "Lava eels eh? That's a tricky one that is, you'll need a lava-proof fishing line. The " +
            "method for making this would be to take an ordinary fishing rod, and then cover it " +
            "with the fire-proof Blamish Oil.",
    )
    chatNpc(
        neutral,
        "You know... thinking about it... I may have a jar of Blamish Slime around here " +
            "somewhere... Now where did I put it?",
    )
    if (access.inv.isFull()) {
        chatPlayer(
            sad,
            "Don't worry about that for now. I don't have space to take anything from you.",
        )
        return
    }
    mesbox("Gerrant searches around a bit.")
    access.invAdd(access.inv, SLIME)
    chatNpc(
        happy,
        "Aha! Here it is! Take this slime, mix it with some Harralander and water and you'll " +
            "have the Blamish Oil you need for treating your fishing rod.",
    )
}
