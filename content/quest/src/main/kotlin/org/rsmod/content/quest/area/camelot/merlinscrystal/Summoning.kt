package org.rsmod.content.quest.area.camelot.merlinscrystal

import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpHeld5
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.BAT_BONES
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.LIT_BLACK_CANDLE
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.SMOKE_PUFF_SPOTANIM
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.STAGE_SPIRIT_BOUND
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.STAGE_SPOKEN_MORGAN
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.SUMMON_SOUND
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.SUMMON_STEP_BACK
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.SYMBOL_COORDS
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.THRANTAX
import org.rsmod.content.quest.area.camelot.merlinscrystal.MerlinsCrystalQuest.Companion.WORDS_OF_BINDING
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The summoning on the magical symbol in Camelot's garden.
 *
 * Bat bones dropped on the symbol by someone holding a lit black candle call up Thrantax the
 * Mighty. He is bound by three words and no more, and the options are shuffled so the phrase has
 * to be remembered rather than recognised by position. Getting it wrong leaves a level 92 demon
 * standing in the garden and burns the candle, which means another bucket of wax.
 */
@Singleton
class Summoning
@Inject
constructor(
    private val quest: MerlinsCrystalQuest,
    private val npcRepo: NpcRepository,
    private val worldRepo: WorldRepository,
    private val aiInteractions: AiPlayerInteractions,
    private val search: NpcSearch,
    private val random: GameRandom,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld5(BAT_BONES) { dropBones(it.slot) }
    }

    private suspend fun ProtectedAccess.dropBones(slot: Int) {
        if (!onSymbol()) {
            invDrop(slot)
            return
        }
        val ready =
            quest.stage(player) == STAGE_SPOKEN_MORGAN && inv.contains(LIT_BLACK_CANDLE)
        if (!ready) {
            mes("You can sense a powerful presence here...")
            return
        }
        if (!player.merlinKnowsWords) {
            mesbox("I think I'd better learn the words of binding before summoning a mighty spirit...")
            return
        }
        if (nearbySpirit() != null) {
            mes("You can sense a powerful presence here...")
            return
        }
        if (invDel(inv, BAT_BONES, slot = slot).failure) {
            return
        }
        summon()
    }

    private suspend fun ProtectedAccess.summon() {
        walk(SUMMON_STEP_BACK)
        delay(2)
        faceSquare(SYMBOL_COORDS)
        spotanimMap(worldRepo, SMOKE_PUFF_SPOTANIM, SYMBOL_COORDS, SMOKE_HEIGHT)
        soundSynth(SUMMON_SOUND)
        val spirit = Npc(THRANTAX, SYMBOL_COORDS)
        spirit.respawns = false
        npcRepo.add(spirit, SPIRIT_TICKS)
        spirit.facePlayer(player)
        delay(1)
        mesbox("Suddenly a mighty spirit appears!")
        recite(spirit)
    }

    private suspend fun ProtectedAccess.recite(spirit: Npc) {
        var correct = false
        val shift = random.of(INCANTATIONS.size)
        val options = List(INCANTATIONS.size) { INCANTATIONS[(it + shift) % INCANTATIONS.size] }
        startDialogue(spirit) {
            chatPlayer(shocked, "Now what were those magic words again?")
            val spoken =
                choice3(options[0], options[0], options[1], options[1], options[2], options[2])
            for (word in spoken.split(' ')) {
                chatPlayer(quiz, "$word...")
            }
            correct = spoken == WORDS_OF_BINDING
            chatNpc(angry, "GRAAAAAARGH!")
            if (!correct) {
                return@startDialogue
            }
            chatNpc(
                angry,
                "Thou hast me in thine control. So that I mayst return from whence I came, I " +
                    "must grant thee a boon. What dost thou wish of me?",
            )
            chatPlayer(shocked, "I wish to free Merlin from his giant crystal!")
            chatNpc(angry, "GRAAAAAARGH!")
            quest.advanceTo(access, STAGE_SPIRIT_BOUND)
            chatNpc(
                angry,
                "The deed is done. Thou mayst now shatter Merlin's crystal with Excalibur, and I " +
                    "can once more rest. Begone! And leave me once more in peace.",
            )
        }
        if (correct) {
            banish(spirit)
            return
        }
        invDel(inv, LIT_BLACK_CANDLE)
        mes("The words come out wrong. The spirit turns on you.")
        spirit.opPlayer2(player, aiInteractions)
    }

    private fun ProtectedAccess.banish(spirit: Npc) {
        if (!spirit.isSlotAssigned) {
            return
        }
        spotanimMap(worldRepo, SMOKE_PUFF_SPOTANIM, spirit.coords, SMOKE_HEIGHT)
        soundSynth(SUMMON_SOUND)
        npcRepo.del(spirit, Int.MAX_VALUE)
    }

    /** The symbol and the ring of tiles around it, which is as close as the ritual needs. */
    private fun ProtectedAccess.onSymbol(): Boolean =
        coords.level == SYMBOL_COORDS.level &&
            abs(coords.x - SYMBOL_COORDS.x) <= 1 &&
            abs(coords.z - SYMBOL_COORDS.z) <= 1

    private fun ProtectedAccess.nearbySpirit(): Npc? =
        npcFind(SYMBOL_COORDS, THRANTAX, SPIRIT_SEARCH_RANGE, HuntVis.Off, search)

    private companion object {
        const val SMOKE_HEIGHT = 124
        const val SPIRIT_TICKS = 300
        const val SPIRIT_SEARCH_RANGE = 8

        /** The binding phrase and the two near-misses Jagex offers beside it. */
        val INCANTATIONS =
            listOf(
                WORDS_OF_BINDING,
                "Snarthtrick Candanto Termon",
                "Snarthanto Candon Termtrick",
            )
    }
}
