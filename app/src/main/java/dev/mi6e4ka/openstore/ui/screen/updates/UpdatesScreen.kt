package dev.mi6e4ka.openstore.ui.screen.updates

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.mi6e4ka.openstore.R
import dev.mi6e4ka.openstore.internal.installer.ApkInstaller
import dev.mi6e4ka.openstore.ui.components.AppIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(navController: NavController) {
    val context = LocalContext.current
    val apkInstaller = remember { ApkInstaller(context) }
    val viewModel: UpdatesViewModel = viewModel(
        factory = UpdatesViewModelFactory(apkInstaller)
    )
    val state by viewModel.state.collectAsState()
    var showHashDetails by remember { mutableStateOf(false) }

    if (state.invalidDownloadedApkFiles != null && state.invalidDownloadedAppPackage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearInvalidDownloadState() },
            title = { Text(stringResource(R.string.invalid_download_hash_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.invalid_download_hash_message))
                    Spacer(modifier = Modifier.size(12.dp))
                    TextButton(onClick = { showHashDetails = !showHashDetails }) {
                        Text(stringResource(R.string.show_hashes))
                    }
                    if (showHashDetails) {
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.expected_hash, state.invalidExpectedHash ?: ""))
                        Text(stringResource(R.string.actual_hash, state.invalidActualHash ?: ""))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.retryDownloadAfterInvalid(context) }) {
                    Text(stringResource(R.string.download_again))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.installAsIsAfterInvalid(context) }) {
                    Text(stringResource(R.string.install_as_is))
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.loadAndCheckUpdates(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.updates_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24px),
                            contentDescription = stringResource(R.string.back_button_desc)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.checkForUpdates(context) },
                        enabled = !state.isCheckingUpdates && !state.isLoading
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh_24px),
                            contentDescription = stringResource(R.string.check_updates)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.isCheckingUpdates) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.checking_updates))
                }
            } else if (state.appsWithUpdates.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    UpdatesBanner(navController, state.installedApps.size)
                    Spacer(modifier = Modifier.weight(1f))

                    Icon(
                        painter = painterResource(R.drawable.ic_check_circle_24px),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.all_apps_updated),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        UpdatesBanner(navController, state.installedApps.size)
                    }

                    item {
                        Text(
                            stringResource(R.string.available_updates, state.appsWithUpdates.size),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(state.appsWithUpdates, key = { it.packageName }) { app ->
                        UpdateAppCard(
                            app = app,
                            isDownloading = state.downloadingPackages.contains(app.packageName),
                            downloadProgress = state.downloadProgress[app.packageName] ?: 0,
                            onUpdateClick = { viewModel.downloadAndUpdateApp(context, app) },
                            onCancelClick = { viewModel.cancelDownload(app.packageName) },
                            onCardClick = { navController.navigate("app/${app.packageName}?platform=mobile") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UpdatesBanner(navController: NavController, checkAppsCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(0.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = {
                navController.navigate("settings")
            }),
        //colors = CardDefaults.cardColors(containerColor = Color(109, 255, 30, 40))
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Icon(
                painterResource(R.drawable.ic_settings_24px),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            Column {
                Text(stringResource(R.string.updates_app_count, checkAppsCount))
                Text(stringResource(R.string.updates_change_settings))
            }
        }
    }
}

@Composable
fun UpdateAppCard(
    app: AppWithUpdates,
    isDownloading: Boolean,
    downloadProgress: Int,
    onUpdateClick: () -> Unit,
    onCancelClick: () -> Unit,
    onCardClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onCardClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (app.iconUrl != null) {
                AppIcon(
                    app.iconUrl,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_android_24px),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = app.appName,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${app.installedVersionCode} → ${app.latestVersionCode}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
//                Text(
//                    text = "from ${app.installSource}",
//                    fontSize = 12.sp,
//                    color = MaterialTheme.colorScheme.secondary,
//                    maxLines = 1,
//                    overflow = TextOverflow.Ellipsis
//                )
                if (isDownloading) {
                    Spacer(modifier = Modifier.size(4.dp))
                    if (downloadProgress > 100) {
                        Text(stringResource(R.string.installing), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            if (isDownloading) {
                FilledTonalIconButton(onClick = onCancelClick, enabled = downloadProgress <= 100) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close_24px),
                        contentDescription = null
                    )
                }
            } else {
                FilledTonalButton(onClick = onUpdateClick) {
                    Text(stringResource(R.string.update))
                }
            }
        }
    }
}
