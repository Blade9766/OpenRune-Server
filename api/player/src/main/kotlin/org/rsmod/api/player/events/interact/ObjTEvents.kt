package org.rsmod.api.player.events.interact

import dev.openrune.definition.type.widget.ComponentType
import dev.openrune.types.ItemServerType
import org.rsmod.events.EventBus
import org.rsmod.game.obj.Obj

/**
 * Interface component (usually a spell) used on a ground obj: the client's `OpObjT` packet.
 *
 * @param obj The ground obj the component was used on.
 * @param type The [ItemServerType] of [obj].
 * @param objType The inventory obj selected alongside the component, if any.
 */
public class ObjTEvents {
    public class Op(
        public val obj: Obj,
        public val type: ItemServerType,
        public val objType: ItemServerType?,
        public val comsub: Int,
        component: ComponentType,
    ) : OpEvent(EventBus.composeLongKey(type.id, component.packed))

    public class Ap(
        public val obj: Obj,
        public val type: ItemServerType,
        public val objType: ItemServerType?,
        public val comsub: Int,
        component: ComponentType,
    ) : ApEvent(EventBus.composeLongKey(type.id, component.packed))
}

public class ObjTContentEvents {
    public class Op(
        public val obj: Obj,
        public val type: ItemServerType,
        public val objType: ItemServerType?,
        public val comsub: Int,
        component: ComponentType,
        objContent: Int = type.contentGroup,
    ) : OpEvent(EventBus.composeLongKey(objContent, component.packed))

    public class Ap(
        public val obj: Obj,
        public val type: ItemServerType,
        public val objType: ItemServerType?,
        public val comsub: Int,
        component: ComponentType,
        objContent: Int = type.contentGroup,
    ) : ApEvent(EventBus.composeLongKey(objContent, component.packed))
}

public class ObjTDefaultEvents {
    public class Op(
        public val obj: Obj,
        public val type: ItemServerType,
        public val objType: ItemServerType?,
        public val comsub: Int,
        component: ComponentType,
    ) : OpEvent(component.packed.toLong())

    public class Ap(
        public val obj: Obj,
        public val type: ItemServerType,
        public val objType: ItemServerType?,
        public val comsub: Int,
        component: ComponentType,
    ) : ApEvent(component.packed.toLong())
}
