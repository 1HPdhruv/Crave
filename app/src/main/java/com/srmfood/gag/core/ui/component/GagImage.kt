package com.srmfood.gag.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.srmfood.gag.core.ui.theme.GagOnSurfaceVariant
import com.srmfood.gag.core.ui.theme.GagSurfaceVariant

@Composable
fun GagFoodImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    category: String? = null
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = {
            ShimmerBox(modifier = Modifier.fillMaxSize())
        },
        error = {
            FoodFallbackIcon(modifier = Modifier.fillMaxSize(), category = category)
        }
    )
}

@Composable
fun FoodFallbackIcon(
    modifier: Modifier = Modifier,
    category: String? = null
) {
    // Basic category mapping to material icons
    val icon = when (category?.lowercase()) {
        "pizza" -> Icons.Default.LocalPizza
        "beverages", "drinks", "juice" -> Icons.Default.LocalDrink
        "burger", "sandwiches", "snacks" -> Icons.Default.Fastfood
        else -> Icons.Default.Restaurant
    }

    Box(
        modifier = modifier.background(GagSurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GagOnSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
    }
}
