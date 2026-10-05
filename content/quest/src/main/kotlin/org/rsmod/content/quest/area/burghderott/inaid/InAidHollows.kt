package org.rsmod.content.quest.area.burghderott.inaid

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.other.pets.PetFollowers
import org.rsmod.content.other.pets.Pets
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.IVAN_BODY
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.IVAN_FOOD
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.IVAN_FOOD_HEAL
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.IVAN_HELM
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.IVAN_HOLLOWS
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.IVAN_LEGS
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.IVAN_SICKLE
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_IVAN_DELIVERED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_PARTY_TOLD
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_RELOCATION_BRIEFED
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_RETURN_TO_HOLLOWS
import org.rsmod.content.quest.area.burghderott.inaid.InAidOfTheMyrequeQuest.Companion.STAGE_STARTED
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Myreque in their old hideout under the Hollows during In Aid of the Myreque. The npcs
 * themselves belong to In Search of the Myreque's scripts, which hand over to these conversations
 * once that quest is done; only using items on Ivan is bound here.
 *
 * Everything Ivan is given for the journey is recorded on cache varbits: one flag per piece of
 * steel armour and the sickle, the number of portions of food (at most [IVAN_FOOD_MAX]) and how
 * much the weakest of them heals. None of it comes back.
 */
@Singleton
class InAidHollows
@Inject
constructor(
    private val iaom: InAidOfTheMyrequeQuest,
    private val escort: IvanEscort,
    private val pets: PetFollowers,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpcU(IVAN_HOLLOWS) { giveIvan(it.objType, it.invSlot) }
    }

    /* Veliaf */

    suspend fun Dialogue.veliaf() {
        val stage = iaom.stage(player)
        when {
            stage == 0 -> offer()
            stage < STAGE_RETURN_TO_HOLLOWS -> reminder()
            stage == STAGE_RETURN_TO_HOLLOWS -> briefing()
            stage < STAGE_IVAN_DELIVERED -> plans()
            else -> chatNpc(neutral, "Get yourself down to Burgh de Rott, friend. That's where we'll be.")
        }
    }

    private suspend fun Dialogue.offer() {
        chatNpc(quiz, "You're back. Any luck in Canifis?")
        chatPlayer(sad, "I went back to where I first met Vanstrom. He was gone though.")
        chatNpc(neutral, "I feared as much. Worry not. The day will come when we have our revenge on him and the rest of his kind.")
        chatNpc(neutral, "Anyway, now that you're back, there's something I was hoping you'd help us with.")
        if (!choice2("Yes.", true, "No.", false, title = "Start the In Aid of the Myreque quest?")) {
            chatPlayer(neutral, "Sorry. I'm not interested.")
            chatNpc(neutral, "Very well. You know where we are if you change your mind.")
            return
        }
        chatPlayer(quiz, "What do you need?")
        chatNpc(worried, "Now that this hideout is no longer safe, we need to move before the vyrewatch descend on us.")
        chatPlayer(neutral, "Seems wise. What part do I play?")
        chatNpc(neutral, "South of Mort'ton there's an old town called Burgh de Rott. It was abandoned, but I hear some escapees from Meiyerditch have made their home there.")
        chatPlayer(quiz, "And you want me to scout the place out?")
        chatNpc(neutral, "Exactly. Go to Burgh de Rott and see if you can find a good place for our new hideout. While you're there, do what you can to win the townspeople over.")
        chatPlayer(neutral, "I'll get it done.")
        iaom.advanceTo(access, STAGE_STARTED)
        chatNpc(neutral, "Take a silver weapon with you. You'll be glad of it if vampyres attack. Good luck.")
    }

    private suspend fun Dialogue.reminder() {
        chatNpc(neutral, "Well met, ${player.displayName}.")
        chatPlayer(quiz, "What do I need to do again?")
        chatNpc(neutral, "Head to Burgh de Rott and see if the town has somewhere suitable for our new hideout. While you're there, do what you can to win the townspeople over.")
        chatNpc(neutral, "You'll find the town just south of Mort'ton. Good luck.")
    }

    private suspend fun Dialogue.briefing() {
        chatNpc(happy, "Well met, friend. Glad you made it back in one piece.")
        chatPlayer(quiz, "Likewise. So, what happens now?")
        chatNpc(quiz, "Do you know of Drezel?")
        chatPlayer(neutral, "The priest at Paterdomus? Yes, I've helped him before.")
        chatNpc(neutral, "Excellent. Drezel is an old friend, and he's agreed to do us a favour. Ivan is too young and inexperienced to be out here with us, and he wants to be a priest himself.")
        chatNpc(neutral, "Drezel will take him in and train him. When he's older and wiser he can rejoin our fight as a spiritual advisor.")
        chatNpc(neutral, "Polmafi, Radigad and I will soon be heading down to Burgh de Rott. While we do, I'd be grateful if you'd escort Ivan to Paterdomus.")
        chatPlayer(neutral, "I'm sure I can manage that.")
        chatNpc(neutral, "Thank you. There's something else I could use your help with, too.")
        chatNpc(worried, "You'll have noticed our weapons do little against the vampyres. Even silver only goes so far. If we're to have any hope of beating them, we need something better.")
        chatNpc(neutral, "If you've met Drezel, he'll have told you of the Seven Priestly Warriors who blessed the Salve and saved Misthalin.")
        chatPlayer(neutral, "He has indeed.")
        chatNpc(neutral, "I've heard that one of them, a man called Ivandis Seergaze, used a weapon of great power against the vampyres. And that he isn't buried at Paterdomus with the others.")
        chatPlayer(quiz, "I see. And you'd like me to look into this?")
        chatNpc(neutral, "If you could, you'd have my gratitude. Drezel will likely know more, though he isn't always forthcoming where rumours are concerned.")
        chatPlayer(neutral, "I'll see what I can dig up.")
        chatNpc(neutral, "Before you go, would you tell Polmafi and Radigad that it's time for us to leave?")
        chatPlayer(neutral, "Sure.")
        iaom.advanceTo(access, STAGE_RELOCATION_BRIEFED)
    }

    private suspend fun Dialogue.plans() {
        chatPlayer(neutral, "Hello again.")
        chatNpc(neutral, "Well met. Do you need something?")
        while (true) {
            when (
                choice5(
                    "What do I need to do now?", 1,
                    "What do you make of what Gadderanks said?", 2,
                    "Why does Ivan need to go to Paterdomus?", 3,
                    "Tell me more about Ivandis.", 4,
                    "Okay, thanks.", 5,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "What do I need to do now?")
                    if (iaom.stage(player) < STAGE_PARTY_TOLD) {
                        chatNpc(neutral, "First, let Polmafi and Radigad know it's time for us to leave. Then please escort Ivan to Paterdomus.")
                        chatNpc(neutral, "Once you're there, Drezel might be able to help you with this Ivandis business.")
                    } else {
                        chatNpc(neutral, "Please escort Ivan to Paterdomus. Once you're there, Drezel might be able to help you with this Ivandis business.")
                    }
                }
                2 -> {
                    chatPlayer(quiz, "What do you make of what Gadderanks said?")
                    chatNpc(neutral, "It could be nonsense, but it may be worth looking into. I recognise at least two of the things he mentioned as ingredients for a potion.")
                    chatNpc(neutral, "Garlic and silver are interesting though. We know vampyres are weak to both, but the way he spoke suggests they could go into a potion as well.")
                    chatPlayer(quiz, "How would I test that?")
                    chatNpc(neutral, "A good question, my friend, but I have no answer for you. Perhaps you could experiment?")
                }
                3 -> {
                    chatPlayer(quiz, "Why does Ivan need to go to Paterdomus?")
                    chatNpc(neutral, "He'll be safer there under Drezel's eye. Drezel can help him grow spiritually, so that when he's older and wiser he can rejoin the fight.")
                }
                4 -> {
                    chatPlayer(quiz, "Tell me more about Ivandis.")
                    chatNpc(neutral, "Long ago, the Seven Priestly Warriors drove the vampyres out of Misthalin and blessed the Salve so they could never return.")
                    chatNpc(neutral, "The priests of Saradomin would have you believe all seven lie beneath Paterdomus. But rumour says one of them, Ivandis Seergaze, is buried elsewhere.")
                    chatPlayer(quiz, "So where is he buried instead?")
                    chatNpc(neutral, "He's said to have led countless charges into Morytania and died somewhere in these lands. If so, his followers would have buried him near where he fell.")
                    chatNpc(neutral, "It matters because Ivandis is said to have wielded a weapon of great power against the vampyres. If he died in Morytania, it may have been buried with him.")
                }
                else -> {
                    chatPlayer(neutral, "Okay, thanks.")
                    return
                }
            }
        }
    }

    /* Polmafi and Radigad */

    suspend fun Dialogue.polmafi() {
        partyMember(other = "Radigad", generic = {
            chatPlayer(neutral, "Hey, how's it going?")
            chatNpc(sad, "Not so good. It's great to see you're helping us out though.")
        })
    }

    suspend fun Dialogue.radigad() {
        partyMember(other = "Polmafi", generic = {
            chatPlayer(neutral, "Hello again.")
            chatNpc(neutral, "Hello. I hear you're doing important work for Veliaf. You'd best get to it.")
        })
    }

    private suspend fun Dialogue.partyMember(other: String, generic: suspend Dialogue.() -> Unit) {
        val stage = iaom.stage(player)
        when {
            stage < STAGE_RELOCATION_BRIEFED -> generic()
            stage == STAGE_RELOCATION_BRIEFED -> {
                chatPlayer(neutral, "Hey. Veliaf says it's time to head down to the new hideout in Burgh de Rott. Could you let $other know as well? I'm taking Ivan to Paterdomus.")
                chatNpc(neutral, "Okay. Thanks for letting me know. I'll tell $other.")
                iaom.advanceTo(access, STAGE_PARTY_TOLD)
            }
            else -> chatNpc(neutral, "Don't worry, I'm just getting my things together before we leave. Good luck with your trip to the temple.")
        }
    }

    /* Ivan */

    suspend fun Dialogue.ivan() {
        val stage = iaom.stage(player)
        when {
            stage < STAGE_RELOCATION_BRIEFED -> {
                chatNpc(happy, "Is something new and exciting happening?")
                chatPlayer(neutral, "Maybe. Veliaf has asked me to do some work for him. Hopefully it'll help you all.")
            }
            stage == STAGE_RELOCATION_BRIEFED -> {
                chatNpc(happy, "Hey there. I hear you're going to be taking me to the temple.")
                chatPlayer(neutral, "That's right. I just need to speak with Polmafi and Radigad and then we'll head off.")
            }
            hasFollower(player) ->
                chatNpc(neutral, "Hmmm, it seems you have a follower. I think it might get lost on this journey if it's left to wander on its own. Let's leave when your follower is safe.")
            else -> with(escort) { setOff() }
        }
    }

    /** A pet out or a pet item carried; Ivan won't set off with either. */
    fun hasFollower(player: Player): Boolean =
        pets.hasFollower(player) || player.inv.any { it != null && Pets.forObj(it.id) != null }

    private suspend fun ProtectedAccess.giveIvan(obj: ItemServerType, slot: Int) {
        val stage = iaom.stage(player)
        if (stage !in STAGE_RELOCATION_BRIEFED until STAGE_IVAN_DELIVERED) {
            mes("Nothing interesting happens.")
            return
        }
        val armour = Armour.entries.firstOrNull { obj.isObj(it.obj) }
        when {
            armour != null -> giveArmour(armour)
            obj.isObj(SILVER_SICKLE) -> giveSickle()
            obj.id in IVAN_FOODS.map { it.asObjId() } -> giveFood(obj, slot)
            isFood(obj.id) -> startDialogue {
                chatNpcSpecific(IVAN_NAME, IVAN_HOLLOWS, happy, "Thanks for the kind offer of food, but my favourites are cooked snails, salmon, slimy eel or stew!")
            }
            obj.wearpos1 >= 0 -> startDialogue {
                chatNpcSpecific(IVAN_NAME, IVAN_HOLLOWS, neutral, "Thanks for the kind offer of some armour, but I can only wear a steel medium helm, steel chainbody and steel platelegs.")
            }
            else -> mes("Nothing interesting happens.")
        }
    }

    private suspend fun ProtectedAccess.giveArmour(armour: Armour) {
        if (player.vars[armour.varbit] == 1) {
            startDialogue { chatNpcSpecific(IVAN_NAME, IVAN_HOLLOWS, happy, "Thanks for the offer of ${armour.offered}, but you've already given me ${armour.already}!") }
            return
        }
        if (invDel(inv, armour.obj).failure) return
        VarPlayerIntMapSetter.set(player, armour.varbit, 1)
        startDialogue {
            chatNpcSpecific(IVAN_NAME, IVAN_HOLLOWS, happy, "Thanks for the ${armour.thanks}! That's great! It's going to help a lot! I'm going to wear it under my normal clothes.")
        }
    }

    private suspend fun ProtectedAccess.giveSickle() {
        if (player.vars[IVAN_SICKLE] == 1) {
            startDialogue { chatNpcSpecific(IVAN_NAME, IVAN_HOLLOWS, happy, "You've already given me a sickle, thanks!") }
            return
        }
        if (invDel(inv, SILVER_SICKLE).failure) return
        VarPlayerIntMapSetter.set(player, IVAN_SICKLE, 1)
        startDialogue {
            objbox(SILVER_SICKLE, "You offer the sickle to Ivan.")
            chatPlayer(neutral, "You might find this sickle helpful on our travels.")
            chatNpcSpecific(IVAN_NAME, IVAN_HOLLOWS, happy, "Many thanks, friend. I shall try to wield it as bravely as Veliaf and yourself!")
        }
    }

    private suspend fun ProtectedAccess.giveFood(obj: ItemServerType, slot: Int) {
        val room = IVAN_FOOD_MAX - player.vars[IVAN_FOOD]
        if (room <= 0) {
            startDialogue { chatNpcSpecific(IVAN_NAME, IVAN_HOLLOWS, happy, "I'm totally stocked up with food now, thanks!") }
            return
        }
        val symbol = obj.internalName
        val carried = inv.count(symbol)
        var all = false
        if (carried > 1) {
            startDialogue {
                objbox(symbol, "Would you like to offer Ivan all of this food type in your inventory?")
                all = choice2("Yes, I'll offer all of this food item in my inventory to Ivan.", true, "Nope, I'll just give him one.", false)
            }
        }
        if (inv[slot]?.id != obj.id && symbol !in inv) return
        val give = minOf(if (all) carried else 1, room)
        if (invDel(inv, symbol, give).failure) return
        val heal = foodHeal(obj.id)
        val current = player.vars[IVAN_FOOD_HEAL]
        VarPlayerIntMapSetter.set(player, IVAN_FOOD_HEAL, if (current == 0) heal else minOf(current, heal))
        VarPlayerIntMapSetter.set(player, IVAN_FOOD, player.vars[IVAN_FOOD] + give)
        val left = IVAN_FOOD_MAX - player.vars[IVAN_FOOD]
        startDialogue {
            objbox(symbol, "You hand over some food to Ivan. His eyes light up when he sees it!")
            if (left > 0) {
                chatNpcSpecific(IVAN_NAME, IVAN_HOLLOWS, happy, "Ohh delicious, some food! I'll keep that for the trip. I could carry another $left bits of food.")
            } else {
                chatNpcSpecific(IVAN_NAME, IVAN_HOLLOWS, happy, "Ohh delicious, some food! I'll keep that for the trip. I'm totally stocked up with food now, thanks!")
            }
        }
    }

    enum class Armour(val obj: String, val varbit: String, val thanks: String, val offered: String, val already: String) {
        Helm("obj.steel_med_helm", IVAN_HELM, "helmet", "a helmet", "one"),
        Body("obj.steel_chainbody", IVAN_BODY, "chainbody", "a chainbody", "one"),
        Legs("obj.steel_platelegs", IVAN_LEGS, "steel legs", "some platelegs", "some"),
    }

    companion object {
        const val IVAN_NAME = "Ivan Strom"
        const val SILVER_SICKLE = "obj.silver_sickle"
        const val IVAN_FOOD_MAX = 15

        val IVAN_FOODS =
            listOf(
                "obj.stew",
                "obj.salmon",
                "obj.snail_corpse_cooked1",
                "obj.snail_corpse_cooked2",
                "obj.snail_corpse_cooked3",
                "obj.mort_slimey_eel_cooked",
            )

        private fun String.asObjId(): Int = dev.openrune.rscm.RSCM.getRSCM(this)
    }
}
