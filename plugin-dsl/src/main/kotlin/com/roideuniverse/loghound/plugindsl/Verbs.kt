package com.roideuniverse.loghound.plugindsl

import androidx.compose.runtime.MutableState
import androidx.compose.ui.text.TextStyle

internal sealed interface Verb

internal class ColumnVerb(val children: List<Verb>) : Verb
internal class RowVerb(val children: List<Verb>) : Verb
internal class SectionVerb(val children: List<Verb>) : Verb
internal class CenteredVerb(val children: List<Verb>) : Verb
internal class TextVerb(val value: String, val style: TextStyle?) : Verb
internal class SpacerVerb(val widthDp: Int, val heightDp: Int) : Verb
internal class DividerVerb : Verb
internal class LoadingVerb : Verb
internal class ButtonVerb(val label: String, val onClick: () -> Unit) : Verb
internal class WeightVerb(val weight: Float, val children: List<Verb>) : Verb
internal class ListVerb<T>(
    val items: List<T>,
    val key: (T) -> Any,
    val itemContent: (T) -> List<Verb>,
) : Verb

internal class TextFieldVerb(
    val state: MutableState<String>,
    val placeholder: String,
) : Verb

internal class ClickableVerb(
    val onClick: () -> Unit,
    val children: List<Verb>,
) : Verb

internal class TabsVerb(
    val controller: TabController,
    val master: UiScope.() -> Unit,
    val detail: UiScope.(String) -> Unit,
) : Verb

@PluginDslMarker
class UiScope internal constructor() {
    internal val verbs = mutableListOf<Verb>()

    fun column(content: UiScope.() -> Unit) {
        val child = UiScope()
        child.content()
        verbs.add(ColumnVerb(child.verbs.toList()))
    }

    fun row(content: UiScope.() -> Unit) {
        val child = UiScope()
        child.content()
        verbs.add(RowVerb(child.verbs.toList()))
    }

    fun section(content: UiScope.() -> Unit) {
        val child = UiScope()
        child.content()
        verbs.add(SectionVerb(child.verbs.toList()))
    }

    fun centered(content: UiScope.() -> Unit) {
        val child = UiScope()
        child.content()
        verbs.add(CenteredVerb(child.verbs.toList()))
    }

    fun loading() {
        verbs.add(LoadingVerb())
    }

    fun text(value: String, style: TextStyle? = null) {
        verbs.add(TextVerb(value, style))
    }

    fun spacer(width: Int = 0, height: Int = 0) {
        verbs.add(SpacerVerb(width, height))
    }

    fun divider() {
        verbs.add(DividerVerb())
    }

    fun button(label: String, onClick: () -> Unit) {
        verbs.add(ButtonVerb(label, onClick))
    }

    fun weight(weight: Float, content: UiScope.() -> Unit) {
        val child = UiScope()
        child.content()
        verbs.add(WeightVerb(weight, child.verbs.toList()))
    }

    fun <T> list(
        items: List<T>,
        key: (T) -> Any,
        itemContent: UiScope.(T) -> Unit,
    ) {
        verbs.add(
            ListVerb(items, key) { item ->
                val child = UiScope()
                child.itemContent(item)
                child.verbs.toList()
            }
        )
    }

    fun textField(state: MutableState<String>, placeholder: String = "") {
        verbs.add(TextFieldVerb(state, placeholder))
    }

    fun clickable(onClick: () -> Unit, content: UiScope.() -> Unit) {
        val child = UiScope()
        child.content()
        verbs.add(ClickableVerb(onClick, child.verbs.toList()))
    }

    fun tabs(
        controller: TabController,
        master: UiScope.() -> Unit,
        detail: UiScope.(String) -> Unit,
    ) {
        verbs.add(TabsVerb(controller, master, detail))
    }
}
