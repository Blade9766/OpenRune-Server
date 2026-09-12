package org.rsmod.api.player.interact

import dev.openrune.definition.type.widget.ComponentType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.ApEvent
import org.rsmod.api.player.events.interact.ObjTContentEvents
import org.rsmod.api.player.events.interact.ObjTDefaultEvents
import org.rsmod.api.player.events.interact.ObjTEvents
import org.rsmod.api.player.events.interact.OpEvent
import org.rsmod.events.EventBus
import org.rsmod.game.obj.Obj
import org.rsmod.game.type.getObj

/** Resolves the script triggers for an interface component used on a ground obj. */
public class ObjTInteractions @Inject constructor(private val eventBus: EventBus) {
    public fun opTrigger(
        obj: Obj,
        objType: ItemServerType?,
        component: ComponentType,
        comsub: Int,
        type: ItemServerType = getObj(obj),
    ): OpEvent? {
        val typeEvent = ObjTEvents.Op(obj, type, objType, comsub, component)
        if (eventBus.contains(typeEvent::class.java, typeEvent.id)) {
            return typeEvent
        }

        val contentEvent = ObjTContentEvents.Op(obj, type, objType, comsub, component)
        if (eventBus.contains(contentEvent::class.java, contentEvent.id)) {
            return contentEvent
        }

        val defaultEvent = ObjTDefaultEvents.Op(obj, type, objType, comsub, component)
        if (eventBus.contains(defaultEvent::class.java, defaultEvent.id)) {
            return defaultEvent
        }

        return null
    }

    public fun hasOpTrigger(
        obj: Obj,
        objType: ItemServerType?,
        component: ComponentType,
        comsub: Int,
        type: ItemServerType = getObj(obj),
    ): Boolean = opTrigger(obj, objType, component, comsub, type) != null

    public fun apTrigger(
        obj: Obj,
        objType: ItemServerType?,
        component: ComponentType,
        comsub: Int,
        type: ItemServerType = getObj(obj),
    ): ApEvent? {
        val typeEvent = ObjTEvents.Ap(obj, type, objType, comsub, component)
        if (eventBus.contains(typeEvent::class.java, typeEvent.id)) {
            return typeEvent
        }

        val contentEvent = ObjTContentEvents.Ap(obj, type, objType, comsub, component)
        if (eventBus.contains(contentEvent::class.java, contentEvent.id)) {
            return contentEvent
        }

        val defaultEvent = ObjTDefaultEvents.Ap(obj, type, objType, comsub, component)
        if (eventBus.contains(defaultEvent::class.java, defaultEvent.id)) {
            return defaultEvent
        }

        return null
    }

    public fun hasApTrigger(
        obj: Obj,
        objType: ItemServerType?,
        component: ComponentType,
        comsub: Int,
        type: ItemServerType = getObj(obj),
    ): Boolean = apTrigger(obj, objType, component, comsub, type) != null
}
