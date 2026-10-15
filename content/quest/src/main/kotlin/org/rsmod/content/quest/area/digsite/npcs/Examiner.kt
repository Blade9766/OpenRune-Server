package org.rsmod.content.quest.area.digsite.npcs

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.herbloreLvl
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.digsite.DigSiteExams
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.AGILITY_REQ
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CERTIFICATE_1
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CERTIFICATE_2
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CERTIFICATE_3
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.EXAMINERS
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.HERBLORE_REQ
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.PLAIN_LETTER
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAGE_FAILED
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAGE_LETTER
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAGE_LEVEL1
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAGE_LEVEL2
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAGE_LEVEL3
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAGE_STAMPED
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAMPED_LETTER
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.THIEVING_REQ
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.TROWEL
import org.rsmod.content.quest.area.digsite.carriesOrBanks
import org.rsmod.content.quest.area.digsite.setVarBit
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The three examiners in the Exam Centre. They hand out the letter of recommendation, sit the
 * player through each Earth Sciences exam and replace lost trowels.
 *
 * The exam itself is three multiple-choice questions. Only three of each question's four answers
 * are ever offered, and the right one only appears once the student who knows it has been paid in
 * lost property - so the first sitting cannot be passed, exactly as the quest intends.
 */
class Examiner @Inject constructor(private val quest: TheDigSiteQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        for (examiner in EXAMINERS) {
            onOpNpc1(examiner) { startDialogue(it.npc) { examiner() } }
            onOpNpcU(examiner) { useOnExaminer(it.npc, it.objType.internalName) }
        }
    }

    private suspend fun ProtectedAccess.useOnExaminer(npc: Npc, obj: String) {
        when (obj) {
            PLAIN_LETTER ->
                startDialogue(npc) {
                    chatNpc(bored, "I don't really want this back until it's been stamped!")
                }
            STAMPED_LETTER ->
                startDialogue(npc) {
                    if (quest.stage(player) == STAGE_STAMPED) {
                        handInLetter()
                    } else {
                        chatNpc(bored, "You've already shown me that! Get digging!")
                    }
                }
            else -> mes("Nothing interesting happens.")
        }
    }

    private suspend fun Dialogue.examiner() {
        when (quest.stage(player)) {
            0 -> introduction()
            STAGE_LETTER -> waitingForLetter()
            STAGE_STAMPED -> handInLetter()
            STAGE_FAILED -> retakeLevel1()
            STAGE_LEVEL1 -> laterExam(2)
            STAGE_LEVEL2 -> laterExam(3)
            else -> chatNpc(bored, "Well, what are you doing here? Get digging!")
        }
    }

    private suspend fun Dialogue.introduction() {
        chatPlayer(neutral, "Hello.")
        chatNpc(
            happy,
            "Ah hello there! I am the resident lecturer on antiquities and artefacts. I also set " +
                "the Earth Sciences exams.",
        )
        chatPlayer(quiz, "Earth Sciences?")
        chatNpc(
            neutral,
            "That is right dear, the world of Gielinor holds many wonders beneath its surface. " +
                "Students come to me to take exams so that they may join in on the archaeological " +
                "dig going on just north of here.",
        )
        chatPlayer(quiz, "So if they don't pass the exams they can't dig at all?")
        chatNpc(
            neutral,
            "That's right! We have to make sure that students know enough to be able to dig " +
                "safely and not damage the artefacts.",
        )
        val start =
            choice2("Can I take an exam?", true, "Interesting...", false)
        if (!start) {
            chatPlayer(neutral, "Interesting...")
            chatNpc(neutral, "You could gain much with an understanding of the world below.")
            return
        }
        chatPlayer(quiz, "Can I take an exam?")
        chatNpc(neutral, "Are you sure you want to? Our exams are serious business.")
        if (missingRequirements().isNotEmpty()) {
            mesbox(
                "Before starting this quest, be aware that one or more of your skill levels are " +
                    "lower than what is required to fully complete it.",
            )
        }
        val accept =
            choice2("Yes.", true, "No.", false, title = "Start The Dig Site quest?")
        if (!accept) {
            chatPlayer(neutral, "On second thoughts, I'd better not.")
            return
        }
        if (access.inv.isFull()) {
            chatNpc(bored, "You have nowhere to put my letter. Come back when your hands are freer.")
            return
        }
        chatPlayer(happy, "Of course.")
        chatNpc(
            neutral,
            "Very well. You can if you get this letter stamped by the Curator of Varrock's museum.",
        )
        chatPlayer(quiz, "Why's that then?")
        chatNpc(
            neutral,
            "Because he is a very knowledgeable man and employs our archaeological expert. I'm " +
                "sure he knows a lot about your exploits and can judge whether you'd make a good " +
                "archaeologist or not.",
        )
        chatNpc(neutral, "Besides, the museum contributes funds to the dig.")
        chatPlayer(quiz, "But why are you writing the letter? Shouldn't he?")
        chatNpc(
            neutral,
            "He's also a very busy man, so I write the letters and he just stamps them if he " +
                "approves.",
        )
        quest.advanceTo(access, STAGE_LETTER)
        giveLetter(PLAIN_LETTER, "The examiner hands you a letter.")
        setVarBit(player, "varbit.itexaminerletter", 1)
        chatPlayer(
            happy,
            "Oh, I see. I'll ask him if he'll approve me, and bring my stamped letter back here. " +
                "Thanks.",
        )
    }

    private fun Dialogue.missingRequirements(): List<String> = buildList {
        if (player.agilityLvl < AGILITY_REQ) add("Agility")
        if (player.herbloreLvl < HERBLORE_REQ) add("Herblore")
        if (player.thievingLvl < THIEVING_REQ) add("Thieving")
    }

    private suspend fun Dialogue.waitingForLetter() {
        chatPlayer(neutral, "Hello.")
        chatNpc(neutral, "Hello again.")
        chatNpc(neutral, "I am still waiting for your letter of recommendation.")
        val lost =
            choice2(
                "I have lost the letter you gave me.",
                true,
                "Alright I'll try and get it.",
                false,
            )
        if (!lost) {
            chatPlayer(neutral, "Alright, I'll try and get it.")
            chatNpc(
                neutral,
                "I am sure you won't get any problems. Speak to the Curator of Varrock's museum.",
            )
            return
        }
        chatPlayer(sad, "I have lost the letter you gave me.")
        if (access.carriesOrBanks(PLAIN_LETTER)) {
            chatNpc(angry, "Oh now come on. You have it with you!")
            return
        }
        chatNpc(bored, "That was foolish. Take this one and don't lose it!")
        giveLetter(PLAIN_LETTER, "The examiner hands you another letter.")
    }

    private suspend fun Dialogue.handInLetter() {
        chatPlayer(neutral, "Hello.")
        chatNpc(neutral, "Hello again.")
        if (!access.player.inv.contains(STAMPED_LETTER)) {
            chatNpc(bored, "Bring me the stamped letter and we can begin.")
            return
        }
        chatPlayer(happy, "Here is the stamped letter you asked for.")
        chatNpc(happy, "Good good, we will begin the exam...")
        access.invDel(access.inv, STAMPED_LETTER)
        setVarBit(player, "varbit.itcuratorletter", 0)
        sitExam(1)
    }

    private suspend fun Dialogue.retakeLevel1() {
        chatPlayer(neutral, "Hello.")
        chatNpc(quiz, "Hello again. Are you ready for another shot at the exam?")
        val retake = choice2("Yes, I certainly am.", true, "No, not at the moment.", false)
        if (!retake) {
            chatPlayer(neutral, "No, not at the moment.")
            chatNpc(neutral, "Okay, take your time if you wish.")
            return
        }
        sitExam(1)
    }

    private suspend fun Dialogue.laterExam(level: Int) {
        chatPlayer(neutral, "Hello.")
        if (level == 2) {
            chatNpc(happy, "Hi there!")
        } else {
            chatNpc(happy, "Ah, hello again.")
        }
        val readyText =
            if (level == 2) "I am ready for the next exam." else "I am ready for the last exam..."
        val choice =
            choice4(
                readyText,
                1,
                "I am stuck on a question.",
                2,
                "Sorry, I didn't mean to disturb you.",
                3,
                "I have lost my trowel.",
                4,
            )
        when (choice) {
            1 -> {
                chatPlayer(happy, readyText)
                sitExam(level)
            }
            2 -> {
                chatPlayer(sad, "I am stuck on a question.")
                chatNpc(
                    bored,
                    "Well, well, have you not been doing any studies? I am not giving you the " +
                        "answers, talk to the other students and remember the answers.",
                )
            }
            3 -> {
                chatPlayer(neutral, "Sorry, I didn't mean to disturb you.")
                chatNpc(happy, "Oh, no problem at all.")
            }
            else -> replaceTrowel()
        }
    }

    private suspend fun Dialogue.replaceTrowel() {
        chatPlayer(sad, "I have lost my trowel.")
        if (access.carriesOrBanks(TROWEL)) {
            chatNpc(bored, "Really? Look in your backpack and make sure first.")
            return
        }
        chatNpc(
            bored,
            "Deary me... That was a good one as well. It's a good job I have another. Here you go...",
        )
        giveLetter(TROWEL, "The examiner hands you a trowel.")
    }

    private suspend fun Dialogue.sitExam(level: Int) {
        val rewardSlots = if (level == 1) 2 else 1
        if (access.inv.freeSpace() < rewardSlots) {
            chatNpc(
                bored,
                "There would be nowhere to put your certificate. Make some room in your backpack " +
                    "and come back to me.",
            )
            return
        }
        chatNpc(neutral, EXAM_INTROS.getValue(level))
        var correct = 0
        val questions = DigSiteExams.questions(level)
        for ((index, question) in questions.withIndex()) {
            if (index > 0) {
                chatNpc(neutral, "Okay, next question...")
            }
            chatNpc(quiz, question.ask)
            val options = question.offered(quest.knowsAnswer(player, level, question.student))
            val picked =
                choice3(options[0], options[0], options[1], options[1], options[2], options[2])
            chatPlayer(neutral, picked)
            if (picked == question.answer) {
                correct++
            }
        }
        chatNpc(neutral, "Okay, that concludes the level $level Earth Sciences exam.")
        chatNpc(neutral, if (level == 1) "Let's see how you did..." else "Let me add up the results..")
        when (level) {
            1 -> gradeLevel1(correct)
            2 -> gradeLevel2(correct)
            else -> gradeLevel3(correct)
        }
    }

    private suspend fun Dialogue.gradeLevel1(correct: Int) {
        when (correct) {
            0 -> {
                chatNpc(
                    angry,
                    "Oh deary me! This is appalling, none correct at all! I suggest you go and " +
                        "study properly.",
                )
                chatPlayer(sad, "Oh dear...")
                chatNpc(
                    neutral,
                    "Why don't you use the resources here? There are books and the researchers, " +
                        "and you could even ask other students who are also studying for these " +
                        "exams.",
                )
            }
            1 -> {
                chatNpc(bored, "You got 1 question correct. Better luck next time.")
                chatPlayer(sad, "Oh bother!")
                chatNpc(neutral, "Do some more research. I'm sure other students could help you out.")
            }
            2 -> {
                chatNpc(neutral, "You got 2 questions correct. Not bad, just a little more revision needed.")
                chatPlayer(sad, "Oh well...")
            }
            else -> {
                chatNpc(happy, "You got all of the questions correct! Well done!")
                chatPlayer(happy, "Hey! Excellent!")
                chatNpc(
                    happy,
                    "You have now passed the Earth Sciences level 1 general exam. Here is your " +
                        "certificate to prove it. You also get a decent trowel to dig with. Of " +
                        "course, you'll want to get studying for your next exam now!",
                )
                quest.advanceTo(access, STAGE_LEVEL1)
                access.invAdd(access.inv, CERTIFICATE_1)
                access.invAdd(access.inv, TROWEL)
                doubleobjbox(
                    CERTIFICATE_1,
                    TROWEL,
                    "The examiner hands you a certificate and a trowel.",
                )
                return
            }
        }
        quest.advanceTo(access, STAGE_FAILED)
    }

    private suspend fun Dialogue.gradeLevel2(correct: Int) {
        when (correct) {
            0 -> {
                chatNpc(angry, "No, no, no! This will not do. They are all wrong; start again!")
                chatPlayer(sad, "Oh no!")
                chatNpc(bored, "More studying for you my friend.")
            }
            1 -> {
                chatNpc(bored, "You got 1 question correct. At least it's a start.")
                chatPlayer(sad, "Oh well...")
                chatNpc(neutral, "Get out and explore the digsite, talk to people and learn!")
            }
            2 -> {
                chatNpc(neutral, "You got 2 questions correct. Not too bad, but you can do better...")
                chatPlayer(neutral, "Nearly got it.")
            }
            else -> {
                chatNpc(happy, "You got all the questions correct, well done!")
                chatPlayer(happy, "Great, I'm getting good at this.")
                chatNpc(
                    happy,
                    "You have now passed the Earth Sciences level 2 intermediate exam. Here is " +
                        "your certificate. Of course, you'll want to get studying for your next " +
                        "exam now!",
                )
                quest.advanceTo(access, STAGE_LEVEL2)
                access.invAdd(access.inv, CERTIFICATE_2)
                objbox(CERTIFICATE_2, "The examiner hands you a certificate.")
            }
        }
    }

    private suspend fun Dialogue.gradeLevel3(correct: Int) {
        when (correct) {
            0 -> {
                chatNpc(
                    angry,
                    "I cannot believe this! Absolutely none right at all. I doubt you did any " +
                        "research before you took this exam...",
                )
                chatPlayer(sad, "Ah... Yes... Erm... I think I had better go and revise first!")
            }
            1 -> {
                chatNpc(bored, "You got 1 question correct. Try harder!")
                chatPlayer(sad, "Oh bother!")
            }
            2 -> {
                chatNpc(neutral, "You got 2 questions correct. A little more study and you will pass it.")
                chatPlayer(neutral, "I'm nearly there...")
            }
            else -> {
                chatNpc(happy, "You got all the questions correct, well done!")
                chatPlayer(happy, "Hooray!")
                chatNpc(
                    happy,
                    "Congratulations! You have now passed the Earth Sciences level 3 exam. Here " +
                        "is your level 3 certificate.",
                )
                quest.advanceTo(access, STAGE_LEVEL3)
                access.invAdd(access.inv, CERTIFICATE_3)
                objbox(CERTIFICATE_3, "The examiner hands you a certificate.")
                chatPlayer(happy, "I can dig wherever I want now!")
                chatNpc(
                    neutral,
                    "Perhaps you should use your newfound skills to find an artefact on the " +
                        "digsite that will impress the archaeological expert.",
                )
            }
        }
    }

    private suspend fun Dialogue.giveLetter(obj: String, text: String) {
        access.invAdd(access.inv, obj)
        objbox(obj, text)
    }

    private companion object {
        val EXAM_INTROS =
            mapOf(
                1 to "Okay, we will start with the first exam: Earth Sciences level 1 - Beginner.",
                2 to
                    "Okay, this is the next part of the Earth Sciences exam: Earth Sciences " +
                        "level 2 - Intermediate.",
                3 to
                    "Attention, this is the final part of the Earth Sciences exam: Earth Sciences " +
                        "level 3 - Advanced.",
            )
    }
}
