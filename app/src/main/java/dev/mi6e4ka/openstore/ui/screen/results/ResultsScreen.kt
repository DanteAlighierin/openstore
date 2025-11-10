package dev.mi6e4ka.openstore.ui.screen.results

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.mi6e4ka.openstore.R
import dev.mi6e4ka.openstore.ui.components.AppIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    query: String,
    navController: NavController = rememberNavController(),
) {
    val viewModel : ResultsViewModel = viewModel(
        factory = ResultsViewModelFactory(query)
    )

    val resultsLazyPaging = viewModel.searchFlow.collectAsLazyPagingItems()
    var platformSelectorExpanded by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = { TopAppBar(
            title = {Text(stringResource(R.string.search_title))},
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                TextButton({platformSelectorExpanded = true}) {
                    Text(state.selectedPlatform.label)
                }
                DropdownMenu(
                    expanded = platformSelectorExpanded,
                    onDismissRequest = {platformSelectorExpanded = false}
                ) {
                    DropdownMenuItem(
                        text = { Text("Mobile") },
                        onClick = { viewModel.selectPlatform(Platform("Mobile", "mobile")); platformSelectorExpanded=false }
                    )
                    DropdownMenuItem(
                        text = { Text("TV") },
                        onClick = { viewModel.selectPlatform(Platform("TV", "tv")); platformSelectorExpanded=false }
                    )
                }
            }
        ) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize().padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                resultsLazyPaging.itemCount,
                key = resultsLazyPaging.itemKey { it.appId }
            ) { index ->
                val app = resultsLazyPaging[index]
                if (app != null) {
                    Card(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = { navController.navigate("app/${app.packageName}?platform=${state.selectedPlatform.value}") })
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(app.iconUrl, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)))
                            Column(
                                modifier = Modifier.weight(1f).padding(start = 16.dp,end = 4.dp)
                            ) {
                                Text(text = app.appName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = app.packageName, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                } else {
                    Text(stringResource(R.string.loading_error))
                }
            }
            resultsLazyPaging.apply {
                when {
                    loadState.refresh is LoadState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    loadState.append is LoadState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    loadState.refresh is LoadState.Error -> {
                        if (loadState.refresh is LoadState.Error) {
                            item {
                                Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                        Text(stringResource(R.string.error), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                        Icon(painterResource(R.drawable.pest_control_24px), null, Modifier.size(62.dp))
                                        Text("${(loadState.refresh as LoadState.Error).error}", modifier = Modifier.padding(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (resultsLazyPaging.itemCount == 0 && resultsLazyPaging.loadState.isIdle) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 56.dp)
                    .fillMaxSize()
                    .padding(innerPadding)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Search, "", modifier = Modifier.size(64.dp))
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.not_found_error))
            }
        }
    }
}