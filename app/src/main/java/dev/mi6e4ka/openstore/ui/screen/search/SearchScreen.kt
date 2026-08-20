package dev.mi6e4ka.openstore.ui.screen.search

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import dev.mi6e4ka.openstore.BuildConfig
import dev.mi6e4ka.openstore.R
import dev.mi6e4ka.openstore.ui.screen.results.Platform
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavController = rememberNavController()) {
    var search by remember { mutableStateOf("") }
    var isDomainsVerified by remember { mutableStateOf(true) }
    var areNotificationsEnabled by remember { mutableStateOf(true) }
    val context = LocalContext.current

    OnResumeEffect {
        isDomainsVerified = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(DomainVerificationManager::class.java)
            val states = manager.getDomainVerificationUserState(context.packageName)
            states?.hostToStateMap?.forEach { state ->
                Log.d("state", "$state")
                if (state.value != 1) {
                    isDomainsVerified = false
                }
            }
        }
        areNotificationsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.areNotificationsEnabled()
        } else true
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(
            title = {Text(stringResource(R.string.app_name))},
            actions = {
                IconButton(onClick = { navController.navigate("updates") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_update_24px),
                        contentDescription = stringResource(R.string.updates_title)
                    )
                }
                IconButton({navController.navigate("settings")}) {
                    Icon(
                        painterResource(R.drawable.ic_settings_24px),
                        contentDescription = stringResource(R.string.settings_title)
                    )
                }
            }
        ) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 15.dp)
                .imePadding()
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!areNotificationsEnabled) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    context.startActivity(intent)
                                }
                            }),
                        colors = CardDefaults.cardColors(containerColor = Color(109, 255, 30, 40))
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 10.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_notifications_unread_24px),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                            )
                            Text(stringResource(R.string.notifications_disabled), fontSize = 18.sp)
                        }
                    }
                }
                if (!isDomainsVerified) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val intent = Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS)
                                        .apply { data = Uri.fromParts("package", context.packageName, null) }
                                    context.startActivity(intent)
                                }
                            }),
                        colors = CardDefaults.cardColors(containerColor = Color(109, 255, 30, 40))
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 10.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_add_link_24px),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                            )
                            Text(stringResource(R.string.unconfirmed_domains), fontSize = 18.sp)
                        }
                    }
                }
            }
            OutlinedTextField(
                value = search,
                onValueChange = {s -> search=s},
                leadingIcon = { Icon(painterResource(R.drawable.ic_search_24px), contentDescription = null) },
                modifier = Modifier.fillMaxWidth().semantics{
                    contentDescription = context.getString(R.string.search_title)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (search.isNotBlank()) {
                            navController.navigate("search/${URLEncoder.encode(search, "UTF-8")}")
                        }
                    }
                )
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("source code", textDecoration = TextDecoration.Underline, modifier = Modifier.clickable(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://codeberg.org/mi6e4ka/openstore".toUri())
                    context.startActivity(intent)
                }))
                Text(BuildConfig.VERSION_NAME)
            }
        }
    }
}

@Composable
fun OnResumeEffect(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResume by rememberUpdatedState(onResume)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}