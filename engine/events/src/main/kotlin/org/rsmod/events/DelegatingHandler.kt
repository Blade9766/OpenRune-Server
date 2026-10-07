package org.rsmod.events

/**
 * A subscriber that wraps another, so [EventBus.removeByClassLoader] attributes it to whoever
 * registered [delegate] rather than to the module that did the wrapping.
 */
public interface DelegatingHandler {
    public val delegate: Any
}

internal fun Any.registrantClassLoader(): ClassLoader? {
    var handler: Any = this
    while (handler is DelegatingHandler) {
        handler = handler.delegate
    }
    return handler.javaClass.classLoader
}
