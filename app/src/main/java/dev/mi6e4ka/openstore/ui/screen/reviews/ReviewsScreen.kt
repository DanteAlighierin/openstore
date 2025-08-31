package dev.mi6e4ka.openstore.ui.screen.reviews

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbDownOffAlt
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbUpAlt
import androidx.compose.material.icons.filled.ThumbUpOffAlt
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.StarRate
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import dev.mi6e4ka.openstore.data.model.AppCommentsComments
import dev.mi6e4ka.openstore.di.AppModule
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

enum class ReviewsFilters(val value: String, val displayName: String) {
    NewFirst("NEW_FIRST", "Новые"),
    UsefulFirst("USEFUL_FIRST", "Полезные"),
    PositiveFirst("POSITIVE_FIRST", "Позитивные"),
    NegativeFirst("NEGATIVE_FIRST", "Негативные")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsScreen(packageName: String, navController: NavController = rememberNavController()) {
    var reviews: List<AppCommentsComments>? by remember { mutableStateOf(null) }
    var selectedFilter by remember { mutableStateOf(ReviewsFilters.NewFirst) }
    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedFilter) {
        isLoading = true
        scope.launch {
            reviews = try {
                AppModule.searchRepository.getAppComments(packageName, 50, selectedFilter.value)
            } catch (e: Exception) {
                listOf(AppCommentsComments(
                    appRating = 1,
                    firstName = "Семен",
                    commentDate = "2025-08-30 10:25:20.000",
                    commentText = "Не работает этот ваш опенстор, удаляю",
                    likeCounter = 100,
                    dislikeCounter = 1,
                    updatedAt = "2025-08-30 10:25:20.000",
                    devResponse = "",
                    devResponseDate = "",
                ))
            }
        }
        isLoading = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(
            title = {Text("Отзывы")},
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
        ) },
    ) { innerPadding ->
        Column(
            Modifier.padding(innerPadding).padding(horizontal = 16.dp)
        ) {
            if (isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReviewsFilters.entries.forEach { filter ->
                            FilterChip(
                                selected = filter == selectedFilter,
                                onClick = { selectedFilter = filter },
                                label = {Text(filter.displayName)},
                                leadingIcon = if (selectedFilter == filter) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null
                            )
                        }
                    }
                    if ((reviews?.size ?: 0) == 0 && !isLoading) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Text("Отзывов пока нет")
                        }
                    }
                }
                items(reviews ?: listOf()) { review ->
                    Card(
                        Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(16.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(review.firstName, fontWeight = FontWeight.Bold)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(formatTimestamp(review.commentDate), color = Color.Gray)
                                }

                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(review.appRating) {
                                    Icon(Icons.Filled.Star, null, Modifier.size(16.dp))
                                }
                                repeat(5 - review.appRating) {
                                    Icon(Icons.Outlined.StarBorder, null, Modifier.size(16.dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(review.commentText)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.ThumbUpOffAlt, contentDescription = null, Modifier.size(20.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("${review.likeCounter}")
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.ThumbDownOffAlt, contentDescription = null, Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("${review.dislikeCounter}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatTimestamp(timestamp: String): String {
    val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    val outputFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())

    val date = inputFormat.parse(timestamp)
    return outputFormat.format(date ?: "")
}