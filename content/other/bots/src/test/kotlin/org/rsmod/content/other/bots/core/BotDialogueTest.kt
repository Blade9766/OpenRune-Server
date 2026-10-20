package org.rsmod.content.other.bots.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BotDialogueTest {
    private val sarimMenu = listOf("No, not right now.", "Yes, okay, I'm ready to go.")

    @Test
    fun `continues every page`() {
        val dialogue = BotDialogue(emptyList())
        val page = DialogueScreen.Page("component.chat_left:continue")
        assertEquals(DialogueReply.Continue("component.chat_left:continue"), dialogue.reply(page))
        assertEquals(DialogueReply.Continue("component.chat_left:continue"), dialogue.reply(page))
    }

    @Test
    fun `picks the wanted option by its 1-based line`() {
        val dialogue = BotDialogue(listOf("Yes, okay, I'm ready to go."))
        assertEquals(DialogueReply.Pick(2), dialogue.reply(DialogueScreen.Choice(sarimMenu)))
    }

    @Test
    fun `matching ignores case, tags, spacing and end punctuation`() {
        val dialogue = BotDialogue(listOf("yes,  okay, i'm READY to go"))
        val menu = listOf("<col=ff0000>No, not right now.</col>", "<col=0000ff>Yes, okay, I'm ready to go!</col>")
        assertEquals(DialogueReply.Pick(2), dialogue.reply(DialogueScreen.Choice(menu)))
    }

    @Test
    fun `a wanted line may be the start of a longer option`() {
        val dialogue = BotDialogue(listOf("Yes"))
        assertEquals(DialogueReply.Pick(2), dialogue.reply(DialogueScreen.Choice(sarimMenu)))
    }

    @Test
    fun `an exact match wins over an earlier option that only starts with it`() {
        val dialogue = BotDialogue(listOf("Yes"))
        val menu = listOf("Yes, but later.", "Yes.")
        assertEquals(DialogueReply.Pick(2), dialogue.reply(DialogueScreen.Choice(menu)))
    }

    @Test
    fun `the first wanted line on offer wins, whatever its position in the menu`() {
        val dialogue = BotDialogue(listOf("Take me to Entrana.", "Yes, okay, I'm ready to go."))
        assertEquals(DialogueReply.Pick(2), dialogue.reply(DialogueScreen.Choice(sarimMenu)))
        val both = listOf("Yes, okay, I'm ready to go.", "Take me to Entrana.")
        assertEquals(DialogueReply.Pick(2), dialogue.reply(DialogueScreen.Choice(both)))
    }

    @Test
    fun `gives up on a menu offering nothing it was told to pick`() {
        val dialogue = BotDialogue(listOf("Yes, I'm ready to go."))
        val reply = dialogue.reply(DialogueScreen.Choice(listOf("Will you keep them safe for me?", "I'll leave them somewhere myself.")))
        assertTrue(reply is DialogueReply.GiveUp)
    }

    @Test
    fun `gives up on a menu whose lines it could not read`() {
        assertTrue(BotDialogue(listOf("Yes")).reply(DialogueScreen.Choice(emptyList())) is DialogueReply.GiveUp)
    }

    @Test
    fun `blank wanted lines never match`() {
        assertTrue(BotDialogue(listOf("", "  ")).reply(DialogueScreen.Choice(sarimMenu)) is DialogueReply.GiveUp)
    }

    @Test
    fun `gives up on a conversation that never ends`() {
        val dialogue = BotDialogue(emptyList(), maxScreens = 3)
        val page = DialogueScreen.Page("component.messagebox:continue")
        repeat(3) { assertTrue(dialogue.reply(page) is DialogueReply.Continue) }
        assertTrue(dialogue.reply(page) is DialogueReply.GiveUp)
    }
}
