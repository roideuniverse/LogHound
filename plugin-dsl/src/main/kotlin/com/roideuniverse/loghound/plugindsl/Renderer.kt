package com.roideuniverse.loghound.plugindsl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
internal fun renderVerbs(verbs: List<Verb>) {
    verbs.forEach { verb ->
        when (verb) {
            is ColumnVerb -> Column { renderVerbs(verb.children) }
            is TextVerb -> Text(verb.value)
            is ListVerb<*> -> RenderList(verb)
        }
    }
}

@Composable
private fun RenderList(verb: ListVerb<*>) {
    @Suppress("UNCHECKED_CAST")
    val v = verb as ListVerb<Any?>
    LazyColumn {
        items(items = v.items, key = { item -> v.key(item) }) { item ->
            renderVerbs(v.itemContent(item))
        }
    }
}
