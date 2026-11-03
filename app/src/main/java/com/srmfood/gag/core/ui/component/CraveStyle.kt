package com.srmfood.gag.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srmfood.gag.core.ui.theme.CraveAqua
import com.srmfood.gag.core.ui.theme.CraveChartreuse
import com.srmfood.gag.core.ui.theme.CraveInk
import com.srmfood.gag.core.ui.theme.CraveLavender
import com.srmfood.gag.core.ui.theme.CraveOrange
import com.srmfood.gag.core.ui.theme.CraveOrangeDeep
import com.srmfood.gag.core.ui.theme.CravePaper
import com.srmfood.gag.core.ui.theme.CraveSun
import com.srmfood.gag.core.ui.theme.GagEyebrowStyle
import com.srmfood.gag.core.ui.theme.GagMonoStyle

// Building blocks that carry the website's "paper + ink" look:
// hard offset shadows, ink borders, mono eyebrows, tinted placeholder art.

/** Pastel tones the website cycles through for food/outlet art backgrounds. */
val CraveTones: List<Color> = listOf(CraveOrange, CraveChartreuse, CraveLavender, CraveAqua, CraveSun)

/** Stable tone for a given key so placeholder art does not change between recompositions. */
fun craveToneFor(key: String?): Color =
    CraveTones[((key ?: "").hashCode() and Int.MAX_VALUE) % CraveTones.size]

/** The website's signature solid offset shadow (`5px 6px 0 ink`). */
fun Modifier.craveShadow(
    shape: Shape = RoundedCornerShape(18.dp),
    offsetX: Dp = 4.dp,
    offsetY: Dp = 5.dp,
    color: Color = CraveInk
): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val dx = offsetX.toPx()
    val dy = offsetY.toPx()
    translateOutline(outline, dx, dy, color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.translateOutline(
    outline: androidx.compose.ui.graphics.Outline,
    dx: Float,
    dy: Float,
    color: Color
) {
    translate(dx, dy) {
        when (outline) {
            is androidx.compose.ui.graphics.Outline.Rectangle ->
                drawRect(color, outline.rect.topLeft, outline.rect.size)
            is androidx.compose.ui.graphics.Outline.Rounded -> {
                val r = outline.roundRect
                drawRoundRect(color, Offset(r.left, r.top), androidx.compose.ui.geometry.Size(r.width, r.height),
                    CornerRadius(r.topLeftCornerRadius.x, r.topLeftCornerRadius.y))
            }
            is androidx.compose.ui.graphics.Outline.Generic -> drawPath(outline.path, color)
        }
    }
}

/** Surface with an ink border and the hard shadow — hero cards, summary cards, selected tiles. */
@Composable
fun CraveBrutalCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    shape: Shape = RoundedCornerShape(18.dp),
    borderWidth: Dp = 2.dp,
    shadow: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .then(if (shadow) Modifier.craveShadow(shape) else Modifier)
            .clip(shape)
            .background(container)
            .border(borderWidth, CraveInk, shape)
    ) { content() }
}

/** Flat white card with the website's 1px "line" border. */
@Composable
fun CravePanel(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    shape: Shape = RoundedCornerShape(16.dp),
    borderColor: Color = MaterialTheme.colorScheme.outline,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(container)
            .border(1.dp, borderColor, shape)
    ) { content() }
}

/** DM Mono uppercase kicker above headings, e.g. "AROUND CAMPUS". */
@Composable
fun CraveEyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(text = text.uppercase(), style = GagEyebrowStyle, color = color, modifier = modifier)
}

/** Heading block used by every section on the website: eyebrow + title + optional "see all ↗". */
@Composable
fun CraveHeading(
    eyebrow: String?,
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            if (eyebrow != null) CraveEyebrow(eyebrow)
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (actionText != null && onAction != null) {
            Row(
                modifier = Modifier
                    .padding(start = 12.dp, bottom = 4.dp)
                    .clickableNoRipple(onAction),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(" ↗", color = CraveOrange, fontSize = 15.sp)
            }
        }
    }
}

