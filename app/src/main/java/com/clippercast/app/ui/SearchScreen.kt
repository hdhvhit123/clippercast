package com.clippercast.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.clippercast.app.ClipperApp
import com.clippercast.app.data.Article
import com.clippercast.app.data.TextUtils
import kotlinx.coroutines.flow.flowOf

/** Tìm kiếm toàn văn, gõ không dấu vẫn ra kết quả có dấu (spec 2.4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, openArticle: (Long) -> Unit) {
    val app = LocalContext.current.applicationContext as ClipperApp
    var q by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val flow = remember(q) {
        val match = TextUtils.ftsQuery(q)
        if (match == null) flowOf(emptyList<Article>()) else app.repo.articles.search(match)
    }
    val results by flow.collectAsState(initial = emptyList())
    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
            title = {
                TextField(
                    q, { q = it }, placeholder = { Text("Tìm trong tiêu đề và nội dung…") }, singleLine = true,
                    modifier = Modifier.focusRequester(focus),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                )
            },
            actions = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Outlined.Close, "Xóa") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when {
                q.isBlank() -> EmptyState("🔎", "Tìm trong thư viện", "Gõ không dấu vẫn tìm được: \"kinh te\" sẽ ra \"kinh tế\".")
                results.isEmpty() -> EmptyState("🤔", "Không tìm thấy \"$q\"", "Thử từ khóa khác ngắn hơn.")
                else -> LazyColumn {
                    item { Text("${results.size} kết quả", Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge) }
                    items(results, key = { it.id }) { a ->
                        ArticleCard(a, compact = true, speed = 1f, onClick = { openArticle(a.id) }, onLongClick = { openArticle(a.id) })
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}
