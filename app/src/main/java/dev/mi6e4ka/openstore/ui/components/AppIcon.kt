package dev.mi6e4ka.openstore.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import dev.mi6e4ka.openstore.R

@Composable
fun AppIcon(
    imageUrl: String,
    modifier: Modifier = Modifier
) {
    SubcomposeAsyncImage(
        model = imageUrl,
        contentDescription = "Icon",
        modifier = modifier,
        loading = {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp)
            )
        },
        error = {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(R.drawable.ic_broken_image_24px),
                contentDescription = "Error"
            )
        }
    )
}