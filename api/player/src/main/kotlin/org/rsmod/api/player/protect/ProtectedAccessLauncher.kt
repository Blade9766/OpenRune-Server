package org.rsmod.api.player.protect

import jakarta.inject.Inject
import kotlin.coroutines.startCoroutine
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.output.mes
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.coroutine.suspension.GameCoroutineSimpleCompletion
import org.rsmod.game.entity.Player

public class ProtectedAccessLauncher
@Inject
constructor(private val contextFactory: ProtectedAccessContextFactory) {
    public fun launch(
        player: Player,
        busyText: String? = null,
        block: suspend ProtectedAccess.() -> Unit,
    ): Boolean = withProtectedAccess(player, contextFactory.create(), busyText, block)

    @InternalApi(message = "Usage of this function should only be used internally, or sparingly.")
    public fun launchLenient(player: Player, block: suspend ProtectedAccess.() -> Unit) {
        if (player.isDelayed) {
            launchBesideDelayedScript(player, block)
            return
        }
        player.launch {
            val protectedAccess = ProtectedAccess(player, this, contextFactory.create())
            block(protectedAccess)
        }
    }

    /**
     * Overlay buttons (prayers, the special attack orb, combat styles) are clicked mid-delay, and
     * launching them normally would cancel the delayed script - a death sequence cancelled this
     * way leaves the player at zero hitpoints and never respawns them. The button runs in its own
     * coroutine instead, and is dropped if it tries to suspend, as a delayed player cannot.
     */
    private fun launchBesideDelayedScript(
        player: Player,
        block: suspend ProtectedAccess.() -> Unit,
    ) {
        val coroutine = GameCoroutine()
        val body: suspend GameCoroutine.() -> Unit = {
            block(ProtectedAccess(player, this, contextFactory.create()))
        }
        body.startCoroutine(coroutine, GameCoroutineSimpleCompletion)
        if (coroutine.isSuspended) {
            coroutine.cancel()
        }
    }

    public companion object {
        public fun withProtectedAccess(
            player: Player,
            context: ProtectedAccessContext,
            busyText: String? = null,
            block: suspend ProtectedAccess.() -> Unit,
        ): Boolean {
            if (player.isAccessProtected) {
                busyText?.let { player.mes(it) }
                return false
            }
            player.launch {
                val protectedAccess = ProtectedAccess(player, this, context)
                block(protectedAccess)
            }
            return true
        }
    }
}
