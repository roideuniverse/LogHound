package com.roideuniverse.loghound.plugindsl

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

data class TabRef(val id: String, val name: String)

class TabController internal constructor(val masterName: String) {
    private val _openTabs: MutableState<List<TabRef>> = mutableStateOf(emptyList())
    private val _activeId: MutableState<String?> = mutableStateOf(null)

    /** Read-only snapshot of currently open tabs. Plugins should use [open]/[close] to mutate. */
    val openTabs: State<List<TabRef>> = _openTabs

    /** Read-only snapshot of the active tab id, or null if the master tab is selected. */
    val activeId: State<String?> = _activeId

    fun open(id: String, name: String) {
        if (_openTabs.value.none { it.id == id }) {
            _openTabs.value = _openTabs.value + TabRef(id, name)
        }
        _activeId.value = id
    }

    fun close(id: String) {
        val before = _openTabs.value
        val idx = before.indexOfFirst { it.id == id }
        if (idx < 0) return
        val after = before.toMutableList().also { it.removeAt(idx) }
        _openTabs.value = after
        if (_activeId.value == id) {
            _activeId.value = after.getOrNull(idx - 1)?.id ?: after.firstOrNull()?.id
        }
    }

    fun activate(id: String?) {
        _activeId.value = id
    }
}

fun tabController(masterName: String): TabController = TabController(masterName)
