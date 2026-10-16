package org.rsmod.content.travel.wayfinder

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
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
 * opens the teleport window; Teleport opens it straight away. The window's rows are rebuilt from
 * the registry and the player's view varbits on every click, so a click only ever names a row
 * index the server maps back to a destination itself.
 */
class WayfinderScript @Inject constructor(private val travel: WayfinderTravel) : PluginScript() {
    private val registry: WayfinderRegistry
        get() = travel.registry

    override fun ScriptContext.startup() {
        for (mage in WayfinderMages.ALL) {
            onOpNpc1(mage.npc) { talk(it.npc) }
            onOpNpc3(mage.npc) { openWindow() }
        }
        onIfModalButton("$COMPONENT:categories") { selectView(it.comsub) }
        onIfModalButton("$COMPONENT:list") { selectRow(it.comsub) }
        onIfModalButton("$COMPONENT:search") { search() }
        onIfModalButton("$COMPONENT:favourite") { toggleFavourite() }
        onIfModalButton("$COMPONENT:teleport") { teleportSelected() }
        onIfClose(INTERFACE) { player.attr.remove(SEARCH_QUERY) }
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
        val start = if (WayfinderPlayerState.favouriteSlots(player).any { registry.bySlot(it) != null }) VIEW_FAVOURITES else VIEW_FIRST_CATEGORY
        setVar(CATEGORY_VARBIT, start)
        setVar(SELECTED_VARBIT, 0)
        ifOpenMainModal(INTERFACE)
        ifSetEvents("$COMPONENT:categories", 0 until VIEW_COUNT, IfEvent.Op1)
        ifSetEvents("$COMPONENT:list", 0..WayfinderRegistry.MAX_SLOT, IfEvent.Op1)
        for (button in listOf("search", "favourite", "teleport")) {
            ifSetEvents("$COMPONENT:$button", -1..-1, IfEvent.Op1)
        }
        drawViews()
        drawRows(resetScroll = true)
        drawDetails()
    }

    private fun ProtectedAccess.selectView(view: Int) {
        if (view !in 0 until VIEW_COUNT || (view == VIEW_SEARCH && player.attr[SEARCH_QUERY] == null)) {
            return
        }
        setVar(CATEGORY_VARBIT, view)
        drawViews()
        drawRows(resetScroll = true)
    }

    private fun ProtectedAccess.selectRow(row: Int) {
        val destination = rows(player).getOrNull(row) ?: return
        setVar(SELECTED_VARBIT, destination.slot)
        drawRows(resetScroll = false)
        drawDetails()
    }

    private suspend fun ProtectedAccess.search() {
        val query = stringDialog("Search for a destination:").trim()
        if (query.isEmpty()) {
            return
        }
        player.attr[SEARCH_QUERY] = query.take(MAX_QUERY)
        setVar(CATEGORY_VARBIT, VIEW_SEARCH)
        drawViews()
        drawRows(resetScroll = true)
        if (registry.search(query).isEmpty()) {
            mes("No wayfinder destination matches '${query.take(MAX_QUERY)}'.")
        }
    }

    private fun ProtectedAccess.toggleFavourite() {
        val destination = selected(player) ?: return
        val favourite = WayfinderPlayerState.toggleFavourite(player, destination.slot)
        mes(if (favourite) "${destination.name} added to your favourites." else "${destination.name} removed from your favourites.")
        drawViews()
        drawRows(resetScroll = false)
        drawDetails()
    }

