package com.clippercast.app.ui

import com.clippercast.app.tr
import com.clippercast.app.R

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clippercast.app.ClipperApp
import com.clippercast.app.player.Playback
import kotlinx.coroutines.launch

/** Onboarding 3 bước, bỏ qua được (spec 4.1). */
@Composable
fun OnboardingScreen(onDone: (sampleId: Long?) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ClipperApp
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { 3 }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    val deviceLang = remember { java.util.Locale.getDefault().language }
    val langName = remember { langDisplayName(deviceLang) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun finish(sample: Boolean) = scope.launch {
        app.settings.update { it.copy(onboardingDone = true) }
        val id = if (sample) SampleArticle.insert(app) else null
        onDone(id)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { finish(false) }) { Text(tr(R.string.skip)) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(
                Modifier.fillMaxSize().padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (page) {
                    0 -> {
                        Illustration(tr(R.string.ob1_art))
                        Title(tr(R.string.ob1_title))
                        Body(tr(R.string.ob1_body))
                    }
                    1 -> {
                        Illustration(tr(R.string.ob2_art))
                        Title(tr(R.string.ob2_title))
                        Body(tr(R.string.ob2_body))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { finish(true) }) { Text(tr(R.string.try_sample_now)) }
                    }
                    else -> {
                        Illustration("🔔  🗣️")
                        Title(tr(R.string.ob3_title))
                        Body(tr(R.string.ob3_body, langName))
                        Spacer(Modifier.height(16.dp))
                        if (Build.VERSION.SDK_INT >= 33) {
                            OutlinedButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(tr(R.string.allow_notifications)) }
                        }
                        OutlinedButton(onClick = {
                            voiceStatus = tr(R.string.checking)
                            Playback.voicesFor(deviceLang) { list ->
                                val offline = list.filter { !it.isNetworkConnectionRequired }
                                voiceStatus = when {
                                    offline.isNotEmpty() -> tr(R.string.voice_ok, offline.size, langName)
                                    list.isNotEmpty() -> tr(R.string.voice_online_only, langName)
                                    else -> tr(R.string.voice_none, langName)
                                }
                            }
                        }) { Text(tr(R.string.check_voice)) }
                        voiceStatus?.let { Text(it, Modifier.padding(top = 8.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i ->
                    Box(Modifier.size(8.dp).clip(CircleShape).background(
                        if (pager.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                if (pager.currentPage < 2) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else finish(false)
            }) { Text(if (pager.currentPage < 2) tr(R.string.next) else tr(R.string.get_started)) }
        }
    }
}

@Composable
private fun Illustration(text: String) {
    Box(
        Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center) }
    Spacer(Modifier.height(32.dp))
}

@Composable
private fun Title(t: String) {
    Text(t, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Body(t: String) = Text(t, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant)
