package com.roideuniverse.loghound.plugins.logviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal data class PackageInfo(val packageName: String, val uid: Int)

@Composable
internal fun PackageUidLookupBar(modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PackageInfo>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    fun runLookup() {
        scope.launch {
            loading = true
            status = null
            val outcome = lookupPackageUids(query)
            loading = false
            when (outcome) {
                is LookupOutcome.Success -> {
                    results = outcome.packages
                    status = if (outcome.packages.isEmpty()) "No matching packages" else null
                }
                LookupOutcome.NoDevice -> {
                    results = emptyList()
                    status = "No device"
                }
                LookupOutcome.NoAdb -> {
                    results = emptyList()
                    status = "adb not found"
                }
            }
        }
    }

    Surface(modifier = modifier.fillMaxWidth(), color = Color(0xFFFAFAFA)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LookupInput(
                    value = query,
                    onChange = { query = it },
                    onSubmit = ::runLookup,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = ::runLookup,
                    enabled = !loading && query.isNotBlank(),
                    modifier = Modifier.testTag(TestTags.PACKAGE_LOOKUP_FIND),
                ) {
                    Text(if (loading) "Finding…" else "Find UID")
                }
            }
            status?.let { msg ->
                Text(
                    text = msg,
                    color = Color(0xFF888888),
                    style = TextStyle(fontSize = 12.sp),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (results.isNotEmpty()) {
                Spacer(Modifier.padding(top = 2.dp))
                results.forEach { row -> ResultRow(row, clipboard::setText) }
            }
        }
    }
}

@Composable
private fun LookupInput(
    value: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(4.dp)
    val style: TextStyle = LocalTextStyle.current.copy(color = Color.Black, fontSize = 13.sp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = style,
        modifier = modifier
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Color(0xFFCCCCCC), shape)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(TestTags.PACKAGE_LOOKUP_INPUT),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        "Find UID by package, e.g. com.app.debug",
                        color = Color(0xFFAAAAAA),
                        style = style,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun ResultRow(row: PackageInfo, copy: (AnnotatedString) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .testTag(TestTags.PACKAGE_LOOKUP_RESULT_ROW),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.packageName,
            modifier = Modifier.weight(1f),
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF222222)),
        )
        Text(
            text = "uid ${row.uid}",
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF555555)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Copy",
            style = TextStyle(fontSize = 12.sp, color = Color(0xFF1976D2)),
            modifier = Modifier
                .clickable { copy(AnnotatedString(row.uid.toString())) }
                .testTag(TestTags.PACKAGE_LOOKUP_COPY)
                .padding(horizontal = 4.dp),
        )
    }
    HorizontalDivider(color = Color(0xFFEEEEEE))
}

internal sealed class LookupOutcome {
    data class Success(val packages: List<PackageInfo>) : LookupOutcome()
    data object NoAdb : LookupOutcome()
    data object NoDevice : LookupOutcome()
}

private val PACKAGE_LINE_REGEX = Regex("""^package:(\S+)\s+uid:(\d+)$""")

internal suspend fun lookupPackageUids(query: String): LookupOutcome = withContext(Dispatchers.IO) {
    val adb = findAdbExecutable() ?: return@withContext LookupOutcome.NoAdb
    if (!hasAnyDevice(adb)) return@withContext LookupOutcome.NoDevice
    val proc = ProcessBuilder(adb, "shell", "pm", "list", "packages", "-U", query)
        .redirectErrorStream(true)
        .start()
    val out = proc.inputStream.bufferedReader().readText()
    proc.waitFor()
    val packages = parsePackageUidLines(out)
    LookupOutcome.Success(packages)
}

internal fun parsePackageUidLines(output: String): List<PackageInfo> {
    return output.lineSequence()
        .mapNotNull { line ->
            val m = PACKAGE_LINE_REGEX.matchEntire(line.trim()) ?: return@mapNotNull null
            val uid = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            PackageInfo(packageName = m.groupValues[1], uid = uid)
        }
        .toList()
}

private fun hasAnyDevice(adb: String): Boolean {
    return try {
        val proc = ProcessBuilder(adb, "devices").redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        out.lineSequence().drop(1).any { it.trim().endsWith("\tdevice") }
    } catch (_: Exception) {
        false
    }
}

// Duplicated from LogcatDataPlugin's resolver. On third use this gets extracted to a
// shared helper module; today (second use) the duplication is a deliberate trade.
private fun findAdbExecutable(): String? {
    val sdkRoot = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    if (sdkRoot != null) {
        val adb = File(sdkRoot, "platform-tools/adb")
        if (adb.canExecute()) return adb.absolutePath
    }
    val macDefault = File(
        System.getProperty("user.home"),
        "Library/Android/sdk/platform-tools/adb",
    )
    if (macDefault.canExecute()) return macDefault.absolutePath
    return "adb"
}
