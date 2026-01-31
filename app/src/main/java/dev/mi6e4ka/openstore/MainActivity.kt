package dev.mi6e4ka.openstore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dev.mi6e4ka.openstore.ui.screen.details.DetailsScreen
import dev.mi6e4ka.openstore.ui.screen.results.ResultsScreen
import dev.mi6e4ka.openstore.ui.screen.reviews.ReviewsScreen
import dev.mi6e4ka.openstore.ui.screen.search.SearchScreen
import dev.mi6e4ka.openstore.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cacheDir = externalCacheDir ?: cacheDir
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".apk", ignoreCase = true)) {
                file.delete()
                println("CacheCleaner" + "Удален: ${file.name}")
            }
        }
        val imageLoader = ImageLoader.Builder(this)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(10L * 1024 * 1024)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizeBytes(10 * 1024 * 1024)
                    .build()
            }
            .networkCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
        Coil.setImageLoader(imageLoader)

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "search",
                        enterTransition = {
                            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
                        },
                        exitTransition = {
                            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
                        },
                        popEnterTransition = {
                            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
                        },
                        popExitTransition = {
                            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
                        }
                    ) {
                        composable("search") {
                            SearchScreen(navController)
                        }
                        composable("search/{query}") {
                            val query = it.arguments?.getString("query") ?: ""
                            ResultsScreen(query = query, navController = navController)
                        }
                        composable("app/{appId}?platform={platform}", deepLinks = listOf(
                            navDeepLink { uriPattern = "https://www.rustore.ru/catalog/app/{appId}" },
                            navDeepLink { uriPattern = "https://apps.rustore.ru/app/{appId}" },
                            navDeepLink { uriPattern = "https://www.rustore.ru/instruction?appName={appName}&utm_campaign={appId}" }
                        )) { backStackEntry ->
                            val itemId = backStackEntry.arguments?.getString("appId") ?: ""
                            val platform = backStackEntry.arguments?.getString("platform") ?: "mobile"
                            AnimatedVisibility(
                                visible = true,
                                enter = slideInHorizontally(initialOffsetX = { 1000 }) + fadeIn(),
                                exit = slideOutHorizontally(targetOffsetX = { -1000 }) + fadeOut()
                            ) {
                                DetailsScreen(itemId = itemId, appPlatform=platform, navController = navController)
                            }
                        }
                        composable("app/{packageName}/reviews") {
                            val packageName = it.arguments?.getString("packageName") ?: ""
                            ReviewsScreen(packageName = packageName, navController = navController)
                        }
                    }
                }
            }
        }
    }
}