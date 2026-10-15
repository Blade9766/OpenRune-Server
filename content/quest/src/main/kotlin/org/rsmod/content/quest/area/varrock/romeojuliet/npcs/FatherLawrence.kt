package org.rsmod.content.quest.area.varrock.romeojuliet.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.CADAVA_POTION
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.FATHER_LAWRENCE
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_MESSAGE_DELIVERED
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_SEEN_APOTHECARY
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_SEEN_FATHER
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Father Lawrence preaches to a sleeping congregation in the church north-east of Varrock Square.
 * He and the two snoozing parishioners run ai timers (`timer = 1` in `npcs.toml`) so the sermon and
 * the snoring carry on whether or not anyone is listening.
 */
class FatherLawrence
@Inject
constructor(
    private val quest: RomeoJulietQuest,
    private val npcRepo: NpcRepository,
    private val random: GameRandom,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(FATHER_LAWRENCE) { startDialogue(it.npc) { father() } }
        onAiTimer(FATHER_LAWRENCE) { preach(npc) }
        for (sleeper in CONGREGATION) {
            onAiTimer(sleeper) { snore(npc) }
        }
    }

    private suspend fun Dialogue.father() {
        when (quest.stage(player)) {
            STAGE_MESSAGE_DELIVERED -> sermon()
            STAGE_SEEN_FATHER -> {
                chatNpc(quiz, "Ah, have you found the Apothecary yet? Remember: a cadava potion, for Juliet.")
            }
            STAGE_SEEN_APOTHECARY -> berries()
            0 -> seekAQuest()
            else -> smallTalk()
        }
    }

    private suspend fun Dialogue.sermon() {
        chatNpc(neutral, "'...and may Saradomin light the way before you...' Argh!")
        chatNpc(angry, "Can't you see I'm in the middle of a sermon?!")
        chatPlayer(neutral, "But Romeo sent me!")
        chatNpc(angry, "And I'm busy preaching to my congregation!")
        showCongregation()
        chatPlayer(neutral, "Well, you certainly seem to have a captive audience!")
        access.say("Well, you certainly seem to have a captive audience!")
        chatNpc(angry, "Alright, alright... what do you want, so I can be rid of you and get on with it?")
        chatPlayer(neutral, "Romeo sent me. He says you might be able to help.")
        chatNpc(neutral, "Ah, Romeo. A fine lad, if a little muddled.")
        chatPlayer(
            neutral,
            "Very muddled... Anyway, Romeo wants to marry Juliet! She has to be rescued from " +
                "her father!",
        )
        chatNpc(happy, "I agree, and I have an idea! A potion that makes her appear to be dead...")
        chatPlayer(worried, "Dead?! That sounds a bit creepy... but go on.")
        chatNpc(
            neutral,
            "The potion only makes Juliet LOOK dead. Then she'll be laid to rest in the crypt...",
        )
        chatPlayer(worried, "The crypt! Creepier still! You have some odd hobbies.")
        quest.setStage(access, STAGE_SEEN_FATHER)
        chatNpc(
            happy,
            "And then Romeo can collect her from the crypt! Go to the Apothecary, tell him I " +
                "sent you, and that you need a 'cadava' potion.",
        )
        chatPlayer(
            neutral,
            "Apart from the heavy overtones of death, this is turning into quite the love story.",
        )
    }

    private suspend fun Dialogue.showCongregation() {
        val church = access.player.coords
        access.camMoveTo(PEW_CAMERA_FROM, height = CAMERA_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
        access.camLookAt(PEW_CAMERA_AT, height = LOOK_HEIGHT, rate = CAMERA_RATE, rate2 = CAMERA_RATE)
        for (sleeper in congregationNear(church)) {
            sleeper.say(SNORE)
        }
        mesbox("The Father gestures at his congregation, fast asleep in the pews.")
        access.camReset()
    }

    private fun congregationNear(coords: CoordGrid): List<Npc> =
        npcRepo.findAll(ZoneKey.from(coords), ZONE_RADIUS).filter { npc -> CONGREGATION.any(npc::isType) }.toList()

    private suspend fun Dialogue.berries() {
        chatNpc(quiz, "Did you find the Apothecary?")
        if (access.inv.count(CADAVA_POTION) > 0) {
            chatPlayer(happy, "I've got the cadava potion.")
            chatNpc(
                happy,
                "Good! Excellent work! Now take it to Juliet - she's expecting you. I'll have a " +
                    "word with Romeo and make sure he knows what to do.",
            )
            return
        }
        chatPlayer(neutral, "I did. He says I need to find some cadava berries.")
        chatNpc(neutral, "Well, good luck with that... they're tricky to come by.")
        chatPlayer(quiz, "Any idea where I should start looking?")
        chatNpc(
            neutral,
            "I overheard some children saying they'd seen some the other day, on a trip to the " +
                "mine south-east of Varrock.",
        )
        chatPlayer(neutral, "Alright, that's as good a place to start as any.")
    }

    private suspend fun Dialogue.seekAQuest() {
        chatNpc(neutral, "Hello, adventurer. Are you looking for a quest?")
        val answer =
            choice3(
                "I'm always looking for a quest.",
                1,
                "No, I'd rather just kill things.",
                2,
                "Can you recommend a good bar?",
                3,
            )
        when (answer) {
            1 -> {
                chatPlayer(happy, "I'm always looking for a quest.")
                chatNpc(
                    neutral,
                    "Well, I've seen poor Romeo drifting around the square. I think he could do " +
                        "with some help.",
                )
                chatNpc(sad, "I used to help him and Juliet meet, but it's become impossible.")
            }
            2 -> {
                chatPlayer(happy, "No, I'd rather just kill things.")
                chatNpc(neutral, "A fine career in these lands. There's more needing killing every day.")
            }
            else -> smallTalk()
        }
    }

    private suspend fun Dialogue.smallTalk() {
        chatPlayer(quiz, "Can you recommend a good bar?")
        chatNpc(angry, "Drink will be the death of you!")
        chatNpc(
            neutral,
            "Though the Blue Moon Inn is cheap enough, and they let you stay all night so long as " +
                "you buy a drink every hour.",
        )
    }

    private fun preach(father: Npc) {
        father.say(SERMON[random.of(SERMON.size)])
        father.aiTimer(random.of(SERMON_MIN_TICKS, SERMON_MAX_TICKS))
    }

    private fun snore(sleeper: Npc) {
        sleeper.say(SNORE)
        sleeper.aiTimer(random.of(SNORE_MIN_TICKS, SNORE_MAX_TICKS))
    }

    private companion object {
        val CONGREGATION =
            listOf(
                "npc.romeo_juliet_pew_sleeping_man",
                "npc.romeo_juliet_pew_sleeping_woman",
            )

        const val SNORE = "Zzzzzzzzz"
        const val ZONE_RADIUS = 1
        const val CAMERA_HEIGHT = 700
        const val LOOK_HEIGHT = 150
        const val CAMERA_RATE = 100

        const val SERMON_MIN_TICKS = 20
        const val SERMON_MAX_TICKS = 45
        const val SNORE_MIN_TICKS = 8
        const val SNORE_MAX_TICKS = 20

        val PEW_CAMERA_FROM = CoordGrid(3254, 3486, 0)
        val PEW_CAMERA_AT = CoordGrid(3254, 3477, 0)

        val SERMON =
            listOf(
                "...and so Saradomin said unto the people...",
                "Repent, for the end of the sermon is nigh! Well... nigh-ish.",
                "Blessed are the patient, for they shall hear my closing remarks.",
                "Wake up at the back there!",
                "...which brings me neatly to my fourteenth point...",
            )
    }
}
