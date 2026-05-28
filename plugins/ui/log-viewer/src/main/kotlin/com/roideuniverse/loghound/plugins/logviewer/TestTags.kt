package com.roideuniverse.loghound.plugins.logviewer

/**
 * Stable test tags exposed by the Log Viewer UI. Treated as load-bearing for E2E tests —
 * renaming requires updating tests.
 */
object TestTags {
    const val LOG_LIST = "logViewer.logList"
    const val LOG_ROW = "logViewer.logRow"
    const val LOG_ROW_DETAIL_META = "logViewer.logRowDetailMeta"
    const val LOG_ROW_DETAIL_MESSAGE = "logViewer.logRowDetailMessage"
    const val FILTER_INPUT = "logViewer.filterInput"
    const val FILTER_BUILDER_BUTTON = "logViewer.filterBuilderButton"
    const val FILTER_BUILDER_PANEL = "logViewer.filterBuilderPanel"
    const val FILTER_BUILDER_VALUE = "logViewer.filterBuilderValue"
    const val FILTER_BUILDER_APPLY = "logViewer.filterBuilderApply"
    const val JUMP_TO_BOTTOM = "logViewer.jumpToBottom"

    const val PACKAGE_LOOKUP_INPUT = "logViewer.packageLookupInput"
    const val PACKAGE_LOOKUP_FIND = "logViewer.packageLookupFind"
    const val PACKAGE_LOOKUP_RESULT_ROW = "logViewer.packageLookupResultRow"
    const val PACKAGE_LOOKUP_COPY_PACKAGE = "logViewer.packageLookupCopyPackage"
    const val PACKAGE_LOOKUP_COPY_UID = "logViewer.packageLookupCopyUid"

    const val LOADING_OLDER = "logViewer.loadingOlder"
}
