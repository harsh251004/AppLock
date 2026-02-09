package com.secure.applock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppListItem(
    val packageName: String,
    val label: String
)

/**
 * Loads launchable apps asynchronously and caches app icons on demand
 * so the UI doesn't stutter with 100+ icons.
 */
class AppListViewModel(
    private val context: Context,
    private val repository: AppLockRepository
) : ViewModel() {

    private val _apps = MutableStateFlow<List<AppListItem>>(emptyList())
    val apps: StateFlow<List<AppListItem>> = _apps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredApps: StateFlow<List<AppListItem>> = combine(
        _apps,
        _searchQuery,
        repository.lockStateFlow
    ) { list, query, lockState ->
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) list
        else list.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
        val locked = lockState?.lockedPackages ?: emptySet()
        filtered.sortedWith(
            compareBy<AppListItem> { !locked.contains(it.packageName) }
                .thenBy { it.label.lowercase() }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val lockStateFlow: StateFlow<LockState?> = repository.lockStateFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _iconCache = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val iconCache: StateFlow<Map<String, ImageBitmap>> = _iconCache.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadApps()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            val list = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val ourPackage = context.packageName
                val main = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                @Suppress("QueryPermissionsNeeded")
                pm.queryIntentActivities(main, PackageManager.MATCH_ALL)
                    .map { ri ->
                        AppListItem(
                            packageName = ri.activityInfo.packageName,
                            label = ri.loadLabel(pm).toString()
                        )
                    }
                    .distinctBy { it.packageName }
                    .filter { it.packageName != ourPackage }
                    .sortedBy { it.label.lowercase() }
            }
            _apps.value = list
            _isLoading.value = false
        }
    }

    /** Load icon on demand; cache is updated asynchronously so the list row recomposes. */
    fun loadIconIfNeeded(packageName: String) {
        if (_iconCache.value.containsKey(packageName)) return
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val drawable: Drawable = context.packageManager.getApplicationIcon(packageName)
                    drawable.toBitmap().asImageBitmap()
                } catch (_: PackageManager.NameNotFoundException) { null }
            }
            if (bitmap != null) {
                _iconCache.update { it + (packageName to bitmap) }
            }
        }
    }

    fun setLocked(packageName: String, locked: Boolean) {
        if (locked) {
            repository.addLockedPackageImmediate(packageName)
            viewModelScope.launch { repository.addLockedPackage(packageName) }
        } else {
            repository.removeLockedPackageImmediate(packageName)
            viewModelScope.launch { repository.removeLockedPackage(packageName) }
        }
    }

    suspend fun setLockMode(mode: LockMode) {
        repository.setLockMode(mode)
    }
}
