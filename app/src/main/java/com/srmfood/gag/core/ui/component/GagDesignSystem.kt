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
import androidx.compose.ui.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.srmfood.gag.core.ui.theme.GagPink
import com.srmfood.gag.core.ui.theme.GagSpacing

@Composable
fun GagSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Search food, restaurants, cuisines...") {
    Row(modifier = modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(if (query.isEmpty()) placeholder else query, style = MaterialTheme.typography.bodyLarge, color = if (query.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun GagCategoryChip(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, iconUrl: String? = null, emoji: String? = null) {
    Column(modifier = modifier.width(76.dp).clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(if (isSelected) GagPink else MaterialTheme.colorScheme.surface).border(1.dp, if (isSelected) GagPink else MaterialTheme.colorScheme.outlineVariant, CircleShape), contentAlignment = Alignment.Center) {
            if (iconUrl != null) AsyncImage(model = iconUrl, contentDescription = label, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.Search, contentDescription = null, tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) GagPink else MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun GagSectionHeader(title: String, modifier: Modifier = Modifier, actionText: String? = null, onActionClick: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (actionText != null && onActionClick != null) Text(actionText, style = MaterialTheme.typography.labelLarge, color = GagPink, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onActionClick() })
    }
}

@Composable
fun GagBadge(text: String, color: Color, containerColor: Color, modifier: Modifier = Modifier) = Box(modifier.clip(RoundedCornerShape(6.dp)).background(containerColor).padding(horizontal = 8.dp, vertical = 4.dp)) { Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color) }

@Composable
fun GagPrice(price: Double, modifier: Modifier = Modifier, originalPrice: Double? = null) = Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("₹${price.toInt()}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
    if (originalPrice != null && originalPrice > price) Text("₹${originalPrice.toInt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textDecoration = TextDecoration.LineThrough)
}

@Composable
fun GagIconButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, containerColor: Color = MaterialTheme.colorScheme.surfaceVariant, contentColor: Color = MaterialTheme.colorScheme.onSurface) = Box(modifier.size(40.dp).clip(CircleShape).background(containerColor).clickable { onClick() }, contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp)) }

@Composable
fun GagQuantitySelector(quantity: Int, onIncrease: () -> Unit, onDecrease: () -> Unit, modifier: Modifier = Modifier) = Row(modifier.height(36.dp).clip(RoundedCornerShape(8.dp)).background(GagPink.copy(alpha = .08f)).border(1.dp, GagPink, RoundedCornerShape(8.dp)), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(36.dp).clickable { onDecrease() }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Remove, "Decrease", tint = GagPink, modifier = Modifier.size(18.dp)) }
    Text(quantity.toString(), style = MaterialTheme.typography.titleSmall, color = GagPink, modifier = Modifier.padding(horizontal = 8.dp))
    Box(Modifier.size(36.dp).clickable { onIncrease() }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "Increase", tint = GagPink, modifier = Modifier.size(18.dp)) }
}

@Composable
fun GagCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) = Card(modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), elevation = CardDefaults.cardElevation(0.dp)) { content() }

@Composable
fun GagEmptyState(title: String, description: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Default.Search, actionButton: @Composable (() -> Unit)? = null) = Column(modifier.fillMaxWidth().padding(GagSpacing.Huge), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Box(Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    Spacer(Modifier.height(20.dp)); Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center); Spacer(Modifier.height(8.dp)); Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    if (actionButton != null) { Spacer(Modifier.height(20.dp)); actionButton() }
}

@Composable
fun GagErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) = GagEmptyState("Something went wrong", message, modifier, Icons.Rounded.ErrorOutline) { GagPrimaryButton("Retry", onRetry, Modifier.width(160.dp)) }

@Composable
fun GagLoadingSkeleton(modifier: Modifier = Modifier) = Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)))
