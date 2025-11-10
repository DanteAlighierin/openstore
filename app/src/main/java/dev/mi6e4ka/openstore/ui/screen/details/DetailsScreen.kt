package dev.mi6e4ka.openstore.ui.screen.details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.SupervisedUserCircle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import dev.mi6e4ka.openstore.R
import dev.mi6e4ka.openstore.di.AppModule
import dev.mi6e4ka.openstore.internal.installer.ApkInstaller
import dev.mi6e4ka.openstore.ui.components.AppIcon
import dev.mi6e4ka.openstore.ui.components.GalleryWithPager
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class,
    FormatStringsInDatetimeFormats::class, ExperimentalTime::class
)
@Composable
fun DetailsScreen(
    itemId: String,
    appPlatform: String,
    navController: NavController = rememberNavController()
) {
    val context = LocalContext.current
    val apkInstaller = ApkInstaller(context)

    //val viewModel = remember (itemId) { DetailsViewModel(itemId, apkInstaller) }
    val viewModel : DetailsViewModel = viewModel(
        factory = DetailsViewModelFactory(appPlatform, apkInstaller)
    )
    val state by viewModel.state.collectAsState()

    val scope = rememberCoroutineScope()

    var showAppDescriptionSheet by remember { mutableStateOf(false) }
//    val appDescriptionSheetState = rememberModalBottomSheetState(
//        skipPartiallyExpanded = true
//    )

    LaunchedEffect(itemId) {
        viewModel.loadDetails(context, itemId)
    }

    LaunchedEffect(Unit) {
        AppModule.uninstallerEventFlow.events.collect {
            viewModel.updateInstallStatus(false)
        }
    }

    Scaffold(
        topBar = { TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                IconButton(onClick = {
                    scope.launch {
                        viewModel.downloadFileToFolder(context)
                    }
                }, enabled = !(state.splitOnly ?: true)) {
                    Icon(painterResource(R.drawable.apk_install_24px), null)
                }
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://www.rustore.ru/catalog/app/${state.item?.packageName ?: ""}".toUri()).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        setSelector(Intent(Intent.ACTION_VIEW, "http://".toUri()))
                    }
                    context.startActivity(intent)
                }, enabled = state.item != null) {
                    Icon(Icons.Default.OpenInBrowser, null)
                }
            }
        ) }
    ) {
        innerPadding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {
            if (showAppDescriptionSheet) {
                ModalBottomSheet(
                    onDismissRequest = {showAppDescriptionSheet = false},
                    Modifier.systemBarsPadding()
                ) {
                    Column(
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(stringResource(R.string.about_app), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(state.item?.fullDescription ?: "")
                    }
                }
            }
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .fillMaxWidth())
            } else if (state.error != null) {
                Text(stringResource(R.string.error_message, state.error!!), modifier = Modifier.padding(16.dp))
            } else {
                val item = state.item
                if (item != null) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.aggregatorInfo == null)
                                    Color(0, 255, 0, 35)
                                    else Color(255, 187, 0, 35)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        "https://codeberg.org/mi6e4ka/openstore/src/branch/main/SAFETY.md".toUri()
                                    )
                                    context.startActivity(intent)
                                })
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(15.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                                if (item.aggregatorInfo != null) {
                                    Text(stringResource(R.string.provided_by, item.aggregatorInfo.source))
                                } else {
                                    Text(stringResource(R.string.provided_by, "RuStore"))
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(92.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val animatedSize by animateDpAsState(
                                    targetValue = if (state.isDownloading) 80.dp else 92.dp,
                                    animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                                    label = "sizeAnimation"
                                )
                                val animatedCorner by animateDpAsState(
                                    targetValue = if (state.isDownloading) 80.dp / 2 else 12.dp,
                                    animationSpec = spring(dampingRatio = 0.6f),
                                    label = "cornerAnimation"
                                )
                                if (state.installStatus is InstallStatus.Pending || state.downloadTotalBytes.toInt() == 0) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .size(128.dp)
                                            .alpha(if (state.isDownloading) 1f else 0f),
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        progress = { state.downloadProgress / 100f },
                                        modifier = Modifier
                                            .size(128.dp)
                                            .alpha(if (state.isDownloading) 1f else 0f),
                                        strokeWidth = 3.dp
                                    )
                                }
                                AppIcon(
                                    item.iconUrl, modifier = Modifier
                                        .size(animatedSize)
                                        .clip(RoundedCornerShape(animatedCorner))
                                )
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(item.appName, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(item.aggregatorInfo?.companyName ?: item.companyName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (!state.isSupported) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                                Text(stringResource(R.string.device_unsupported), color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Column {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!state.isInstalled || state.isDownloading) {
                                    //FilledTonalButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Copy apk link") }
                                    Button(onClick = {
                                        viewModel.downloadAndInstallApp(context)
                                    }, modifier = Modifier.weight(1f), enabled = !state.isDownloading && state.isSupported) {
                                        if (state.installStatus is InstallStatus.Pending) {
                                            Text(stringResource(R.string.installing))
                                        } else if (state.isDownloading) {
                                            if (state.downloadTotalBytes.toInt() != 0) {
                                                Text(stringResource(R.string.downloading, " ${state.downloadCurrentBytes / 1024 / 1024}/${state.downloadTotalBytes / 1024 / 1024}МБ"))
                                            } else {
                                                Text(stringResource(R.string.downloading, ""))
                                            }
                                        } else {
                                            Text(stringResource(R.string.install_button))
                                        }
                                    }
                                } else {
                                    FilledTonalButton(onClick = {
                                        viewModel.deleteApplication(context, itemId)
                                    }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.uninstall)) }
                                    if (state.isNeedUpgrade) {
                                        Button(onClick = {
                                            viewModel.downloadAndInstallApp(context)
                                        }, modifier = Modifier.weight(1f), enabled = state.isSupported) { Text(stringResource(R.string.update)) }
                                    } else {
                                        Button(onClick = {
                                            viewModel.runApplication(context, itemId)
                                        }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.open)) }
                                    }
                                }
                                if (state.isDownloading) {
                                    FilledTonalIconButton(onClick = {
                                        viewModel.cancelDownload()
                                    }, enabled = state.installStatus != InstallStatus.Pending) {
                                        Icon(imageVector = Icons.Default.Close, null)
                                    }
                                }
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(bottom = 4.dp, top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AssistChip(
                                    label = {
                                        Text("%.1f MB".format(
                                            (state.appFiles?.sumOf { it.size } ?: 0L) / 1024.0 / 1024.0)
                                        )
                                    },
                                    leadingIcon = {Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)},
                                    onClick = {},
                                )
                                AssistChip(
                                    onClick = {},
                                    label = {Text(item.versionName)},
                                    leadingIcon = {Icon(imageVector = Icons.Default.ArrowCircleUp, contentDescription = null)}
                                )
                                AssistChip(
                                    onClick = {},
                                    label = {FormattedDateText(item.appVerUpdatedAt)},
                                    leadingIcon = {Icon(imageVector = Icons.Rounded.Update, contentDescription = null)}
                                )
                                var detailedApiVersion by remember { mutableStateOf(false) }
                                AssistChip(
                                    onClick = {
                                        detailedApiVersion = !detailedApiVersion
                                        },
                                    label = {
                                        if (!detailedApiVersion) {
                                            Text("Android ${getAndroidVersion(item.minSdkVersion)} +")
                                        } else {
                                            Text("API ${item.minSdkVersion} +")
                                        }
                                            },
                                    leadingIcon = {Icon(imageVector = Icons.Rounded.Android, contentDescription = null)}
                                )
                                AssistChip(
                                    onClick = {},
                                    label = {Text(if ((state.appFiles?.size ?: 1) > 1) "${state.appFiles?.size} Split APKs" else  "APK")},
                                    leadingIcon = {Icon(imageVector = Icons.Rounded.Adb, contentDescription = null)}
                                )
                                AssistChip(
                                    onClick = {},
                                    label = {Text(item.roundedDownloadsText)},
                                    leadingIcon = {Icon(imageVector = Icons.Rounded.Download, contentDescription = null)}
                                )
                                AssistChip(
                                    onClick = {},
                                    label = {Text(item.ageLegal)},
                                    leadingIcon = {Icon(imageVector = Icons.Default.SupervisedUserCircle, contentDescription = null)}
                                )
                            }
                            GalleryWithPager(item.fileUrls)
                        }
                        var isWhatsNewExpanded by remember { mutableStateOf(false) }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = {isWhatsNewExpanded = !isWhatsNewExpanded},
                                    onLongClick = {
                                        val clipboardManager = getSystemService(context, ClipboardManager::class.java) as ClipboardManager
                                        clipboardManager.setPrimaryClip(ClipData.newPlainText("about app", item.whatsNew))
                                    }
                                ),
                        ) {
                            Column(
                                Modifier
                                    .padding(16.dp)
                                    .animateContentSize(
                                        animationSpec = tween(
                                            durationMillis = 300,
                                            easing = LinearOutSlowInEasing
                                        )
                                    )
                            ) {
                                Row {
                                    Text(stringResource(R.string.whats_new), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }

                                Text(item.whatsNew, maxLines = if (isWhatsNewExpanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        val interactionSource = remember { MutableInteractionSource() }
                        Column(
                            Modifier
                                .padding(bottom = 8.dp)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) {
                                    showAppDescriptionSheet = true
                                }
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.about_app), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                IconButton(onClick = {showAppDescriptionSheet = true}) {
                                    Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                                }
                            }
                            Text(text = item.shortDescription)
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = {
                                    navController.navigate("app/${item.packageName}/reviews") {
                                        restoreState = true
                                    }
                                })
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.rating_and_reviews), fontWeight = FontWeight.Bold, fontSize = 18.sp)
//                                FilledTonalIconButton(onClick = {}) {
//                                    Icon(imageVector = Icons.Rounded.ArrowForward, contentDescription = null)
//                                }
                            }
                            Row(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("5", fontWeight = FontWeight.Bold)
                                        LinearProgressIndicator(progress = {
                                            ((state.rating?.ratings?.amountFive ?: 0) / 100f)
                                        }, modifier = Modifier.fillMaxWidth())
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("4", fontWeight = FontWeight.Bold)
                                        LinearProgressIndicator(progress = {
                                            ((state.rating?.ratings?.amountFour ?: 0) / 100f)
                                        }, modifier = Modifier.fillMaxWidth())
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("3", fontWeight = FontWeight.Bold)
                                        LinearProgressIndicator(progress = {
                                            ((state.rating?.ratings?.amountThree ?: 0) / 100f)
                                        }, modifier = Modifier.fillMaxWidth())
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("2", fontWeight = FontWeight.Bold)
                                        LinearProgressIndicator(progress = {
                                            ((state.rating?.ratings?.amountTwo ?: 0) / 100f)
                                        }, modifier = Modifier.fillMaxWidth())
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("1", fontWeight = FontWeight.Bold)
                                        LinearProgressIndicator(progress = {
                                            ((state.rating?.ratings?.amountOne ?: 0) / 100f)
                                        }, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                                Column(
                                    modifier = Modifier.padding(start=24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("${state.rating?.averageUserRating ?: "?"}", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                    Text("${state.rating?.totalRatings ?: "?"}", fontSize = 16.sp)
                                }
                            }
                        }
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(item.packageName, color = Color.Gray, fontSize = 14.sp)
                        }
                        //Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTime::class, FormatStringsInDatetimeFormats::class)
@Composable
fun FormattedDateText(dateString: String) {
    val formattedDate = remember(dateString) {
        try {
            Instant.parse(dateString)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .format(LocalDateTime.Format { byUnicodePattern("dd.MM.yyyy") })
        } catch (e: Exception) {
            "..."
        }
    }

    Text(text = formattedDate)
}

fun getAndroidVersion(apiLevel: Int): String {
    val versions = mapOf(
        1 to "1.0",
        2 to "1.1",
        3 to "1.5",
        4 to "1.6",
        5 to "2.0",
        6 to "2.0.1",
        7 to "2.1",
        8 to "2.2",
        9 to "2.3",
        10 to "2.3.3",
        11 to "3.0",
        12 to "3.1",
        13 to "3.2",
        14 to "4.0",
        15 to "4.0.3",
        16 to "4.1",
        17 to "4.2",
        18 to "4.3",
        19 to "4.4",
        20 to "4.4W", // Wear
        21 to "5.0",
        22 to "5.1",
        23 to "6.0",
        24 to "7.0",
        25 to "7.1",
        26 to "8.0",
        27 to "8.1",
        28 to "9.0",
        29 to "10",
        30 to "11",
        31 to "12",
        32 to "12L", // Android 12L
        33 to "13",
        34 to "14",
        35 to "15",
        36 to "16",
        37 to "17"
    )

    return versions[apiLevel] ?: "? (API $apiLevel)"
}
