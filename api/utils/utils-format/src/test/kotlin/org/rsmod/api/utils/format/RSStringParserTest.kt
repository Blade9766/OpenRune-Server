package org.rsmod.api.utils.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import toRs

class RSStringParserTest {
    @Test
    fun `converts friendly colours to client tags`() {
        assertEquals("<col=800000>danger</col>", "<red>danger</red>".toRs())
        assertEquals("<col=000080>calm</col>", "<blue>calm</blue>".toRs())
        assertEquals("<col=0000ff>hex</col>", "<0000ff>hex</0000ff>".toRs())
    }

    @Test
    fun `reopens the enclosing colour after a nested one closes`() {
        val result = "<blue>a <red>b</red> c</blue>".toRs()
        assertEquals("<col=000080>a </col><col=800000>b</col><col=000080> c</col>", result)
    }

    @Test
    fun `styles keep their colour and close in order`() {
        assertEquals("<col=000000><str>gone</str></col>", "<black><strike>gone</strike></black>".toRs())
        assertEquals("<str=800000>gone</str>", "<strike=red>gone</strike>".toRs())
    }

    @Test
    fun `passes unknown tags through without counting them when wrapping`() {
        val result = "<br><br><col=FF0000>QUEST COMPLETE!</col>".toRs(wrapAt = 64)
        assertEquals("<br><br><col=ff0000>QUEST COMPLETE!</col>", result)
    }

    @Test
    fun `wraps at the last space before the limit`() {
        val lines = ("word ".repeat(30)).trim().toRs(wrapAt = 20).split("<br>")
        assertTrue(lines.size > 1)
        for (line in lines) {
            assertTrue(line.visibleLength() <= 20, "line over the limit: '$line'")
        }
    }

    /**
     * Every wrapped line has to stand on its own: the client resolves `</col>` against the text it
     * is drawing, so a colour left open at a line break bleeds over the whole of the next line.
     */
    @Test
    fun `each wrapped line opens and closes its own tags`() {
        val text =
            "<blue>Dark wizards cursed the stone circle south of Varrock long ago. " +
                "<red>Kaqemeex</red>, at the druids' circle north of Taverley, has asked me to " +
                "help purify it.</blue>"
        for (line in text.toRs(wrapAt = 64).split("<br>")) {
            assertBalanced(line)
        }
    }

    @Test
    fun `a highlight starting a wrapped line does not colour the rest of it`() {
        val text =
            "<blue>Sanfew needs the raw meat of four different animals for his potion to " +
                "honour Guthix. Each one has to be dipped in the <red>Cauldron of Thunder</red>, " +
                "deep in the dungeon south of Taverley.</blue>"
        val highlighted = text.toRs(wrapAt = 64).split("<br>").single { "Cauldron of Thunder" in it }
        assertTrue(
            highlighted.endsWith("<col=000080>, deep in the dungeon south of Taverley.</col>"),
            "highlight leaked into the rest of the line: '$highlighted'",
        )
    }

    /**
     * Both callers split the result on `<br>` and hand each piece to its own widget, so a colour
     * that is open across an explicit break would bleed over the line after it.
     */
    @Test
    fun `an explicit break ends the line and its tags`() {
        val result = "<blue>keep this<br>and this</blue>".toRs()
        assertEquals("<col=000080>keep this</col><br><col=000080>and this</col>", result)
        for (line in result.split("<br>")) {
            assertBalanced(line)
        }
    }

    @Test
    fun `wrapping preserves every visible character`() {
        val text = "<blue>one <red>two</red> three four five six seven eight nine ten eleven</blue>"
        val wrapped = text.toRs(wrapAt = 16).split("<br>").joinToString("") { it.stripTags() }
        assertEquals("one two three four five six seven eight nine ten eleven", wrapped)
    }

    private fun assertBalanced(line: String) {
        val opens = ArrayDeque<String>()
        for (tag in TAG.findAll(line).map { it.value }) {
            val name = tag.removeSurrounding("<", ">")
            if (name.startsWith("/")) {
                val expected = opens.removeLastOrNull()
                assertEquals(name.removePrefix("/"), expected, "unbalanced close in '$line'")
            } else if (name != "br") {
                opens.addLast(name.substringBefore('='))
            }
        }
        assertTrue(opens.isEmpty(), "tags left open in '$line': $opens")
    }

    private fun String.stripTags(): String = TAG.replace(this, "")

    private fun String.visibleLength(): Int = stripTags().length

    private companion object {
        val TAG = Regex("<[^>]*>")
    }
}
