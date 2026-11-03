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
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import com.srmfood.gag.core.ui.theme.CraveInk
import com.srmfood.gag.core.ui.theme.CraveOrange
import com.srmfood.gag.core.ui.theme.CravePaper
import com.srmfood.gag.core.ui.theme.CraveWhite
import com.srmfood.gag.core.ui.theme.GagEyebrowStyle
import com.srmfood.gag.core.ui.theme.GagMonoStyle
import com.srmfood.gag.core.ui.theme.GagStarYellow
import com.srmfood.gag.core.ui.theme.VegGreen
import com.srmfood.gag.core.ui.theme.NonVegRed
import com.srmfood.gag.domain.model.FoodItem

// ─── Food Item Card (website `.food-card`) ───────────────────────

@Composable
fun FoodItemCard(
    foodItem: FoodItem,
    onClick: () -> Unit,
    onAddToCart: () -> Unit,
    onFavoriteToggle: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val tone = craveToneFor(foodItem.id)
    val canAdd = foodItem.isAvailable

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
    ) {
        // ─── Image: tinted art block, ink rule underneath ───────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .background(tone)
        ) {
            if (foodItem.imageUrl.isNullOrBlank()) {
                Text(
                    text = "🍽",
                    fontSize = 44.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // Shimmer while loading, tidy icon fallback if the URL fails.
                GagFoodImage(
                    model = foodItem.imageUrl,
                    contentDescription = foodItem.name,
                    category = foodItem.category,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Category tag — top left
            Text(
                text = (foodItem.category.ifBlank { "campus classic" }).uppercase(),
                style = GagEyebrowStyle.copy(fontSize = 8.sp, letterSpacing = 0.3.sp),
                color = CraveInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 8.dp, end = 40.dp)
                    .background(CraveWhite)
                    .border(1.dp, CraveInk)
                    .padding(horizontal = 5.dp, vertical = 3.dp)
            )

            // Favorite — top right
            if (onFavoriteToggle != null) {
                // 44dp touch target around a 28dp visual
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { onFavoriteToggle() }
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isFavorite) CraveOrange else CraveWhite)
                            .border(1.dp, CraveInk, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = CraveInk,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            if (!canAdd) {
                Text(
                    text = "SOLD OUT",
                    style = GagEyebrowStyle.copy(fontSize = 8.sp),
                    color = CravePaper,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(CraveInk)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(CraveInk)
        )

        // ─── Body ───────────────────────────────────────────────
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp, end = 6.dp)
                        .size(11.dp)
                        .border(1.dp, if (foodItem.isVeg) VegGreen else NonVegRed, RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (foodItem.isVeg) VegGreen else NonVegRed)
                    )
                }
                Text(
                    text = foodItem.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (foodItem.outletName.isNotBlank()) {
                Text(
                    text = foodItem.outletName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
            Spacer(modifier = Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "₹${foodItem.price.toInt()}",
                    style = GagMonoStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (foodItem.totalReviews > 0 && foodItem.rating > 0) {
                    Text(
                        text = "  ★ ${"%.1f".format(foodItem.rating)}",
                        style = GagMonoStyle.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // 44dp touch target around a 30dp visual (negative end padding keeps the footer compact)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .offset(x = 7.dp, y = 0.dp)
                        .clip(CircleShape)
                        .then(if (canAdd) Modifier.clickable(onClick = onAddToCart) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (canAdd) CraveInk else MaterialTheme.colorScheme.outline)
                            .border(1.dp, CraveInk, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = if (canAdd) "Add ${foodItem.name} to cart" else "${foodItem.name} is sold out",
                            tint = if (canAdd) CravePaper else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
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
                color = MaterialTheme.colorScheme.onSurface
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
                    .background(CraveInk)
                    .clickable(onClick = onAddToCart),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add to cart",
                    tint = CravePaper,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
