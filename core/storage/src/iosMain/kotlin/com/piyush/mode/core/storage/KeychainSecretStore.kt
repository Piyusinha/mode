@file:OptIn(ExperimentalForeignApi::class)

package com.piyush.mode.core.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecSuccess
import platform.Security.errSecItemNotFound
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Secrets (the model API key) in the iOS Keychain: encrypted, this device
 * only, readable after the first unlock so background runs can use them.
 */
public class KeychainSecretStore(private val service: String = "com.piyush.mode") : SecretStore {

    override fun get(name: String): String? = memScoped {
        val account = name
        val query = base(account)
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) return null
        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    override fun set(name: String, value: String?) {
        val account = name
        val query = base(account)
        if (value.isNullOrBlank()) {
            val status = SecItemDelete(query)
            CFRelease(query)
            check(status == errSecSuccess || status == errSecItemNotFound) { "Could not remove a credential from Keychain ($status)." }
            return
        }
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            ?: error("Could not encode a credential for Keychain.")
        val retained = CFBridgingRetain(data)
        val changes = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(changes, kSecValueData, retained)
        CFDictionaryAddValue(changes, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        // Updating preserves the previous value if a rotated-token write fails.
        var status = SecItemUpdate(query, changes)
        if (status == errSecItemNotFound) {
            CFDictionaryAddValue(query, kSecValueData, retained)
            CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            status = SecItemAdd(query, null)
        }
        CFRelease(changes)
        CFRelease(query)
        CFRelease(retained)
        check(status == errSecSuccess) { "Could not save a credential in Keychain ($status). Unlock this iPhone and try again." }
    }

    /** Every item under Mode's service: keys, tokens and sign-ins. */
    override fun clear() {
        val dict = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dict, kSecAttrService, CFStringCreateWithCString(null, service, kCFStringEncodingUTF8))
        val status = SecItemDelete(dict)
        CFRelease(dict)
        check(status == errSecSuccess || status == errSecItemNotFound) { "Could not remove Mode's credentials from Keychain ($status)." }
    }

    private fun base(account: String): CFMutableDictionaryRef? {
        val dict = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dict, kSecAttrService, CFStringCreateWithCString(null, service, kCFStringEncodingUTF8))
        CFDictionaryAddValue(dict, kSecAttrAccount, CFStringCreateWithCString(null, account, kCFStringEncodingUTF8))
        return dict
    }
}
