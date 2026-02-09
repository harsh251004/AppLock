package com.secure.applock

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secure.applock.ui.theme.AppLockTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import com.secure.applock.lock.LockScreenContent
import com.secure.applock.lock.PinKeypad
import com.secure.applock.lock.PatternLock

@Composable
private fun switchColors() = SwitchDefaults.colors(
    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary
)

class MainActivity : FragmentActivity() {

    var isUnlocked by mutableStateOf(false)
        private set

    fun onUnlockSuccess() {
        isUnlocked = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AppLockApplication
        val repository = app.appLockRepository
        repository.refreshSnapshotBlocking()
        setContent {
            val amoledBlack by repository.amoledBlackFlow.collectAsState(initial = false)
            val lockState by repository.lockStateFlow.collectAsState(
                initial = LockState(
                    lockedPackages = repository.getLockedPackages(),
                    lockMode = repository.getLockMode(),
                    sessionUnlocked = repository.getSessionUnlockedPackages(),
                    lockType = repository.getLockType(),
                    useFingerprintWithCustom = repository.getUseFingerprintWithCustom()
                )
            )
            val lockType = lockState?.lockType ?: LockType.DEVICE_CREDENTIAL
            val useFingerprint = lockState?.useFingerprintWithCustom ?: false
            AppLockTheme(dynamicColor = true, useAmoledBlack = amoledBlack) {
                if (!isUnlocked) {
                    if (lockType == LockType.DEVICE_CREDENTIAL) {
                        AppUnlockGate(activity = this@MainActivity)
                    } else {
                        CustomAppUnlockGate(
                            activity = this@MainActivity,
                            repository = repository,
                            lockType = lockType,
                            useFingerprint = useFingerprint
                        )
                    }
                } else {
                    AppContent(activity = this@MainActivity, repository = repository)
                }
            }
        }
    }

    /** System unlock: fingerprint or device PIN/pattern. Used when lock type is DEVICE_CREDENTIAL. */
    fun showUnlockPrompt(onSuccess: () -> Unit, onCancel: (() -> Unit)? = null) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        when (canAuthenticate) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.app_name))
                    .setSubtitle(getString(R.string.unlock_app_lock))
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build()
                val biometricPrompt = BiometricPrompt(
                    this,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            onSuccess()
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            onCancel?.invoke()
                        }
                    }
                )
                biometricPrompt.authenticate(promptInfo)
            }
            else -> onCancel?.invoke() ?: onSuccess()
        }
    }

    /**
     * Biometric-only unlock (fingerprint/face, no device PIN). Used when custom lock has
     * "use fingerprint" enabled: show fingerprint first; if not available or user cancels, fall back to custom PIN.
     */
    fun showBiometricOnlyUnlockPrompt(onSuccess: () -> Unit, onCancel: () -> Unit) {
        try {
            val executor = ContextCompat.getMainExecutor(this)
            val biometricManager = BiometricManager.from(this)
            val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            when (canAuthenticate) {
                BiometricManager.BIOMETRIC_SUCCESS -> {
                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.app_name))
                        .setSubtitle(getString(R.string.unlock_app_lock))
                        .setNegativeButtonText(getString(R.string.cancel))
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .build()
                    val biometricPrompt = BiometricPrompt(
                        this,
                        executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                onSuccess()
                            }
                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                onCancel()
                            }
                        }
                    )
                    biometricPrompt.authenticate(promptInfo)
                }
                else -> {
                    Toast.makeText(this, getString(R.string.fingerprint_not_available), Toast.LENGTH_SHORT).show()
                    onCancel()
                }
            }
        } catch (e: Exception) {
            onCancel()
        }
    }
}

@Composable
private fun AppUnlockGate(activity: MainActivity) {
    LaunchedEffect(Unit) {
        delay(150)
        activity.showUnlockPrompt(onSuccess = { activity.onUnlockSuccess() })
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.unlock_app_lock),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { activity.showUnlockPrompt(onSuccess = { activity.onUnlockSuccess() }) }
            ) {
                Text(stringResource(R.string.unlock))
            }
        }
    }
}

