package com.roideuniverse.loghound.plugins.uuidgrouping

/**
 * Stable test tags exposed by the UUID Grouping UI. Treated as load-bearing for E2E tests —
 * renaming requires updating tests.
 */
object UuidTestTags {
    const val UUID_LIST = "uuidGrouping.uuidList"
    const val UUID_ROW = "uuidGrouping.uuidRow"

    const val UUID_TAB = "uuidGrouping.tab"
    const val UUID_TAB_CLOSE = "uuidGrouping.tabClose"

    const val UUID_DETAIL_LIST = "uuidGrouping.detailList"
    const val UUID_DETAIL_ROW = "uuidGrouping.detailRow"
    const val UUID_DETAIL_LOADING = "uuidGrouping.detailLoading"
    const val UUID_DETAIL_EMPTY = "uuidGrouping.detailEmpty"
    const val UUID_DETAIL_LOADING_OLDER = "uuidGrouping.detailLoadingOlder"
}
