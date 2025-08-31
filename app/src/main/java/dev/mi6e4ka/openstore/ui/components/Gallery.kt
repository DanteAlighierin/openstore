package dev.mi6e4ka.openstore.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import dev.mi6e4ka.openstore.data.model.Files

@Composable
fun GalleryWithPager(images: List<Files>) {
    var selectedImageIndex by remember { mutableStateOf<Int?>(null) }

    if (selectedImageIndex != null) {
        Dialog(
            onDismissRequest = { selectedImageIndex = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            FullScreenGallery(
                images = images,
                initialIndex = selectedImageIndex ?: 0,
                onDismiss = { selectedImageIndex = null }
            )
        }
    }


    Box(
        modifier = Modifier.height(250.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(images) { i, file ->
                var isLoading by remember { mutableStateOf(true) }
                AsyncImage(
                    model = file.fileUrl,
                    contentDescription = null,
                    onSuccess = { isLoading = false },
                    onError = { isLoading = false },
                    onLoading = { isLoading = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .fillMaxHeight()
                        .clickable(onClick = {
                            selectedImageIndex = i
                        }),
                    contentScale = ContentScale.FillHeight
                )
                if (isLoading) {
                    Card(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .fillMaxHeight()
                            .aspectRatio(9f / 16f),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullScreenGallery(
    images: List<Files>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { images.size })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            //contentPadding = PaddingValues(horizontal = 16.dp),
        ) { page ->
            Box(
                Modifier
                    .fillMaxSize()
//                    .padding(horizontal = ((
//                            (pagerState.currentPage - page) + pagerState
//                                .currentPageOffsetFraction
//                            ).absoluteValue * 0).dp)
//                    .graphicsLayer {
//                        val pageOffset = (
//                                (pagerState.currentPage - page) + pagerState
//                                    .currentPageOffsetFraction
//                                ).absoluteValue
//                        alpha = lerp(
//                            start = 0.5f,
//                            stop = 1f,
//                            fraction = 1f - pageOffset.coerceIn(0f, 1f)
//                        )
//                    }
            ) {
                Image(
                    painter = rememberAsyncImagePainter(images[page].fileUrl),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onDismiss),
                    contentScale = ContentScale.Fit
                )
            }
        }

        Row(
            Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pagerState.pageCount) { iteration ->
                val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primaryContainer
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(color)
                        .size(8.dp)
                )
            }
        }
    }
}