package org.rsmod.content.other.bots.core

import kotlin.random.Random

/** Silly display names: at most 12 characters, never ending in "bot", unique among [taken]. */
object BotNames {
    private const val MAX_LENGTH = 12

    private val CURATED =
        listOf(
            "Soggy Waffle",
            "Captn Turnip",
            "Sir Wobbles",
            "Lord Pudding",
            "Noodle Arms",
            "Grumpy Goose",
            "Mr Crumpet",
            "Pickle Pete",
            "Turbo Snail",
            "Dame Biscuit",
            "Wonky Donkey",
            "Baron Bagel",
            "Sneaky Prawn",
            "Moist Muffin",
            "Lil Tadpole",
            "Chonky Yak",
            "Bean Wizard",
            "Duke Nugget",
            "Fluffy Axe",
            "Spud Lord",
            "Clumsy Gnome",
            "Gravy Train",
            "Mr Wiggles",
            "Rusty Spork",
            "Toast Ghost",
            "Cabbage Lord",
            "Dizzy Llama",
            "Lumpy Gravy",
            "Snoozy Yeti",
            "Goblin Pal",
        )

    private val ADJECTIVES =
        listOf(
            "Soggy", "Wobbly", "Grumpy", "Sneaky", "Chonky", "Dizzy", "Lumpy", "Fuzzy",
            "Rusty", "Sleepy", "Cheeky", "Spicy", "Moist", "Crispy", "Bouncy", "Salty",
            "Sticky", "Funky", "Jolly", "Nifty", "Goofy", "Zesty", "Squishy", "Snazzy",
        )

    private val NOUNS =
        listOf(
            "Turnip", "Goose", "Waffle", "Pickle", "Muffin", "Noodle", "Yak", "Bagel",
            "Prawn", "Gnome", "Spork", "Llama", "Potato", "Crumpet", "Badger", "Cabbage",
            "Nugget", "Biscuit", "Walrus", "Toad", "Kebab", "Parsnip", "Pudding", "Weasel",
        )

    fun generate(taken: Set<String>, random: Random = Random.Default): String {
        val lowerTaken = taken.mapTo(HashSet()) { it.lowercase() }
        val curated = CURATED.filter { it.isValid(lowerTaken) }
        if (curated.isNotEmpty()) {
            return curated.random(random)
        }
        repeat(200) {
            val candidate = combine(random)
            if (candidate.isValid(lowerTaken)) {
                return candidate
            }
        }
        while (true) {
            val base = combine(random).take(MAX_LENGTH - 3)
            val candidate = "$base${random.nextInt(100, 1000)}"
            if (candidate.isValid(lowerTaken)) {
                return candidate
            }
        }
    }

    fun isAcceptable(name: String): Boolean =
        name.length in 1..MAX_LENGTH &&
            name.all { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' } &&
            !name.trimEnd().lowercase().endsWith("bot") &&
            name == name.trim()

    private fun combine(random: Random): String {
        val adjective = ADJECTIVES.random(random)
        val noun = NOUNS.random(random)
        val spaced = "$adjective $noun"
        return if (spaced.length <= MAX_LENGTH) spaced else "$adjective$noun"
    }

    private fun String.isValid(lowerTaken: Set<String>): Boolean =
        isAcceptable(this) && lowercase() !in lowerTaken
}
