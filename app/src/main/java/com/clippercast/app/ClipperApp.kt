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
        appContext = this
        Notifications.createChannels(this)
        com.clippercast.app.player.Playback.init(this)
        scope.launch {
            runCatching { repo.housekeeping(settings.current().autoDeleteArchivedDays) }
        }
    }
}

/** Ngữ cảnh ứng dụng, dùng để lấy chuỗi theo ngôn ngữ của máy ở mọi nơi. */
lateinit var appContext: android.content.Context
    private set

/** Lấy chuỗi đã dịch theo ngôn ngữ máy. */
fun tr(@androidx.annotation.StringRes id: Int, vararg args: Any): String =
    if (args.isEmpty()) appContext.getString(id) else appContext.getString(id, *args)
