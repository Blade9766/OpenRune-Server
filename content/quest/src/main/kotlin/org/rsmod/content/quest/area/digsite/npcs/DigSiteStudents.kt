package org.rsmod.content.quest.area.digsite.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.digsite.DigSiteExams
import org.rsmod.content.quest.area.digsite.DigSiteStudent
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.OPAL
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAGE_LETTER
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.UNCUT_OPAL
import org.rsmod.content.quest.area.digsite.digsiteOpalRequested
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The three students revising on the digsite. Each of them knows one answer per exam and will only
 * part with the first one after their lost keepsake is handed back; the student in the purple skirt
 * wants an opal for her last answer on top of that.
 */
class DigSiteStudents @Inject constructor(private val quest: TheDigSiteQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        for (student in DigSiteStudent.entries) {
            onOpNpc1(student.npc) { startDialogue(it.npc) { student(student) } }
        }
    }

    private suspend fun Dialogue.student(student: DigSiteStudent) {
        if (quest.stage(player) < STAGE_LETTER) {
            chatPlayer(neutral, "Hello there.")
            chatNpc(bored, "Sorry, I'm revising. The exams are next week.")
            return
        }
        val level = (quest.examLevel(player) + 1).coerceAtMost(MAX_LEVEL)
        val notes = DigSiteExams.question(level, student).notes
        if (quest.knowsAnswer(player, level, student)) {
            repeatNotes(notes)
            return
        }
        when {
            level == 1 -> firstAnswer(student, notes)
            level == 3 && student == DigSiteStudent.PurpleSkirt -> opalAnswer(student, notes)
            else -> laterAnswer(student, notes, level)
        }
    }

    private suspend fun Dialogue.firstAnswer(student: DigSiteStudent, notes: String) {
        if (access.player.inv.contains(student.lostItem)) {
            returnKeepsake(student, notes)
            return
        }
        if (!quest.askedFor(player, student)) {
            askForHelp(student)
            quest.markAsked(player, student)
            return
        }
        stillLooking(student)
    }

    private suspend fun Dialogue.askForHelp(student: DigSiteStudent) {
        when (student) {
            DigSiteStudent.GreenTop -> {
                chatPlayer(neutral, "Hello there.")
                chatPlayer(quiz, "Can you help me with the Earth Sciences exams at all?")
                chatNpc(neutral, "Well... Maybe I will if you help me with something.")
                chatPlayer(quiz, "What's that?")
                chatNpc(sad, "I have lost my recent good find.")
                chatPlayer(quiz, "What does it look like?")
                chatNpc(neutral, "Err... Like an animal skull!")
                chatPlayer(
                    bored,
                    "Well, that's not too helpful, there are lots of those around here; can you " +
                        "remember where you last had it?",
                )
                chatNpc(neutral, "It was around here for sure. Maybe someone picked it up?")
                chatPlayer(neutral, "Okay, I'll have a look for you.")
            }
            DigSiteStudent.PurpleSkirt -> {
                chatPlayer(quiz, "Can you help me with the Earth Sciences exams at all?")
                chatNpc(neutral, "I can if you help me...")
                chatPlayer(quiz, "How can I do that?")
                chatNpc(sad, "I have lost my teddy bear. He was my lucky mascot.")
                chatPlayer(quiz, "Do you know where you dropped him?")
                chatNpc(
                    neutral,
                    "Well, I was doing a lot of walking that day... Oh yes, that's right - we " +
                        "were studying ceramics in fact, near the edge of the digsite.",
                )
                chatNpc(neutral, "I found some pottery that seemed to match the design on those large urns.")
                chatNpc(
                    sad,
                    "I was in the process of checking this out, and when we got back to the " +
                        "centre my lucky mascot had gone!",
                )
                chatPlayer(happy, "Leave it to me, I'll find it.")
                chatNpc(happy, "Oh great! Thanks!")
            }
            DigSiteStudent.OrangeShirt -> {
                chatPlayer(neutral, "Hello there.")
                chatPlayer(quiz, "Can you help me with the Earth Sciences exams at all?")
                chatNpc(sad, "I can't do anything unless I find my special cup.")
                chatPlayer(quiz, "Your what?")
                chatNpc(neutral, "My special cup. I won it for a particularly good find last month.")
                chatPlayer(quiz, "Oh, right. So if I find it you'll help me?")
                chatNpc(happy, "I sure will!")
                chatPlayer(quiz, "Any ideas where it may be?")
                chatNpc(neutral, "All I remember is that I was working near the tents when I lost it.")
                chatPlayer(neutral, "Okay, I'll see what I can do.")
            }
        }
    }

    private suspend fun Dialogue.stillLooking(student: DigSiteStudent) {
        when (student) {
            DigSiteStudent.GreenTop -> {
                chatPlayer(neutral, "Hello there.")
                chatPlayer(quiz, "How's the study going?")
                chatNpc(neutral, "Very well thanks. Have you found my animal skull yet?")
                chatPlayer(sad, "No sorry, not yet.")
                chatNpc(
                    neutral,
                    "Oh well, I am sure it's been picked up. Couldn't you try looking through " +
                        "some pockets?",
                )
            }
            DigSiteStudent.PurpleSkirt -> {
                chatPlayer(quiz, "How's the study going?")
                chatNpc(neutral, "Very well thanks. Have you found my lucky mascot yet?")
                chatPlayer(sad, "No sorry, not yet.")
                chatNpc(neutral, "I'm sure it's just outside the digsite somewhere...")
            }
            DigSiteStudent.OrangeShirt -> {
                chatPlayer(neutral, "Hello there.")
                chatPlayer(quiz, "How's the study going?")
                chatNpc(neutral, "I'm getting there. Have you found my special cup yet?")
                chatPlayer(sad, "No sorry, not yet.")
                chatNpc(
                    sad,
                    "Oh dear, I hope it didn't fall into the stream; I might never find it again.",
                )
            }
        }
    }

    private suspend fun Dialogue.returnKeepsake(student: DigSiteStudent, notes: String) {
        when (student) {
            DigSiteStudent.GreenTop -> {
                chatPlayer(neutral, "Hello there.")
                chatPlayer(happy, "Is this your animal skull?")
                chatNpc(
                    happy,
                    "Oh wow! You've found it! Thank you so much. I'll be glad to tell you what I " +
                        "know about the exam.",
                )
            }
            DigSiteStudent.PurpleSkirt -> {
                chatPlayer(happy, "Guess what I found.")
                chatNpc(
                    happy,
                    "Hey! My lucky mascot! Thanks ever so much. Let me help you with those " +
                        "questions now.",
                )
            }
            DigSiteStudent.OrangeShirt -> {
                chatPlayer(neutral, "Hello there.")
                chatPlayer(happy, "Look what I found!")
                chatNpc(happy, "Excellent! I'm so happy. Let me now help you with your exams...")
            }
        }
        access.invDel(access.inv, student.lostItem)
        teach(student, notes, level = 1)
    }

    private suspend fun Dialogue.laterAnswer(student: DigSiteStudent, notes: String, level: Int) {
        when (student) {
            DigSiteStudent.GreenTop -> {
                chatPlayer(neutral, "Hello there.")
                chatNpc(quiz, "How's it going?")
                chatPlayer(neutral, "I need more help with the exam.")
                chatNpc(neutral, "Well ok, this is what I have learned since I last spoke to you...")
            }
            DigSiteStudent.PurpleSkirt -> {
                chatNpc(quiz, "How's it going?")
                chatPlayer(neutral, "I am stuck on some more exam questions.")
                chatNpc(neutral, "Okay, I'll tell you my latest notes...")
            }
            DigSiteStudent.OrangeShirt -> {
                chatPlayer(neutral, "Hello there.")
                chatNpc(quiz, "How's it going?")
                chatPlayer(neutral, "There are more exam questions I'm stuck on.")
                chatNpc(neutral, "Hey, I'll tell you what I've learned. That may help.")
            }
        }
        teach(student, notes, level)
    }

    /** Her last answer is not free: she wants a precious stone out of the river first. */
    private suspend fun Dialogue.opalAnswer(student: DigSiteStudent, notes: String) {
        if (!access.player.digsiteOpalRequested) {
            chatPlayer(neutral, "Hello there.")
            chatNpc(quiz, "What, you want more help?")
            chatPlayer(happy, "Err... Yes please!")
            chatNpc(shifty, "Well... It's going to cost you...")
            chatPlayer(quiz, "Oh, well how much?")
            chatNpc(
                happy,
                "I'll tell you what I would like: a precious stone. I don't find many of them. My " +
                    "favourites are opals; they are beautiful.",
            )
            chatNpc(laugh, "Just like me! Tee hee hee!")
            chatPlayer(neutral, "Err... OK I'll see what I can do, but I'm not sure where I'd get one.")
            chatNpc(neutral, "Well, I have seen people get them from panning occasionally.")
            chatPlayer(neutral, "OK, I'll see what I can turn up for you.")
            access.player.digsiteOpalRequested = true
            return
        }
        val opal = OPALS.firstOrNull { access.player.inv.contains(it) }
        chatPlayer(neutral, "Hello there.")
        chatNpc(quiz, "Oh, hi again. Did you bring me the opal?")
        if (opal == null) {
            chatPlayer(sad, "I haven't found one yet.")
            chatNpc(
                neutral,
                "Oh well, tell me when you do. Remember that they can be found around the " +
                    "digsite; perhaps try panning the river.",
            )
            return
        }
        chatPlayer(happy, "Would an opal look like this by any chance?")
        chatNpc(
            happy,
            "Wow, great, you've found one. This will look beautiful set in my necklace. Thanks " +
                "for that; now I'll tell you what I know...",
        )
        access.invDel(access.inv, opal)
        teach(student, notes, level = 3)
    }

    private suspend fun Dialogue.repeatNotes(notes: String) {
        chatPlayer(neutral, "Hello there.")
        chatNpc(quiz, "How's it going?")
        chatPlayer(neutral, "Could you go over that again?")
        chatNpc(neutral, notes)
        chatPlayer(neutral, "Thanks, I'll remember that.")
    }

    private suspend fun Dialogue.teach(student: DigSiteStudent, notes: String, level: Int) {
        chatNpc(neutral, notes)
        quest.learnAnswer(player, level, student)
        when (student) {
            DigSiteStudent.GreenTop -> chatPlayer(happy, "Okay, I'll remember that.")
            DigSiteStudent.PurpleSkirt -> chatPlayer(happy, "Great, thanks for your advice.")
            DigSiteStudent.OrangeShirt -> chatPlayer(happy, "Thanks for the information.")
        }
    }

    private companion object {
        const val MAX_LEVEL = 3

        val OPALS = listOf(OPAL, UNCUT_OPAL)
    }
}
