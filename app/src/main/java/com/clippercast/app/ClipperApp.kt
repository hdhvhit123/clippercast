package com.clippercast.app

import android.app.Application
import com.clippercast.app.clip.Notifications
import com.clippercast.app.data.Repository
import com.clippercast.app.data.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClipperApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repo by lazy { Repository.get(this) }
    val settings by lazy { SettingsRepo(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        com.clippercast.app.player.Playback.init(this)
        scope.launch {
            runCatching { repo.housekeeping(settings.current().autoDeleteArchivedDays) }
        }
    }
}
