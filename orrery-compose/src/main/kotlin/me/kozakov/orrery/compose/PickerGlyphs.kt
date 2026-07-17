package me.kozakov.orrery.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Canvas keyboard icon — material3 1.4 no longer bundles material-icons. */
@Composable
internal fun KeyboardGlyph(
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val color = LocalContentColor.current
    Canvas(
        modifier
            .size(24.dp)
            .semantics { contentDescription?.let { this.contentDescription = it } },
    ) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(
            color = color,
            topLeft = Offset(3.dp.toPx(), 7.dp.toPx()),
            size = Size((21 - 3).dp.toPx(), (17 - 7).dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = stroke,
        )
        val dotRadius = 1.2.dp.toPx()
        val dotY = 11.dp.toPx()
        listOf(7f, 12f, 17f).forEach { x ->
            drawCircle(color, radius = dotRadius, center = Offset(x.dp.toPx(), dotY))
        }
        drawLine(
            color,
            Offset(8.dp.toPx(), 14.5f.dp.toPx()),
            Offset(16.dp.toPx(), 14.5f.dp.toPx()),
            stroke.width,
            StrokeCap.Round,
        )
    }
}

/** Canvas calendar icon — material3 1.4 no longer bundles material-icons. */
@Composable
internal fun CalendarGlyph(
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val color = LocalContentColor.current
    Canvas(
        modifier
            .size(24.dp)
            .semantics { contentDescription?.let { this.contentDescription = it } },
    ) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(
            color = color,
            topLeft = Offset(4.dp.toPx(), 6.dp.toPx()),
            size = Size((20 - 4).dp.toPx(), (19 - 6).dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = stroke,
        )
        drawLine(
            color,
            Offset(4.dp.toPx(), 10.dp.toPx()),
            Offset(20.dp.toPx(), 10.dp.toPx()),
            stroke.width,
            StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(8.dp.toPx(), 4.5f.dp.toPx()),
            Offset(8.dp.toPx(), 7.5f.dp.toPx()),
            stroke.width,
            StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(16.dp.toPx(), 4.5f.dp.toPx()),
            Offset(16.dp.toPx(), 7.5f.dp.toPx()),
            stroke.width,
            StrokeCap.Round,
        )
    }
}

/** Canvas close icon — material3 1.4 no longer bundles material-icons. */
@Composable
internal fun CloseGlyph(
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val color = LocalContentColor.current
    Canvas(
        modifier
            .size(24.dp)
            .semantics { contentDescription?.let { this.contentDescription = it } },
    ) {
        val stroke = 2.dp.toPx()
        drawLine(color, Offset(7.dp.toPx(), 7.dp.toPx()), Offset(17.dp.toPx(), 17.dp.toPx()), stroke, StrokeCap.Round)
        drawLine(color, Offset(17.dp.toPx(), 7.dp.toPx()), Offset(7.dp.toPx(), 17.dp.toPx()), stroke, StrokeCap.Round)
    }
}
