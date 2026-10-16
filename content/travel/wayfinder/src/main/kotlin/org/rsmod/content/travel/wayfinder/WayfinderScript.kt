package org.rsmod.content.travel.wayfinder

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.IfScriptArgs
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfScriptTrigger
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Archmage Vaelor (Edgeville) and Arcanist Elowen (Lumbridge): Talk-to explains the network and
 * opens the teleport window; Teleport opens it straight away.
 *
 * The window shows one of three views: every destination in the chosen category, the player's
 * favourites, or their recent journeys, each narrowed by the live search text. Rows are rebuilt
 * from the registry on every change, so a click only ever names a row index (or a star) that the
 * server maps back to a destination itself.
 */
class WayfinderScript @Inject constructor(private val travel: WayfinderTravel) : PluginScript() {
    private val registry: WayfinderRegistry
        get() = travel.registry

    internal data class SearchArgs(val comsub: Int, val op: Int, val text: String) : IfScriptArgs

    override fun ScriptContext.startup() {
        for (mage in WayfinderMages.ALL) {
            onOpNpc1(mage.npc) { talk(it.npc) }
            onOpNpc3(mage.npc) { openWindow() }
        }
        onIfModalButton("$COMPONENT:categories") { selectCategory(it.comsub / CATEGORY_CHILDREN) }
        onIfModalButton("$COMPONENT:list") { clickRow(it.comsub) }
        onIfModalButton("$COMPONENT:view_all") { selectMode(MODE_ALL) }
        onIfModalButton("$COMPONENT:view_favourites") { selectMode(MODE_FAVOURITES) }
        onIfModalButton("$COMPONENT:view_recent") { selectMode(MODE_RECENT) }
        onIfModalButton("$COMPONENT:teleport") { teleportSelected() }
        onIfScriptTrigger<SearchArgs>("$COMPONENT:searchtext") { search(it.text) }
        onIfClose(INTERFACE) {
            player.attr.remove(SEARCH_QUERY)
            ClientScripts.chatDefaultRestoreInput(player)
        }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        startDialogue(npc) {
            chatNpc(happy, "The world stretches far beyond these roads. Where would you like my magic to take you?")
            converse()
        }
    }

    private suspend fun Dialogue.converse() {
        val pick =
            choice3(
                "Show me the teleport destinations.",
                OPEN,
                "What is this teleport network?",
                EXPLAIN,
                "Never mind.",
                LEAVE,
            )
        when (pick) {
            OPEN -> access.openWindow()
            EXPLAIN -> {
                chatPlayer(quiz, "What is this teleport network?")
                chatNpc(
                    neutral,
                    "The wayfinders keep a web of marked places across Gielinor. Stand beside one " +
                        "of us and we can send you along any thread of it, free of charge.",
                )
                chatNpc(
                    neutral,
                    "We will not carry you past a locked door, though. If a place lies beyond a " +
                        "quest, you'll need to finish it before we can send you there.",
                )
                chatNpc(
                    neutral,
                    "And mind the Wilderness. I'll always warn you before sending you somewhere " +
                        "other adventurers may try to take everything you carry.",
                )
                converse()
            }
            else -> chatPlayer(neutral, "Never mind.")
        }
    }

    private fun ProtectedAccess.openWindow() {
        if (travel.mageNear(player) == null) {
            mes("You need to stand beside a wayfinder to travel.")
            return
        }
        player.attr.remove(SEARCH_QUERY)
        setVar(MODE_VARBIT, MODE_ALL)
        setVar(CATEGORY_VARBIT, WayfinderCategory.Towns.id)
        setVar(SELECTED_VARBIT, rows(player).firstOrNull()?.slot ?: 0)
        ifOpenMainModal(INTERFACE)
        ifSetEvents("$COMPONENT:categories", 0 until WayfinderCategory.entries.size * CATEGORY_CHILDREN, IfEvent.Op1)
        ifSetEvents("$COMPONENT:list", 0 until (WayfinderRegistry.MAX_SLOT + 1) * ROW_CHILDREN, IfEvent.Op1)
        ifSetEvents("$COMPONENT:searchtext", -1..-1, IfEvent.Op1, IfEvent.ScriptTrigger)
        for (button in listOf("view_all", "view_favourites", "view_recent", "teleport")) {
            ifSetEvents("$COMPONENT:$button", -1..-1, IfEvent.Op1)
        }
        script(PORTRAIT_SCRIPT, PORTRAIT_NPC.asRSCM(RSCMType.NPC))
        redraw(resetScroll = true)
    }

    private fun ProtectedAccess.selectCategory(index: Int) {
        val category = WayfinderCategory.of(index) ?: return
        setVar(MODE_VARBIT, MODE_ALL)
        setVar(CATEGORY_VARBIT, category.id)
        keepSelectionVisible()
        redraw(resetScroll = true)
    }

