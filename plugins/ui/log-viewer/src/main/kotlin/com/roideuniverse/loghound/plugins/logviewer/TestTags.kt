package com.roideuniverse.loghound.plugins.logviewer

/**
 * Stable test tags exposed by the Log Viewer UI. Treated as load-bearing for E2E tests —
 * renaming requires updating tests.
 */
object TestTags {
    const val LOG_LIST = "logViewer.logList"
    const val LOG_ROW = "logViewer.logRow"
    const val FILTER_INPUT = "logViewer.filterInput"
}
