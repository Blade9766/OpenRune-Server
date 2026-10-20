package org.rsmod.content.other.cheatmenu

import dev.openrune.definition.type.widget.IfEvent
import java.util.WeakHashMap
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.Appearance

/**
 * Who `interface.player_design` edits for an administrator. It is their own character unless
 * another feature (the bot appearance editor) opened it for a different player with [open]; the
 * designer's arrows, gender and pronoun buttons then change that player instead, and Confirm,
 * closing and any extra buttons go to the [Session].
 */
object PlayerDesign {
    interface Session {
        val target: Player

        fun confirm(access: ProtectedAccess)

        /** A button the session drew on `player_design:main`, by its comsub. */
        fun button(access: ProtectedAccess, comsub: Int)

        /** The window closed; [confirmed] says whether Confirm was pressed first. */
        fun closed(admin: Player, confirmed: Boolean)
    }

    private class Open(val session: Session, var confirmed: Boolean = false)

    private val open = WeakHashMap<Player, Open>()

    /** Opens the designer on [session]'s target, wired the same way as the cheat menu's Looks. */
    fun open(access: ProtectedAccess, session: Session) {
        val admin = access.player
        end(admin)
        access.ifOpenMainModal(CheatMenuScript.DESIGN)
        open[admin] = Open(session)
        for (component in CheatMenuScript.designButtons()) {
            access.ifSetEvents(component, -1..-1, IfEvent.Op1)
        }
        val offset = CheatMenuScript.PRONOUN_ROW_OFFSET
        access.ifSetEvents(CheatMenuScript.PRONOUN_BUTTONS, offset + Appearance.PRONOUN_HE..offset + Appearance.PRONOUN_THEY, IfEvent.Op1)
        val target = session.target.appearance
        access.vars[CheatMenuScript.DESIGN_BODY_TYPE] = target.bodyType
        access.vars[CheatMenuScript.PRONOUN_SETTING] = target.pronoun
    }

    fun close(access: ProtectedAccess) {
        access.ifCloseSub(CheatMenuScript.DESIGN)
    }

    /** Points the designer's gender and pronoun buttons back at [target]'s current look. */
    fun refresh(access: ProtectedAccess, target: Player) {
        access.vars[CheatMenuScript.DESIGN_BODY_TYPE] = target.appearance.bodyType
        access.vars[CheatMenuScript.PRONOUN_SETTING] = target.appearance.pronoun
    }

    fun target(admin: Player): Player = open[admin]?.session?.target ?: admin

    fun session(admin: Player): Session? = open[admin]?.session

    fun markConfirmed(admin: Player) {
        open[admin]?.confirmed = true
    }

    /** Ends the admin's session, if any, telling it the window closed. */
    fun end(admin: Player) {
        val ended = open.remove(admin) ?: return
        admin.pronounSetting = admin.appearance.pronoun
        ended.session.closed(admin, ended.confirmed)
    }
}

private var Player.pronounSetting by intVarBit(CheatMenuScript.PRONOUN_SETTING)
