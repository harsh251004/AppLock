package com.secure.applock

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AppListViewModelFactory(
    private val repository: AppLockRepository,
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass != AppListViewModel::class.java) {
            throw IllegalArgumentException("Unknown ViewModel class")
        }
        return AppListViewModel(context, repository) as T
    }
}
