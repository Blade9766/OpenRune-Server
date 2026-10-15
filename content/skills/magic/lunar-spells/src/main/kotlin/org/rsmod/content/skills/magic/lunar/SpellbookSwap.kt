package org.rsmod.content.skills.magic.lunar

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.commons.magic.SpellCastHook
import org.rsmod.api.combat.commons.magic.SpellQuestRequirement
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.autocast.MagicSpellbookManager
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private var Player.spellbookSwapped by boolVarBit("varbit.dream_mentor_swapped")

/**
 * Spellbook Swap lends the player another spellbook for a single spell. The borrowed book goes
 * back to Lunar once a spell from it has been cast, after two minutes, or on logout, so the swap
 * can never be saved with the character.
 */
@Singleton
class SpellbookSwap @Inject constructor(private val books: MagicSpellbookManager) : SpellCastHook {
    fun isSwapped(player: Player): Boolean = player.spellbookSwapped

    fun lend(player: Player, book: Spellbook) {
        books.setSpellbook(player, book)
        player.spellbookSwapped = true
        player.softTimer(SWAP_TIMER, SWAP_TICKS)
    }

    fun returnBook(player: Player) {
        if (!player.spellbookSwapped) {
            return
        }
        player.spellbookSwapped = false
        player.clearSoftTimer(SWAP_TIMER)
        books.setSpellbook(player, Spellbook.Lunars)
    }

    override fun onCast(player: Player, spell: MagicSpell) {
        if (player.spellbookSwapped && spell.spellbook != null && spell.spellbook != Spellbook.Lunars) {
            returnBook(player)
        }
    }

    companion object {
        const val SWAP_TIMER = "timer.dream_mentor_spellbook_swap"
        const val SWAP_TICKS = 200
    }
}

class SpellbookSwapScript
@Inject
constructor(
    private val swap: SpellbookSwap,
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val questRequirements: Set<SpellQuestRequirement>,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfOverlayButton(LunarDreamSpell.SpellbookSwap.component) { chooseBook() }
        onPlayerSoftTimer(SpellbookSwap.SWAP_TIMER) {
            if (swap.isSwapped(player)) {
                player.mes("Your borrowed spellbook returns to the Lunar spells.")
            }
            swap.returnBook(player)
        }
        onPlayerLogout { swap.returnBook(player) }
    }

    private suspend fun ProtectedAccess.chooseBook() {
        val spell = LunarDreamSpell.SpellbookSwap.resolve(spells) ?: return
        if (!runes.canCastSpell(player, spell)) {
            return
        }
        val books = buildList {
            add("Standard" to Spellbook.Standard)
            if (questRequirements.all { it.hasCompleted(player, DESERT_TREASURE) }) {
                add("Ancient" to Spellbook.Ancients)
            }
            add("Arceuus" to Spellbook.Arceuus)
        }
        var picked: Spellbook? = null
        startDialogue {
            picked =
                if (books.size == 3) {
                    choice4(books[0].first, books[0].second, books[1].first, books[1].second, books[2].first, books[2].second, "Cancel", null, title = "Select a spellbook")
                } else {
                    choice3(books[0].first, books[0].second, books[1].first, books[1].second, "Cancel", null, title = "Select a spellbook")
                }
        }
        val book = picked ?: return
        if (runes.attemptCast(player, spell).isFailure()) {
            return
        }
        anim(LunarDreamSpell.SpellbookSwap.seq)
        spotanim(LunarDreamSpell.SpellbookSwap.spotanim)
        statAdvance("stat.magic", spell.castXp)
        swap.lend(player, book)
    }

    private companion object {
        const val DESERT_TREASURE = "quest_deserttreasure"
    }
}
