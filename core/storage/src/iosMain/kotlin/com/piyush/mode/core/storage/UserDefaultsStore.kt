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

    override fun clear() {
        NSBundle.mainBundle.bundleIdentifier?.let(defaults::removePersistentDomainForName)
        defaults.dictionaryRepresentation().keys.forEach { (it as? String)?.let(defaults::removeObjectForKey) }
    }
}
