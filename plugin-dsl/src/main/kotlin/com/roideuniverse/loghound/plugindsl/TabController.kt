package com.roideuniverse.loghound.plugindsl

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

data class TabRef(val id: String, val name: String)

class TabController internal constructor(val masterName: String) {
    internal val openTabs: MutableState<List<TabRef>> = mutableStateOf(emptyList())
    internal val activeId: MutableState<String?> = mutableStateOf(null)

    fun open(id: String, name: String) {
        if (openTabs.value.none { it.id == id }) {
            openTabs.value = openTabs.value + TabRef(id, name)
        }
        activeId.value = id
    }

    fun close(id: String) {
        val before = openTabs.value
        val idx = before.indexOfFirst { it.id == id }
        if (idx < 0) return
        val after = before.toMutableList().also { it.removeAt(idx) }
        openTabs.value = after
        if (activeId.value == id) {
            activeId.value = after.getOrNull(idx - 1)?.id ?: after.firstOrNull()?.id
        }
    }

    fun activate(id: String?) {
        activeId.value = id
    }
}

fun tabController(masterName: String): TabController = TabController(masterName)
