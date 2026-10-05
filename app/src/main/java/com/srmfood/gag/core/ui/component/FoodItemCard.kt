package com.srmfood.gag.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.srmfood.gag.core.ui.theme.GagStarYellow
import com.srmfood.gag.core.ui.theme.VegGreen
import com.srmfood.gag.core.ui.theme.NonVegRed
import com.srmfood.gag.domain.model.FoodItem

// ─── Food Item Card (Vertical — used in grids and lists) ─────────

@Composable
fun FoodItemCard(
    foodItem: FoodItem,
    onClick: () -> Unit,
    onAddToCart: () -> Unit,
    onFavoriteToggle: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "card_scale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // ─── Image Section ──────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)                      // Taller: 140 → 148dp
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = foodItem.imageUrl,
                contentDescription = foodItem.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Veg/Non-veg indicator — top left
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.92f))
                    .align(Alignment.TopStart),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.FiberManualRecord,
                    contentDescription = if (foodItem.isVeg) "Vegetarian" else "Non-vegetarian",
                    tint = if (foodItem.isVeg) VegGreen else NonVegRed,
                    modifier = Modifier.size(12.dp)
                )
            }

            // Favorite button — top right
            if (onFavoriteToggle != null) {
                var heartScale by remember { mutableFloatStateOf(1f) }
                val animatedHeartScale by animateFloatAsState(
                    targetValue = heartScale,
                    animationSpec = spring(dampingRatio = 0.3f),
                    label = "heart_scale"
                )
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(30.dp)                 // Slightly larger: 28 → 30dp
                        .scale(animatedHeartScale)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.95f))
                        .clickable {
                            heartScale = 0.75f
                            onFavoriteToggle()
                        }
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite
                                      else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites"
                                             else "Add to favorites",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary
                               else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                LaunchedEffect(heartScale) {
                    if (heartScale != 1f) {
                        kotlinx.coroutines.delay(150)
                        heartScale = 1f
                    }
                }
            }

            // Add to cart button — bottom right
            var isAdding by remember { mutableStateOf(false) }
            val addScale by animateFloatAsState(
                targetValue = if (isAdding) 0.82f else 1f,
                animationSpec = spring(dampingRatio = 0.4f),
                label = "add_btn_scale"
            )
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(36.dp)                     // Larger: 32 → 36dp
                    .scale(addScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        isAdding = true
                        onAddToCart()
                    }
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add ${foodItem.name} to cart",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            LaunchedEffect(isAdding) {
                if (isAdding) {
                    kotlinx.coroutines.delay(200)
                    isAdding = false
                }
            }
        }

        // ─── Info Section ───────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = foodItem.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "₹${foodItem.price.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary   // Price uses primary for hierarchy
            )
        }
    }
}

// ─── Food Item List Row (used in restaurant menu lists) ──────────

@Composable
fun FoodItemListRow(
    foodItem: FoodItem,
    onClick: () -> Unit,
    onAddToCart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Info column
        Column(modifier = Modifier.weight(1f)) {
            // Veg/Non-veg dot
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Transparent),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FiberManualRecord,
                        contentDescription = null,
                        tint = if (foodItem.isVeg) VegGreen else NonVegRed,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (foodItem.isVeg) "Veg" else "Non-veg",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (foodItem.isVeg) VegGreen else NonVegRed
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = foodItem.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "₹${foodItem.price.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary    // Price uses primary
            )
            if (foodItem.description?.isNotBlank() == true) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = foodItem.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Image + add button
        Box(modifier = Modifier.size(90.dp)) {
            AsyncImage(
                model = foodItem.imageUrl,
                contentDescription = foodItem.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))     // 12 → 14dp
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onAddToCart),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add to cart",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
