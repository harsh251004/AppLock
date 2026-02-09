package com.secure.applock

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.secure.applock.lock.LockScreenContent
import com.secure.applock.ui.theme.AppLockTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fullscreen overlay shown when the user opens a locked app.
 * Shows custom PIN, pattern, or password (Android-style) or device credential;
 * optional fingerprint when using custom lock.
 * Handles onNewIntent when user switches to a different locked app (singleInstance).
 */
class LockOverlayActivity : FragmentActivity() {

    private var lockedPackage: String = ""
    private lateinit var repository: AppLockRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val pkg = intent.getStringExtra(AppLockAccessibilityService.EXTRA_LOCKED_PACKAGE)
            ?: run { finish(); return }
        lockedPackage = pkg
        val app = application as AppLockApplication
        repository = app.appLockRepository

        fun unlockAndFinish() {
            repository.unlockSessionForPackage(lockedPackage)
            finish()
        }

        setContent {
            val amoledBlack by repository.amoledBlackFlow.collectAsState(initial = false)
            var overlayState by remember { mutableStateOf<OverlayState?>(null) }
            LaunchedEffect(Unit) {
                withContext(Dispatchers.Default) { repository.refreshSnapshot() }
                overlayState = OverlayState(
                    lockType = repository.getLockType(),
                    useFingerprint = repository.getUseFingerprintWithCustom()
                )
            }
            AppLockTheme(dynamicColor = true, useAmoledBlack = amoledBlack) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    when (val state = overlayState) {
                        null -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                        else -> LockOverlayContent(
                            lockType = state.lockType,
                            useFingerprint = state.useFingerprint,
                            repository = repository,
                            unlockAndFinish = { unlockAndFinish() },
                            onCancelAndFinish = { finish() },
                            showBiometricOnly = { onSuccess, onFallback ->
                                Handler(Looper.getMainLooper()).postDelayed(
                                    { showBiometricOnlyPrompt(onSuccess, onFallback) },
                                    150
                                )
                            },
                            showDeviceCredential = { onSuccess, onFallback -> showDeviceCredentialPrompt(onSuccess, onFallback) }
                        )
                    }
                }
            }
        }
    }

    /** Biometric-only (fingerprint/face). Used for custom lock + "use fingerprint" so device PIN is not shown. */
    private fun showBiometricOnlyPrompt(onSuccess: () -> Unit, onFallback: () -> Unit) {
        try {
            val executor = ContextCompat.getMainExecutor(this)
            val biometricManager = BiometricManager.from(this)
            val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            when (canAuthenticate) {
                BiometricManager.BIOMETRIC_SUCCESS -> {
                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.app_name))
                        .setSubtitle(getString(R.string.unlock_to_continue))
                        .setNegativeButtonText(getString(R.string.cancel))
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .build()
                    BiometricPrompt(
                        this,
                        executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                onSuccess()
                            }
                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                onFallback()
                            }
                        }
                    ).authenticate(promptInfo)
                }
                else -> {
                    Toast.makeText(this, getString(R.string.fingerprint_not_available), Toast.LENGTH_SHORT).show()
                    onFallback()
                }
            }
        } catch (e: Exception) {
            onFallback()
        }
    }

    /** Fingerprint or device PIN/pattern. Used when lock type is DEVICE_CREDENTIAL. */
    private fun showDeviceCredentialPrompt(onSuccess: () -> Unit, onFallback: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        when (canAuthenticate) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.app_name))
                    .setSubtitle(getString(R.string.unlock_to_continue))
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build()
                BiometricPrompt(
                    this,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            onSuccess()
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            onFallback()
                        }
                    }
                ).authenticate(promptInfo)
            }
            else -> onFallback()
        }
    }
}

private data class OverlayState(val lockType: LockType, val useFingerprint: Boolean)

@androidx.compose.runtime.Composable
private fun LockOverlayContent(
    lockType: LockType,
    useFingerprint: Boolean,
    repository: AppLockRepository,
    unlockAndFinish: () -> Unit,
    onCancelAndFinish: () -> Unit,
    showBiometricOnly: (onSuccess: () -> Unit, onFallback: () -> Unit) -> Unit,
    showDeviceCredential: (onSuccess: () -> Unit, onFallback: () -> Unit) -> Unit
) {
    var showPinFallback by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    when (lockType) {
        LockType.DEVICE_CREDENTIAL -> {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                showDeviceCredential(unlockAndFinish, onCancelAndFinish)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            )
        }
        else -> {
            if (useFingerprint && !showPinFallback) {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    showBiometricOnly(unlockAndFinish) { showPinFallback = true }
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.unlock_to_continue),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LockScreenContent(
                    lockType = lockType,
                    useFingerprint = useFingerprint,
                    onFingerprintClick = {
                        Handler(Looper.getMainLooper()).postDelayed(
                            { showBiometricOnly(unlockAndFinish) { /* cancelled, stay on PIN screen */ } },
                            150
                        )
                    },
                    onUnlockSuccess = unlockAndFinish,
                    verifyPin = { pin ->
                        val ok = repository.verifyPin(pin)
                        if (ok) repository.setPinLength(pin.length)
                        ok
                    },
                    verifyPattern = { repository.verifyPattern(it) },
                    verifyPassword = { repository.verifyPassword(it) },
                    pinLength = repository.getPinLength(),
                    pinLengthUnknown = !repository.hasStoredPinLength(),
                    subtitle = stringResource(R.string.unlock_to_continue)
                )
            }
        }
    }
}
