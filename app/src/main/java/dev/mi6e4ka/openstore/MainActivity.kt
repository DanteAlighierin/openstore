package dev.mi6e4ka.openstore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavArgument
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.mi6e4ka.openstore.ui.theme.OpenStoreTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenStoreTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "search") {
                    composable("search") {
                        MainPage(onSearch = {search ->
                            navController.navigate("search/$search")
                        })
                    }
                    composable("search/{query}",
                        arguments = listOf(
                            navArgument("query") {
                                defaultValue = ""
                            }
                        )
                    ) { entry ->
                        val query = entry.arguments?.getString("query")
                        SearchScreen(navController=navController, query=query)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavController, query: String?) {
    data class AppInfo(
        val id: String,
        val name: String,
        val iconUrl: String
    )


    Scaffold { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            TopAppBar(
                title = {Text("Поиск: $query")},
                navigationIcon = {
                    IconButton(
                        onClick = {navController.navigateUp()}
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
            Text("goida search $query")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPage(onSearch: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(bottom=10.dp, start=10.dp, end=10.dp, top=0.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column {
                TopAppBar(title = {Text(stringResource(R.string.app_name))})
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp, 0.dp)
                        .height(50.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(stringResource(R.string.safety_card_text), fontSize = 18.sp)
                    }
                }
            }
            OutlinedTextField(
                value = search,
                onValueChange = {s -> search=s},
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onSearch(search)
                    }
                )
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("source code")
                Text("v1.4.0")
            }
        }
    }
}