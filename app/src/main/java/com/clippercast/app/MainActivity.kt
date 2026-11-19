package com.clippercast.app

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.clippercast.app.player.PlaybackService
import com.clippercast.app.player.Playback
import com.clippercast.app.player.PlayerScreen
import com.clippercast.app.reader.ReaderScreen
import com.clippercast.app.ui.BrowserScreen
import com.clippercast.app.ui.ClipperTheme
import com.clippercast.app.ui.HomeNav
import com.clippercast.app.ui.HomeScreen
import com.clippercast.app.ui.OnboardingScreen
import com.clippercast.app.ui.SearchScreen
import com.clippercast.app.ui.SettingsScreen
import com.clippercast.app.ui.TrashScreen
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val pendingIntent = MutableStateFlow<Intent?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingIntent.value = intent
        setContent { ClipperTheme { App() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingIntent.value = intent
    }

    override fun onStart() {
        super.onStart()
        // Kết nối với dịch vụ phát để thông báo và màn hình khóa hoạt động
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    @Composable
    private fun App() {
        val app = application as ClipperApp
        val settings by app.settings.flow.collectAsState(initial = null)
        val s = settings
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (s == null) return@Box
            val nav = rememberNavController()
            var openAdd by remember { mutableStateOf(false) }
            val start = remember { if (s.onboardingDone) "home" else "onboarding" }
            val incoming by pendingIntent.collectAsState()
            LaunchedEffect(incoming) {
                val i = incoming ?: return@LaunchedEffect
                pendingIntent.value = null
                handleIntent(i, nav) { openAdd = true }
            }
            NavHost(nav, startDestination = start) {
                composable("onboarding") {
                    OnboardingScreen { sampleId ->
                        nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                        sampleId?.let { nav.navigate("reader/$it") }
                    }
                }
                composable("home") {
                    HomeScreen(
                        HomeNav(
                            openArticle = { nav.navigate("reader/$it") },
                            listen = { id -> nav.navigate("reader/$id?play=true") },
                            openPlayer = { nav.navigate("player") },
                            search = { nav.navigate("search") },
                            settings = { nav.navigate("settings") },
                            trash = { nav.navigate("trash") },
                            browser = { nav.navigate("browser/$it") },
                        ),
                        initialAdd = openAdd.also { openAdd = false },
                    )
                }
                composable(
                    "reader/{id}?play={play}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType },
                        navArgument("play") { type = NavType.BoolType; defaultValue = false },
                    ),
                ) { e ->
                    ReaderScreen(
                        id = e.arguments!!.getLong("id"),
                        autoplay = e.arguments!!.getBoolean("play"),
                        onBack = { if (!nav.popBackStack()) nav.navigate("home") },
                        openPlayer = { nav.navigate("player") },
                        openBrowser = { nav.navigate("browser/$it") },
                    )
                }
                composable("player") {
                    PlayerScreen(
                        onBack = { nav.popBackStack() },
                        openArticle = { nav.navigate("reader/$it") },
                        openQueue = { nav.popBackStack("home", false) },
                    )
                }
                composable("search") { SearchScreen(onBack = { nav.popBackStack() }, openArticle = { nav.navigate("reader/$it") }) }
                composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }, onTrash = { nav.navigate("trash") }) }
                composable("trash") { TrashScreen(onBack = { nav.popBackStack() }) }
                composable("browser/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    BrowserScreen(e.arguments!!.getLong("id"), onDone = { nav.popBackStack() })
                }
            }
        }
    }

    private fun handleIntent(i: Intent, nav: NavHostController, openAdd: () -> Unit) {
        val id = i.getLongExtra(EXTRA_ID, -1)
        val app = application as ClipperApp
        when (i.action) {
            ACTION_OPEN -> if (id > 0) nav.navigate("reader/$id")
            ACTION_LISTEN -> if (id > 0) nav.navigate("reader/$id?play=true")
            ACTION_BROWSER -> if (id > 0) nav.navigate("browser/$id")
            ACTION_PLAYER -> nav.navigate("player")
            ACTION_SEARCH -> nav.navigate("search")
            ACTION_PASTE -> openAdd()
            ACTION_RESUME -> { Playback.resume(); nav.navigate("player") }
            ACTION_LISTEN_SHARED -> lifecycleScope.launch {
                val res = app.repo.saveShared(i.getStringExtra(Intent.EXTRA_TEXT), i.getStringExtra(Intent.EXTRA_SUBJECT))
                    ?: return@launch
                nav.navigate("reader/${res.id}?play=true")
            }
        }
    }

    companion object {
        const val EXTRA_ID = "article_id"
        const val ACTION_OPEN = "com.clippercast.app.OPEN"
        const val ACTION_LISTEN = "com.clippercast.app.LISTEN"
        const val ACTION_LISTEN_SHARED = "com.clippercast.app.LISTEN_SHARED"
        const val ACTION_BROWSER = "com.clippercast.app.BROWSER"
        const val ACTION_PLAYER = "com.clippercast.app.PLAYER"
        const val ACTION_SEARCH = "com.clippercast.app.SEARCH"
        const val ACTION_PASTE = "com.clippercast.app.PASTE"
        const val ACTION_RESUME = "com.clippercast.app.RESUME"
    }
}
