package org.rsmod.content.quest.area.lumbridge.dorgeshuun

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.STAGE_TOUR
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.ZANIK_CHATHEAD
import org.rsmod.content.quest.area.lumbridge.dorgeshuun.DeathToTheDorgeshuunQuest.Companion.inLumbridge
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

/**
 * Zanik's tour of Lumbridge. The residents' own scripts ask [isTouring] and hand the conversation
 * over to the matching introduction here; a stop that the quest counts sets its `dttd_tour_*`
 * varbit. The sky, the goblins and wandering too far from town are noticed as the two of them walk
 * about, from Zanik's per-tick hook.
 */
@Singleton
class ZanikTour
@Inject
constructor(
    private val dttd: DeathToTheDorgeshuunQuest,
    private val follower: ZanikFollower,
    private val scenes: DttdScenes,
    private val npcRepo: NpcRepository,
    private val launcher: ProtectedAccessLauncher,
    private val random: GameRandom,
) {
    private val goblinTypes by lazy { GOBLINS.map { it.asRSCM(RSCMType.NPC) }.toSet() }

    init {
        follower.onTick(::tick)
    }

    /** Whether Zanik is out with the player on her tour and close enough to join in. */
    fun isTouring(player: Player): Boolean {
        if (dttd.stage(player) != STAGE_TOUR) {
            return false
        }
        val zanik = follower.following(player) ?: return false
        return zanik.coords.level == player.coords.level &&
            zanik.coords.isWithinDistance(player.coords, JOIN_DISTANCE)
    }

    fun inCastle(coords: CoordGrid): Boolean = coords.x in 3200..3227 && coords.z in 3200..3237 && coords.z < 6400

    fun inHamHideout(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in 3140..3190 && coords.z in 9600..9660

    private suspend fun Dialogue.zanik(mood: dev.openrune.types.MesAnimType, text: String) =
        chatNpcSpecific("Zanik", ZANIK_CHATHEAD, mood, text)

    /* The residents */

    suspend fun Dialogue.cookIntro() {
        chatNpc(confused, "What, er, who is this? Is it a cave goblin?")
        zanik(happy, "I'm Zanik of the Dorgeshuun! Pleased to meet you!")
        chatPlayer(
            happy,
            "Zanik is the first cave goblin to visit the surface! I'm giving her a tour of Lumbridge.",
        )
        chatNpc(neutral, "Well, er, pleased to meet you, Zanik.")
        chatNpc(
            happy,
            "In fact, it's a good thing I met you. It's the Duke's birthday today, and I should be making " +
                "him a lovely big birthday cake. I need eggs, flour and milk...",
        )
        zanik(quiz, "I don't know what those things are! Are they exotic surface foods?")
        chatNpc(neutral, "Well, they're not exactly exotic, they're just the basic ingredients of a cake.")
        zanik(quiz, "If they're the basic ingredients, why don't you already have them?")
        chatNpc(worried, "Well, er...")
        chatPlayer(neutral, "I don't think Zanik has time to help you.")
        chatNpc(sad, "It was worth a try. Anyway...")
    }

    suspend fun Dialogue.dukeVisit() {
        chatNpc(happy, "Greetings. Welcome to my castle.")
        if (!player.dttdTourDuke) {
            chatNpc(happy, "It's good to see a visitor from the Dorgeshuun! I am Duke Horacio.")
            zanik(happy, "I'm Zanik of the Dorgeshuun! Headman Ur-Tag sends his greetings.")
            chatNpc(
                neutral,
                "Tell Ur-Tag that I wish to speak with him again. I think that our cities could profit from " +
                    "trading with one another, but there can be little trade if he does not allow visitors " +
                    "to enter the city.",
            )
            zanik(
                sad,
                "I know! But the ruling council is so slow to make decisions. It's made up of old fogeys who " +
                    "are set in their ways.",
            )
            chatNpc(neutral, "Now then, child, there is room for caution and experience in politics.")
            zanik(happy, "Maybe when I return I can convince them to open the gates of the city.")
            player.dttdTourDuke = true
        } else {
            chatNpc(
                happy,
                "Hello again, Zanik, ${player.displayName}. I hope you are enjoying your tour. Perhaps " +
                    "someday a human can have a similar tour of your city.",
            )
        }
        when (
            menu(
                "I'd like to get into the city too." to 1,
                "Tell me about the treaty." to 2,
                "Have you heard of any HAM activity lately?" to 3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I'd like to get into the city too.")
                zanik(sad, "I wish I could show it to you, ${player.displayName}!")
                zanik(
                    neutral,
                    "Someday I will, but I don't think the council will agree to open the doors any time soon.",
                )
            }
            2 -> {
                chatPlayer(quiz, "Tell me about the treaty.")
                chatNpc(
                    neutral,
                    "It basically defines the borders between our two societies. It's not usual for two " +
                        "states to be above one another rather than side-by-side!",
                )
                chatNpc(
                    neutral,
                    "Everything above ground, within the borders of Lumbridge, belongs to Lumbridge. " +
                        "Everything below ground belongs to the Dorgeshuun. Except for the cellars of our " +
                        "buildings, those belong to Lumbridge.",
                )
                chatNpc(
                    neutral,
                    "Of course both our underground systems might expand. Neither state can build new " +
                        "tunnels that are too close to the other's systems...",
                )
                zanik(bored, "This is boring. Let's go.")
            }
            3 -> {
                chatPlayer(quiz, "Have you heard of any HAM activity lately?")
                chatNpc(
                    neutral,
                    "No... I know they've been gaining members but I haven't heard anything else from them. " +
                        "They may be planning something, but then again they may not.",
                )
            }
        }
    }

    suspend fun Dialogue.hansIntro() {
        chatNpc(neutral, "Hello. What are you doing here?")
        chatPlayer(happy, "I'm giving Zanik here a tour of Lumbridge.")
        zanik(happy, "I'm Zanik of the Dorgeshuun! Pleased to meet you!")
        chatNpc(
            happy,
            "Pleased to meet you, Zanik! I know not everyone in Lumbridge was pleased with the peace " +
                "treaty, but I'm delighted to have new underground neighbours.",
        )
    }

    /** Bob throws them out; returns true when the player disowns Zanik and wants to shop anyway. */
    suspend fun Dialogue.bobVisit(): Boolean {
        chatNpc(angry, "Get out of my shop! GET OUT!")
        chatPlayer(confused, "What?")
        chatNpc(angry, "We don't serve the likes of that thing in here.")
        zanik(angry, "I don't need anything you've got to sell anyway!")
        return if (choice2("Let's go, Zanik.", false, "I'm not with her.", true)) {
            chatPlayer(neutral, "I'm not with her.")
            zanik(shocked, "!")
            chatNpc(neutral, "Oh, well... what do you want?")
            true
        } else {
            chatPlayer(neutral, "Let's go, Zanik.")
            chatNpc(angry, "And don't come back, you filthy goblin!")
            false
        }
    }

    suspend fun Dialogue.aereckVisit() {
        chatNpc(happy, "Welcome to the church of Holy Saradomin!")
        zanik(confused, "What's a church? And what is Holy Saradomin?")
        chatNpc(shocked, "Surely you have heard of the god, Saradomin?")
        chatNpc(
            angry,
            "He who creates the forces of goodness and purity in this world? I cannot believe your ignorance!",
        )
        zanik(neutral, "Oh, a GOD.")
        chatNpc(
            neutral,
            "Not just A god! Saradomin is the god with more followers than any other! At least in this part " +
                "of the world.",
        )
        chatNpc(quiz, "Which god do you cave goblins worship?")
        zanik(angry, "We DON'T worship any god!")
        zanik(
            sad,
            "Thousands of years ago the gods made our ancestors fight in horrible wars. It was only after we " +
                "escaped from them that we were able to build a civilisation.",
        )
        player.dttdTourPriest = true
        while (true) {
            when (
                menu(
                    "What god did the Dorgeshuun use to follow?" to 1,
                    "What happened in the wars?" to 2,
                    "Not all the gods are bad!" to 3,
                    "Bye." to 0,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "What god did the Dorgeshuun use to follow?")
                    zanik(
                        neutral,
                        "Our history does not remember his name. We know only that he was a violent god of war.",
                    )
                    chatNpc(neutral, "Sounds like Zamorak to me! Or one of the minor gods who followed him.")
                    chatNpc(neutral, "Saradomin would never be so barbaric.")
                }
                2 -> {
                    chatPlayer(quiz, "What happened in the wars?")
                    chatNpc(
                        neutral,
                        "We do have stories about a war between the gods. I had always interpreted them as " +
                            "symbolic of the spiritual war between light and darkness in our hearts.",
                    )
                    zanik(
                        neutral,
                        "Dorgeshuun history remembers them as real. But we don't know much detail. Our " +
                            "ancestors were just footsoldiers, they didn't know what they were fighting for.",
                    )
                    chatNpc(
                        neutral,
                        "I am sure they were engaged in a noble struggle, although perhaps on the wrong side!",
                    )
                    chatNpc(
                        neutral,
                        "Saradomin was fighting to bring goodness and order to the universe, but Zamorak was " +
                            "leading the forces of darkness in a bid to bring eternal night to the world!",
                    )
                    zanik(
                        neutral,
                        "Our history doesn't make a distinction. There was your own god who would kill you if " +
                            "you refused to fight, and someone else's god who would only kill you if you lost.",
                    )
                }
                3 -> {
                    chatPlayer(neutral, "Not all the gods are bad!")
                    chatNpc(happy, "Quite right! The Dorgeshuun have just had bad experiences of them.")
                    zanik(neutral, "Maybe that's true. I should try to approach things with an open mind!")
                    chatNpc(
                        happy,
                        "Exactly! You Dorgeshuun have lived in darkness for too long. But now you can come " +
                            "into the light of Holy Saradomin!",
                    )
                    zanik(sad, "We don't like bright lights. They hurt our eyes.")
                }
                else -> {
                    chatPlayer(neutral, "Bye.")
                    chatNpc(happy, "May Saradomin bless you in all your pursuits!")
                    zanik(neutral, "I can manage without Saradomin, but thanks anyway.")
                    return
                }
            }
        }
    }

    suspend fun Dialogue.guideIntro() {
        chatNpc(happy, "Greetings adventurer. I am Phileas the Lumbridge Guide.")
        zanik(happy, "I'm Zanik of the Dorgeshuun! Pleased to meet you!")
        chatNpc(
            happy,
            "Greetings Zanik! I am here to give information and directions to new players. Do you require " +
                "any help?",
        )
        zanik(happy, "No thank you! ${player.displayName} is showing me around!")
        chatNpc(neutral, "Well, can I help you, ${player.displayName}?")
    }

    suspend fun Dialogue.shopIntro() {
        chatNpc(happy, "Can I help you at all?")
        zanik(happy, "It's a surface shop full of exotic surface goods! What are you selling?")
        chatNpc(confused, "Um, yes, exotic surface goods... here, have a look at our wares...")
        zanik(quiz, "Wow... The bucket and the hammer, what are they made of?")
        chatNpc(confused, "Um... wood. It, er, grows on trees?")
        zanik(happy, "Amazing!")
        chatNpc(neutral, "So, do you want to buy anything?")
        zanik(happy, "A wood bucket! And a wood hammer! And a newcomer map! I brought some surface money.")
        chatNpc(happy, "There you go!")
        zanik(happy, "Thank you!")
        player.dttdTourShop = true
        chatNpc(quiz, "How about you?")
    }

    /** The townsfolk who aren't quite so pleased about the treaty. */
    suspend fun Dialogue.citizen(npc: Npc) {
        if (!player.dttdTourCitizens) {
            chatNpc(shocked, "Eeek! What is it?")
            zanik(happy, "I'm Zanik of the Dorgeshuun!")
            chatNpc(angry, "A cave goblin? Well, there goes the neighbourhood!")
            chatPlayer(quiz, "What do you mean?")
            chatNpc(
                neutral,
                "It's all very well the duke signing a peace treaty with those creatures. Maybe that'll stop " +
                    "them attacking us.",
            )
            chatNpc(
                angry,
                "But for them to come wandering around the surface and mixing with decent people? I don't " +
                    "know, what's the world coming to?",
            )
            zanik(angry, "We were never going to attack you! YOU nearly attacked US!")
            chatNpc(
                neutral,
                "Oh, they may say they're civilised, but they're still goblins. You can't trust them. The only " +
                    "reason they're not a threat is that they're so stupid.",
            )
            zanik(angry, "Stop talking about me like I'm not here!")
            player.dttdTourCitizens = true
            return
        }
        when (random.of(maxExclusive = 6)) {
            0 -> chatNpc(angry, "Keep away from me you dirty goblin!")
            1 -> {
                chatNpc(
                    angry,
                    "I don't know, what will be next? Cave goblins LIVING up here? Taking our jobs? I wouldn't " +
                        "want my daughter to marry a goblin!",
                )
                zanik(laugh, "I'm not sure a Dorgeshuun would want to marry a human!")
                chatNpc(angry, "What rudeness! This is why they say goblins have no manners!")
            }
            2 ->
                chatNpc(
                    neutral,
                    "Oh, I've got nothing against goblins in their own place. I just don't think they ought " +
                        "to be wandering around in our city and making the place look bad.",
                )
            3 ->
                chatNpc(
                    neutral,
                    "Goblins wandering around Lumbridge and talking to the duke like they're normal people? I " +
                        "don't know what the world's coming to.",
                )
            4 -> {
                chatNpc(
                    worried,
                    "It just makes me uncomfortable, is all. What if I was in bed and some horrible little " +
                        "goblin poked its head up through the floor?",
                )
                zanik(
                    angry,
                    "We would never do that! We don't tunnel upwards and we're very careful not to damage " +
                        "human cellars.",
                )
            }
            else -> {
                chatNpc(worried, "You cave goblins... don't you, you know, eat people?")
                zanik(angry, "No!")
                chatNpc(happy, "Oh! Well, that's all right then.")
            }
        }
    }

    /* Zanik's own remarks */

    suspend fun Dialogue.remark() {
        val p = player
        val remarks = buildList<suspend Dialogue.() -> Unit> {
            if (!p.dttdTourDuke) {
                add {
                    chatNpc(
                        neutral,
                        "I ought to visit the Duke of Lumbridge while I'm here. Ur-Tag asked me to give him his regards.",
                    )
                }
            } else {
                add {
                    chatNpc(
                        laugh,
                        "Duke Horacio seemed nice. But politics is just as boring up here as it is at home!",
                    )
                }
            }
            if (!p.dttdTourCitizens) {
                add {
                    chatNpc(
                        happy,
                        "There are so many people around! You must introduce me to them, ${p.displayName}!",
                    )
                }
            } else {
                add {
                    chatNpc(
                        sad,
                        "It's a pity some people up here don't like me. I suppose I should have expected that.",
                    )
                    chatNpc(happy, "But at least you're friendly, ${p.displayName}!")
                }
            }
            if (!p.dttdTourShop) {
                add {
                    chatNpc(
                        happy,
                        "Why don't you take me to a shop? I promised Tignik I would bring back a souvenir from " +
                            "the surface.",
                    )
                }
            } else {
                add {
                    chatNpc(
                        neutral,
                        "This 'wood' is an odd material. It's heavier than bone but it doesn't seem to be as " +
                            "brittle. It would be good for making crossbows out of!",
                    )
                }
            }
            if (!p.dttdTourGoblins) {
                add {
                    chatNpc(
                        quiz,
                        "Are there still goblins living on the surface, ${p.displayName}? Are there any near " +
                            "Lumbridge?",
                    )
                    chatNpc(happy, "I should reunite the two sides of the race!")
                }
            } else {
                add {
                    chatNpc(
                        sad,
                        "Those poor goblins. It's hard to believe I'm the same species as them. No wonder the " +
                            "HAM people hate us if they assume we're like them.",
                    )
                }
            }
            if (!p.dttdTourPriest) {
                add {
                    chatNpc(worried, "So far on the surface we haven't seen any... you know... gods.")
                    chatPlayer(laugh, "Of course not! The gods don't just wander around Lumbridge!")
                    chatNpc(confused, "Don't they? I have no idea what to expect!")
                }
            } else {
                add {
                    chatNpc(
                        neutral,
                        "That priest seemed friendly. I thought any servant of a god would be much more aggressive.",
                    )
                }
            }
            if (p.coords.z >= DeathToTheDorgeshuunQuest.UNDERGROUND_OFFSET) {
                add { chatNpc(angry, "What are we doing down here? I want to see the surface!") }
            }
        }
        remarks[random.of(maxExclusive = remarks.size)].invoke(this)
    }

    /** Says what Zanik still wants to see; false once she has seen it all. */
    suspend fun Dialogue.stillToSee(): Boolean {
        val p = player
        when {
            !p.dttdTourDuke ->
                chatNpc(
                    neutral,
                    "Perhaps we could talk to the Duke of Lumbridge. Headman Ur-Tag asked me to give him his regards.",
                )
            !p.dttdTourSun -> chatNpc(happy, "I haven't even seen the sky yet! Let's go outside!")
            !p.dttdTourCitizens -> chatNpc(happy, "You must introduce me to some of the surface people!")
            !p.dttdTourPriest ->
                chatNpc(
                    quiz,
                    "Well... our legends all talk about the gods living on the surface, but since I've been up " +
                        "here I haven't see any. Is there anyone around here who might know about them?",
                )
            !p.dttdTourGoblins -> {
                chatNpc(
                    quiz,
                    "Are there still goblins living on the surface, ${p.displayName}? Are there any near Lumbridge?",
                )
                chatNpc(happy, "I should reunite the two sides of the race!")
            }
            !p.dttdTourShop ->
                chatNpc(
                    happy,
                    "I promised Tignik I would bring a souvenir back from the surface. Will you take me to a shop?",
                )
            else -> return false
        }
        return true
    }

    /* Noticed while walking about */

    private fun tick(player: Player, zanik: Npc) {
        if (dttd.stage(player) != STAGE_TOUR || follower.isWaiting(player)) {
            return
        }
        val coords = player.coords
        when {
            !inLumbridge(coords) -> {
                if (!player.dttdEdgeWarned) {
                    player.dttdEdgeWarned = true
                    launcher.launch(player) { tooFar(zanik) }
                }
            }
            player.dttdEdgeWarned -> player.dttdEdgeWarned = false
        }
        if (!player.dttdTourSun && outdoors(coords) && zanik.coords.isWithinDistance(coords, JOIN_DISTANCE)) {
            launcher.launch(player) { with(scenes) { sunrise(zanik) } }
            return
        }
        if (!player.dttdTourGoblins && coords.level == 0 && coords.z < DeathToTheDorgeshuunQuest.UNDERGROUND_OFFSET) {
            val goblin = nearbyGoblin(coords) ?: return
            launcher.launch(player) { meetGoblin(zanik, goblin) }
        }
    }

    private fun outdoors(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.z < DeathToTheDorgeshuunQuest.UNDERGROUND_OFFSET && !inCastle(coords) &&
            inLumbridge(coords)

    private fun nearbyGoblin(coords: CoordGrid): Npc? =
        npcRepo.findAll(ZoneKey.from(coords), 1).firstOrNull {
            it.type.id in goblinTypes && it.coords.isWithinDistance(coords, GOBLIN_DISTANCE)
        }

    private suspend fun ProtectedAccess.tooFar(zanik: Npc) {
        startDialogue(zanik) {
            chatNpc(
                worried,
                "We're getting a bit far away from Lumbridge, ${player.displayName}. I'd like to see the rest " +
                    "of the world someday but not right now. Let's stick to Lumbridge for now.",
            )
            if (choice2("Okay.", true, "I'm going anyway.", false)) {
                chatPlayer(neutral, "Okay.")
            } else {
                chatPlayer(neutral, "I'm going anyway.")
                chatNpc(neutral, "All right. I'll wait for you back in Lumbridge castle cellar.")
                follower.sendHome(player)
            }
        }
    }

    private suspend fun ProtectedAccess.meetGoblin(zanik: Npc, goblin: Npc) {
        goblin.facePlayer(player)
        startDialogue(zanik) {
            chatNpc(worried, "Are those... goblins?")
            chatPlayer(neutral, "Yes.")
            chatNpc(worried, "H-hello? Goblin?")
            goblinLine(goblin, "Who you?")
            chatNpc(happy, "I'm Zanik of the Dorgeshuun!")
            goblinLine(goblin, "Dorgeshuun?")
            chatNpc(
                happy,
                "Yes! The Dorgeshuun tribe? We live underground. We've been cut off from other goblins for " +
                    "generations but now we're back!",
            )
            goblinLine(goblin, "What?")
            chatNpc(worried, "We, er, we're your distant cousins? We want to learn about your culture.")
            goblinLine(goblin, "Culture?")
            chatNpc(
                happy,
                "Your art! Your philosophy! Your way of life! Now that the godwars are over you must have " +
                    "been able to build a peaceful civilisation!",
            )
            goblinLine(goblin, "Peace is for weak. We goblins! We strong!")
            chatNpc(confused, "Why is peace for the weak?")
            goblinLine(goblin, "Big High War God say so! He make us strong!")
            chatNpc(shocked, "Big High War...")
            chatNpc(sad, "Oh. I see.")
            chatNpc(sad, "Let's go, ${player.displayName}.")
            player.dttdTourGoblins = true
        }
        goblin.resetFaceEntity()
    }

    private suspend fun Dialogue.goblinLine(goblin: Npc, text: String) {
        chatNpcSpecific("Goblin", GOBLIN_CHATHEAD, angry, text)
    }

    private companion object {
        const val JOIN_DISTANCE = 8
        const val GOBLIN_DISTANCE = 5
        const val GOBLIN_CHATHEAD = "npc.goblin_unarmed_melee_1"

        val GOBLINS = (1..8).map { "npc.goblin_unarmed_melee_$it" }
    }
}