@Composable
private fun CustomAppUnlockGate(
    activity: MainActivity,
    repository: AppLockRepository,
    lockType: LockType,
    useFingerprint: Boolean
) {
    var showPinFallback by remember { mutableStateOf(false) }
    if (useFingerprint && !showPinFallback) {
        LaunchedEffect(Unit) {
            delay(150)
            activity.showBiometricOnlyUnlockPrompt(
                onSuccess = { activity.onUnlockSuccess() },
                onCancel = { try { showPinFallback = true } catch (_: Exception) { } }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.unlock_app_lock),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        LockScreenContent(
            lockType = lockType,
            useFingerprint = useFingerprint,
            onFingerprintClick = {
                Handler(Looper.getMainLooper()).postDelayed({
                    activity.showBiometricOnlyUnlockPrompt(
                        onSuccess = { activity.onUnlockSuccess() },
                        onCancel = { }
                    )
                }, 150)
            },
            onUnlockSuccess = { activity.onUnlockSuccess() },
            verifyPin = { pin ->
                val ok = repository.verifyPin(pin)
                if (ok) repository.setPinLength(pin.length)
                ok
            },
            verifyPattern = { repository.verifyPattern(it) },
            verifyPassword = { repository.verifyPassword(it) },
            pinLength = repository.getPinLength(),
            pinLengthUnknown = !repository.hasStoredPinLength(),
            subtitle = stringResource(R.string.unlock_app_lock)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(
    activity: MainActivity,
    repository: AppLockRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: AppListViewModel = viewModel(
        factory = AppListViewModelFactory(repository, context)
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(
                        onClick = {
                            try {
                                activity.startActivity(Intent(activity, SettingsActivity::class.java))
                            } catch (e: Exception) {
                                Toast.makeText(activity, activity.getString(R.string.cannot_open_settings, e.message.orEmpty()), Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.content_description_settings)
                        )
                    }
                }
            )
        }
    ) { padding ->
        AppListScreen(
            modifier = Modifier.padding(padding),
            viewModel = viewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppListScreen(
    modifier: Modifier,
    viewModel: AppListViewModel
) {
    val context = LocalContext.current
    val apps by viewModel.filteredApps.collectAsState()
    val lockState by viewModel.lockStateFlow.collectAsState(
        initial = LockState(
            lockedPackages = emptySet(),
            lockMode = LockMode.LOCK_ON_EXIT,
            sessionUnlocked = emptySet(),
            lockType = LockType.DEVICE_CREDENTIAL,
            useFingerprintWithCustom = false
        )
    )
    val iconCache by viewModel.iconCache.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val lockedPackages = lockState?.lockedPackages ?: emptySet()

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_apps)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    item(key = "apps_header") {
                        Text(
                            text = stringResource(R.string.locked_apps),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                    items(
                        items = apps,
                        key = { it.packageName }
                    ) { app ->
                        viewModel.loadIconIfNeeded(app.packageName)
                        val icon = iconCache[app.packageName]
                        val isLocked = lockedPackages.contains(app.packageName)
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (icon != null) {
                                        Image(
                                            painter = BitmapPainter(icon),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(MaterialTheme.shapes.small),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                Switch(
                                    checked = isLocked,
                                    onCheckedChange = { viewModel.setLocked(app.packageName, it) },
                                    colors = switchColors()
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                headlineColor = MaterialTheme.colorScheme.onSurface,
                                supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
}

@Composable
private fun SetPinDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var firstPin by remember { mutableStateOf("") }
    var wrongCount by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == 0) stringResource(R.string.set_pin) else stringResource(R.string.confirm_pin)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (step == 0) {
                    PinKeypad(
                        setupMode = true,
                        onVerify = { null },
                        onWrong = { },
                        onPinEntered = { pin ->
                            firstPin = pin
                            step = 1
                        }
                    )
                } else {
                    if (wrongCount > 0) {
                        Text(
                            stringResource(R.string.pins_dont_match),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    key("confirm_$wrongCount") {
                        PinKeypad(
                            setupMode = true,
                            onVerify = { null },
                            onWrong = { },
                            onPinEntered = { pin ->
                                if (pin == firstPin) {
                                    onConfirm(pin)
                                } else {
                                    wrongCount++
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun SetPatternDialog(
    onDismiss: () -> Unit,
    onConfirm: (List<Int>) -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var firstPattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var wrongCount by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == 0) stringResource(R.string.set_pattern) else stringResource(R.string.confirm_pattern)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (step == 0) {
                    PatternLock(
                        onVerify = { null },
                        onWrong = { },
                        setupMode = true,
                        onPatternEntered = { pattern ->
                            firstPattern = pattern
                            step = 1
                        }
                    )
                } else {
                    if (wrongCount > 0) {
                        Text(
                            stringResource(R.string.patterns_dont_match),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    key("confirm_$wrongCount") {
                        PatternLock(
                            onVerify = { null },
                            onWrong = { },
                            setupMode = true,
                            onPatternEntered = { pattern ->
                                if (pattern == firstPattern) {
                                    onConfirm(pattern)
                                } else {
                                    wrongCount++
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun SetPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (wrong) {
                    Text(
                        stringResource(R.string.passwords_dont_match),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; wrong = false },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; wrong = false },
                    label = { Text(stringResource(R.string.confirm_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (password == confirm && password.isNotEmpty()) {
                            onConfirm(password)
                        } else {
                            wrong = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.set_password))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    modifier: Modifier,
    repository: AppLockRepository,
    viewModel: AppListViewModel
) {
    val context = LocalContext.current
    val lockState by viewModel.lockStateFlow.collectAsState(
        initial = LockState(
            lockedPackages = emptySet(),
            lockMode = LockMode.LOCK_ON_EXIT,
            sessionUnlocked = emptySet(),
            lockType = LockType.DEVICE_CREDENTIAL,
            useFingerprintWithCustom = false
        )
    )
    val lockMode = lockState?.lockMode ?: LockMode.LOCK_ON_EXIT
    val lockType = lockState?.lockType ?: LockType.DEVICE_CREDENTIAL
    val useFingerprintWithCustom = lockState?.useFingerprintWithCustom ?: false
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionRefresh by remember { mutableStateOf(0) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showSetPatternDialog by remember { mutableStateOf(false) }
    var showSetPasswordDialog by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionItems: List<PermissionItem> = remember(permissionRefresh, context) {
        PermissionStatus.all(context)
    }
    val amoledBlack by repository.amoledBlackFlow.collectAsState(initial = false)

    if (showSetPinDialog) {
        SetPinDialog(
            onDismiss = { showSetPinDialog = false },
            onConfirm = { pin ->
                repository.setPin(pin)
                scope.launch { repository.setLockType(LockType.PIN) }
                showSetPinDialog = false
            }
        )
    }
    if (showSetPatternDialog) {
        SetPatternDialog(
            onDismiss = { showSetPatternDialog = false },
            onConfirm = { pattern ->
                repository.setPattern(pattern)
                scope.launch { repository.setLockType(LockType.PATTERN) }
                showSetPatternDialog = false
            }
        )
    }
    if (showSetPasswordDialog) {
        SetPasswordDialog(
            onDismiss = { showSetPasswordDialog = false },
            onConfirm = { password ->
                repository.setPassword(password)
                scope.launch { repository.setLockType(LockType.PASSWORD) }
                showSetPasswordDialog = false
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item(key = "amoled_black") {
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.amoled_black_mode),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.amoled_black_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Switch(
                        checked = amoledBlack,
                        onCheckedChange = { enabled ->
                            scope.launch { repository.setAmoledBlack(enabled) }
                        },
                        colors = switchColors()
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    headlineColor = MaterialTheme.colorScheme.onSurface,
                    supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        item(key = "lock_type_header") {
            Text(
                text = stringResource(R.string.lock_type),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        LockType.entries.forEach { type ->
            item(key = "lock_type_$type") {
                ListItem(
                    headlineContent = {
                        Text(
                            text = when (type) {
                                LockType.DEVICE_CREDENTIAL -> stringResource(R.string.lock_type_device)
                                LockType.PIN -> stringResource(R.string.lock_type_pin)
                                LockType.PATTERN -> stringResource(R.string.lock_type_pattern)
                                LockType.PASSWORD -> stringResource(R.string.lock_type_password)
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    supportingContent = {
                        if (type == lockType) {
                            Text(
                                text = if (type.isCustom) stringResource(R.string.tap_to_change)
                                else stringResource(R.string.selected),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier.clickable {
                        when (type) {
                            LockType.DEVICE_CREDENTIAL -> scope.launch { repository.setLockType(LockType.DEVICE_CREDENTIAL) }
                            LockType.PIN -> {
                                if (!repository.secureLockStorage.hasPin()) showSetPinDialog = true
                                else if (type == lockType) showSetPinDialog = true
                                else scope.launch { repository.setLockType(LockType.PIN) }
                            }
                            LockType.PATTERN -> {
                                if (!repository.secureLockStorage.hasPattern()) showSetPatternDialog = true
                                else if (type == lockType) showSetPatternDialog = true
                                else scope.launch { repository.setLockType(LockType.PATTERN) }
                            }
                            LockType.PASSWORD -> {
                                if (!repository.secureLockStorage.hasPassword()) showSetPasswordDialog = true
                                else if (type == lockType) showSetPasswordDialog = true
                                else scope.launch { repository.setLockType(LockType.PASSWORD) }
                            }
                        }
                    },
                    trailingContent = {
                        if (type == lockType) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        headlineColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
        if (lockType.isCustom) {
            item(key = "use_fingerprint") {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.use_fingerprint_with_custom),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(R.string.use_fingerprint_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = useFingerprintWithCustom,
                            onCheckedChange = { enabled ->
                                scope.launch { repository.setUseFingerprintWithCustom(enabled) }
                            },
                            colors = switchColors()
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        headlineColor = MaterialTheme.colorScheme.onSurface,
                        supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
        item(key = "lock_timing_header") {
            Text(
                text = stringResource(R.string.lock_timing),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        LockMode.entries.forEach { mode ->
            item(key = "lock_mode_$mode") {
                ListItem(
                    headlineContent = {
                        Text(
                            text = lockModeLabel(mode),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    supportingContent = {
                        if (mode == lockMode) {
                            Text(
                                text = stringResource(R.string.selected),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier.clickable {
                        scope.launch { viewModel.setLockMode(mode) }
                    },
                    trailingContent = {
                        if (mode == lockMode) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        headlineColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
        item(key = "lock_timing_divider") {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
        item(key = "uninstall_prevention") {
            val isDeviceAdmin = remember(permissionRefresh, context) {
                PermissionStatus.isDeviceAdminActive(context)
            }
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.uninstall_prevention),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.uninstall_prevention_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Switch(
                        checked = isDeviceAdmin,
                        onCheckedChange = { checked ->
                            if (checked) {
                                openAddDeviceAdmin(context)
                            } else {
                                openDeviceAdminSettings(context)
                            }
                        },
                        colors = switchColors()
                    )
                },
                modifier = Modifier.clickable {
                    if (isDeviceAdmin) openDeviceAdminSettings(context)
                    else openAddDeviceAdmin(context)
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    headlineColor = MaterialTheme.colorScheme.onSurface,
                    supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        item(key = "permissions_divider") {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
        item(key = "permissions_header") {
            Text(
                text = stringResource(R.string.permissions),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        items(
            items = permissionItems,
            key = { it.title }
        ) { permission ->
            ListItem(
                headlineContent = {
                    Text(
                        text = permission.title,
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                supportingContent = {
                    Text(
                        text = if (permission.isEnabled) stringResource(R.string.granted) else stringResource(R.string.not_granted),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (permission.isEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clickable { permission.openSettings() },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    headlineColor = MaterialTheme.colorScheme.onSurface,
                    supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun LockTimingDialog(
    currentMode: LockMode,
    onDismiss: () -> Unit,
    onSelect: (LockMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lock_timing)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.lock_timing_subtitle))
                LockMode.entries.forEach { mode ->
                    TextButton(
                        onClick = { onSelect(mode) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = lockModeLabel(mode),
                            style = if (mode == currentMode)
                                MaterialTheme.typography.titleSmall
                            else
                                MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        }
    )
}

@Composable
private fun lockModeLabel(mode: LockMode): String = when (mode) {
    LockMode.LOCK_ON_EXIT -> stringResource(R.string.lock_timing_immediately)
    LockMode.LOCK_ON_SCREEN_OFF -> stringResource(R.string.lock_timing_screen_off)
    LockMode.LOCK_ON_RECENTS_CLEAR -> stringResource(R.string.lock_timing_recents_clear)
}
