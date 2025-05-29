package com.kludgeworks.eu.legislation.cellarclient2.models.core

import kotlin.reflect.KProperty

/**
 * Delegate for handling optional XML elements
 */
class OptionalXmlElement<T>(private val defaultValue: T? = null) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T? = defaultValue
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        // Handle setting if needed
    }
}
