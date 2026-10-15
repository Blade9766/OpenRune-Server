package org.rsmod.content.quest.area.mortmyre.naturespirit

import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.APPLE_PIE
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.MEAT_PIE
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_BLESSED
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_FIRST_GHAST
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_GOT_SPELL
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_HEARD_SPIRIT
import org.rsmod.content.quest.area.mortmyre.naturespirit.NatureSpiritQuest.Companion.STAGE_STARTED

typealias DrezelLine = suspend Dialogue.(MesAnimType, String) -> Unit

@Singleton
class NatureSpiritDrezel
@Inject
constructor(
    private val natureSpirit: NatureSpiritQuest,
    private val objRepo: ObjRepository,
) {
    suspend fun Dialogue.talk(drezel: DrezelLine) {
        val stage = natureSpirit.stage(player)
        when {
            natureSpirit.isComplete(player) -> {
                chatPlayer(happy, "Hi there! Filliman says I've completed the quest!")
                drezel(happy, "That's fantastic, well done!")
            }
            stage == 0 -> offer(drezel)
            stage == STAGE_HEARD_SPIRIT && !natureSpirit.toldDrezelOfSpirit.get(player) -> sadNews(drezel)
            stage == STAGE_GOT_SPELL -> bless(drezel)
            stage >= STAGE_FIRST_GHAST -> ghastKiller(drezel)
            stage >= STAGE_BLESSED -> sinceBlessed(drezel)
            else -> stillLooking(drezel)
        }
    }

    private suspend fun Dialogue.offer(drezel: DrezelLine) {
        chatPlayer(happy, "Hello again, Drezel. Is there anything that I can help you out with around here?")
        if (!natureSpirit.meetsRequirements(player)) {
            drezel(neutral, "Well... I don't think so. There's not much to do in that place.")
            mesbox("You do not meet all of the requirements to start the Nature Spirit quest.")
            return
        }
        drezel(neutral, "Well... there is something you could do for me if you're interested. Though it is quite dangerous.")
        if (!choice2("Well, what is it, I may be able to help?", true, "Sorry, not interested.", false)) {
            chatPlayer(neutral, "Sorry, not interested.")
            drezel(neutral, "Of course. I understand.")
            return
        }
        chatPlayer(quiz, "Well, what is it, I may be able to help?")
        drezel(
            worried,
            "There's a man called Filliman who lives in Mort Myre. He comes by the temple from time " +
                "to time but I haven't seen him recently. I was wondering if you could check in on him?",
        )
        drezel(worried, "I must warn you, it could be dangerous. Mort Myre is filled with ghasts!")
        if (!choice2("Yes.", true, "No.", false, title = "Start the Nature Spirit quest?")) {
            chatPlayer(neutral, "Sorry, I don't think I can help.")
            drezel(
                neutral,
                "That's fine, I'm sure someone else will be along shortly who can. Now, if you will " +
                    "excuse me, I do have some things to be getting on with.",
            )
            return
        }
        chatPlayer(happy, "Yes, I'll go and look for him.")
        natureSpirit.advanceTo(access, STAGE_STARTED)
        drezel(happy, "That's great! Many thanks!")
        drezel(
            worried,
            "Now, please be aware of the ghasts, you cannot attack them, only Filliman knew how to " +
                "take them on. Just run from them if you can. If you get lost, try to make your way " +
                "back to the temple.",
        )
        repeat(PIES) { access.invAddOrDrop(objRepo, MEAT_PIE) }
        repeat(PIES) { access.invAddOrDrop(objRepo, APPLE_PIE) }
        doubleobjbox(MEAT_PIE, APPLE_PIE, "Drezel hands you some food.")
        drezel(
            neutral,
            "Please take this food to Filliman, he'll likely appreciate it. His home is somewhere " +
                "in the southern end of Mort Myre. I don't know the exact location I'm afraid.",
        )
        chatPlayer(neutral, "Don't worry, if he's there and he's still alive, I'll find him.")
        while (true) {
            when (
                choice4(
                    "Who exactly is this Filliman?",
                    1,
                    "Where's Mort Myre?",
                    2,
                    "What's a ghast?",
                    3,
                    "I'd better get going.",
                    4,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "Who exactly is this Filliman?")
                    drezel(
                        neutral,
                        "Filliman Tarlock is his full name and he's a druid. He lives in Mort Myre " +
                            "much like a hermit, but there's many a traveller who he's helped.",
                    )
                    drezel(
                        neutral,
                        "Most people that come this way tell stories of when they were lost and " +
                            "paths that just seemed to 'open up' before them! I think it was " +
                            "Filliman Tarlock helping out.",
                    )
                }
                2 -> {
                    chatPlayer(quiz, "Where's Mort Myre?")
                    drezel(
                        neutral,
                        "Mort Myre is a decayed and dangerous swamp to the south east of here. " +
                            "Legend says it was once a beautiful forest called Humblethorn. Now, " +
                            "it's filled with vile emanations from within Morytania.",
                    )
                    drezel(
                        worried,
                        "The swamp decays everything. We put a fence around it to stop unwary " +
                            "travellers going in. Anyone who dies in the swamp is forever cursed to " +
                            "haunt it as a ghast.",
                    )
                    drezel(worried, "Ghasts attack travellers, turning food to rotten filth.")
                }
                3 -> {
                    chatPlayer(quiz, "What's a ghast?")
                    drezel(
                        worried,
                        "A Ghast is a poor soul who died in Mort Myre. They're undead of a special " +
                            "class and they're untouchable as far as I'm aware! Filliman knows how " +
                            "to tackle them though.",
                    )
                    drezel(
                        worried,
                        "When they attack, ghasts will devour any food you have. If you have no " +
                            "food, they'll draw their nourishment from you!",
                    )
                }
                else -> {
                    chatPlayer(neutral, "I'd better get going.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.stillLooking(drezel: DrezelLine) {
        chatPlayer(happy, "Hello again!")
        drezel(quiz, "Have you managed to find Filliman yet?")
        chatPlayer(neutral, "No not yet.")
        drezel(neutral, "Please go and look for him, I would appreciate it!")
        while (true) {
            when (
                choice5(
                    "Where should I look for Filliman Tarlock again?",
                    1,
                    "Explain to me what a Ghast is again.",
                    2,
                    "What's the story with Mort Myre?",
                    3,
                    "What's the story with Filliman Tarlock?",
                    4,
                    "Ok, thanks.",
                    5,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "Where should I look for Filliman Tarlock again?")
                    drezel(
                        neutral,
                        "Search to the south of Mort Myre. Remember that he's a druid so he can " +
                            "conceal himself within nature quite well. My guess is that he lives in " +
                            "the southern area of the swamp, though he could be anywhere.",
                    )
                }
                2 -> {
                    chatPlayer(quiz, "Explain to me what a Ghast is again.")
                    drezel(
                        neutral,
                        "A Ghast is a poor soul who died in Mort Myre. They're undead of a special " +
                            "class, they're untouchable apart from with special druidic items.",
                    )
                    drezel(
                        worried,
                        "Filliman knew how to tackle them, but I've not heard from him in a long " +
                            "time. Ghasts, when they attack, will devour any food you have. If you " +
                            "have no food, they'll draw their nourishment from you!",
                    )
                }
                3 -> {
                    chatPlayer(quiz, "What's the story with Mort Myre?")
                    drezel(
                        sad,
                        "Mort Myre was once a beautiful forest by the name of Humblethorn until the " +
                            "evil denizens of Morytania descended. Now their evil emanations have " +
                            "putrified and diseased the forest into a decaying swamp of death.",
                    )
                }
                4 -> {
                    chatPlayer(quiz, "What's the story with Filliman Tarlock?")
                    drezel(
                        neutral,
                        "Filliman is a druid of some considerable power. He helped many people in " +
                            "Morytania escape when the evil descended upon the land. His knowledge " +
                            "of plants and nature was exceptional.",
                    )
                    drezel(
                        sad,
                        "But one day, he was betrayed by some of the people who he had tried to " +
                            "help. This naturally made him more careful when dealing with strangers " +
                            "again and so, instead of showing himself, he would follow them.",
                    )
                    drezel(
                        happy,
                        "He would follow them at a distance and make the path clear for them, " +
                            "showing them to the temple and to salvation. Saradomin bless him! He is " +
                            "a good man.",
                    )
                }
                else -> {
                    chatPlayer(neutral, "Ok, thanks.")
                    drezel(happy, "Many thanks to you, ${player.displayName}!")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.sadNews(drezel: DrezelLine) {
        chatPlayer(sad, "I've found Filliman and you should prepare for some sad news.")
        drezel(worried, "You mean... he's dead?")
        chatPlayer(
            neutral,
            "Well, er sort of. I got to his camp and I encountered a spirit of some kind. I don't " +
                "think it was a Ghast, it tried to communicate with me, but made no sense, it was " +
                "all 'ooooh' this and 'oooh' that.",
        )
        drezel(
            neutral,
            "Hmmm, that's very interesting, I seem to remember Father Aereck in Lumbridge and his " +
                "predecessor Father Urhney having a similar issue. Though this is probably not " +
                "related to your problem.",
        )
        natureSpirit.toldDrezelOfSpirit.set(player, true)
        drezel(
            sad,
            "I will pray that it wasn't the spirit of my friend Filliman, but some lost soul who " +
                "needs some help. Please do let me know how you get on with it.",
        )
    }

    private suspend fun Dialogue.bless(drezel: DrezelLine) {
        chatPlayer(
            happy,
            "Hello again! I'm helping Filliman, he plans to become a nature spirit. I have a spell " +
                "to cast but first I need to be blessed. Can you bless me?",
        )
        drezel(laugh, "But you haven't sneezed!")
        chatPlayer(laugh, "You're so funny!")
        chatPlayer(quiz, "But can you bless me?")
        drezel(neutral, "Very well my friend, prepare yourself for the blessings of Saradomin. Here we go!")
        npc?.let {
            it.say(BLESSING_CHANT)
            it.anim(PRAY_SEQ)
        }
        access.spotanim(BLESS_SPOTANIM)
        access.soundSynth(BLESS_SOUND)
        delay(BLESS_TICKS)
        natureSpirit.advanceTo(access, STAGE_BLESSED)
        drezel(
            happy,
            "There you go my friend, you're now blessed. It's funny, now I look at you, there " +
                "seems to be something of the faith about you. Anyway, good luck with your quest!",
        )
        chatPlayer(happy, "Many thanks!")
    }

    private suspend fun Dialogue.sinceBlessed(drezel: DrezelLine) {
        drezel(quiz, "How's life been treating you since you got blessed?")
        chatPlayer(happy, "Not so bad!")
        drezel(
            neutral,
            "It's funny, because when I look at you, there does seem to be something of the faith " +
                "about you. Have you considered a life of service to Saradomin?",
        )
        chatPlayer(neutral, "I serve Saradomin in other ways.")
        drezel(happy, "Fair enough!")
    }

    private suspend fun Dialogue.ghastKiller(drezel: DrezelLine) {
        val killed = natureSpirit.ghastsKilled(player)
        chatPlayer(happy, "Hiya, I'm a mighty Ghast killer! I've killed $killed so far!")
        drezel(quiz, "That's great! How many did Filliman ask you kill?")
        chatPlayer(neutral, "He asked me to kill 3!")
        when (killed) {
            1 -> drezel(neutral, "So, you've got two more to kill then!")
            2 -> drezel(neutral, "So, you've got one more to kill then!")
            else -> drezel(happy, "So, you've killed them all then! Go and tell him, I'll bet he'll be pleased.")
        }
    }

    private companion object {
        const val PIES = 3
        const val BLESSING_CHANT = "Ashustru, blessidium, adverturasi, fidum!"
        const val PRAY_SEQ = "seq.human_pray"
        const val BLESS_SPOTANIM = "spotanim.druidicspirit_priest_bless"
        const val BLESS_SOUND = "synth.prayer_recharge"
        const val BLESS_TICKS = 2
    }
}
