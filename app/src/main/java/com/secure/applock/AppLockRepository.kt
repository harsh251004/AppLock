package com.secure.applock

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Collections

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_lock")

/** Exposed state for UI and flow. */
data class LockState(
    val lockedPackages: Set<String>,
    val lockMode: LockMode,
    val sessionUnlocked: Set<String>,
    val lockType: LockType,
    val useFingerprintWithCustom: Boolean
)

/**
 * Single source of truth for locked packages, lock mode, and session-unlocked state.
 * Uses DataStore for persistence; maintains a synchronous snapshot so the
 * AccessibilityService can read without blocking (no runBlocking in hot path).
 */
class AppLockRepository(context: Context) {

    private val store = context.dataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var snapshot = LockState(
        lockedPackages = emptySet(),
        lockMode = LockMode.LOCK_ON_EXIT,
        sessionUnlocked = emptySet(),
        lockType = LockType.DEVICE_CREDENTIAL,
        useFingerprintWithCustom = false
    )

    private val secureStorage = SecureLockStorage(context)

    val lockStateFlow: Flow<LockState> = store.data.map { prefs ->
        LockState(
            lockedPackages = prefs[KEY_LOCKED_PACKAGES].orEmpty(),
            lockMode = LockMode.from(prefs[KEY_LOCK_MODE]),
            sessionUnlocked = prefs[KEY_SESSION_UNLOCKED].orEmpty(),
            lockType = LockType.from(prefs[KEY_LOCK_TYPE]),
            useFingerprintWithCustom = prefs[KEY_USE_FINGERPRINT_WITH_CUSTOM] ?: false
        )
    }

    val amoledBlackFlow: Flow<Boolean> = store.data.map { prefs ->
        prefs[KEY_AMOLED_BLACK] ?: false
    }

    init {
        scope.launch {
            lockStateFlow.collect { snapshot = it }
        }
    }

    // --- Synchronous API for AccessibilityService / receivers ---

    fun getLockedPackages(): Set<String> = Collections.unmodifiableSet(snapshot.lockedPackages)
    fun getLockMode(): LockMode = snapshot.lockMode
    fun getLockType(): LockType = snapshot.lockType
    fun getUseFingerprintWithCustom(): Boolean = snapshot.useFingerprintWithCustom
    fun getSessionUnlockedPackages(): Set<String> = Collections.unmodifiableSet(snapshot.sessionUnlocked)
    fun isPackageLocked(packageName: String): Boolean = snapshot.lockedPackages.contains(packageName)
    fun isSessionUnlocked(packageName: String): Boolean = snapshot.sessionUnlocked.contains(packageName)

    // --- Custom lock (PIN / pattern / password) ---
    val secureLockStorage: SecureLockStorage get() = secureStorage
    fun verifyPin(pin: String): Boolean = secureStorage.verifyPin(pin)
    fun getPinLength(): Int = secureStorage.getPinLength()
    fun hasStoredPinLength(): Boolean = secureStorage.hasStoredPinLength()
    fun setPinLength(length: Int) = secureStorage.setPinLength(length)
    fun verifyPattern(pattern: List<Int>): Boolean = secureStorage.verifyPattern(pattern)
    fun verifyPassword(password: String): Boolean = secureStorage.verifyPassword(password)
    fun setPin(pin: String) = secureStorage.setPin(pin)
    fun setPattern(pattern: List<Int>) = secureStorage.setPattern(pattern)
    fun setPassword(password: String) = secureStorage.setPassword(password)

    fun lockPackage(packageName: String) {
        snapshot = snapshot.copy(sessionUnlocked = snapshot.sessionUnlocked - packageName)
        scope.launch {
            store.edit { prefs ->
                val set = (prefs[KEY_SESSION_UNLOCKED].orEmpty() - packageName)
                prefs[KEY_SESSION_UNLOCKED] = set
            }
        }
    }

