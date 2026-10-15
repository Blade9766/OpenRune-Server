
public enum class RsColor(public val tag: String, public val hex: String) {
    RED("red", "800000"),
    GREEN("green", "00ff00"),
    BLUE("blue", "000080"),
    WHITE("white", "ffffff"),
    YELLOW("yellow", "ffff00"),
    CYAN("cyan", "00ffff"),
    MAGENTA("magenta", "ff00ff"),
    BLACK("black", "000000");

    public companion object {
        public fun lookup(nameOrHex: String): String? {
            val key = nameOrHex.lowercase()
            for (c in values()) if (c.tag == key) return c.hex
            return if (key.matches(Regex("^[0-9a-fA-F]{3,8}$"))) key else null
        }
    }
}

public enum class RsStyle(public val tag: String, public val rsBase: String) {
    STRIKE("strike", "str"),
    UNDERLINE("underline", "u"),
    SHADOW("shad", "shad");

    public companion object {
        public fun fromTag(tag: String): RsStyle? =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) || it.rsBase.equals(tag, ignoreCase = true) }
    }
}

private data class ActiveTag(val type: String, val value: String?)

/** The colour and styles in force at one point in the text. */
private data class RsFormat(val colour: ActiveTag?, val styles: List<ActiveTag>) {
    fun open(): String = buildString {
        colour?.let { append(it.render()) }
        for (style in styles) append(style.render())
    }

    fun close(): String = buildString {
        for (style in styles.asReversed()) append("</${style.type}>")
        if (colour != null) append("</col>")
    }

    companion object {
        val NONE = RsFormat(null, emptyList())
    }
}

private fun ActiveTag.render(): String = if (value != null) "<$type=$value>" else "<$type>"

private sealed interface RsPiece {
    val format: RsFormat
}

private data class RsChar(val char: Char, override val format: RsFormat) : RsPiece

/** A tag this parser does not understand, passed through untouched and not counted when wrapping. */
private data class RsRaw(val text: String, override val format: RsFormat) : RsPiece

/** An explicit `<br>` in the source, which ends the line it appears on. */
private data class RsBreak(override val format: RsFormat) : RsPiece

/**
 * Converts the friendly markup used by quest journals and scrolls (`<red>`, `<strike>`,
 * `<shad=ffffff>`, `<0000ff>`) into the client's own tags, optionally wrapping at [wrapAt] visible
 * characters with `<br>`.
 *
 * The result is a series of lines joined by `<br>`, and each line opens and closes every tag it
 * uses: tags are emitted flat rather than nested. The client resolves `</col>` against the text it
 * is drawing rather than a tag stack, so a colour left open at a line break leaks over the whole
 * of the next line.
 */
public fun String.toRs(wrapAt: Int? = null): String {
    val pieces = parsePieces()
    return pieces.splitLines(wrapAt).joinToString("<br>") { it.render() }
}

private fun String.parsePieces(): List<RsPiece> {
    val pieces = mutableListOf<RsPiece>()
    val stack = ArrayDeque<ActiveTag>()
    var i = 0

    fun format(): RsFormat =
        RsFormat(stack.lastOrNull { it.type == "col" }, stack.filter { it.type != "col" })

    while (i < length) {
        val ch = this[i]
        if (ch != '<') {
            pieces += RsChar(ch, format())
            i++
            continue
        }

        val closeIndex = indexOf('>', i)
        if (closeIndex == -1) {
            substring(i).forEach { pieces += RsChar(it, format()) }
            break
        }

        val rawTag = substring(i + 1, closeIndex)
        val tagContent = rawTag.lowercase().trim()
        i = closeIndex + 1

        val styleWithColour = STYLE_WITH_COLOUR.matchEntire(tagContent)
        val explicitColour = EXPLICIT_COLOUR.matchEntire(tagContent)
        when {
            tagContent.startsWith("/") -> stack.popClosed(tagContent.removePrefix("/"))

            styleWithColour != null -> {
                val style = RsStyle.fromTag(styleWithColour.groupValues[1])!!
                val colour = RsColor.lookup(styleWithColour.groupValues[2]) ?: styleWithColour.groupValues[2]
                stack.addLast(ActiveTag(style.rsBase, colour))
            }

            RsStyle.fromTag(tagContent) != null ->
                stack.addLast(ActiveTag(RsStyle.fromTag(tagContent)!!.rsBase, null))

            explicitColour != null -> stack.addLast(ActiveTag("col", explicitColour.groupValues[1]))

            RsColor.lookup(tagContent) != null ->
                stack.addLast(ActiveTag("col", RsColor.lookup(tagContent)!!))

            tagContent == "br" || tagContent == "br/" -> pieces += RsBreak(format())

            else -> pieces += RsRaw("<$rawTag>", format())
        }
    }
    return pieces
}

/** Removes the innermost tag [name] closes, falling back to the innermost tag of any kind. */
private fun ArrayDeque<ActiveTag>.popClosed(name: String) {
    val type =
        when {
            name == "col" || RsColor.lookup(name) != null -> "col"
            else -> RsStyle.fromTag(name)?.rsBase
        }
    val index = indexOfLast { type == null || it.type == type }
    if (index != -1) removeAt(index) else removeLastOrNull()
}

/**
 * Splits [this] at every explicit break and, when [maxLength] is set, wherever a line would run
 * past that many visible characters, breaking at the last space before the limit. Unknown tags
 * take up no room, matching how the client measures text.
 */
private fun List<RsPiece>.splitLines(maxLength: Int?): List<List<RsPiece>> {
    val lines = mutableListOf<List<RsPiece>>()
    var current = mutableListOf<RsPiece>()
    var visible = 0
    var lastSpace = -1
    var lastSpaceVisible = 0

    for (piece in this) {
        if (piece is RsBreak) {
            lines += current
            current = mutableListOf()
            visible = 0
            lastSpace = -1
            lastSpaceVisible = 0
            continue
        }
        current += piece
        if (piece !is RsChar || maxLength == null) {
            continue
        }
        visible++
        if (piece.char.isWhitespace()) {
            lastSpace = current.size - 1
            lastSpaceVisible = visible
        }
        if (visible < maxLength) {
            continue
        }
        val cut = if (lastSpace != -1) lastSpace + 1 else current.size
        lines += current.subList(0, cut).toList()
        current = current.subList(cut, current.size).toMutableList()
        visible = if (lastSpace != -1) visible - lastSpaceVisible else 0
        lastSpace = -1
        lastSpaceVisible = 0
    }
    lines += current
    return lines
}

private fun List<RsPiece>.render(): String {
    val out = StringBuilder()
    var open = RsFormat.NONE
    for (piece in this) {
        if (piece.format != open) {
            out.append(open.close())
            out.append(piece.format.open())
            open = piece.format
        }
        when (piece) {
            is RsChar -> out.append(piece.char)
            is RsRaw -> out.append(piece.text)
            is RsBreak -> Unit
        }
    }
    out.append(open.close())
    return out.toString()
}

private val STYLE_WITH_COLOUR = Regex("^(strike|underline|shad|str|u)=([a-z0-9]+)$")
private val EXPLICIT_COLOUR = Regex("^col=([0-9a-f]{1,8})$")
