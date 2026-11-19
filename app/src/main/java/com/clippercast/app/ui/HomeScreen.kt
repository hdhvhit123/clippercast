package com.clippercast.app.ui

import com.clippercast.app.tr
import com.clippercast.app.R

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.dp
import com.clippercast.app.ClipperApp
import com.clippercast.app.clip.UrlNormalizer
import com.clippercast.app.data.AppSettings
import com.clippercast.app.data.Article
import com.clippercast.app.data.ClipStatus
import com.clippercast.app.data.TextUtils
import com.clippercast.app.player.Playback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class HomeTab(val labelRes: Int, val icon: ImageVector) {
    INBOX(R.string.tab_inbox, Icons.Outlined.Inbox),
    QUEUE(R.string.tab_queue, Icons.Outlined.Headphones),
    ARCHIVE(R.string.tab_archive, Icons.Outlined.Archive);

    val label: String get() = tr(labelRes)
}

data class HomeNav(
    val openArticle: (Long) -> Unit,
    val listen: (Long) -> Unit,
    val openPlayer: () -> Unit,
    val search: () -> Unit,
    val settings: () -> Unit,
    val trash: () -> Unit,
    val browser: (Long) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: HomeNav, initialAdd: Boolean = false) {
    val context = LocalContext.current
    val app = context.applicationContext as ClipperApp
    val repo = app.repo
    val scope = rememberCoroutineScope()
    val settings by app.settings.flow.collectAsState(initial = AppSettings())
    val play by Playback.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(HomeTab.INBOX) }
    var filter by rememberSaveable { mutableStateOf(0) } // 0 tất cả, 1 yêu thích, 2 cần xử lý
    var showAdd by rememberSaveable { mutableStateOf(initialAdd) }
    var sheetFor by remember { mutableStateOf<Article?>(null) }
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val online = rememberOnline()

    val inbox by repo.articles.inbox(settings.sort, filter == 1, filter == 2).collectAsState(initial = null)
    val archive by repo.articles.archive(filter == 1).collectAsState(initial = null)
    val queue by repo.queue().collectAsState(initial = emptyList())

    fun undoable(message: String, action: suspend () -> Unit, undo: suspend () -> Unit) {
        scope.launch {
            action()
            val r = snackbar.showSnackbar(message, actionLabel = tr(R.string.undo), duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) undo()
        }
    }
    val actions = ArticleActions(
        archive = { a -> undoable(tr(R.string.moved_to_archive), { repo.setArchived(a.id, true) }, { repo.setArchived(a.id, false) }) },
        unarchive = { a -> scope.launch { repo.setArchived(a.id, false); snackbar.showSnackbar(tr(R.string.moved_to_inbox)) } },
        delete = { a -> undoable(tr(R.string.moved_to_trash), { repo.moveToTrash(a.id) }, { repo.restore(a.id) }) },
        enqueue = { a ->
            scope.launch {
                if (!ClipStatus.isReadable(a.status)) { snackbar.showSnackbar(tr(R.string.not_downloaded_yet)); return@launch }
                if (repo.inQueue(a.id)) snackbar.showSnackbar(tr(R.string.already_in_queue))
                else { repo.enqueueEnd(a.id); snackbar.showSnackbar(tr(R.string.added_to_queue)) }
            }
        },
        playNext = { a -> scope.launch { repo.playNext(a.id, play.article?.id); snackbar.showSnackbar(tr(R.string.will_play_next)) } },
        favorite = { a -> scope.launch { repo.setFavorite(a.id, !a.favorite) } },
        retry = { a -> scope.launch { repo.retry(a.id) } },
        openOriginal = { a -> if (a.url.isNotBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.url))) },
    )

    // Gợi ý lưu link vừa sao chép (tùy chọn, spec 2.5)
    LaunchedEffect(settings.clipboardSuggest) {
        if (!settings.clipboardSuggest) return@LaunchedEffect
        delay(600)
        val cm = context.getSystemService(ClipboardManager::class.java)
        val text = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
        val url = UrlNormalizer.extractUrl(text) ?: return@LaunchedEffect
        if (repo.articles.findByUrl(UrlNormalizer.normalize(url)) != null) return@LaunchedEffect
        val r = snackbar.showSnackbar(tr(R.string.save_copied_link), actionLabel = tr(R.string.save), duration = SnackbarDuration.Long)
        if (r == SnackbarResult.ActionPerformed) repo.saveUrl(url)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tab.label) },
                actions = {
                    IconButton(onClick = nav.search) { Icon(Icons.Outlined.Search, tr(R.string.search)) }
                    if (tab == HomeTab.INBOX) {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, tr(R.string.sort)) }
                        DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf(R.string.sort_newest, R.string.sort_oldest, R.string.sort_shortest, R.string.sort_longest, R.string.sort_source).map { tr(it) }.forEachIndexed { i, s ->
                                DropdownMenuItem(text = { Text((if (settings.sort == i) "✓ " else "    ") + s) }, onClick = {
                                    sortMenu = false; scope.launch { app.settings.update { it.copy(sort = i) } }
                                })
                            }
                        }
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, tr(R.string.more)) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (settings.compactList) tr(R.string.view_magazine) else tr(R.string.view_compact)) },
                            leadingIcon = { Icon(Icons.Outlined.ViewAgenda, null) },
                            onClick = { menu = false; scope.launch { app.settings.update { it.copy(compactList = !it.compactList) } } })
                        DropdownMenuItem(text = { Text(tr(R.string.trash)) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = { menu = false; nav.trash() })
                        DropdownMenuItem(text = { Text(tr(R.string.settings)) }, leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                            onClick = { menu = false; nav.settings() })
                    }
                },
            )
        },
        floatingActionButton = {
            if (tab != HomeTab.QUEUE) FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, tr(R.string.add_article)) }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                MiniPlayer(play, onOpen = nav.openPlayer, onToggle = { Playback.toggle() }, onNext = { Playback.nextArticle() })
                NavigationBar {
                    HomeTab.entries.forEach { t ->
                        NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, null) },
                            label = { Text(if (t == HomeTab.QUEUE && queue.isNotEmpty()) "${t.label} (${queue.size})" else t.label) })
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!online) {
                Text(
                    tr(R.string.offline_banner),
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            when (tab) {
                HomeTab.INBOX, HomeTab.ARCHIVE -> {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val labels = if (tab == HomeTab.INBOX) listOf(tr(R.string.all), tr(R.string.favorite), tr(R.string.filter_needs_attention)) else listOf(tr(R.string.all), tr(R.string.favorite))
                        itemsIndexed(labels) { i, l -> FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(l) }) }
                    }
                    val list = if (tab == HomeTab.INBOX) inbox else archive
                    var refreshing by remember { mutableStateOf(false) }
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = { scope.launch { refreshing = true; repo.retryAll(); delay(800); refreshing = false } },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when {
                            list == null -> Unit
                            list.isEmpty() && tab == HomeTab.INBOX && filter == 0 -> InboxEmpty(
                                firstTime = archive.isNullOrEmpty(),
                                onSample = { scope.launch { val id = SampleArticle.insert(app); nav.openArticle(id) } },
                                onArchive = { tab = HomeTab.ARCHIVE },
                            )
                            list.isEmpty() -> EmptyState("🗂️", tr(R.string.empty_list_title), if (filter == 2) tr(R.string.empty_problems) else tr(R.string.empty_list))
                            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                                items(list, key = { it.id }) { a ->
                                    SwipeRow(
                                        a = a, archiveTab = tab == HomeTab.ARCHIVE, compact = settings.compactList,
                                        speed = settings.speed, actions = actions,
                                        onClick = {
                                            when {
                                                ClipStatus.isReadable(a.status) -> nav.openArticle(a.id)
                                                else -> sheetFor = a
                                            }
                                        },
                                        onLongClick = { sheetFor = a },
                                    )
                                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                                }
                            }
                        }
                    }
                }
                HomeTab.QUEUE -> QueueList(queue, play.article?.id, settings.speed, nav, onAddLatest = {
                    scope.launch {
                        inbox.orEmpty().filter { ClipStatus.isReadable(it.status) }.take(5).forEach { repo.enqueueEnd(it.id) }
                    }
                })
            }
        }
    }

    sheetFor?.let { a ->
        ArticleActionSheet(a, actions, nav, onDismiss = { sheetFor = null })
    }
    if (showAdd) AddDialog(onDismiss = { showAdd = false }, onSaved = { msg -> scope.launch { snackbar.showSnackbar(msg) } })
}

