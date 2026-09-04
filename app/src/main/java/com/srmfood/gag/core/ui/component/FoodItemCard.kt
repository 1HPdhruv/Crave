package com.srmfood.gag.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.srmfood.gag.R
import com.srmfood.gag.core.ui.theme.GagAmber
import com.srmfood.gag.core.ui.theme.GagOnSurfaceVariant
import com.srmfood.gag.core.ui.theme.GagOrange
import com.srmfood.gag.core.ui.theme.GagSurface
import com.srmfood.gag.core.ui.theme.GagSurfaceVariant
import com.srmfood.gag.core.ui.theme.NonVegRed
import com.srmfood.gag.core.ui.theme.VegGreen
import com.srmfood.gag.domain.model.FoodItem

@Composable
fun FoodItemCard(
    foodItem: FoodItem,
    onClick: () -> Unit,
    onAddToCart: () -> Unit,
    onFavoriteToggle: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = GagSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Food image
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GagSurfaceVariant)
            ) {
                GagFoodImage(
                    model = foodItem.imageUrl,
                    contentDescription = stringResource(R.string.cd_food_image),
                    category = foodItem.category,
                    modifier = Modifier.matchParentSize()
                )
                // Veg / Non-veg indicator
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(14.dp)
                        .background(Color.White, CircleShape)
                        .align(Alignment.TopStart),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (foodItem.isVeg) VegGreen else NonVegRed,
                                CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = foodItem.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = foodItem.outletName,
                    style = MaterialTheme.typography.bodySmall,
                    color = GagOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = GagOnSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${foodItem.prepTimeMinutes} min",
                        style = MaterialTheme.typography.labelSmall,
                        color = GagOnSurfaceVariant
                    )
                    if (foodItem.rating > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = GagAmber,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = foodItem.rating.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = GagOnSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "₹${foodItem.price}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = GagOrange
                    )
                    if (foodItem.isAvailable) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GagOrange)
                                .clickable(onClick = onAddToCart)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add to cart",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "Unavailable",
                            style = MaterialTheme.typography.labelSmall,
                            color = GagOnSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuantitySelector(
    quantity: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    minQuantity: Int = 0,
    maxQuantity: Int = 10
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(GagSurfaceVariant),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onDecrease,
            enabled = quantity > minQuantity,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease",
                tint = if (quantity > minQuantity) GagOrange else GagOnSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        IconButton(
            onClick = onIncrease,
            enabled = quantity < maxQuantity,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase",
                tint = if (quantity < maxQuantity) GagOrange else GagOnSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
