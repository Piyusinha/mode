@file:OptIn(ExperimentalForeignApi::class)

package com.piyush.mode.app

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
import platform.Security.errSecSuccess
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
object Keychain {
    private const val SERVICE = "com.piyush.mode"

    fun get(account: String): String? = memScoped {
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

    /** Stores [value], or removes the entry when it is null or blank. */
    fun set(account: String, value: String?) {
        val delete = base(account)
        SecItemDelete(delete)
        CFRelease(delete)
        if (value.isNullOrBlank()) return
        val add = base(account)
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        CFDictionaryAddValue(add, kSecValueData, CFBridgingRetain(data))
        CFDictionaryAddValue(add, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        SecItemAdd(add, null)
        CFRelease(add)
    }

    private fun base(account: String): CFMutableDictionaryRef? {
        val dict = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dict, kSecAttrService, CFStringCreateWithCString(null, SERVICE, kCFStringEncodingUTF8))
        CFDictionaryAddValue(dict, kSecAttrAccount, CFStringCreateWithCString(null, account, kCFStringEncodingUTF8))
        return dict
    }
}
