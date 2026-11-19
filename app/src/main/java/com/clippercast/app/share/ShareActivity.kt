package com.clippercast.app.share

import com.clippercast.app.tr
import com.clippercast.app.R

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.clippercast.app.ClipperApp
import com.clippercast.app.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Nhận link từ Share Sheet (spec 2.1). Lưu ngay, hiện Toast rồi đóng để người dùng
 * tiếp tục lướt web. Việc tải nội dung chạy nền bằng WorkManager.
 */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ClipperApp
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT)
        val listen = intent?.component?.className?.endsWith("ShareListen") == true
        if (listen) {
            // "Nghe ngay": mở app ngay (đang ở tiền cảnh nên được phép), app sẽ lưu và phát khi tải xong
            startActivity(Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_LISTEN_SHARED
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
            return
        }
        finish()

        app.scope.launch {
            val res = runCatching { app.repo.saveShared(text, subject) }.getOrNull()
            withContext(Dispatchers.Main) {
                val msg = when {
                    res == null -> tr(R.string.share_nothing)
                    res.duplicate -> tr(R.string.already_saved)
                    res.isNote -> tr(R.string.share_text_saved)
                    else -> tr(R.string.share_saved)
                }
                Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
