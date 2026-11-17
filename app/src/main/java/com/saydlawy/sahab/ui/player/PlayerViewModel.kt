package com.saydlawy.sahab.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Episode
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.Program
import com.saydlawy.sahab.data.SeriesDetails
import com.saydlawy.sahab.util.Ar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable

data class PlaybackProgress(
    val position: Long = 0L,
    val bufferedPosition: Long = 0L,
    val duration: Long = 0L,
)

@Immutable
data class PlayerUiState(
    val playlist: List<PlayItem>,
    val index: Int,
    val isPlaying: Boolean = false,
    val buffering: Boolean = true,
    val error: String? = null,
    val playbackSpeed: Float = 1.0f,
    val speedIndex: Int = 0,
    val resizeIndex: Int = 0,
    val retryKey: Int = 0,
    val formatAttempt: Int = 0,
    val autoReconnectCount: Int = 0,
    val reconnecting: Boolean = false,
    val epg: List<Program> = emptyList(),
    val seriesDetails: SeriesDetails? = null,
    val requestPlayFocusTick: Int = 0,
) {
    val currentItem: PlayItem
        get() = playlist.getOrElse(index.coerceAtLeast(0)) { PlayItem(Kind.LIVE, "", "") }
    val isLive: Boolean
        get() = currentItem.kind == Kind.LIVE
}

