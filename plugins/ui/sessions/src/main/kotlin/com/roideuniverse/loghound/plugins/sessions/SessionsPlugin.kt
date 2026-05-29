package com.roideuniverse.loghound.plugins.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roideuniverse.loghound.core.UIPlugin
import com.roideuniverse.loghound.design.LogHoundDesign
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SessionsTestTags {
    const val SIDEBAR = "sessions.sidebar"
    const val CURRENT_NAME = "sessions.currentName"
    const val END_AND_NEW = "sessions.endAndNew"
    const val ARCHIVED_ROW = "sessions.archivedRow"
}

/**
 * Sidebar plugin for the Sessions feature. Renders the live session at the
 * top, then a list of archived sessions below.
 *
 * The actions (end + archive, rename) delegate to the injected
 * [SessionsManager]. This UI only triggers them and re-reads the resulting
 * StateFlows.
 *
 * Archive sub-tabs (open an archived session in a read-only Log Viewer) are
 * deliberately out of scope here — see issue #21's follow-up tasks.
 */
class SessionsPlugin @Inject constructor(
    private val manager: SessionsManager,
) : UIPlugin {
    override val id: String = "core.sessions"
    override val name: String = "Sessions"

    @Composable
    override fun content(modifier: Modifier) {
        val active by manager.active.collectAsState()
        val archived by manager.archived.collectAsState()
        val scope = rememberCoroutineScope()

        Surface(modifier = modifier.fillMaxSize(), color = LogHoundDesign.Colors.Background) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp).testTag(SessionsTestTags.SIDEBAR)) {
                Text("Current session", style = LogHoundDesign.Text.Tab.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(8.dp))
                if (active != null) {
                    CurrentSessionCard(
                        name = active!!.name,
                        startedAt = active!!.startedAt,
                        onEndAndNew = {
                            scope.launch { manager.endAndStartNew() }
                        },
                    )
                } else {
                    Text(
                        "No active session — initializing…",
                        style = LogHoundDesign.Text.Status,
                    )
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = LogHoundDesign.Colors.Border)
                Spacer(Modifier.height(16.dp))

                Text(
                    "Archived sessions (${archived.size})",
                    style = LogHoundDesign.Text.Tab.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(8.dp))
                if (archived.isEmpty()) {
                    Text(
                        "No archives yet.",
                        style = LogHoundDesign.Text.Status,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(archived, key = { it.id }) { session ->
                            ArchivedSessionRow(session)
                            HorizontalDivider(color = LogHoundDesign.Colors.Border)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentSessionCard(
    name: String,
    startedAt: Long,
    onEndAndNew: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(LogHoundDesign.Colors.Surface)
            .padding(12.dp),
    ) {
        Text(
            name,
            style = LogHoundDesign.Text.Tab.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.testTag(SessionsTestTags.CURRENT_NAME),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Started " + formatTimestamp(startedAt),
            style = LogHoundDesign.Text.Status,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onEndAndNew,
            colors = ButtonDefaults.buttonColors(
                containerColor = LogHoundDesign.Colors.OnSurface,
                contentColor = Color.White,
            ),
            modifier = Modifier.testTag(SessionsTestTags.END_AND_NEW),
        ) {
            Text("End & Start New Session", style = LogHoundDesign.Text.Button.copy(color = Color.White))
        }
    }
}

@Composable
private fun ArchivedSessionRow(session: Session) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag(SessionsTestTags.ARCHIVED_ROW),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(session.name, style = LogHoundDesign.Text.Tab)
            Spacer(Modifier.height(2.dp))
            Text(
                "${formatTimestamp(session.startedAt)} · ${session.lineCount} lines",
                style = LogHoundDesign.Text.Status,
            )
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    return fmt.format(Date(millis))
}