    private fun ProtectedAccess.selectMode(mode: Int) {
        setVar(MODE_VARBIT, mode)
        keepSelectionVisible()
        redraw(resetScroll = true)
    }

    private fun ProtectedAccess.search(text: String) {
        val query = text.trim().take(MAX_QUERY)
        if (query.isEmpty()) {
            player.attr.remove(SEARCH_QUERY)
        } else {
            player.attr[SEARCH_QUERY] = query
        }
        keepSelectionVisible()
        drawRows(resetScroll = true)
        drawHeading()
        drawDetails()
    }

    /** Each row has [ROW_CHILDREN] click targets: the row itself, then its favourite star. */
    private fun ProtectedAccess.clickRow(comsub: Int) {
        val destination = rows(player).getOrNull(comsub / ROW_CHILDREN) ?: return
        if (comsub % ROW_CHILDREN == STAR_CHILD) {
            WayfinderPlayerState.toggleFavourite(player, destination.key) { registry[it] != null }
            if (player.vars[MODE_VARBIT] == MODE_FAVOURITES) {
                keepSelectionVisible()
            }
            drawRows(resetScroll = false)
            drawDetails()
            return
        }
        setVar(SELECTED_VARBIT, destination.slot)
        drawRows(resetScroll = false)
        drawDetails()
    }

    private suspend fun ProtectedAccess.teleportSelected() {
        val destination = selected(player)?.takeIf { it in rows(player) } ?: return
        val mage = travel.mageNear(player)
        if (mage == null) {
            ifClose()
            mes("You need to stand beside a wayfinder to travel.")
            return
        }
        travel.travel(this, mage, destination.key, WayfinderTraveller.Player) { target -> confirmDanger(target) }
    }

    private suspend fun ProtectedAccess.confirmDanger(destination: WayfinderDestination): Boolean {
        val level = travel.wildernessLevel(destination)
        val where =
            when {
                level > 0 -> "level $level Wilderness"
                travel.isWilderness(destination) -> "the Wilderness"
                else -> "a dangerous area"
            }
        val warning = destination.warning ?: "Monsters there may attack you on sight."
        return confirmOverlay(
            target = "$COMPONENT:popup",
            title = "Teleport to ${destination.name}?",
            text = "${destination.name} is in $where.<br><br>$warning",
            cancel = "<col=ff0000>Stay here</col>",
            confirm = "<col=0dc10d>Teleport anyway</col>",
        )
    }

    private fun rows(player: Player): List<WayfinderDestination> {
        val view =
            when (player.vars[MODE_VARBIT]) {
                MODE_FAVOURITES -> WayfinderPlayerState.favouriteKeys(player).mapNotNull { registry[it] }
                MODE_RECENT -> WayfinderPlayerState.recentKeys(player).mapNotNull { registry[it] }
                else -> WayfinderCategory.of(player.vars[CATEGORY_VARBIT])?.let(registry::inCategory).orEmpty()
            }
        val query = player.attr[SEARCH_QUERY] ?: return view
        return view.filter { it.matches(query) }
    }

    private fun selected(player: Player): WayfinderDestination? = registry.bySlot(player.vars[SELECTED_VARBIT])

    private fun ProtectedAccess.keepSelectionVisible() {
        val rows = rows(player)
        if (rows.none { it.slot == player.vars[SELECTED_VARBIT] }) {
            setVar(SELECTED_VARBIT, rows.firstOrNull()?.slot ?: 0)
        }
    }

    private fun ProtectedAccess.redraw(resetScroll: Boolean) {
        val mode = player.vars[MODE_VARBIT]
        script(CATEGORIES_SCRIPT, if (mode == MODE_ALL) player.vars[CATEGORY_VARBIT] else -1)
        script(VIEWS_SCRIPT, mode)
        drawHeading()
        drawRows(resetScroll)
        drawDetails()
    }

    private fun ProtectedAccess.drawHeading() {
        val base =
            when (player.vars[MODE_VARBIT]) {
                MODE_FAVOURITES -> "Favourites"
                MODE_RECENT -> "Recent"
                else -> WayfinderCategory.of(player.vars[CATEGORY_VARBIT])?.label.orEmpty()
            }
        val query = player.attr[SEARCH_QUERY]
        ifSetText("$COMPONENT:heading", if (query == null) base else "$base: \"$query\"")
    }

    private fun ProtectedAccess.drawRows(resetScroll: Boolean) {
        val rows = rows(player)
        val favourites = WayfinderPlayerState.favouriteKeys(player).toSet()
        val encoded =
            rows.joinToString("") { destination ->
                val favourite = if (destination.key in favourites) 1 else 0
                "${destination.icon},$favourite,${clean(rowLabel(destination))}|"
            }
        val selected = rows.indexOfFirst { it.slot == player.vars[SELECTED_VARBIT] }
        script(ROWS_SCRIPT, encoded, selected, if (resetScroll) 1 else 0, emptyMessage())
    }

