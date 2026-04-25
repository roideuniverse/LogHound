package com.roideuniverse.loghound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.roideuniverse.loghound.core.UIPlugin

class LogViewerPlugin : UIPlugin {
    override val id: String = "core.log-viewer"
    override val name: String = "Log Viewer"

    @Composable
    override fun content(modifier: Modifier) {
        Column(modifier = modifier.fillMaxSize()) {
            FilterBar()
            HorizontalDivider(color = Color(0xFFCCCCCC))
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text("No logs yet", color = Color(0xFF999999))
            }
        }
    }
}

private val LOG_LEVELS = listOf("Verbose", "Debug", "Info", "Warn", "Error", "Assert")

@Composable
private fun FilterBar() {
    var search by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var packageProcess by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(LOG_LEVELS.first()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF7F7F7),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Filter logs") },
                singleLine = true,
            )
            LevelDropdown(
                selected = level,
                onSelect = { level = it },
                modifier = Modifier.width(140.dp),
            )
            OutlinedTextField(
                value = tag,
                onValueChange = { tag = it },
                modifier = Modifier.width(180.dp),
                placeholder = { Text("Tag") },
                singleLine = true,
            )
            OutlinedTextField(
                value = packageProcess,
                onValueChange = { packageProcess = it },
                modifier = Modifier.width(200.dp),
                placeholder = { Text("Package / Process") },
                singleLine = true,
            )
        }
    }
}

@Composable
private fun LevelDropdown(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selected, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(4.dp))
            Text("▾")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            LOG_LEVELS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
