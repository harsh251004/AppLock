package com.secure.applock

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.secure.applock.ui.theme.AppLockTheme

@OptIn(ExperimentalMaterial3Api::class)
class SettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AppLockApplication
        val repository = app.appLockRepository
        val viewModel: AppListViewModel = ViewModelProvider(
            this,
            AppListViewModelFactory(repository, this)
        )[AppListViewModel::class.java]

        setContent {
            val context = LocalContext.current
            val amoledBlack by repository.amoledBlackFlow.collectAsState(initial = false)
            AppLockTheme(dynamicColor = true, useAmoledBlack = amoledBlack) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(context.getString(R.string.settings_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = context.getString(R.string.content_description_back)
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                ) { padding ->
                    SettingsScreen(
                        modifier = Modifier.padding(padding),
                        repository = repository,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
