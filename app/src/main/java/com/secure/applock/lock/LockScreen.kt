package com.secure.applock.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.secure.applock.R
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.secure.applock.LockType

private const val PIN_MAX_LENGTH = 6
private const val PATTERN_MIN_POINTS = 4
private const val PATTERN_SIZE = 3
private val PIN_KEY_SIZE = 64.dp
private val PIN_KEY_SPACING = 28.dp

@Composable
fun LockScreenContent(
    lockType: LockType,
    useFingerprint: Boolean,
    onFingerprintClick: () -> Unit,
    onUnlockSuccess: () -> Unit,
    verifyPin: (String) -> Boolean,
    verifyPattern: (List<Int>) -> Boolean,
    verifyPassword: (String) -> Boolean,
    pinLength: Int = 6,
    pinLengthUnknown: Boolean = false,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (useFingerprint) {
                IconButton(
                    onClick = onFingerprintClick,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.content_description_use_fingerprint),
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = stringResource(R.string.or_enter_below),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (lockType) {
                LockType.PIN -> PinKeypad(
                    pinLength = pinLength,
                    pinLengthUnknown = pinLengthUnknown,
                    onVerify = { pin ->
                        if (verifyPin(pin)) {
                            onUnlockSuccess()
                            true
                        } else false
                    },
                    onWrong = { }
                )
                LockType.PATTERN -> PatternLock(
                    onVerify = { pattern ->
                        if (verifyPattern(pattern)) {
                            onUnlockSuccess()
                            true
                        } else false
                    },
                    onWrong = { }
                )
                LockType.PASSWORD -> PasswordField(
                    onVerify = { pwd ->
                        if (verifyPassword(pwd)) {
                            onUnlockSuccess()
                            true
                        } else false
                    },
                    onWrong = { }
                )
                LockType.DEVICE_CREDENTIAL -> { }
            }
            if (useFingerprint) {
                Spacer(modifier = Modifier.size(24.dp))
                Button(
                    onClick = onFingerprintClick,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.content_description_use_fingerprint))
                }
            }
        }
    }
}

@Composable
fun PinKeypad(
    pinLength: Int = 6,
    pinLengthUnknown: Boolean = false,
    onVerify: (String) -> Boolean?,
    onWrong: () -> Unit,
    modifier: Modifier = Modifier,
    setupMode: Boolean = false,
    onPinEntered: (String) -> Unit = {}
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val maxLen = if (setupMode) PIN_MAX_LENGTH else pinLength.coerceIn(4, 6)

    if (!setupMode) {
        LaunchedEffect(pin) {
            val shouldVerify = when {
                pinLengthUnknown -> pin.length == 4 || pin.length == 6
                else -> pin.length == pinLength
            }
            if (shouldVerify) {
                val result = onVerify(pin)
                if (result == false) {
                    wrong = true
                    pin = ""
                    onWrong()
                }
            }
        }
    }

    Column(
        modifier = modifier.padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            repeat(maxLen) { i ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < pin.length)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                        .then(
                            if (wrong) Modifier.border(2.dp, MaterialTheme.colorScheme.error, CircleShape)
                            else Modifier
                        )
                )
            }
        }
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "back")
        )
        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(PIN_KEY_SPACING),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                row.forEach { key ->
                    when {
                        key.isEmpty() -> Spacer(modifier = Modifier.size(PIN_KEY_SIZE))
                        key == "back" -> Box(
                            modifier = Modifier
                                .size(PIN_KEY_SIZE)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    wrong = false
                                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.content_description_delete),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        else -> PinDigitButton(
                            digit = key,
                            onClick = {
                                wrong = false
                                if (pin.length < maxLen) pin += key
                            },
                            modifier = Modifier.size(PIN_KEY_SIZE)
                        )
                    }
                }
            }
        }
        if (setupMode && pin.length in 4..maxLen) {
            Spacer(modifier = Modifier.size(16.dp))
            Button(onClick = { onPinEntered(pin) }) {
                Text(stringResource(R.string.next))
            }
        }
    }
}

@Composable
private fun PinDigitButton(
    digit: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = digit,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PatternLock(
    onVerify: (List<Int>) -> Boolean?,
    onWrong: () -> Unit,
    modifier: Modifier = Modifier,
    setupMode: Boolean = false,
    onPatternEntered: (List<Int>) -> Unit = {}
) {
    val path = remember { mutableStateListOf<Int>() }
    var wrong by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val dotSize = 24.dp
    val gap = 48.dp
    val cellSizePx = with(LocalDensity.current) { (dotSize + gap).toPx() }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        path.clear()
                        wrong = false
                        dragPosition = offset
                        val col = (offset.x / cellSizePx).toInt().coerceIn(0, PATTERN_SIZE - 1)
                        val row = (offset.y / cellSizePx).toInt().coerceIn(0, PATTERN_SIZE - 1)
                        val idx = row * PATTERN_SIZE + col
                        if (idx !in path) path.add(idx)
                    },
                    onDrag = { _, dragAmount ->
                        dragPosition = Offset(dragPosition.x + dragAmount.x, dragPosition.y + dragAmount.y)
                        val col = (dragPosition.x / cellSizePx).toInt().coerceIn(0, PATTERN_SIZE - 1)
                        val row = (dragPosition.y / cellSizePx).toInt().coerceIn(0, PATTERN_SIZE - 1)
                        val idx = row * PATTERN_SIZE + col
                        if (idx !in path) path.add(idx)
                    },
                    onDragEnd = {
                        if (path.size >= PATTERN_MIN_POINTS) {
                            if (setupMode) {
                                onPatternEntered(path.toList())
                            } else {
                                val result = onVerify(path.toList())
                                if (result == false) {
                                    wrong = true
                                    path.clear()
                                    onWrong()
                                }
                            }
                        }
                        path.clear()
                    }
                )
            }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(PATTERN_SIZE) { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    modifier = Modifier.padding(0.dp)
                ) {
                    repeat(PATTERN_SIZE) { col ->
                        val idx = row * PATTERN_SIZE + col
                        val selected = idx in path
                        Box(
                            modifier = Modifier
                                .size(dotSize)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        wrong && selected -> MaterialTheme.colorScheme.error
                                        selected -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PasswordField(
    onVerify: (String) -> Boolean?,
    onWrong: () -> Unit,
    modifier: Modifier = Modifier
) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = password,
            onValueChange = {
                wrong = false
                password = it
            },
            label = { Text(stringResource(R.string.password)) },
            placeholder = { Text(stringResource(R.string.enter_password)) },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (visible) stringResource(R.string.content_description_hide_password) else stringResource(R.string.content_description_show_password)
                    )
                }
            },
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .then(
                    if (wrong) Modifier.border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                    else Modifier
                ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                errorBorderColor = MaterialTheme.colorScheme.error,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
        Button(
            onClick = {
                if (password.isNotEmpty()) {
                    val result = onVerify(password)
                    if (result == false) {
                        wrong = true
                        password = ""
                        onWrong()
                    }
                }
            }
        ) {
            Text(stringResource(R.string.unlock))
        }
    }
}
