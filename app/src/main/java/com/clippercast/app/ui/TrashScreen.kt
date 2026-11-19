package com.clippercast.app.ui

import com.clippercast.app.tr
import com.clippercast.app.R

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.clippercast.app.ClipperApp
import kotlinx.coroutines.launch

/** Thùng rác: giữ 30 ngày trước khi xóa hẳn (spec 4, 7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as ClipperApp
    val scope = rememberCoroutineScope()
    val items by app.repo.articles.trash().collectAsState(initial = emptyList())
    var confirm by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(tr(R.string.trash)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.back)) } },
            actions = { if (items.isNotEmpty()) TextButton(onClick = { confirm = true }) { Text(tr(R.string.empty_trash)) } },
        )
    }) { pad ->
        if (items.isEmpty()) {
            Row(Modifier.padding(pad)) { EmptyState("🗑️", tr(R.string.trash_empty_title), tr(R.string.trash_empty_body)) }
        } else LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            items(items, key = { it.id }) { a ->
                ArticleCard(a, compact = true, speed = 1f, onClick = {}, onLongClick = {}, trailing = {
                    Row {
                        IconButton(onClick = { scope.launch { app.repo.restore(a.id) } }) { Icon(Icons.Outlined.RestoreFromTrash, tr(R.string.restore_item)) }
                        IconButton(onClick = { scope.launch { app.repo.deleteForever(a.id) } }) { Icon(Icons.Outlined.DeleteForever, tr(R.string.delete_forever)) }
                    }
                })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(tr(R.string.delete_forever_q, items.size)) },
        text = { Text(tr(R.string.cannot_undo)) },
        confirmButton = { TextButton(onClick = { confirm = false; scope.launch { app.repo.emptyTrash() } }) { Text(tr(R.string.delete_forever)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr(R.string.cancel)) } },
    )
}
