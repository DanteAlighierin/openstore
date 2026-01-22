package dev.mi6e4ka.openstore.ui.screen.updates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import dev.mi6e4ka.openstore.data.model.InstalledApp
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

    LaunchedEffect(Unit) {
        viewModel.initPreferences(context)
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
                            contentDescription = "Back"
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
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        "${state.checkedCount} / ${state.totalToCheck}",
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    LinearProgressIndicator(
                        progress = { 
                            if (state.totalToCheck > 0) {
                                state.checkedCount.toFloat() / state.totalToCheck
                            } else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                    )
                }
            } else if (state.appsWithUpdates.isEmpty() && state.checkedCount > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.exclude_google_play),
                            fontSize = 14.sp
                        )
                        Switch(
                            checked = state.excludeGooglePlay,
                            onCheckedChange = { viewModel.toggleExcludeGooglePlay(context) }
                        )
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Icon(
                        painter = painterResource(R.drawable.ic_check_circle_24px),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
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
            } else if (state.appsWithUpdates.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.exclude_google_play),
                                fontSize = 14.sp
                            )
                            Switch(
                                checked = state.excludeGooglePlay,
                                onCheckedChange = { viewModel.toggleExcludeGooglePlay(context) }
                            )
                        }
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
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_update_24px),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.check_updates_hint),
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Button(onClick = { viewModel.checkForUpdates(context) }) {
                        Text(stringResource(R.string.check_updates))
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateAppCard(
    app: InstalledApp,
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
                    text = "${app.installedVersionName} → ${app.latestVersionName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isDownloading) {
                    Spacer(modifier = Modifier.size(4.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            if (isDownloading) {
                FilledTonalIconButton(onClick = onCancelClick) {
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
