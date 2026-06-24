package com.roideuniverse.loghound.e2e

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.roideuniverse.loghound.plugins.logviewer.LogViewerPlugin
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalTestApi::class)
class LogViewerE2eTest {

    @get:Rule val tmp = TemporaryFolder()
    @get:Rule val compose = createComposeRule()

    /** Pads to 3 digits so substrings like "msg-001" don't accidentally match "msg-010". */
    private fun pad(i: Int): String = i.toString().padStart(3, '0')

    /**
     * Pumps a frame, lets idle work settle, then yields real time so a LaunchedEffect that
     * resumes after withContext(Dispatchers.IO) can post its state change back before we poll.
     */
    private fun pumpUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            if (condition()) return
            Thread.sleep(10)
        }
        if (!condition()) throw AssertionError("Condition not met within ${timeoutMillis}ms")
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
        pumpUntil {
            compose.onAllNodesWithText("initial-005", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        runBlocking {
            env.repository.append(listOf(makeEntry(message = "live-arrival-XYZ")))
        }

        // Auto-scroll keeps us at bottom; "live-arrival-XYZ" must appear.
        pumpUntil {
            compose.onAllNodesWithText("live-arrival-XYZ", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("live-arrival-XYZ", substring = true).assertExists()
    }
}