/** Small tilted label — "sticker" from the website (ink or orange fill, 1px ink border, mono). */
@Composable
fun CraveSticker(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = CraveInk,
    content: Color = CravePaper,
    tilt: Float = -2f
) {
    Text(
        text = text.uppercase(),
        style = GagEyebrowStyle.copy(fontSize = 9.sp, letterSpacing = 0.6.sp),
        color = content,
        modifier = modifier
            .rotate(tilt)
            .clip(RoundedCornerShape(4.dp))
            .background(container)
            .border(1.dp, CraveInk, RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp)
    )
}

/** Round "blob" icon chip: ink border, tinted fill, emoji or icon inside. */
@Composable
fun CraveBlob(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    container: Color = CraveLavender,
    tilt: Float = -10f,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .rotate(tilt)
            .craveShadow(CircleShape, 2.dp, 3.dp)
            .clip(CircleShape)
            .background(container)
            .border(2.dp, CraveInk, CircleShape),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Mono price/amount text — "DM Mono 500". */
@Composable
fun CraveMono(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: Float = 13f
) {
    Text(text = text, style = GagMonoStyle.copy(fontSize = fontSize.sp), color = color, modifier = modifier)
}

/** Ink-bordered small status pill (`.order-state`, `.badge`). */
@Composable
fun CraveStateBadge(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = CraveChartreuse,
    content: Color = CraveInk
) {
    Text(
        text = text.uppercase(),
        style = GagEyebrowStyle.copy(fontSize = 9.sp, letterSpacing = 0.3.sp),
        color = content,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(container)
            .border(1.dp, CraveInk, RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp)
    )
}

/** CRAVE wordmark (glyph "C" in a tilted rounded square + "RAVE" + orange spark). */
@Composable
fun CraveBrandMark(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onBackground
) {
    val glyph = if (compact) 26.dp else 33.dp
    val word = if (compact) 20.sp else 25.sp
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(glyph)
                .rotate(-8f)
                .border(if (compact) 2.dp else 3.dp, color, RoundedCornerShape(12.dp, 17.dp, 8.dp, 16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("C", color = color, fontWeight = FontWeight.Bold, fontSize = if (compact) 16.sp else 21.sp)
        }
        Text(
            text = "RAVE",
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = word,
            letterSpacing = (-1.5).sp,
            modifier = Modifier.padding(start = 2.dp)
        )
        Text("✦", color = CraveOrange, fontSize = 13.sp, modifier = Modifier.padding(start = 1.dp, bottom = 12.dp))
    }
}

/** Standard horizontal page padding (website mobile `content-wrap`: 17px). */
val CravePagePadding = PaddingValues(horizontal = 17.dp)

/** Deep-orange for text that must sit on paper (website `orange-ink`). */
val CraveOrangeInk: Color get() = CraveOrangeDeep

/** Click target without a ripple (text links like "see all ↗"). */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

/**
 * Two-per-row grid for use inside a LazyColumn (website `.food-grid` on mobile).
 * Each pair is laid out in a Row with equal-height cells; an odd last item leaves an empty cell.
 */
fun <T> androidx.compose.foundation.lazy.LazyListScope.craveGridItems(
    items: List<T>,
    columns: Int = 2,
    spacing: Dp = 10.dp,
    horizontalPadding: Dp = 17.dp,
    key: ((T) -> Any)? = null,
    itemContent: @Composable (T) -> Unit
) {
    val rows = items.chunked(columns)
    items(
        count = rows.size,
        key = if (key != null) { index: Int -> rows[index].joinToString("|") { key(it).toString() } } else null
    ) { rowIndex ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .padding(bottom = spacing),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            val row = rows[rowIndex]
            for (i in 0 until columns) {
                Box(modifier = Modifier.weight(1f)) {
                    if (i < row.size) itemContent(row[i])
                }
            }
        }
    }
}
