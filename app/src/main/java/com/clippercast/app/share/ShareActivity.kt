package com.clippercast.app.share

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
        overridePendingTransition(0, 0)

        app.scope.launch {
            val res = runCatching { app.repo.saveShared(text, subject) }.getOrNull()
            withContext(Dispatchers.Main) {
                val msg = when {
                    res == null -> "Không tìm thấy link hoặc nội dung để lưu"
                    res.duplicate -> "Bài này đã có trong thư viện"
                    res.isNote -> "Đã lưu đoạn văn bản vào ClipperCast"
                    else -> "Đã lưu vào ClipperCast"
                }
                Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
