package com.roideuniverse.loghound.e2e

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.roideuniverse.loghound.plugins.uuidgrouping.UuidGroupingPlugin
import java.io.File
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalTestApi::class)
class UuidGroupingE2eTest {

    @get:Rule val tmp = TemporaryFolder()
    @get:Rule val compose = createComposeRule()

    @Test
    fun ui_shows_uuids_matching_appended_entries() {
        val env = E2eEnv(tmp.newFolder())
        // Three distinguishable UUIDs (each is unique as a substring).
        val u1 = "11111111-1111-1111-1111-111111111111"
        val u2 = "22222222-2222-2222-2222-222222222222"
        val u3 = "33333333-3333-3333-3333-333333333333"

        // u1 ×5, u2 ×3, u3 ×1, plus 4 unrelated rows.
        val inputs = buildList {
            repeat(5) { add(makeEntry(message = "op started req=$u1")) }
            repeat(3) { add(makeEntry(message = "session=$u2 active")) }
            add(makeEntry(message = "trace=$u3 flushed"))
            repeat(4) { add(makeEntry(message = "no uuid here line $it")) }
        }
        runBlocking { env.repository.append(inputs) }

        val pluginDbFile = File(env.pluginDbDir, "uuid-grouping.db")
        val plugin = UuidGroupingPlugin(pluginDbFile, env.repository)
        val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        bgScope.launch { plugin.run(env.repository) }

        // Drain backfill — busy-yield until scanning is false and ≥3 UUIDs recorded.
        // Determinism: we never sleep; we only yield until a state predicate flips.
        runBlocking {
            while (true) {
                val p = plugin.progress.value
                if (!p.scanning && p.uuidCount >= 3L) break
                yield()
            }
        }

        compose.setContent { plugin.content(Modifier.fillMaxSize()) }

        // Wait until each UUID is realised in the LazyColumn's visible range.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(u1, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Each UUID is rendered as a row.
        compose.onAllNodesWithText(u1, substring = true)[0].assertExists()
        compose.onAllNodesWithText(u2, substring = true)[0].assertExists()
        compose.onAllNodesWithText(u3, substring = true)[0].assertExists()

        // Cross-check the main logs DB still has every entry we appended.
        val persisted = runBlocking { env.repository.query(limit = 10_000).entries }
        assertEquals(inputs.size, persisted.size)

        bgScope.cancel()
    }
}
