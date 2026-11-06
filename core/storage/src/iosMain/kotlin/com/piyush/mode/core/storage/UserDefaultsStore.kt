package com.piyush.mode.core.storage

import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

/** Settings in the app's standard UserDefaults. */
public class UserDefaultsStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(name: String): String? = defaults.stringForKey(name)

    override fun putString(name: String, value: String) {
        defaults.setObject(value, forKey = name)
    }

    /**
     * Mode's own settings: the app's persistent domain (not the global and system defaults that standard
     * defaults also answers), only text values, as Mode saves nothing else.
     */
    override fun names(): Set<String> {
        val domain = NSBundle.mainBundle.bundleIdentifier?.let(defaults::persistentDomainForName) ?: return emptySet()
        return domain.entries.mapNotNull { (key, value) -> (key as? String)?.takeIf { value is String } }.toSet()
    }

    override fun clear() {
        NSBundle.mainBundle.bundleIdentifier?.let(defaults::removePersistentDomainForName)
        defaults.dictionaryRepresentation().keys.forEach { (it as? String)?.let(defaults::removeObjectForKey) }
    }
}
