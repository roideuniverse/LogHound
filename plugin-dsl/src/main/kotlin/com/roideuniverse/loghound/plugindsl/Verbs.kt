package com.roideuniverse.loghound.plugindsl

internal sealed interface Verb

internal class ColumnVerb(val children: List<Verb>) : Verb
internal class TextVerb(val value: String) : Verb
internal class ListVerb<T>(
    val items: List<T>,
    val key: (T) -> Any,
    val itemContent: (T) -> List<Verb>,
) : Verb

@PluginDslMarker
class UiScope internal constructor() {
    internal val verbs = mutableListOf<Verb>()

    fun column(content: UiScope.() -> Unit) {
        val child = UiScope()
        child.content()
        verbs.add(ColumnVerb(child.verbs.toList()))
    }

    fun text(value: String) {
        verbs.add(TextVerb(value))
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
}
