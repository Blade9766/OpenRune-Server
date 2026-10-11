package org.rsmod.content.events.halloween

import org.rsmod.api.player.music.MusicAreaTrackHook
import org.rsmod.plugin.module.PluginModule

class HalloweenModule : PluginModule() {
    override fun bind() {
        addSetBinding<MusicAreaTrackHook>(HalloweenMusic::class.java)
    }
}
