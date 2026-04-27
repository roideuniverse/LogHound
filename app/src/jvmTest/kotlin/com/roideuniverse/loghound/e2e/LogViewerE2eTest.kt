package com.roideuniverse.loghound.e2e

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import com.roideuniverse.loghound.core.LogPriority
import com.roideuniverse.loghound.plugins.logviewer.LogViewerPlugin
import com.roideuniverse.loghound.plugins.logviewer.TestTags
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LogViewerE2eTest {

    @get:Rule val tmp = TemporaryFolder()
    @get:Rule val compose = createComposeRule()

    /** Pads to 3 digits so substrings like "msg-001" don't accidentally match "msg-010". */
    private fun pad(i: Int): String = i.toString().padStart(3, '0')

    @Test
    fun renders_appended_entries_and_db_matches_inputs() {
        val env = E2eEnv(tmp.newFolder())
        val inputs = (1..30).map { i ->
            makeEntry(message = "viewer-msg-${pad(i)}", priority = LogPriority.Info)
        }
        runBlocking { env.repository.append(inputs) }

        compose.setContent {
            LogViewerPlugin(env.repository).content(Modifier.fillMaxSize())
        }

        // Wait until at least one row is realised in the semantics tree.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(TestTags.LOG_ROW).fetchSemanticsNodes().isNotEmpty()
        }

        // DB cross-check: every appended message is persisted, in chronological order.
        val persisted = runBlocking { env.repository.query(limit = 1_000).entries }
        assertEquals(inputs.size, persisted.size)
        assertEquals(inputs.map { it.message }, persisted.map { it.message })

        // UI cross-check: the most recent row (auto-scroll keeps the tail visible) is in
        // the tree. "viewer-msg-030" is unique — only one row has it as a substring.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("viewer-msg-030", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("viewer-msg-030", substring = true).assertExists()
    }

    @Test
    fun scrolling_up_to_top_shows_oldest_entries() {
        val env = E2eEnv(tmp.newFolder())
        val inputs = (1..200).map { i -> makeEntry(message = "row-${pad(i)}") }
        runBlocking { env.repository.append(inputs) }

        compose.setContent {
            LogViewerPlugin(env.repository).content(Modifier.fillMaxSize())
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(TestTags.LOG_ROW).fetchSemanticsNodes().isNotEmpty()
        }

        // Scroll to top; the very first row "row-001" must enter the realised set.
        compose.onNodeWithTag(TestTags.LOG_LIST).performScrollToIndex(0)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("row-001", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("row-001", substring = true).assertExists()
    }

    @Test
    fun live_appended_batch_appears_in_ui() {
        val env = E2eEnv(tmp.newFolder())
        runBlocking {
            env.repository.append((1..5).map { makeEntry(message = "initial-${pad(it)}") })
        }

        compose.setContent {
            LogViewerPlugin(env.repository).content(Modifier.fillMaxSize())
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("initial-005", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        runBlocking {
            env.repository.append(listOf(makeEntry(message = "live-arrival-XYZ")))
        }

        // Auto-scroll keeps us at bottom; "live-arrival-XYZ" must appear.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("live-arrival-XYZ", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("live-arrival-XYZ", substring = true).assertExists()
    }

    @Test
    fun typing_filter_restricts_visible_rows_to_matches() {
        val env = E2eEnv(tmp.newFolder())
        val inputs = listOf(
            makeEntry(message = "INFO_LINE_alpha", priority = LogPriority.Info),
            makeEntry(message = "WARN_LINE_beta", priority = LogPriority.Warn),
            makeEntry(message = "ERR_LINE_gamma", priority = LogPriority.Error),
            makeEntry(message = "INFO_LINE_delta", priority = LogPriority.Info),
            makeEntry(message = "WARN_LINE_epsilon", priority = LogPriority.Warn),
        )
        runBlocking { env.repository.append(inputs) }

        compose.setContent {
            LogViewerPlugin(env.repository).content(Modifier.fillMaxSize())
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("WARN_LINE_epsilon", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Apply level:W → only Warn + Error remain (3 rows).
        compose.onNodeWithTag(TestTags.FILTER_INPUT).performTextInput("level:W")

        // Wait until INFO rows have been removed from the realised tree.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("INFO_LINE_alpha", substring = true)
                .fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("INFO_LINE_alpha", substring = true).assertDoesNotExist()
        compose.onNodeWithText("INFO_LINE_delta", substring = true).assertDoesNotExist()
        compose.onNodeWithText("WARN_LINE_beta", substring = true).assertExists()
        compose.onNodeWithText("ERR_LINE_gamma", substring = true).assertExists()
        compose.onNodeWithText("WARN_LINE_epsilon", substring = true).assertExists()
    }
}