data class ArticleActions(
    val archive: (Article) -> Unit,
    val unarchive: (Article) -> Unit,
    val delete: (Article) -> Unit,
    val enqueue: (Article) -> Unit,
    val playNext: (Article) -> Unit,
    val favorite: (Article) -> Unit,
    val retry: (Article) -> Unit,
    val openOriginal: (Article) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(
    a: Article, archiveTab: Boolean, compact: Boolean, speed: Float, actions: ArticleActions,
    onClick: () -> Unit, onLongClick: () -> Unit,
) {
    // Vuốt phải: Đã đọc / Lưu trữ. Vuốt trái: thêm vào hàng chờ nghe. Không xóa bằng vuốt (spec 4).
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.StartToEnd -> if (archiveTab) actions.unarchive(a) else actions.archive(a)
            SwipeToDismissBoxValue.EndToStart -> actions.enqueue(a)
            else -> Unit
        }
        false
    })
    val a11y = listOf(
        CustomAccessibilityAction(if (archiveTab) tr(R.string.move_to_inbox) else tr(R.string.archive)) { if (archiveTab) actions.unarchive(a) else actions.archive(a); true },
        CustomAccessibilityAction(tr(R.string.add_to_queue)) { actions.enqueue(a); true },
        CustomAccessibilityAction(tr(R.string.delete)) { actions.delete(a); true },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val dir = state.dismissDirection
            val (icon, label, align) = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(if (archiveTab) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                    if (archiveTab) tr(R.string.swipe_to_inbox) else tr(R.string.archive), Alignment.CenterStart)
                else -> Triple(Icons.AutoMirrored.Outlined.PlaylistAdd, tr(R.string.swipe_queue), Alignment.CenterEnd)
            }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 24.dp), contentAlignment = align) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        },
    ) {
        ArticleCard(a, compact, speed, onClick, onLongClick, a11y)
    }
}

