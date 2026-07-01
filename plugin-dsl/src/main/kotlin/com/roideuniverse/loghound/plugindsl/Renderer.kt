package com.roideuniverse.loghound.plugindsl

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun renderVerbs(verbs: List<Verb>) {
    val theme = LocalPluginTheme.current
    verbs.forEach { verb ->
        when (verb) {
            is ColumnVerb -> Column { renderColumnChildren(verb.children) }
            is RowVerb ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    renderRowChildren(verb.children)
                }
            is SectionVerb -> RenderSection(verb, theme)
            is CenteredVerb ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        renderVerbs(verb.children)
                    }
                }
            is TextVerb ->
                Text(
                    verb.value,
                    style = verb.style ?: LocalTextStyle.current,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            is SpacerVerb -> RenderSpacer(verb)
            is DividerVerb -> HorizontalDivider(color = theme.divider)
            is LoadingVerb ->
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(24.dp))
            is ButtonVerb ->
                TextButton(onClick = verb.onClick) { Text(verb.label, style = theme.buttonText) }
            is WeightVerb -> renderVerbs(verb.children) // weight has no effect outside Row/Column
            is ListVerb<*> -> RenderList(verb, theme)
            is TextFieldVerb -> RenderTextField(verb, theme)
            is ClickableVerb -> RenderClickable(verb)
            is TabsVerb -> RenderTabs(verb, theme)
        }
    }
}

@Composable
private fun RowScope.renderRowChildren(verbs: List<Verb>) {
    verbs.forEach { verb ->
        if (verb is WeightVerb) {
            Box(modifier = Modifier.weight(verb.weight)) { renderVerbs(verb.children) }
        } else {
            renderVerbs(listOf(verb))
        }
    }
}

@Composable
private fun ColumnScope.renderColumnChildren(verbs: List<Verb>) {
    verbs.forEach { verb ->
        if (verb is WeightVerb) {
            Box(modifier = Modifier.weight(verb.weight)) { renderVerbs(verb.children) }
        } else {
            renderVerbs(listOf(verb))
        }
    }
}

@Composable
private fun RenderSpacer(verb: SpacerVerb) {
    var m: Modifier = Modifier
    if (verb.widthDp > 0) m = m.width(verb.widthDp.dp)
    if (verb.heightDp > 0) m = m.height(verb.heightDp.dp)
    Spacer(modifier = m)
}

@Composable
private fun RenderSection(verb: SectionVerb, theme: PluginTheme) {
    Surface(modifier = Modifier.fillMaxWidth(), color = theme.toolbarBackground) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            renderColumnChildren(verb.children)
        }
    }
}

@Composable
private fun RenderList(verb: ListVerb<*>, theme: PluginTheme) {
    @Suppress("UNCHECKED_CAST") val v = verb as ListVerb<Any?>
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        SelectionContainer(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(items = v.items, key = { item -> v.key(item) }) { item ->
                    CompositionLocalProvider(LocalTextStyle provides theme.rowText) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                renderVerbs(v.itemContent(item))
                            }
                            HorizontalDivider(color = theme.rowDivider)
                        }
                    }
                }
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(listState),
        )
    }
}

@Composable
private fun RenderTextField(verb: TextFieldVerb, theme: PluginTheme) {
    val state by verb.state
    val shape = RoundedCornerShape(4.dp)
    BasicTextField(
        value = state,
        onValueChange = { verb.state.value = it },
        singleLine = true,
        textStyle = theme.textFieldText,
        modifier =
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(theme.textFieldBackground)
                .border(1.dp, theme.textFieldBorder, shape)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        decorationBox = { inner ->
            Box {
                if (state.isEmpty()) {
                    Text(
                        verb.placeholder,
                        color = theme.placeholderColor,
                        style = theme.textFieldText,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun RenderClickable(verb: ClickableVerb) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = verb.onClick)) {
        renderVerbs(verb.children)
    }
}

@Composable
private fun RenderTabs(verb: TabsVerb, theme: PluginTheme) {
    val controller = verb.controller
    val activeId by controller.activeId
    val openTabs by controller.openTabs

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            color = theme.tabStripBackground,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabPill(
                    text = controller.masterName,
                    selected = activeId == null,
                    onClick = { controller.activate(null) },
                    onClose = null,
                    theme = theme,
                )
                openTabs.forEach { tab ->
                    TabPill(
                        text = tab.name,
                        selected = activeId == tab.id,
                        onClick = { controller.activate(tab.id) },
                        onClose = { controller.close(tab.id) },
                        theme = theme,
                    )
                }
            }
        }
        HorizontalDivider(color = theme.divider)
        Box(modifier = Modifier.fillMaxSize()) {
            val scope = UiScope()
            val active = activeId
            if (active == null) {
                verb.master.invoke(scope)
            } else {
                verb.detail.invoke(scope, active)
            }
            renderVerbs(scope.verbs)
        }
    }
}

@Composable
private fun TabPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    onClose: (() -> Unit)?,
    theme: PluginTheme,
) {
    val bg = if (selected) theme.activeTabBackground else Color.Transparent
    Row(
        modifier =
            Modifier.padding(horizontal = 2.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(bg)
                .clickable { onClick() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = theme.tabText)
        if (onClose != null) {
            Spacer(Modifier.width(6.dp))
            Text("✕", style = theme.closeText, modifier = Modifier.clickable { onClose() })
        }
    }
}
