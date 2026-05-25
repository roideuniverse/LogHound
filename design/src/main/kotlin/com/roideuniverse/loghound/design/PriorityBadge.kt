package com.roideuniverse.loghound.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roideuniverse.loghound.core.LogPriority

/**
 * Swiss-spec priority pill. A 16dp square with 6dp corners, tinted
 * background per priority (`#FBEAEE` for Error, etc.), and the single-letter
 * priority label centered in the priority foreground color (`#E52E2E` for
 * Error, etc.).
 *
 * The badge replaces row-wide priority tinting from the pre-Swiss design.
 * Body text stays near-black for prolonged reading; the badge carries the
 * priority signal in a peripheral-vision-friendly chip.
 */
@Composable
fun PriorityBadge(priority: LogPriority, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(LogHoundDesign.badgeBgFor(priority)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = priority.label.toString(),
            style = LogHoundDesign.Text.Row.copy(
                color = LogHoundDesign.colorFor(priority),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 10.sp,
            ),
        )
    }
}
