package com.srmfood.gag.core.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.srmfood.gag.core.ui.theme.*
import com.srmfood.gag.domain.repository.HostelAddress
import com.srmfood.gag.domain.repository.OrderingMode

// ─── Delivery / Pickup Segmented Control ─────────────────────────

/**
 * Premium segmented control for switching between Delivery and Pickup.
 *
 * Light mode:
 *   - Selected: near-black (#191919) fill with white text
 *   - Unselected: light gray (#F5F5F5) with muted text
 *
 * Dark mode:
 *   - Selected: elevated white (#FFFFFF) fill with black text
 *   - Unselected: dark surface (#202020) with muted text
 */
@Composable
fun DeliveryModeSelector(
    selectedMode: OrderingMode,
    onModeSelected: (OrderingMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == CraveDarkBackground

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModeTab(
            label = "Delivery",
            icon = Icons.Outlined.DeliveryDining,
            isSelected = selectedMode == OrderingMode.DELIVERY,
            isDark = isDark,
            onClick = { onModeSelected(OrderingMode.DELIVERY) },
            modifier = Modifier.weight(1f)
        )
        ModeTab(
            label = "Pickup",
            icon = Icons.Outlined.ShoppingBag,
            isSelected = selectedMode == OrderingMode.PICKUP,
            isDark = isDark,
            onClick = { onModeSelected(OrderingMode.PICKUP) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModeTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Selected: dark background + white text (light) / white bg + dark text (dark)
    val bgColor = if (isSelected) {
        if (isDark) Color.White else Color(0xFF191919)
    } else {
        Color.Transparent
    }
    val textColor = if (isSelected) {
        if (isDark) Color(0xFF191919) else Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = textColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}

// ─── Hostel Address Banner ────────────────────────────────────────

/**
 * Compact banner shown when Delivery is selected.
 * Shows hostel address if complete, or a prompt to add one.
 */
@Composable
fun HostelAddressBanner(
    address: HostelAddress,
    outletName: String? = null,
    isDelivery: Boolean,
    onChangeAddress: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isDelivery,
        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
        exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Home,
                contentDescription = null,
                tint = CraveRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Deliver to hostel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(1.dp))
                if (address.isComplete) {
                    Text(
                        text = address.displaySummary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = "Add hostel address",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = CraveRed
                    )
                }
            }
            TextButton(
                onClick = onChangeAddress,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (address.isComplete) "Change" else "Add",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = CraveRed
                )
            }
        }
    }
}

// ─── Pickup Banner ────────────────────────────────────────────────

@Composable
fun PickupRestaurantBanner(
    outletName: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = outletName != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Pickup from",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (outletName != null) {
                    Text(
                        text = outletName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// ─── Hostel Address Dialog ────────────────────────────────────────

@Composable
fun HostelAddressDialog(
    currentAddress: HostelAddress,
    onSave: (HostelAddress) -> Unit,
    onDismiss: () -> Unit
) {
    var hostel by remember(currentAddress) { mutableStateOf(currentAddress.hostel) }
    var block  by remember(currentAddress) { mutableStateOf(currentAddress.block) }
    var room   by remember(currentAddress) { mutableStateOf(currentAddress.room) }
    var notes  by remember(currentAddress) { mutableStateOf(currentAddress.notes) }

    val isValid = hostel.isNotBlank() && block.isNotBlank() && room.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onSave(HostelAddress(hostel.trim(), block.trim(), room.trim(), notes.trim())) },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CraveRed,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        title = {
            Text(
                "Hostel Delivery Address",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "SRM KTR Campus",
                    style = MaterialTheme.typography.labelMedium,
                    color = CraveRed,
                    fontWeight = FontWeight.SemiBold
                )
                AddressField(
                    value = hostel,
                    onValueChange = { hostel = it },
                    label = "Hostel Name",
                    placeholder = "e.g. Krishna Hostel",
                    leadingIcon = Icons.Outlined.Home,
                    imeAction = ImeAction.Next
                )
                AddressField(
                    value = block,
                    onValueChange = { block = it },
                    label = "Block",
                    placeholder = "e.g. B Block",
                    leadingIcon = Icons.Outlined.EditLocation,
                    imeAction = ImeAction.Next
                )
                AddressField(
                    value = room,
                    onValueChange = { room = it },
                    label = "Room Number",
                    placeholder = "e.g. 204",
                    leadingIcon = Icons.Outlined.MeetingRoom,
                    imeAction = ImeAction.Next
                )
                AddressField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Delivery Notes (optional)",
                    placeholder = "e.g. Call on arrival",
                    leadingIcon = null,
                    imeAction = ImeAction.Done
                )
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun AddressField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    imeAction: ImeAction = ImeAction.Next
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        placeholder = {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        },
        leadingIcon = if (leadingIcon != null) {
            {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Words,
            imeAction = imeAction
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CraveRed,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            cursorColor = CraveRed
        )
    )
}