    private fun ProtectedAccess.emptyMessage(): String =
        when {
            player.attr[SEARCH_QUERY] != null -> "No destinations match your search."
            player.vars[MODE_VARBIT] == MODE_FAVOURITES -> "Click a star to add a favourite."
            player.vars[MODE_VARBIT] == MODE_RECENT -> "You haven't travelled yet."
            else -> "Nothing here yet."
        }

    private fun rowLabel(destination: WayfinderDestination): String {
        val level = travel.wildernessLevel(destination)
        return if (level > 0) "${destination.name} <col=ff0000>($level)</col>" else destination.name
    }

    private fun ProtectedAccess.drawDetails() {
        val destination = selected(player)?.takeIf { it in rows(player) }
        if (destination == null) {
            ifSetText("$COMPONENT:details_name", "")
            ifSetText("$COMPONENT:details_desc", "Choose a destination from the list.")
            ifSetText("$COMPONENT:details_tags", "")
            for (field in listOf("arrival", "reqs", "danger", "cost")) {
                ifSetText("$COMPONENT:value_$field", "")
            }
            script(PREVIEW_SCRIPT, -1, -1)
            script(TELEPORT_SCRIPT, 0)
            return
        }
        ifSetText("$COMPONENT:details_name", destination.name)
        ifSetText("$COMPONENT:details_desc", destination.description)
        ifSetText("$COMPONENT:details_tags", destination.tags.joinToString(TAG_SEPARATOR))
        ifSetText("$COMPONENT:value_arrival", destination.arrival)
        ifSetText("$COMPONENT:value_reqs", requirementText(player, destination))
        ifSetText("$COMPONENT:value_danger", dangerText(destination))
        ifSetText("$COMPONENT:value_cost", if (destination.cost == 0) "Free" else "${destination.cost} coins")
        script(PREVIEW_SCRIPT, previewSprite(destination), destination.icon)
        script(TELEPORT_SCRIPT, 1)
    }

    private fun previewSprite(destination: WayfinderDestination): Int = destination.preview ?: -1

    private fun dangerText(destination: WayfinderDestination): String {
        val level = travel.wildernessLevel(destination)
        val danger = "<col=${destination.danger.colour}>${destination.danger.label}</col>"
        return when {
            level > 0 -> "$danger <col=ff0000>(Wild $level)</col>"
            travel.isWilderness(destination) -> "$danger <col=ff0000>(Wild)</col>"
            else -> danger
        }
    }

    private fun requirementText(player: Player, destination: WayfinderDestination): String {
        val quest = destination.quest ?: return "None"
        val name = Quest.get(quest)?.displayName ?: quest
        val colour = if (QuestRequirements.hasCompleted(player, quest)) "00ff00" else "ff0000"
        return "<col=$colour>$name</col>"
    }

    private fun ProtectedAccess.script(name: String, vararg args: Any) {
        player.runClientScript(name.asRSCM(RSCMType.CLIENTSCRIPT), *args)
    }

    private fun clean(text: String): String = text.replace("|", "").replace(",", "")

    private fun ProtectedAccess.setVar(varbit: String, value: Int) = VarPlayerIntMapSetter.set(player, varbit, value)

    private companion object {
        const val INTERFACE = "interface.wayfinder"
        const val COMPONENT = "component.wayfinder"
        const val CATEGORIES_SCRIPT = "clientscript.wayfinder_categories"
        const val VIEWS_SCRIPT = "clientscript.wayfinder_views"
        const val ROWS_SCRIPT = "clientscript.wayfinder_rows"
        const val PREVIEW_SCRIPT = "clientscript.wayfinder_preview"
        const val TELEPORT_SCRIPT = "clientscript.wayfinder_teleport_button"
        const val PORTRAIT_SCRIPT = "clientscript.wayfinder_portrait"
        const val PORTRAIT_NPC = "npc.wayfinder_vaelor"
        const val MODE_VARBIT = "varbit.wayfinder_mode"
        const val CATEGORY_VARBIT = "varbit.wayfinder_category"
        const val SELECTED_VARBIT = "varbit.wayfinder_selected"

        const val MODE_ALL = 0
        const val MODE_FAVOURITES = 1
        const val MODE_RECENT = 2

        /** Click targets per list row and category button (see wayfinder_rows / wayfinder_categories). */
        const val ROW_CHILDREN = 2
        const val STAR_CHILD = 1
        const val CATEGORY_CHILDREN = 1

        const val MAX_QUERY = 24
        const val TAG_SEPARATOR = "  ·  "

        const val OPEN = 1
        const val EXPLAIN = 2
        const val LEAVE = 3

        val SEARCH_QUERY = AttributeKey<String>()
    }
}
