package com.srmfood.gag.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.srmfood.gag.core.ui.theme.CraveRed
import com.srmfood.gag.core.ui.theme.GagPink
import com.srmfood.gag.core.ui.theme.GagSpacing

// ─── Search Bar ───────────────────────────────────────────────────

/**
 * Large, prominent search bar.
 * Styled as a flat rounded-rectangle input — no border, gray surface.
 * In read-only mode (onClick != null) it acts as a tap target to open a search screen.
 */
@Composable
fun GagSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search for food, dishes or outlets",
    readOnly: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (query.isEmpty()) placeholder else query,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Category Chip (outlet/menu category tabs) ───────────────────

@Composable
fun GagCategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconUrl: String? = null,
    emoji: String? = null
) {
    // Compact tab-style chip — no large circle
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (isSelected) CraveRed else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Section Header ───────────────────────────────────────────────

@Composable
fun GagSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelLarge,
                color = CraveRed,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onActionClick)
            )
        }
    }
}

// ─── Badge ────────────────────────────────────────────────────────

@Composable
fun GagBadge(
    text: String,
    color: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(containerColor)
            .padding(horizontal = GagSpacing.Small, vertical = 2.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// ─── Price Display ────────────────────────────────────────────────

@Composable
fun GagPrice(
    price: Double,
    modifier: Modifier = Modifier,
    originalPrice: Double? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "₹${price.toInt()}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (originalPrice != null && originalPrice > price) {
            Text(
                text = "₹${originalPrice.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough
            )
        }
    }
}

// ─── Icon Button (circular) ───────────────────────────────────────

@Composable
fun GagIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ─── Quantity Selector ────────────────────────────────────────────

@Composable
fun GagQuantitySelector(
    quantity: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    modifier: Modifier = Modifier,
    minQuantity: Int = 1
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clickable(onClick = onDecrease),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Remove,
                contentDescription = "Decrease quantity",
                tint = if (quantity <= 1) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = GagSpacing.Medium)
        )
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(CraveRed.copy(alpha = 0.08f))
                .clickable(onClick = onIncrease),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Increase quantity",
                tint = CraveRed,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─── Card Container ───────────────────────────────────────────────

@Composable
fun GagCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

// ─── Food Image ───────────────────────────────────────────────────

@Composable
fun GagFoodImage(
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = imageUrl,
        contentDescription = "Food image",
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(MaterialTheme.shapes.large),
        contentScale = ContentScale.Crop
    )
}

// ─── Empty State ─────────────────────────────────────────────────

@Composable
fun GagEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Search,
    actionButton: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(GagSpacing.Huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(GagSpacing.ExtraLarge))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(GagSpacing.Small))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionButton != null) {
            Spacer(modifier = Modifier.height(GagSpacing.Large))
            actionButton()
        }
    }
}

// ─── Error State ─────────────────────────────────────────────────

@Composable
fun GagErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    GagEmptyState(
        title = "Something went wrong",
        description = message,
        modifier = modifier,
        icon = Icons.Rounded.ErrorOutline,
        actionButton = {
            GagPrimaryButton(
                text = "Try Again",
                onClick = onRetry,
                modifier = Modifier.width(200.dp)
            )
        }
    )
}

// ─── Loading Skeleton ────────────────────────────────────────────

@Composable
fun GagLoadingSkeleton(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(
            MaterialTheme.colorScheme.surfaceVariant,
            RoundedCornerShape(8.dp)
        )
    )
}

// ─── Empty Screen / Error Screen aliases ─────────────────────────

@Composable
fun GagEmptyScreen(
    title: String = "Nothing here yet",
    description: String = "",
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Search,
    action: @Composable (() -> Unit)? = null
) {
    GagEmptyState(
        title = title,
        description = description,
        modifier = modifier,
        icon = icon,
        actionButton = action
    )
}
