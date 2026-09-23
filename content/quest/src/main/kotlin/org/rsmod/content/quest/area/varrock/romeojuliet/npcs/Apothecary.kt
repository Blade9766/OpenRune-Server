package org.rsmod.content.quest.area.varrock.romeojuliet.npcs

import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.APOTHECARY
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.CADAVA_BERRIES
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.CADAVA_POTION
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_JULIET_CRYPT
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_SEEN_APOTHECARY
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_SEEN_FATHER
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Apothecary runs the potion shop in south-west Varrock. Besides brewing the cadava potion for
 * Romeo & Juliet, his "Potions" op opens `interface.apothecary_potions` (556), where he mixes
 * strength, energy and antipoison potions from the player's own ingredients.
 */
class Apothecary
@Inject
constructor(
    private val quest: RomeoJulietQuest,
    private val objRepo: ObjRepository,
    private val random: GameRandom,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(APOTHECARY) { startDialogue(it.npc) { apothecary(it.npc) } }
        onOpNpc3(APOTHECARY) { openPotions() }
        onOpNpcU(APOTHECARY) { useOn(it.npc, it.objType.internalName) }
        for (recipe in Recipe.entries) {
            onIfModalButton(recipe.button) { brew(recipe, it.op) }
        }
    }

    private suspend fun Dialogue.apothecary(npc: Npc) {
        chatNpc(neutral, "I am the Apothecary. I brew potions. Do you need anything specific?")
        val wantsPotions =
            choice2("Can you make potions for me?", true, "Talk about something else.", false)
        if (wantsPotions) {
            chatPlayer(quiz, "Can you make potions for me?")
            access.openPotions()
            return
        }
        somethingElse(npc)
    }

    private suspend fun Dialogue.somethingElse(npc: Npc) {
        val romeoJuliet = quest.stage(player) >= STAGE_SEEN_FATHER
        val topic =
            if (romeoJuliet) {
                choice5(
                    "Talk about Romeo & Juliet.",
                    Topic.RomeoJuliet,
                    "Have you got any decent gossip to share?",
                    Topic.Gossip,
                    "Do you know a potion to make hair fall out?",
                    Topic.Hair,
                    "Have you got any good potions to give away?",
                    Topic.Freebie,
                    "No thanks.",
                    Topic.Leave,
                )
            } else {
                choice4(
                    "Have you got any decent gossip to share?",
                    Topic.Gossip,
                    "Do you know a potion to make hair fall out?",
                    Topic.Hair,
                    "Have you got any good potions to give away?",
                    Topic.Freebie,
                    "No thanks.",
                    Topic.Leave,
                )
            }
        when (topic) {
            Topic.RomeoJuliet -> romeoAndJuliet(npc)
            Topic.Gossip -> gossip()
            Topic.Hair -> {
                chatPlayer(quiz, "Do you know a potion to make hair fall out?")
                chatNpc(happy, "I certainly do. I gave some to my mother. That's why I live alone now.")
                chatNpc(neutral, "Anything else I can do for you?")
            }
            Topic.Freebie -> freebie()
            Topic.Leave -> chatPlayer(neutral, "No thanks.")
        }
    }

    private suspend fun Dialogue.gossip() {
        chatPlayer(quiz, "Have you got any decent gossip to share?")
        if (!quest.isComplete(player)) {
            chatNpc(
                neutral,
                "Well, I hear young Romeo is having a spot of woman trouble, but otherwise all is " +
                    "quiet. Anything I can do for you?",
            )
            return
        }
        chatNpc(sad, "Terrible business with young Romeo and Juliet...")
        chatNpc(
            neutral,
            "They say every time Romeo sees Juliet now, he runs off screaming about ghosts and " +
                "Juliet's cousin.",
        )
        chatNpc(neutral, "Always thought he was a bit of an odd one...")
        chatNpc(happy, "Still! Life goes on, and so does business. Anything I can do for you?")
    }

    private suspend fun Dialogue.freebie() {
        chatPlayer(quiz, "Have you got any good potions to give away?")
        if (random.of(2) != 0) {
            chatNpc(neutral, "Sorry, charity is not my strong point. Do you need anything else?")
            return
        }
        if (access.inv.count(SPOT_CREAM) > 0) {
            chatNpc(neutral, "Only that spot cream you've already got. I hope you're enjoying it.")
            return
        }
        chatNpc(happy, "Alright then. Try this one.")
        access.invAddOrDrop(objRepo, SPOT_CREAM)
        objbox(SPOT_CREAM, "The Apothecary gives you a potion.")
    }

    private suspend fun Dialogue.romeoAndJuliet(npc: Npc) {
        val stage = quest.stage(player)
        when {
            stage == STAGE_SEEN_FATHER -> {
                chatPlayer(
                    neutral,
                    "Apothecary, Father Lawrence sent me. I need a cadava potion to help Romeo " +
                        "and Juliet.",
                )
                chatNpc(neutral, "Cadava potion. Nasty stuff, and tricky to make.")
                chatNpc(neutral, "Wing of rat, tail of frog. Ear of snake and horn of dog.")
                chatNpc(neutral, "All of which I have. What I need are some cadava berries.")
                chatNpc(
                    neutral,
                    "You'll have to find those while I prepare the rest. Bring them to me when " +
                        "you have them - but be careful. They're nasty.",
                )
                quest.setStage(access, STAGE_SEEN_APOTHECARY)
                if (access.inv.count(CADAVA_BERRIES) > 0) {
                    chatPlayer(happy, "As luck would have it, I have some right here.")
                    brewCadava(npc)
                    return
                }
                berryQuestions()
            }
            stage == STAGE_SEEN_APOTHECARY && access.inv.count(CADAVA_POTION) > 0 -> {
                chatPlayer(happy, "Thanks for the cadava potion.")
                chatNpc(happy, "You're welcome. I hope it brings that young couple some happiness.")
            }
            stage == STAGE_SEEN_APOTHECARY && access.inv.count(CADAVA_BERRIES) > 0 -> {
                chatNpc(happy, "Well done. You've got the berries.")
                brewCadava(npc)
            }
            stage == STAGE_SEEN_APOTHECARY -> {
                chatNpc(neutral, "Keep looking for those cadava berries. I can't make the potion without them.")
            }
            stage >= STAGE_JULIET_CRYPT -> {
                chatPlayer(neutral, "I gave Juliet the cadava potion.")
                chatNpc(happy, "Good, good. I hope it brings that young couple some happiness.")
            }
        }
    }

    private suspend fun Dialogue.berryQuestions() {
        while (true) {
            val topic =
                choice4(
                    "What do these berries look like?",
                    1,
                    "Where can I get these berries?",
                    2,
                    "How are these berries dangerous?",
                    3,
                    "Okay, thanks.",
                    4,
                )
            when (topic) {
                1 -> {
                    chatPlayer(quiz, "What do these berries look like?")
                    chatNpc(
                        neutral,
                        "Bright pink, with green leaves and pale brown stalks - you can't miss " +
                            "them. They look rather tasty, but I wouldn't recommend eating them.",
                    )
                }
                2 -> {
                    chatPlayer(quiz, "Where can I get these berries?")
                    chatNpc(
                        neutral,
                        "I believe I saw some nearby not long ago. Yes, that's it! There was a bit " +
                            "of a fuss when a school trip visited the mining pit south-east of " +
                            "Varrock.",
                    )
                    chatNpc(
                        neutral,
                        "Some of the children found cadava bushes and started picking the " +
                            "berries. That trip ended rather quickly!",
                    )
                }
                3 -> {
                    chatPlayer(quiz, "How are these berries dangerous?")
                    chatNpc(
                        neutral,
                        "They're mostly harmless, but handle them wrongly and they can be quite " +
                            "unpleasant. And I certainly wouldn't advise drinking the potion!",
                    )
                }
                else -> {
                    chatPlayer(neutral, "Okay, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.brewCadava(npc: Npc) {
        access.invDel(access.inv, CADAVA_BERRIES)
        mesbox(
            "You hand over the berries, which the Apothecary shakes up in a vial of strange " +
                "liquid.",
        )
        npc.anim(BREW_SEQ)
        npc.spotanim(SMOKE_SPOTANIM)
        access.soundSynth(BREW_SOUND)
        access.soundSynth(SMOKE_SOUND, delay = BREW_SMOKE_DELAY)
        access.delay(BREW_TICKS)
        chatNpc(happy, "Phew! Here's what you need.")
        access.invAddOrDrop(objRepo, CADAVA_POTION)
        objbox(CADAVA_POTION, "The Apothecary gives you a cadava potion.")
    }

    private suspend fun ProtectedAccess.useOn(npc: Npc, obj: String) {
        startDialogue(npc) {
            val stage = quest.stage(player)
            when {
                obj != CADAVA_BERRIES -> chatNpc(neutral, "I don't need that, thank you.")
                stage == STAGE_SEEN_APOTHECARY && access.inv.count(CADAVA_POTION) == 0 -> {
                    chatNpc(happy, "Well done. You've got the berries.")
                    brewCadava(npc)
                }
                stage > STAGE_SEEN_APOTHECARY -> chatNpc(neutral, "I don't need those any more.")
                else -> chatNpc(neutral, "Cadava berries? Careful with those. I've no use for them just now.")
            }
        }
    }

    private fun ProtectedAccess.openPotions() {
        ifOpenMainModal(POTIONS_INTERFACE)
        for (recipe in Recipe.entries) {
            ifSetObj(recipe.picture, recipe.product, POTION_ZOOM)
            ifSetText(recipe.title, recipe.displayName)
            ifSetText(recipe.contents, recipe.describe())
        }
    }

    private suspend fun ProtectedAccess.brew(recipe: Recipe, op: IfButtonOp) {
        val affordable = recipe.batches(this)
        if (affordable == 0) {
            mes("You don't have the ingredients the Apothecary needs for that potion.")
            return
        }
        val wanted =
            when (op) {
                IfButtonOp.Op1 -> 1
                IfButtonOp.Op2 -> BUY_FIVE
                IfButtonOp.Op3 -> countDialog()
                IfButtonOp.Op4 -> affordable
                else -> return
            }
        var made = 0
        repeat(minOf(wanted, recipe.batches(this))) {
            for ((obj, count) in recipe.ingredients) {
                invDel(inv, obj, count)
            }
            if (recipe.coins > 0) {
                invDel(inv, COINS, recipe.coins)
            }
            invAddOrDrop(objRepo, recipe.product)
            made++
        }
        if (made > 0) {
            soundSynth(BREW_SOUND)
            val name = recipe.displayName.lowercase()
            mes(if (made == 1) "The Apothecary brews you a $name." else "The Apothecary brews you $made ${name}s.")
        }
    }

    private enum class Topic { RomeoJuliet, Gossip, Hair, Freebie, Leave }

    private enum class Recipe(
        val component: String,
        val displayName: String,
        val product: String,
        val ingredients: List<Pair<String, Int>>,
        val coins: Int,
    ) {
        Strength(
            "strength",
            "Strength potion",
            "obj.strength4",
            listOf("obj.limpwurt_root" to 1, "obj.red_spiders_eggs" to 1),
            coins = 5,
        ),
        Energy(
            "energy",
            "Energy potion",
            "obj.4dose1energy",
            listOf("obj.limpwurt_root" to 2, "obj.chocolate_dust" to 1),
            coins = 0,
        ),
        Antipoison(
            "antipoison",
            "Antipoison",
            "obj.4doseantipoison",
            listOf("obj.limpwurt_root" to 1, "obj.cadavaberries" to 1),
            coins = 5,
        );

        val button: String
            get() = "component.apothecary_potions:$component"

        val picture: String
            get() = "component.apothecary_potions:${component}_pic"

        val title: String
            get() = "component.apothecary_potions:${component}_title"

        val contents: String
            get() = "component.apothecary_potions:${component}_contents"

        fun describe(): String {
            val names =
                ingredients.map { (obj, count) ->
                    val name = INGREDIENT_NAMES.getValue(obj)
                    if (count == 1) name else "$count x $name"
                }
            val price = if (coins > 0) "$coins coins" else "Free"
            return names.joinToString("<br>") + "<br><col=ffffff>$price</col>"
        }

        fun batches(access: ProtectedAccess): Int {
            var batches = Int.MAX_VALUE
            for ((obj, count) in ingredients) {
                batches = minOf(batches, access.inv.count(obj) / count)
            }
            if (coins > 0) {
                batches = minOf(batches, access.inv.count(COINS) / coins)
            }
            return batches
        }
    }

    private companion object {
        const val POTIONS_INTERFACE = "interface.apothecary_potions"
        const val POTION_ZOOM = 380
        const val BUY_FIVE = 5

        const val COINS = "obj.coins"
        const val SPOT_CREAM = "obj.acne_potion"

        const val BREW_SEQ = "seq.human_apothecary_create_potion"
        const val SMOKE_SPOTANIM = "spotanim.smokepuff"
        const val BREW_SOUND = "synth.vial_mix"
        const val SMOKE_SOUND = "synth.smokepuff"
        const val BREW_SMOKE_DELAY = 30
        const val BREW_TICKS = 4

        val INGREDIENT_NAMES =
            mapOf(
                "obj.limpwurt_root" to "Limpwurt root",
                "obj.red_spiders_eggs" to "Red spiders' eggs",
                "obj.chocolate_dust" to "Chocolate dust",
                "obj.cadavaberries" to "Cadava berries",
            )
    }
}