class PlayerViewModel(
    private val container: AppContainer,
    private val initialRequest: PlayRequest,
) : ViewModel() {

    private val repo = container.repo

    private val _state = MutableStateFlow(
        PlayerUiState(
            playlist = initialRequest.items,
            index = initialRequest.startIndex.coerceIn(0, initialRequest.items.lastIndex.coerceAtLeast(0)),
        ),
    )
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    private var forceFromZeroId: String? = null
    private var usedInitialPos: Boolean = false
    private var lastLoadedSeriesGroupId: String? = null
    private var epgJob: Job? = null
    private var retryJob: Job? = null

    fun saveProgress(positionMs: Long, durationMs: Long) {
        val current = _state.value.currentItem
        if (current.id.isBlank()) return
        container.saveProgress(current, positionMs, durationMs)
    }

    fun saveProgressIfVod(positionMs: Long, durationMs: Long) {
        val current = _state.value.currentItem
        if (current.id.isBlank() || current.kind == Kind.LIVE) return
        container.saveProgress(current, positionMs, durationMs)
    }

    fun flushRecents() {
        container.flushRecents()
    }

    fun onPlaybackPausedOrStopped(positionMs: Long, durationMs: Long) {
        saveProgress(positionMs, durationMs)
        container.flushRecents()
    }

    fun loadSeriesIfNeeded(item: PlayItem) {
        if (item.kind != Kind.EPISODE || item.groupId.isBlank()) return
        if (lastLoadedSeriesGroupId == item.groupId && _state.value.seriesDetails != null) return
        lastLoadedSeriesGroupId = item.groupId
        viewModelScope.launch {
            val loaded = runCatching { repo.seriesDetails(item.groupId) }.getOrNull() ?: return@launch
            _state.update { s ->
                val expanded = if (loaded.episodes.isNotEmpty() && s.playlist.size <= 1) {
                    loaded.episodes.map { e ->
                        PlayItem(
                            kind = Kind.EPISODE,
                            id = e.id,
                            title = loaded.name.ifBlank { item.title },
                            subtitle = "${Ar.seasonEpisode(e.season, e.number)}: ${e.title}",
                            icon = e.image.ifBlank { loaded.cover.ifBlank { item.icon } },
                            ext = e.ext,
                            groupId = loaded.id.ifBlank { item.groupId },
                        )
                    }
                } else {
                    s.playlist
                }
                val matchIdx = expanded.indexOfFirst { it.id == s.currentItem.id }
                s.copy(
                    seriesDetails = loaded,
                    playlist = expanded,
                    index = if (matchIdx >= 0) matchIdx else s.index,
                )
            }
        }
    }

    fun startEpgLoop(itemId: String, isLive: Boolean) {
        epgJob?.cancel()
        _state.update { it.copy(epg = emptyList()) }
        if (!isLive) return
        epgJob = viewModelScope.launch {
            while (isActive) {
                val list = try {
                    repo.shortEpg(itemId, 4)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
                _state.update { it.copy(epg = list) }
                delay(60_000)
            }
        }
    }

    fun goTo(target: Int, positionMs: Long, durationMs: Long): Boolean {
        val current = _state.value
        if (target !in 0..current.playlist.lastIndex || target == current.index) return false
        retryJob?.cancel()
        saveProgress(positionMs, durationMs)
        _state.update {
            it.copy(
                formatAttempt = 0,
                autoReconnectCount = 0,
                reconnecting = false,
                error = null,
                index = target,
            )
        }
        return true
    }

    fun zapNext(positionMs: Long, durationMs: Long): Boolean {
        val s = _state.value
        if (s.playlist.size <= 1) return false
        val target = if (s.index >= s.playlist.lastIndex) 0 else s.index + 1
        return goTo(target, positionMs, durationMs)
    }

    fun zapPrevious(positionMs: Long, durationMs: Long): Boolean {
        val s = _state.value
        if (s.playlist.size <= 1) return false
        val target = if (s.index <= 0) s.playlist.lastIndex else s.index - 1
        return goTo(target, positionMs, durationMs)
    }

    fun playEpisodeFromDrawer(ep: Episode, positionMs: Long, durationMs: Long): Boolean {
        val s = _state.value
        val existingIdx = s.playlist.indexOfFirst { it.id == ep.id }
        if (existingIdx >= 0) {
            return goTo(existingIdx, positionMs, durationMs)
        }
        val details = s.seriesDetails ?: return false
        val allEps = details.episodes
        if (allEps.isEmpty()) return false
        val sName = details.name.ifBlank { s.currentItem.title }
        val sCover = details.cover.ifBlank { s.currentItem.icon }
        val sId = details.id.ifBlank { s.currentItem.groupId }
        val newItems = allEps.map { e ->
            PlayItem(
                kind = Kind.EPISODE,
                id = e.id,
                title = sName,
                subtitle = "${Ar.seasonEpisode(e.season, e.number)}: ${e.title}",
                icon = e.image.ifBlank { sCover },
                ext = e.ext,
                groupId = sId,
            )
        }
        val newIdx = newItems.indexOfFirst { it.id == ep.id }.coerceAtLeast(0)
        saveProgress(positionMs, durationMs)
        _state.update {
            it.copy(
                playlist = newItems,
                formatAttempt = 0,
                autoReconnectCount = 0,
                reconnecting = false,
                error = null,
                index = newIdx,
            )
        }
        return true
    }

    fun cycleSpeed(speeds: FloatArray): Float {
        val nextIdx = (_state.value.speedIndex + 1) % speeds.size
        val nextSpeed = speeds[nextIdx]
        _state.update { it.copy(speedIndex = nextIdx, playbackSpeed = nextSpeed) }
        return nextSpeed
    }

    fun cycleResize(modeCount: Int) {
        _state.update { it.copy(resizeIndex = (it.resizeIndex + 1) % modeCount) }
    }

    fun retry() {
        retryJob?.cancel()
        _state.update {
            it.copy(
                autoReconnectCount = 0,
                formatAttempt = 0,
                reconnecting = false,
                error = null,
                retryKey = it.retryKey + 1,
            )
        }
    }

    fun bumpPlayFocusTick() {
        _state.update { it.copy(requestPlayFocusTick = it.requestPlayFocusTick + 1) }
    }

    fun onIsPlayingChanged(playing: Boolean) {
        _state.update { it.copy(isPlaying = playing) }
    }

    fun onProgressUpdate(position: Long, buffered: Long, duration: Long) {
        _progress.value = PlaybackProgress(position, buffered, duration)
    }

    fun onSeekPosition(position: Long) {
        _progress.update { it.copy(position = position) }
    }

    fun onBufferingReady(isBuffering: Boolean, isReady: Boolean) {
        _state.update { s ->
            if (isReady) {
                s.copy(
                    buffering = isBuffering,
                    error = null,
                    reconnecting = false,
                    autoReconnectCount = 0,
                )
            } else {
                s.copy(buffering = isBuffering)
            }
        }
    }

    fun onPrepareStarted() {
        _state.update { it.copy(error = null, buffering = true) }
    }

    fun onUrlError() {
        _state.update { it.copy(reconnecting = false, error = Ar.errPlayback) }
    }

    fun onPlayerError(errorCodeName: String, causeMessage: String) {
        retryJob?.cancel()

        val codeMatch = Regex("""\b(401|403|404|458|500|502|503)\b""").find(causeMessage)?.value
        val isDeviceConflict = codeMatch == "401" || codeMatch == "403" || codeMatch == "458"
        val s = _state.value
        val live = s.isLive

        // Device-conflict: server keeps the old session alive for several
        // seconds after ExoPlayer released it. Immediate retry => another 403.
        if (isDeviceConflict) {
            if (s.autoReconnectCount < 2) {
                val waitMs = 5000L * (s.autoReconnectCount + 1)
                _state.update { it.copy(
                    reconnecting = true,
                    error = "تعارض اتصال — إعادة المحاولة بعد ${waitMs / 1000} ثانية...",
                ) }
                retryJob = viewModelScope.launch {
                    delay(waitMs)
                    _state.update { it.copy(
                        autoReconnectCount = it.autoReconnectCount + 1,
                        error = null,
                    ) }
                }
                return
            }
            if (live && s.formatAttempt + 1 < repo.liveExtensions().size) {
                _state.update { it.copy(
                    reconnecting = true,
                    error = "جارٍ تبديل الصيغة بعد 5 ثوانٍ...",
                ) }
                retryJob = viewModelScope.launch {
                    delay(5000)
                    _state.update { it.copy(
                        autoReconnectCount = 0,
                        formatAttempt = it.formatAttempt + 1,
                        error = null,
                    ) }
                }
                return
            }
            _state.update { it.copy(
                reconnecting = false,
                error = "تم تجاوز الحد الأقصى للأجهزة المتصلة أو انتهى الاشتراك ($codeMatch)",
            ) }
            return
        }

        // Network / other errors — short backoff
        when {
            s.autoReconnectCount < 2 -> {
                _state.update { it.copy(
                    reconnecting = true,
                    error = "جارٍ إعادة الاتصال بعد 2 ثانية...",
                ) }
                retryJob = viewModelScope.launch {
                    delay(2000)
                    _state.update { it.copy(
                        autoReconnectCount = it.autoReconnectCount + 1,
                        error = null,
                    ) }
                }
            }
            live && s.formatAttempt + 1 < repo.liveExtensions().size -> {
                _state.update { it.copy(
                    reconnecting = true,
                    error = "جارٍ تبديل الصيغة بعد 3 ثوانٍ...",
                ) }
                retryJob = viewModelScope.launch {
                    delay(3000)
                    _state.update { it.copy(
                        autoReconnectCount = 0,
                        formatAttempt = it.formatAttempt + 1,
                        error = null,
                    ) }
                }
            }
            else -> {
                val hint = when (codeMatch) {
                    "404" -> "القناة أو المحتوى غير متوفر حالياً على السيرفر (404)"
                    "500", "502", "503" -> "خلل في خادم البث ($codeMatch)"
                    else -> if (errorCodeName.isNotBlank()) "فشل تشغيل البث ($errorCodeName)" else Ar.errPlayback
                }
                _state.update { it.copy(reconnecting = false, error = hint) }
            }
        }
    }

    fun streamUrl(item: PlayItem, formatAttempt: Int): String = repo.streamUrl(item, formatAttempt)

    suspend fun resolveResumePositionMs(item: PlayItem, currentPlayerPositionMs: Long): Long {
        val s = _state.value
        val resume = when {
            item.kind == Kind.LIVE || initialRequest.fromStart || forceFromZeroId == item.id -> 0L
            s.autoReconnectCount > 0 && currentPlayerPositionMs > 0L -> currentPlayerPositionMs
            initialRequest.initialPositionMs > 0L && !usedInitialPos &&
                item.id == initialRequest.items.getOrNull(initialRequest.startIndex)?.id -> {
                usedInitialPos = true
                initialRequest.initialPositionMs
            }
            else -> container.prefs.positionOf(item.kind, item.id)
        }
        if (forceFromZeroId == item.id) forceFromZeroId = null
        return resume.coerceAtLeast(0L)
    }

    fun onPlaybackEnded(totalDurationMs: Long, onNoNextItem: () -> Unit) {
        val s = _state.value
        if (s.isLive) return
        if (totalDurationMs > 0L) {
            container.saveProgress(s.currentItem, totalDurationMs, totalDurationMs)
        }
        val nextInPlaylist = if (s.index < s.playlist.lastIndex) s.playlist[s.index + 1] else null
        if (nextInPlaylist != null) {
            forceFromZeroId = nextInPlaylist.id
            goTo(s.index + 1, totalDurationMs, totalDurationMs)
            return
        }
        val allEps = s.seriesDetails?.episodes.orEmpty()
        val curEpIdx = allEps.indexOfFirst { it.id == s.currentItem.id }
        val nextEp = if (curEpIdx >= 0 && curEpIdx + 1 < allEps.size) allEps[curEpIdx + 1] else null
        if (nextEp != null) {
            forceFromZeroId = nextEp.id
            playEpisodeFromDrawer(nextEp, totalDurationMs, totalDurationMs)
        } else {
            onNoNextItem()
        }
    }
}
