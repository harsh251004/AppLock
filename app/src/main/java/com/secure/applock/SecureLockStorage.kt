package com.secure.applock

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest

/**
 * Stores and verifies custom PIN, pattern, and password using salted SHA-256 hashes
 * in EncryptedSharedPreferences. Never stores plaintext.
 * Handles init/read failures so the app does not crash on devices where
 * MasterKeys or EncryptedSharedPreferences throw.
 */
class SecureLockStorage(context: Context) {

    private val prefs: SharedPreferences? = try {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "app_lock_secure",
            masterKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        null
    }

    fun setPin(pin: String) {
        val p = prefs ?: return
        try {
            val salt = randomSalt()
            p.edit()
                .putString(KEY_PIN_SALT, salt)
                .putString(KEY_PIN_HASH, hash(pin, salt))
                .putInt(KEY_PIN_LENGTH, pin.length.coerceIn(4, 6))
                .apply()
        } catch (_: Exception) { }
    }

    fun verifyPin(pin: String): Boolean {
        val p = prefs ?: return false
        return try {
            val salt = p.getString(KEY_PIN_SALT, null) ?: return false
            val stored = p.getString(KEY_PIN_HASH, null) ?: return false
            hash(pin, salt) == stored
        } catch (_: Exception) {
            false
        }
    }

    fun hasPin(): Boolean = prefs?.contains(KEY_PIN_HASH) == true

    /** Length of the stored PIN (4–6). Defaults to 6 if not set (e.g. older installs). */
    fun getPinLength(): Int = prefs?.getInt(KEY_PIN_LENGTH, 6)?.coerceIn(4, 6) ?: 6

    /** True if PIN length was ever stored (set when PIN was set or learned on first unlock). */
    fun hasStoredPinLength(): Boolean = prefs?.contains(KEY_PIN_LENGTH) == true

    /** Saves only the PIN length (used when we learn it on first successful unlock). */
    fun setPinLength(length: Int) {
        prefs?.edit()?.putInt(KEY_PIN_LENGTH, length.coerceIn(4, 6))?.apply()
    }

    fun setPattern(pattern: List<Int>) {
        val p = prefs ?: return
        try {
            val value = pattern.joinToString(",")
            val salt = randomSalt()
            p.edit()
                .putString(KEY_PATTERN_SALT, salt)
                .putString(KEY_PATTERN_HASH, hash(value, salt))
                .apply()
        } catch (_: Exception) { }
    }

    fun verifyPattern(pattern: List<Int>): Boolean {
        val p = prefs ?: return false
        return try {
            val value = pattern.joinToString(",")
            val salt = p.getString(KEY_PATTERN_SALT, null) ?: return false
            val stored = p.getString(KEY_PATTERN_HASH, null) ?: return false
            hash(value, salt) == stored
        } catch (_: Exception) {
            false
        }
    }

    fun hasPattern(): Boolean = prefs?.contains(KEY_PATTERN_HASH) == true

    fun setPassword(password: String) {
        val p = prefs ?: return
        try {
            val salt = randomSalt()
            p.edit()
                .putString(KEY_PASSWORD_SALT, salt)
                .putString(KEY_PASSWORD_HASH, hash(password, salt))
                .apply()
        } catch (_: Exception) { }
    }

    fun verifyPassword(password: String): Boolean {
        val p = prefs ?: return false
        return try {
            val salt = p.getString(KEY_PASSWORD_SALT, null) ?: return false
            val stored = p.getString(KEY_PASSWORD_HASH, null) ?: return false
            hash(password, salt) == stored
        } catch (_: Exception) {
            false
        }
    }

    fun hasPassword(): Boolean = prefs?.contains(KEY_PASSWORD_HASH) == true

    fun clearAll() {
        prefs?.edit()?.apply {
            remove(KEY_PIN_SALT)
            remove(KEY_PIN_HASH)
            remove(KEY_PIN_LENGTH)
            remove(KEY_PATTERN_SALT)
            remove(KEY_PATTERN_HASH)
            remove(KEY_PASSWORD_SALT)
            remove(KEY_PASSWORD_HASH)
            apply()
        }
    }

    private fun hash(input: String, salt: String): String {
        val bytes = (salt + input).toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private fun randomSalt(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    companion object {
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_LENGTH = "pin_length"
        private const val KEY_PATTERN_SALT = "pattern_salt"
        private const val KEY_PATTERN_HASH = "pattern_hash"
        private const val KEY_PASSWORD_SALT = "password_salt"
        private const val KEY_PASSWORD_HASH = "password_hash"
    }
}
