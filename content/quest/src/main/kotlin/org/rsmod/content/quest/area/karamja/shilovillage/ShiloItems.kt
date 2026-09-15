package org.rsmod.content.quest.area.karamja.shilovillage

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BEADS_OF_THE_DEAD
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BERVIRIUS_NOTES
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BONE_BEADS
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BONE_KEY
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BONE_SHARD
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.BRONZE_WIRE
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.CHISEL
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.CRUMPLED_SCROLL
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.LOCATING_CRYSTAL
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.RASHILIYIA
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.RASHILIYIA_CORPSE
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.STONE_PLAQUE
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.SWORD_POMMEL
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.TATTERED_SCROLL
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.ZADIMUS
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.ZADIMUS_CORPSE
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The artefacts of Shilo Village: reading the scrolls and plaque, burying Zadimus on Tai Bwo
 * Wannai's sacred ground, crafting the Beads of the Dead and the bone key, the locating crystal,
 * and the fates that befall each artefact when it is dropped.
 */
class ShiloItems
@Inject
constructor(
    private val shilo: ShiloVillageQuest,
    private val undead: ShiloUndead,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(STONE_PLAQUE) { readPlaque() }
        onOpHeld1(TATTERED_SCROLL) { readTatteredScroll() }
        onOpHeld1(CRUMPLED_SCROLL) { readCrumpledScroll() }
        onOpHeld1(BERVIRIUS_NOTES) { readNotes() }
        onOpHeld1(BONE_SHARD) {
            mesbox("You look at the shard of bone. The words of Zadimus come back to you. <col=0000ff>'I am the key, but only kin may approach her.'</col>")
        }
        onOpHeld1(ZADIMUS_CORPSE) { buryZadimus() }
        onOpHeld1(LOCATING_CRYSTAL) { activateCrystal() }

        onOpHeld5(STONE_PLAQUE) { dropArtefact(STONE_PLAQUE, "You decide to throw the item away...", "As you drop the item, it bounces into a stream, never to be seen again.") }
        onOpHeld5(TATTERED_SCROLL) { dropArtefact(TATTERED_SCROLL, "You decide to throw the item away...", "As you drop the item, it falls down a narrow crevice, never to be seen again.") }
        onOpHeld5(CRUMPLED_SCROLL) { dropArtefact(CRUMPLED_SCROLL, "You decide to throw the item away.", "As you drop the item, it gets carried away by the wind, never to be seen again.") }
        onOpHeld5(ZADIMUS_CORPSE) { buryZadimus() }
        onOpHeld5(SWORD_POMMEL) { crumble(SWORD_POMMEL, "You drop the sword pommel on the floor. It turns to dust as soon as it hits the ground.") }
        onOpHeld5(BONE_BEADS) { crumble(BONE_BEADS, "As the beads hit the floor, they disintegrate into puffs of white powder.") }
        onOpHeld5(BERVIRIUS_NOTES) { crumble(BERVIRIUS_NOTES, "As you drop the delicate scrolls onto the floor, they disintegrate immediately.") }
        onOpHeld5(BONE_SHARD) { dropShard() }
        onOpHeld5(BONE_KEY) { dropKey() }
        onOpHeld5(BEADS_OF_THE_DEAD) { dropBeads() }
        onOpHeld5(LOCATING_CRYSTAL) { dropCrystal() }
        onOpHeld5(RASHILIYIA_CORPSE) { dropCorpse() }

        onOpHeldU(CHISEL, SWORD_POMMEL) { carveBeads() }
        onOpHeldU(BRONZE_WIRE, BONE_BEADS) { stringBeads() }
        onOpHeldU(CHISEL, BONE_SHARD) { carveKey() }
    }

    /* Reading */

    private suspend fun ProtectedAccess.readPlaque() {
        if (shilo.decipheredPlaque.get(player)) {
            mesbox("You remember what Trufitus told you about this. <col=0000ff>'Here lies the traitor Zadimus, let his spirit be forever tormented.'</col>")
            return
        }
        mesbox("The markings are very intricate. It's a very strange language. The meaning of it evades you though. Perhaps Trufitus can decipher the markings?")
    }

    private suspend fun ProtectedAccess.readTatteredScroll() {
        mesbox("This looks like part of a scroll about someone called Bervirius. Would you like to read it?")
        var read = false
        startDialogue { read = choice2("Yes please.", true, "No thanks.", false) }
        if (!read) {
            mesbox("You decide not to open the scroll. But instead place it carefully back in your inventory.")
            return
        }
        shilo.readTatteredScroll.set(player, true)
        readScroll("Tattered scroll", TATTERED_SCROLL_TEXT)
    }

    private suspend fun ProtectedAccess.readCrumpledScroll() {
        mesbox("This looks like a scroll about Rashiliyia. Would you like to read it?")
        var read = false
        startDialogue { read = choice2("Yes please.", true, "No thanks.", false) }
        if (!read) {
            mes("You decide to leave the scroll well alone.")
            return
        }
        shilo.readCrumpledScroll.set(player, true)
        readScroll("Crumpled scroll", CRUMPLED_SCROLL_TEXT)
    }

    private suspend fun ProtectedAccess.readNotes() {
        objbox(
            BERVIRIUS_NOTES,
            "This scroll is a collection of writings. Some of them are just scraps of papyrus with what looks like random scribblings. Which would you like to read?",
        )
        var page = 0
        startDialogue {
            page = choice3("Tattered Yellow papyrus.", 1, "Decayed White papyrus.", 2, "Crusty Orange papyrus.", 3)
        }
        when (page) {
            1 -> readScroll("Tattered Yellow papyrus", YELLOW_PAPYRUS_TEXT)
            2 -> readScroll("Decayed White papyrus", WHITE_PAPYRUS_TEXT)
            3 -> readScroll("Crusty Orange papyrus", ORANGE_PAPYRUS_TEXT)
        }
    }

    /* Zadimus */

    private suspend fun ProtectedAccess.buryZadimus() {
        mes("You feel an unearthly compunction to bury this corpse!")
        delay(2)
        if (!ShiloCoords.onSacredGround(player.coords)) {
            mesbox("You hear a ghostly wailing sound coming from the corpse and a whispering voice says, 'Let me rest in a sacred place and assist you I will'")
            return
        }
        val grave = mapFindSquareLineOfWalk(player.coords, 1, 1) ?: player.coords
        faceSquare(grave)
        anim(DIG_SEQ)
        soundSynth(DIG_SOUND)
        mes("You dig a hole for the remains.")
        delay(2)
        mes("You lay the remains of Zadimus to rest.")
        anim(PICKUP_SEQ)
        delay(2)
        mesbox("You hear an unearthly moaning sound as you see an apparition materialise right in front of you.")
        val spirit = undead.spawnApparition(ZADIMUS, grave, SPIRIT_DURATION, player)
        delay(1)
        invDel(inv, ZADIMUS_CORPSE)
        if (spirit != null) {
            startDialogue(spirit) {
                chatNpc(
                    neutral,
                    "You have released me from my torture, and now I shall aid you. You seek to dispell the one who tortured and killed me. Remember this... 'I am the key, but only kin may approach her.'",
                )
            }
            undead.dismissApparition(spirit)
        }
        invAddOrDrop(objRepo, BONE_SHARD)
        objbox(
            BONE_SHARD,
            "The apparition disappears into the ground where you buried the corpse. You see the ground in front of you shake as a shard of bone forces its way to the surface. You take the bone shard and place it in your inventory.",
        )
    }

    /* Crafting */

    private suspend fun ProtectedAccess.carveBeads() {
        objbox(SWORD_POMMEL, "You prepare the ivory pommel and the chisel to start crafting...")
        if (stat(CRAFTING) < POMMEL_LEVEL) {
            mesbox("You need a Crafting level of at least $POMMEL_LEVEL to complete this task.")
            return
        }
        anim(CHISEL_SEQ)
        invDel(inv, SWORD_POMMEL)
        invAdd(inv, BONE_BEADS)
        statAdvance(CRAFTING, POMMEL_XP)
        objbox(BONE_BEADS, "You successfully craft some of the ivory into beads. They may look good as part of a necklace.")
    }

    private suspend fun ProtectedAccess.stringBeads() {
        if (!shilo.readCrumpledScroll.get(player)) {
            mes("You're not really sure how this would fit together.")
            mes("Maybe Ah Za Rhoon has some instructions on this?")
            return
        }
        if (stat(CRAFTING) < BEADS_LEVEL) {
            mes("You need a Crafting level of at least $BEADS_LEVEL to complete this task.")
            return
        }
        invDel(inv, BONE_BEADS)
        invDel(inv, BRONZE_WIRE)
        invAdd(inv, BEADS_OF_THE_DEAD)
        statAdvance(CRAFTING, BEADS_XP)
        objbox(BEADS_OF_THE_DEAD, "You successfully craft the beads and bronze wire into a necklace which you name, 'Beads of the Dead'")
    }

    private suspend fun ProtectedAccess.carveKey() {
        if (stat(CRAFTING) < KEY_LEVEL) {
            mesbox("You need to have a Crafting level of at least $KEY_LEVEL to work this material.")
            return
        }
        if (!shilo.examinedBoneLock.get(player)) {
            mesbox("You're not quite sure what to make with this. Perhaps it will come to you as you discover more about Rashiliyia?")
            return
        }
        anim(CHISEL_SEQ)
        invDel(inv, BONE_SHARD)
        invAdd(inv, BONE_KEY)
        statAdvance(CRAFTING, KEY_XP)
        mesbox("Remembering Zadimus' words and the strange bone lock, you start to craft the bone. You successfully make a key out of the bone shard.")
    }

    /* The locating crystal */

    private suspend fun ProtectedAccess.activateCrystal() {
        if (shilo.isComplete(player)) {
            mes("The crystal does not respond anymore.")
            return
        }
        if (statBase(PRAYER) < CRYSTAL_PRAYER) {
            objbox(
                LOCATING_CRYSTAL,
                "You feel the crystal trying to draw upon your spiritual energy, but you have no spiritual energy at the moment. You need a Prayer level of at least $CRYSTAL_PRAYER for it to work.",
            )
            return
        }
        if (!shilo.alwaysDrawCrystal.get(player)) {
            objbox(LOCATING_CRYSTAL, "You feel the crystal trying to draw upon your spiritual energy. Do you want to let it?")
            var answer = 0
            startDialogue {
                answer =
                    choice3(
                        "Yes, just this once.", 1,
                        "Yes, always let it draw from my spiritual energy.", 2,
                        "No, it sounds a bit dangerous.", 3,
                    )
            }
            when (answer) {
                1 -> Unit
                2 -> shilo.alwaysDrawCrystal.set(player, true)
                else -> {
                    mesbox("You decide not to allow the crystal to draw on your spiritual energy.")
                    return
                }
            }
        }
        statDrain(PRAYER, constant = 0, percent = random.of(CRYSTAL_DRAIN_MIN, CRYSTAL_DRAIN_MAX))
        val distance = player.coords.chebyshevDistance(ShiloCoords.CRYSTAL_TARGET)
        when {
            player.coords.level != 0 || distance >= FAINT_RANGE ->
                objbox(LOCATING_CRYSTAL, "There is nothing different about the crystal. You're most likely not close enough for it to be active.")
            distance < BLAZING_RANGE -> objbox(CRYSTAL_WHITE, "The crystal blazes brilliantly.")
            distance < VERY_BRIGHT_RANGE -> objbox(CRYSTAL_YELLOW, "<col=ffff00>The crystal is very bright.</col>")
            distance < BRIGHT_RANGE -> objbox(CRYSTAL_RED, "<col=ff0000>The crystal glows brightly.</col>")
            else -> objbox(CRYSTAL_BLUE, "<col=0000ff>The crystal glows faintly.</col>")
        }
    }

    /* Dropping */

    private suspend fun ProtectedAccess.dropArtefact(obj: String, thrown: String, fate: String) {
        mesbox("This looks quite important, are you sure you want to drop it?")
        var drop = false
        startDialogue { drop = choice2("Yes, I'm quite sure.", true, "No, I think I'll keep it.", false) }
        if (!drop) {
            mes("You decide against throwing the item away.")
            return
        }
        mesbox(thrown)
        invDel(inv, obj)
        mesbox(fate)
    }

    private suspend fun ProtectedAccess.crumble(obj: String, text: String) {
        invDel(inv, obj)
        objbox(obj, text)
    }

    private suspend fun ProtectedAccess.dropShard() {
        mesbox("You remember the words that Zadimus said when he appeared in front of you. <col=0000ff>'I am the key, but only kin may approach her.'</col> Are you sure you want to drop this?")
        var drop = false
        startDialogue { drop = choice2("Yes, I'll drop it.", true, "No, I'll keep the Bone Shard.", false, title = "Drop the Bone Shard?") }
        if (!drop) {
            objbox(BONE_SHARD, "You decide to keep the Bone Shard.")
            return
        }
        objbox(BONE_SHARD, "You decide to drop the Bone Shard.")
        invDel(inv, BONE_SHARD)
        mes("The Bone Shard crumbles into dust as soon as it hits the floor.")
    }

    private suspend fun ProtectedAccess.dropKey() {
        mesbox("This looks quite valuable. As you go to throw the item away Zadimus' words come to you again. 'I am the key, but only kin may approach her.'")
        var drop = false
        startDialogue { drop = choice2("Yes, I'll drop it.", true, "No, I'll keep the Bone Key.", false, title = "Drop the Bone Key?") }
        if (!drop) {
            objbox(BONE_KEY, "You decide to keep the Bone Key.")
            return
        }
        objbox(BONE_KEY, "You decide to drop the Bone Key.")
        invDel(inv, BONE_KEY)
        mes("The Bone Key crumbles into dust as soon as it hits the floor.")
    }

    private suspend fun ProtectedAccess.dropBeads() {
        objbox(BEADS_OF_THE_DEAD, "Are you sure you want to drop the Beads of the Dead? It looks very rare and unique.")
        var drop = false
        startDialogue { drop = choice2("Yes, I'm sure.", true, "Nope, I've had second thoughts.", false, title = "Drop the Beads of the Dead?") }
        if (!drop) {
            objbox(BEADS_OF_THE_DEAD, "You decide not to drop the Beads of the Dead.")
            return
        }
        invDel(inv, BEADS_OF_THE_DEAD)
        objbox(BEADS_OF_THE_DEAD, "As the necklace hits the floor, it disintigrates into a puff of white powder. and you start to wonder if it ever really existed?")
    }

    private suspend fun ProtectedAccess.dropCrystal() {
        objbox(LOCATING_CRYSTAL, "Are you sure you want to drop this crystal? It looks very delicate and it may break.")
        var drop = false
        startDialogue { drop = choice2("Yes, I am sure.", true, "No, I've reconsidered, I'll keep it!", false, title = "Drop the Locating Crystal?") }
        if (!drop) {
            objbox(LOCATING_CRYSTAL, "You decide to keep the Locating Crystal tucked into your inventory safe and sound.")
            return
        }
        invDel(inv, LOCATING_CRYSTAL)
        soundSynth(GLASS_SOUND)
        val damage = if (player.hitpoints < CRYSTAL_DAMAGE + 5) player.hitpoints / 2 else CRYSTAL_DAMAGE
        if (damage > 0) {
            queueHit(delay = 1, type = HitType.Typeless, damage = damage, strongQueue = false)
        }
        objbox(LOCATING_CRYSTAL, "As you drop the crystal, it hits a rock and explodes. You are lascerated by shards of glass.")
    }

    private suspend fun ProtectedAccess.dropCorpse() {
        objbox(RASHILIYIA_CORPSE, "The remains of Rashiliyia look quite delicate. You sense that a spirit needs to be put to rest. Are you sure that you want to drop the remains?")
        var drop = false
        startDialogue { drop = choice2("Yes, I am sure.", true, "No, I'll keep hold of the remains.", false) }
        if (!drop) {
            mes("You decide to keep hold of Rashiliyia's remains.")
            return
        }
        anim(PICKUP_SEQ)
        invDel(inv, RASHILIYIA_CORPSE)
        delay(2)
        val spot = mapFindSquareLineOfWalk(player.coords, 1, 1) ?: player.coords
        val queen = undead.spawnApparition(RASHILIYIA, spot, SPIRIT_DURATION, player)
        if (queen != null) {
            startDialogue(queen) {
                chatNpc(
                    angry,
                    "You have my gratitude for releasing me, there is still much that needs to be done before Shilo Village is utterly destroyed. Please excuse me, I have plans and people to execute!",
                )
            }
            undead.dismissApparition(queen)
        }
        mesbox("The figure turns and soars away, quickly disappearing into the distance.")
    }

    private companion object {
        const val CRAFTING = "stat.crafting"
        const val PRAYER = "stat.prayer"

        const val POMMEL_LEVEL = 15
        const val BEADS_LEVEL = 16
        const val KEY_LEVEL = 20
        const val POMMEL_XP = 4.0
        const val BEADS_XP = 3.0
        const val KEY_XP = 3.0

        const val CRYSTAL_PRAYER = 10
        const val CRYSTAL_DRAIN_MIN = 2
        const val CRYSTAL_DRAIN_MAX = 4
        const val CRYSTAL_DAMAGE = 10
        const val BLAZING_RANGE = 10
        const val VERY_BRIGHT_RANGE = 50
        const val BRIGHT_RANGE = 100
        const val FAINT_RANGE = 200
        const val CRYSTAL_BLUE = "obj.zqcrystal_blue"
        const val CRYSTAL_RED = "obj.zqcrystal_red"
        const val CRYSTAL_YELLOW = "obj.zqcrystal_yellow"
        const val CRYSTAL_WHITE = "obj.zqcrystal_white"

        const val SPIRIT_DURATION = 30
        const val DIG_SEQ = "seq.human_dig"
        const val PICKUP_SEQ = "seq.human_pickupfloor"
        const val CHISEL_SEQ = "seq.human_cutting"
        const val DIG_SOUND = "synth.digspade"
        const val GLASS_SOUND = "synth.glass_chink_1"

        const val TATTERED_SCROLL_TEXT =
            "Bervirius, son of King Danthalas, was killed in battle. His devout mother Rashiliyia was so " +
                "heartbroken that she swore fealty to Zamorak if he would return her son to her. Bervirius " +
                "returned as an undead creature and terrorized the King and Queen. Many guards died fighting " +
                "the undead Bervirius. Eventually the undead Bervirius was set on fire and soon only the bones " +
                "remained. His remains were taken far to the South, and then towards the setting sun to a tomb " +
                "that is surrounded by and level with the sea. This is the only way to contain the spirits of " +
                "witches and the undead."

        const val CRUMPLED_SCROLL_TEXT =
            "Rashiliyia's rage went unchecked. She killed without mercy for revenge of her son's life. Like a " +
                "spectre through the night she entered houses and one by one quietly strangled life from the " +
                "occupants. It is said only a handful survived, protected by the necklace wards to keep the Witch " +
                "Queen at bay."

        const val YELLOW_PAPYRUS_TEXT =
            "...and rest like your mother who is silent in the peace of her tomb far to the North of Ah Za " +
                "Rhoon. Near the sea, and under the hills deep in the underground to watch all nature from the " +
                "darkness of her final resting place."

        const val WHITE_PAPYRUS_TEXT =
            "...Rashiliyia did so love objects of beauty. Her tomb was adorned with crystals that glowed " +
                "brightly when near to each other."

        const val ORANGE_PAPYRUS_TEXT =
            "...the sphere is activated when power of a spiritual nature is expended upon it, this can be very " +
                "draining on the body..."
    }
}
