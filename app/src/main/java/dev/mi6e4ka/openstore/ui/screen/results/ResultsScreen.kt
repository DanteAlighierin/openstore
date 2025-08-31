package dev.mi6e4ka.openstore.ui.screen.results

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import dev.mi6e4ka.openstore.di.AppModule
import dev.mi6e4ka.openstore.ui.components.AppIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    query: String,
    navController: NavController = rememberNavController(),
) {
    //val viewModel = remember(query) { ResultsViewModel(query) }
    val viewModel : ResultsViewModel = viewModel(
        factory = ResultsViewModelFactory(query)
    )
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(query) {
        viewModel.search()
    }
    Scaffold(
        topBar = { TopAppBar(
            title = {Text("Поиск")},
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
        ) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                )
            } else if (state.error != null) {
                Text("Ошибка: ${state.error}", modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.results) { item ->
                        Card(
                            modifier = Modifier
                                //.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = { navController.navigate("app/${item.packageName}") })
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(item.iconUrl, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)))
                                Column(
                                    modifier = Modifier.weight(1f).padding(start = 16.dp,end = 4.dp)
                                ) {
                                    Text(text = item.appName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(text = item.packageName, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.secondary)
                                }
//                                FilledTonalIconButton (
//                                    onClick = {
//                                        scope.launch {
//                                            val clipboard = context.getSystemService(ClipboardManager::class.java)
//                                            val link = AppModule.searchRepository.getAppDownloadLink(item.appId)
//                                            val clip = ClipData.newPlainText("", link)
//                                            clipboard.setPrimaryClip(clip)
//                                        }
//                                    }
//                                ) {
//                                    Icon(
//                                        imageVector = Icons.Filled.Link,
//                                        contentDescription = "Copy app download link"
//                                    )
//                                }
                            }

                        }
                    }
                }
            }
        }
    }
}