@Composable
private fun InboxEmpty(firstTime: Boolean, onSample: () -> Unit, onArchive: () -> Unit) {
    if (firstTime) {
        EmptyState(
            "📰", tr(R.string.empty_inbox_title),
            tr(R.string.empty_inbox_body),
        ) { Button(onClick = onSample) { Text(tr(R.string.try_sample)) } }
    } else {
        EmptyState("🎉", tr(R.string.all_read_title), tr(R.string.all_read_body)) {
            OutlinedButton(onClick = onArchive) { Text(tr(R.string.open_archive)) }
        }
    }
}

@Composable
private fun QueueList(queue: List<Article>, currentId: Long?, speed: Float, nav: HomeNav, onAddLatest: () -> Unit) {
    val context = LocalContext.current
    val repo = (context.applicationContext as ClipperApp).repo
    val scope = rememberCoroutineScope()
    if (queue.isEmpty()) {
        EmptyState("🎧", tr(R.string.queue_empty_title), tr(R.string.queue_empty_body)) {
            Button(onClick = onAddLatest) { Text(tr(R.string.queue_add_latest)) }
        }
        return
    }
    val totalMin = queue.sumOf { TextUtils.listenMinutes(it.wordCount, speed) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr(R.string.queue_summary, queue.size, totalMin), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Button(onClick = { Playback.play(queue.first().id); nav.openPlayer() }) {
                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text(tr(R.string.play_all))
                }
            }
        }
        itemsIndexed(queue, key = { _, a -> a.id }) { i, a ->
            ArticleCard(
                a, compact = true, speed = speed,
                onClick = { nav.listen(a.id) }, onLongClick = { nav.openArticle(a.id) },
                trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        if (a.id == currentId) Text(tr(R.string.now_playing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Row {
                            IconButton(onClick = { scope.launch { repo.moveInQueue(a.id, -1) } }, enabled = i > 0) {
                                Icon(Icons.Outlined.KeyboardArrowUp, tr(R.string.move_up))
                            }
                            IconButton(onClick = { scope.launch { repo.moveInQueue(a.id, 1) } }, enabled = i < queue.size - 1) {
                                Icon(Icons.Outlined.KeyboardArrowDown, tr(R.string.move_down))
                            }
                            IconButton(onClick = { scope.launch { repo.dequeue(a.id) } }) { Icon(Icons.Outlined.Close, tr(R.string.remove_from_queue)) }
                        }
                    }
                },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

@Composable
fun rememberOnline(): Boolean {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    var online by remember { mutableStateOf(cm.activeNetwork != null) }
    DisposableEffect(Unit) {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = true }
            override fun onLost(network: Network) { online = cm.activeNetwork != null && cm.activeNetwork != network }
        }
        cm.registerDefaultNetworkCallback(cb)
        onDispose { runCatching { cm.unregisterNetworkCallback(cb) } }
    }
    return online
}
