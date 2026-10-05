package com.srmfood.gag.feature.reviews

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.srmfood.gag.core.ui.theme.GagPink
import com.srmfood.gag.core.ui.theme.GagYellow
import com.srmfood.gag.core.ui.component.GagPrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutletReviewComposerBottomSheet(
    orderId: String,
    outletId: String,
    existingReviewId: String?,
    existingRating: Int?,
    existingText: String?,
    onDismiss: () -> Unit,
    onReviewSubmitted: () -> Unit,
    viewModel: OutletReviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initialize(orderId, outletId, existingReviewId, existingRating, existingText)
    }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            onReviewSubmitted()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp, top = 8.dp)
                .imePadding()
        ) {
            Text(
                text = if (existingReviewId != null) "EDIT OUTLET REVIEW" else "RATE YOUR EXPERIENCE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = GagPink
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "How was the outlet?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                for (i in 1..5) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Rate $i stars",
                        tint = if (i <= state.rating) GagYellow else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.setRating(i) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Share your experience (optional)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedTextField(
                value = state.reviewText,
                onValueChange = viewModel::setReviewText,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text("Fast service, friendly staff...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GagPink,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                maxLines = 5
            )
            
            Text(
                text = "${state.reviewText.length} / 2000",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp)
            )
            
            state.error?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            GagPrimaryButton(
                text = if (state.isSubmitting) "Submitting..." else (if (existingReviewId != null) "Update Review" else "Submit Review"),
                onClick = viewModel::submitReview,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting && state.rating > 0
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
