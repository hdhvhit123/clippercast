package com.clippercast.app.player

import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

/**
 * Cầu nối giữa bộ phát TTS và Media3, để có điều khiển ở màn hình khóa, thanh thông báo,
 * nút tai nghe Bluetooth và (sau này) Android Auto.
 */
@OptIn(UnstableApi::class)
class TtsPlayer(looper: Looper) : SimpleBasePlayer(looper) {

    fun refresh() = invalidateState()

    private fun msFor(chars: Int, speed: Float) = (chars / (Playback.CHARS_PER_SEC * speed) * 1000).toLong()

    override fun getState(): State {
        val st = Playback.state.value
        val commands = Player.Commands.Builder().addAll(
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_STOP,
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA, Player.COMMAND_SET_SPEED_AND_PITCH,
        ).build()

        val current = st.article
        var items = st.queue
        if (current != null && items.none { it.id == current.id }) items = listOf(current) + items
        if (current == null || items.isEmpty()) {
            return State.Builder()
                .setAvailableCommands(commands)
                .setPlaybackState(Player.STATE_IDLE)
                .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build()
        }
        val playlist = items.map { a ->
            val durationMs = if (a.id == current.id) msFor(st.totalChars, st.speed)
            else (a.wordCount / (160.0 * st.speed) * 60_000).toLong()
            val meta = MediaMetadata.Builder()
                .setTitle(a.title)
                .setArtist(a.siteName)
                .setAlbumTitle("ClipperCast")
                .apply { a.thumb?.let { setArtworkUri(Uri.fromFile(File(it))) } }
                .build()
            MediaItemData.Builder(a.id)
                .setMediaItem(MediaItem.Builder().setMediaId(a.id.toString()).setMediaMetadata(meta).build())
                .setDurationUs(if (durationMs > 0) durationMs * 1000 else C.TIME_UNSET)
                .setIsSeekable(true)
                .build()
        }
        val index = items.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        return State.Builder()
            .setAvailableCommands(commands)
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(index)
            .setContentPositionMs(msFor(st.charsBefore, st.speed))
            .setPlaybackParameters(PlaybackParameters(st.speed, st.pitch))
            .setPlaybackState(if (st.isLoading) Player.STATE_BUFFERING else Player.STATE_READY)
            .setPlayWhenReady(st.isPlaying || st.isLoading, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setSeekBackIncrementMs(5_000)
            .setSeekForwardIncrementMs(5_000)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) Playback.resume() else Playback.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        Playback.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_BACK -> Playback.seekSentence(-1)
            Player.COMMAND_SEEK_FORWARD -> Playback.seekSentence(1)
            else -> Playback.playQueueIndex(mediaItemIndex, positionMs.takeIf { it != C.TIME_UNSET })
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        Playback.setSpeed(playbackParameters.speed)
        return Futures.immediateVoidFuture()
    }
}