    private suspend fun ProtectedAccess.teleportSelected() {
        val destination = selected(player) ?: return
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

    private fun rows(player: Player): List<WayfinderDestination> =
        when (val view = player.vars[CATEGORY_VARBIT]) {
            VIEW_FAVOURITES -> WayfinderPlayerState.favouriteSlots(player).mapNotNull(registry::bySlot)
            VIEW_RECENT -> WayfinderPlayerState.recentSlots(player).mapNotNull(registry::bySlot)
            VIEW_SEARCH -> player.attr[SEARCH_QUERY]?.let(registry::search).orEmpty()
            else -> WayfinderCategory.of(view - VIEW_FIRST_CATEGORY)?.let(registry::inCategory).orEmpty()
        }

    private fun selected(player: Player): WayfinderDestination? = registry.bySlot(player.vars[SELECTED_VARBIT])

    private fun ProtectedAccess.drawViews() {
        val favourites = WayfinderPlayerState.favouriteSlots(player).count { registry.bySlot(it) != null }
        val labels =
            buildList {
                add("Favourites ($favourites)")
                add("Recent")
                for (category in WayfinderCategory.entries) {
                    add("${category.label} (${registry.inCategory(category).size})")
                }
                player.attr[SEARCH_QUERY]?.let { add("Search: $it") }
            }
        drawList("$COMPONENT:categories", scrollbar = null, labels, player.vars[CATEGORY_VARBIT], resetScroll = true)
    }

    private fun ProtectedAccess.drawRows(resetScroll: Boolean) {
        val rows = rows(player)
        val selected = rows.indexOfFirst { it.slot == player.vars[SELECTED_VARBIT] }
        val labels = rows.map(::rowLabel).ifEmpty { listOf(emptyLabel()) }
        drawList("$COMPONENT:list", "$COMPONENT:scrollbar", labels, selected, resetScroll)
    }

    private fun ProtectedAccess.emptyLabel(): String =
        when (player.vars[CATEGORY_VARBIT]) {
            VIEW_FAVOURITES -> "No favourites yet."
            VIEW_RECENT -> "No recent journeys."
            else -> "Nothing found."
        }

    private fun rowLabel(destination: WayfinderDestination): String {
        val level = travel.wildernessLevel(destination)
        return if (level > 0) "${destination.name} <col=ff0000>($level)</col>" else destination.name
    }

    private fun ProtectedAccess.drawList(component: String, scrollbar: String?, labels: List<String>, selected: Int, resetScroll: Boolean) {
        player.runClientScript(
            DRAW_LIST.asRSCM(RSCMType.CLIENTSCRIPT),
            component.asRSCM(RSCMType.COMPONENT),
            scrollbar?.asRSCM(RSCMType.COMPONENT) ?: -1,
            labels.joinToString("") { "${it.replace("|", "")}|" },
            selected,
            if (resetScroll) 1 else 0,
        )
    }

    private fun ProtectedAccess.drawDetails() {
        val destination = selected(player)
        if (destination == null) {
            ifSetText("$COMPONENT:detail_name", "Choose a destination")
            ifSetText("$COMPONENT:detail_category", "")
            ifSetText("$COMPONENT:detail_danger", "")
            ifSetText("$COMPONENT:detail_desc", "Pick a category on the left, or search, then select a destination from the list.")
            ifSetText("$COMPONENT:detail_reqs", "")
            label("favourite", "Add to favourites", DISABLED_COLOUR)
            label("teleport", "Teleport", DISABLED_COLOUR)
            return
        }
        ifSetText("$COMPONENT:detail_name", destination.name)
        ifSetText("$COMPONENT:detail_category", destination.category.label)
        ifSetText("$COMPONENT:detail_danger", dangerText(destination))
        ifSetText("$COMPONENT:detail_desc", destination.description)
        ifSetText("$COMPONENT:detail_reqs", requirementText(player, destination))
        val favourite = WayfinderPlayerState.isFavourite(player, destination.slot)
        label("favourite", if (favourite) "Remove from favourites" else "Add to favourites", ENABLED_COLOUR)
        label("teleport", "Teleport", ENABLED_COLOUR)
    }

    private fun dangerText(destination: WayfinderDestination): String {
        val level = travel.wildernessLevel(destination)
        val danger = "<col=${destination.danger.colour}>${destination.danger.label}</col>"
        return when {
            level > 0 -> "$danger<br><col=ff0000>Wilderness level $level</col>"
            travel.isWilderness(destination) -> "$danger<br><col=ff0000>Wilderness</col>"
            else -> danger
        }
    }

    private fun requirementText(player: Player, destination: WayfinderDestination): String {
        val quest = destination.quest ?: return "No requirements"
        return met(QuestRequirements.hasCompleted(player, quest), "Quest: ${Quest.get(quest)?.displayName ?: quest}")
    }

    private fun met(ok: Boolean, text: String): String = if (ok) "<col=00ff00>$text</col>" else "<col=ff0000>$text</col>"

    private fun ProtectedAccess.label(button: String, text: String, colour: Int) {
        player.runClientScript(
            LABEL.asRSCM(RSCMType.CLIENTSCRIPT),
            "$COMPONENT:$button".asRSCM(RSCMType.COMPONENT),
            text,
            colour,
        )
    }

    private fun ProtectedAccess.setVar(varbit: String, value: Int) = VarPlayerIntMapSetter.set(player, varbit, value)

    private companion object {
        const val INTERFACE = "interface.wayfinder"
        const val COMPONENT = "component.wayfinder"
        const val DRAW_LIST = "clientscript.wayfinder_draw_list"
        const val LABEL = "clientscript.wayfinder_label"
        const val CATEGORY_VARBIT = "varbit.wayfinder_category"
        const val SELECTED_VARBIT = "varbit.wayfinder_selected"

        const val VIEW_FAVOURITES = 0
        const val VIEW_RECENT = 1
        const val VIEW_FIRST_CATEGORY = 2
        val VIEW_SEARCH = VIEW_FIRST_CATEGORY + WayfinderCategory.entries.size
        val VIEW_COUNT = VIEW_SEARCH + 1

        const val MAX_QUERY = 24
        const val ENABLED_COLOUR = 0xff981f
        const val DISABLED_COLOUR = 0x9f9f9f

        const val OPEN = 1
        const val EXPLAIN = 2
        const val LEAVE = 3

        val SEARCH_QUERY = AttributeKey<String>()
    }
}