    fun unlockSessionForPackage(packageName: String) {
        snapshot = snapshot.copy(sessionUnlocked = snapshot.sessionUnlocked + packageName)
        scope.launch {
            store.edit { prefs ->
                val set = prefs[KEY_SESSION_UNLOCKED].orEmpty() + packageName
                prefs[KEY_SESSION_UNLOCKED] = set
            }
        }
    }

    // --- Async API for UI (updates snapshot via flow) ---

    suspend fun setLockMode(mode: LockMode) {
        if (mode == LockMode.LOCK_ON_EXIT) {
            snapshot = snapshot.copy(lockMode = mode, sessionUnlocked = emptySet())
            store.edit { prefs ->
                prefs[KEY_LOCK_MODE] = mode.value
                prefs[KEY_SESSION_UNLOCKED] = emptySet()
            }
        } else {
            snapshot = snapshot.copy(lockMode = mode)
            store.edit { it[KEY_LOCK_MODE] = mode.value }
        }
    }

    fun addLockedPackageImmediate(packageName: String) {
        snapshot = snapshot.copy(lockedPackages = snapshot.lockedPackages + packageName)
    }

    suspend fun addLockedPackage(packageName: String) {
        snapshot = snapshot.copy(lockedPackages = snapshot.lockedPackages + packageName)
        store.edit { prefs ->
            val set = prefs[KEY_LOCKED_PACKAGES].orEmpty() + packageName
            prefs[KEY_LOCKED_PACKAGES] = set
        }
    }

    fun removeLockedPackageImmediate(packageName: String) {
        snapshot = snapshot.copy(
            lockedPackages = snapshot.lockedPackages - packageName,
            sessionUnlocked = snapshot.sessionUnlocked - packageName
        )
    }

    suspend fun removeLockedPackage(packageName: String) {
        snapshot = snapshot.copy(
            lockedPackages = snapshot.lockedPackages - packageName,
            sessionUnlocked = snapshot.sessionUnlocked - packageName
        )
        store.edit { prefs ->
            prefs[KEY_LOCKED_PACKAGES] = prefs[KEY_LOCKED_PACKAGES].orEmpty() - packageName
            prefs[KEY_SESSION_UNLOCKED] = prefs[KEY_SESSION_UNLOCKED].orEmpty() - packageName
        }
    }

    suspend fun setAmoledBlack(enabled: Boolean) {
        store.edit { it[KEY_AMOLED_BLACK] = enabled }
    }

    suspend fun setLockType(type: LockType) {
        if (type == LockType.DEVICE_CREDENTIAL) secureStorage.clearAll()
        snapshot = snapshot.copy(lockType = type)
        store.edit { it[KEY_LOCK_TYPE] = type.value }
    }

    suspend fun setUseFingerprintWithCustom(enabled: Boolean) {
        snapshot = snapshot.copy(useFingerprintWithCustom = enabled)
        store.edit { it[KEY_USE_FINGERPRINT_WITH_CUSTOM] = enabled }
    }

    /** Blocking read for initial snapshot before flow is collected (e.g. service startup). */
    fun refreshSnapshotBlocking() {
        runBlocking { snapshot = lockStateFlow.first() }
    }

    /** Suspend read to refresh snapshot without blocking the calling thread. */
    suspend fun refreshSnapshot() {
        snapshot = lockStateFlow.first()
    }

    companion object {
        private val KEY_LOCK_MODE = stringPreferencesKey("lock_mode")
        private val KEY_LOCK_TYPE = stringPreferencesKey("lock_type")
        private val KEY_USE_FINGERPRINT_WITH_CUSTOM = booleanPreferencesKey("use_fingerprint_with_custom")
        private val KEY_LOCKED_PACKAGES = stringSetPreferencesKey("locked_packages")
        private val KEY_SESSION_UNLOCKED = stringSetPreferencesKey("session_unlocked")
        private val KEY_AMOLED_BLACK = booleanPreferencesKey("amoled_black")
    }
}
