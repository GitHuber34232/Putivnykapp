package ua.kyiv.putivnyk.audio

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ua.kyiv.putivnyk.R
import ua.kyiv.putivnyk.data.repository.AchievementsRepository
import ua.kyiv.putivnyk.data.repository.LocalizationRepository
import ua.kyiv.putivnyk.data.repository.PlaceRepository
import ua.kyiv.putivnyk.data.repository.StatsRepository
import ua.kyiv.putivnyk.domain.stats.StatsCounters
import ua.kyiv.putivnyk.i18n.AppText
import ua.kyiv.putivnyk.i18n.OnDeviceTranslationService
import ua.kyiv.putivnyk.i18n.dynamicTranslationKey
import ua.kyiv.putivnyk.navigation.DeepLinks
import ua.kyiv.putivnyk.notifications.AppNotifications
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audio guide: reads place stories aloud with the system text-to-speech engine in the app's
 * language, with a media session (lock screen, headset buttons) and audio focus handling.
 */
@Singleton
class AudioGuideEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val placeRepository: PlaceRepository,
    private val translationService: OnDeviceTranslationService,
    private val localizationRepository: LocalizationRepository,
    private val statsRepository: StatsRepository,
    private val achievementsRepository: AchievementsRepository,
    private val appNotifications: AppNotifications,
    private val appText: AppText
) {
    data class Item(
        val placeId: Long,
        val title: String,
        val chunks: List<String>,
        val locale: Locale
    )

    data class State(
        val items: List<Item> = emptyList(),
        val index: Int = 0,
        val chunkIndex: Int = 0,
        val isPlaying: Boolean = false,
        val isPreparing: Boolean = false,
        val unavailable: Boolean = false
    ) {
        val current: Item? get() = items.getOrNull(index)
        val isActive: Boolean get() = items.isNotEmpty() || isPreparing
    }

    private data class Labels(
        val guide: String = "Аудіогід",
        val play: String = "Слухати",
        val pause: String = "Пауза",
        val next: String = "Далі",
        val previous: String = "Назад",
        val stop: String = "Зупинити"
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager: AudioManager = context.getSystemService(AudioManager::class.java)
    private val notificationManager: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private var tts: TextToSpeech? = null
    private var ttsReady: CompletableDeferred<Boolean>? = null
    private var session: MediaSession? = null
    private var focusRequest: AudioFocusRequest? = null
    private var pausedByFocusLoss = false
    private var noisyReceiverRegistered = false
    private var labels = Labels()
    private var prepareJob: Job? = null

    /** Utterances of an older "speak" call are ignored once playback was paused or skipped. */
    private var generation = 0

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    /** Plays the stories of [placeIds] in order (e.g. all stops of a route). */
    fun playPlaces(placeIds: List<Long>, startIndex: Int = 0) {
        val ids = placeIds.distinct()
        if (ids.isEmpty()) return
        prepareJob?.cancel()
        stopSpeaking()
        _state.value = State(isPreparing = true)
        startService()
        prepareJob = scope.launch {
            labels = loadLabels()
            val items = ids.mapNotNull { prepareItem(it) }
            if (items.isEmpty()) {
                _state.value = State(unavailable = true)
                publish()
                return@launch
            }
            _state.value = State(items = items, index = startIndex.coerceIn(0, items.lastIndex))
            play()
        }
    }

    /** Narration on arrival: queued after the current story, or played right away. */
    fun narrate(placeId: Long) {
        scope.launch {
            val current = _state.value
            if (current.items.any { it.placeId == placeId }) return@launch
            if (!current.isActive) {
                playPlaces(listOf(placeId))
                return@launch
            }
            val item = prepareItem(placeId) ?: return@launch
            _state.update { it.copy(items = it.items + item) }
            if (!_state.value.isPlaying && !_state.value.isPreparing) {
                _state.update { it.copy(index = it.items.lastIndex, chunkIndex = 0) }
                play()
            } else {
                publish()
            }
        }
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else play()
    }

    fun play() {
        scope.launch {
            val item = _state.value.current ?: return@launch
            val engine = awaitTts() ?: run {
                _state.update { it.copy(isPlaying = false, unavailable = true) }
                publish()
                return@launch
            }
            if (!requestFocus()) return@launch
            registerNoisyReceiver()
            pausedByFocusLoss = false
            _state.update { it.copy(isPlaying = true, unavailable = false) }
            speakFrom(engine, item, _state.value.chunkIndex)
            publish()
        }
    }

    fun pause() {
        if (!_state.value.isPlaying) return
        stopSpeaking()
        _state.update { it.copy(isPlaying = false) }
        abandonFocus()
        publish()
    }

    fun next() {
        val current = _state.value
        if (current.index >= current.items.lastIndex) {
            stop()
            return
        }
        moveTo(current.index + 1)
    }

    fun previous() {
        val current = _state.value
        if (current.chunkIndex > 0 || current.index == 0) moveTo(current.index) else moveTo(current.index - 1)
    }

    fun stop() {
        prepareJob?.cancel()
        stopSpeaking()
        abandonFocus()
        _state.value = State()
        session?.isActive = false
        notificationManager.cancel(AppNotifications.AUDIO_NOTIFICATION_ID)
    }

    fun buildNotification(): Notification {
        appNotifications.ensureChannels()
        val current = _state.value
        val item = current.current
        val mediaSession = ensureSession()
        val playPause = if (current.isPlaying) {
            action(R.drawable.ic_action_pause, labels.pause, AudioGuideService.ACTION_TOGGLE, 1)
        } else {
            action(R.drawable.ic_action_play, labels.play, AudioGuideService.ACTION_TOGGLE, 1)
        }
        val position = if (current.items.size > 1) " · ${current.index + 1}/${current.items.size}" else ""
        return Notification.Builder(context, AppNotifications.CHANNEL_AUDIO)
            .setSmallIcon(R.drawable.ic_stat_audio)
            .setColor(AppNotifications.BRAND_COLOR)
            .setContentTitle(item?.title ?: labels.guide)
            .setContentText("${labels.guide}$position")
            .setOngoing(current.isPlaying || current.isPreparing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setContentIntent(
                appNotifications.openLink(
                    item?.let { DeepLinks.place(it.placeId) } ?: DeepLinks.map(),
                    REQUEST_OPEN
                )
            )
            .setDeleteIntent(serviceIntent(AudioGuideService.ACTION_STOP, 4))
            .addAction(action(R.drawable.ic_action_previous, labels.previous, AudioGuideService.ACTION_PREVIOUS, 0))
            .addAction(playPause)
            .addAction(action(R.drawable.ic_action_next, labels.next, AudioGuideService.ACTION_NEXT, 2))
            .addAction(action(R.drawable.ic_action_stop, labels.stop, AudioGuideService.ACTION_STOP, 3))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun moveTo(index: Int) {
        val wasPlaying = _state.value.isPlaying
        stopSpeaking()
        _state.update { it.copy(index = index.coerceIn(0, (it.items.size - 1).coerceAtLeast(0)), chunkIndex = 0) }
        if (wasPlaying) {
            _state.update { it.copy(isPlaying = false) }
            play()
        } else {
            publish()
        }
    }

    private fun speakFrom(engine: TextToSpeech, item: Item, chunkIndex: Int) {
        val speakGeneration = ++generation
        val itemIndex = _state.value.index
        engine.setAudioAttributes(attributes)
        engine.setLanguage(item.locale)
        item.chunks.drop(chunkIndex).forEachIndexed { offset, chunk ->
            val index = chunkIndex + offset
            engine.speak(
                chunk,
                if (offset == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                Bundle(),
                "$speakGeneration:$itemIndex:$index"
            )
        }
    }

    private fun stopSpeaking() {
        generation++
        runCatching { tts?.stop() }
    }

    private fun onUtteranceStarted(utteranceId: String) {
        val (speakGeneration, itemIndex, chunk) = parseUtterance(utteranceId) ?: return
        if (speakGeneration != generation || itemIndex != _state.value.index) return
        _state.update { it.copy(chunkIndex = chunk) }
    }

    private fun onUtteranceDone(utteranceId: String) {
        val (speakGeneration, itemIndex, chunk) = parseUtterance(utteranceId) ?: return
        if (speakGeneration != generation || itemIndex != _state.value.index) return
        val item = _state.value.current ?: return
        if (chunk < item.chunks.lastIndex) return
        // The whole story was heard.
        scope.launch {
            runCatching {
                statsRepository.increment(StatsCounters.AUDIO_LISTENED)
                achievementsRepository.evaluate()
            }
        }
        val current = _state.value
        if (current.index < current.items.lastIndex) {
            _state.update { it.copy(index = it.index + 1, chunkIndex = 0) }
            val nextItem = _state.value.current
            val engine = tts
            if (nextItem != null && engine != null) speakFrom(engine, nextItem, 0)
            publish()
        } else {
            stop()
        }
    }

    private fun parseUtterance(utteranceId: String): Triple<Int, Int, Int>? {
        val parts = utteranceId.split(':')
        if (parts.size != 3) return null
        val a = parts[0].toIntOrNull() ?: return null
        val b = parts[1].toIntOrNull() ?: return null
        val c = parts[2].toIntOrNull() ?: return null
        return Triple(a, b, c)
    }

    private suspend fun awaitTts(): TextToSpeech? {
        val existing = tts
        val ready = ttsReady
        if (existing != null && ready != null) {
            return if (withTimeoutOrNull(TTS_INIT_TIMEOUT_MILLIS) { ready.await() } == true) existing else null
        }
        val deferred = CompletableDeferred<Boolean>()
        ttsReady = deferred
        val engine = TextToSpeech(context) { status -> deferred.complete(status == TextToSpeech.SUCCESS) }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.let { id -> scope.launch { onUtteranceStarted(id) } }
            }

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { id -> scope.launch { onUtteranceDone(id) } }
            }

            @Deprecated("Deprecated in API 21")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { id -> scope.launch { onUtteranceDone(id) } }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { id -> scope.launch { onUtteranceDone(id) } }
            }
        })
        tts = engine
        val ok = withTimeoutOrNull(TTS_INIT_TIMEOUT_MILLIS) { deferred.await() } == true
        if (!ok) {
            Log.w(TAG, "Text-to-speech is not available")
            runCatching { engine.shutdown() }
            tts = null
            ttsReady = null
            return null
        }
        return engine
    }

    private suspend fun prepareItem(placeId: Long): Item? {
        val place = placeRepository.getPlaceById(placeId) ?: return null
        val language = appText.language()
        val description = place.description?.takeIf { it.isNotBlank() }
        var name = place.name
        var story = description
        var locale = Locale.forLanguageTag("uk")
        if (language != "uk") {
            val translatedName = translate(place.name, language)
            val translatedStory = description?.let { translate(it, language) }
            val fullyTranslated = translatedName != null && (description == null || translatedStory != null)
            if (fullyTranslated) {
                name = translatedName!!
                story = translatedStory
                locale = Locale.forLanguageTag(language)
            }
        }
        val engine = awaitTts()
        if (engine != null && engine.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) {
            // No voice for that language: read the original text with a Ukrainian voice if possible.
            name = place.name
            story = description
            locale = Locale.forLanguageTag("uk")
            if (engine.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) locale = Locale.getDefault()
        }
        val text = listOfNotNull(name, story).joinToString(". ")
        return Item(placeId = place.id, title = name, chunks = splitIntoChunks(text), locale = locale)
    }

    /** Cached or fresh on-device translation; null when it is not available. */
    private suspend fun translate(text: String, language: String): String? {
        val source = text.trim()
        val key = dynamicTranslationKey(source)
        localizationRepository.getValue(key, language)?.takeIf { it.isNotBlank() }?.let { return it }
        if (!translationService.isSupportedByMlKit(language)) return null
        val translated = runCatching {
            translationService.translateText(source, sourceLanguageIso = "uk", targetLanguageIso = language)
        }.getOrNull()?.takeIf { it.isNotBlank() && it != source } ?: return null
        localizationRepository.upsertValue(key = key, locale = language, value = translated, source = "mlkit-dynamic")
        return translated
    }

    private suspend fun loadLabels() = Labels(
        guide = appText.get("audio.title", "Аудіогід"),
        play = appText.get("audio.listen", "Слухати"),
        pause = appText.get("audio.pause", "Пауза"),
        next = appText.get("audio.next", "Далі"),
        previous = appText.get("audio.previous", "Назад"),
        stop = appText.get("audio.stop", "Зупинити")
    )

    private fun requestFocus(): Boolean {
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { change -> scope.launch { onFocusChange(change) } }
            .build()
            .also { focusRequest = it }
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        unregisterNoisyReceiver()
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedByFocusLoss = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (_state.value.isPlaying) {
                    stopSpeaking()
                    _state.update { it.copy(isPlaying = false) }
                    pausedByFocusLoss = true
                    publish()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (pausedByFocusLoss) play()
        }
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        ContextCompat.registerReceiver(
            context,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        noisyReceiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        runCatching { context.unregisterReceiver(noisyReceiver) }
        noisyReceiverRegistered = false
    }

    private fun ensureSession(): MediaSession = session ?: MediaSession(context, "PutivnykAudioGuide").also { created ->
        created.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = play()
            override fun onPause() = pause()
            override fun onSkipToNext() = next()
            override fun onSkipToPrevious() = previous()
            override fun onStop() = stop()
        })
        session = created
    }

    /** Pushes the state to the media session and the notification. */
    private fun publish() {
        val current = _state.value
        val mediaSession = ensureSession()
        if (!current.isActive) {
            mediaSession.isActive = false
            notificationManager.cancel(AppNotifications.AUDIO_NOTIFICATION_ID)
            return
        }
        mediaSession.isActive = true
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP
                )
                .setState(
                    when {
                        current.isPreparing -> PlaybackState.STATE_BUFFERING
                        current.isPlaying -> PlaybackState.STATE_PLAYING
                        else -> PlaybackState.STATE_PAUSED
                    },
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    1f
                )
                .build()
        )
        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, current.current?.title ?: labels.guide)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, context.getString(R.string.app_name))
                .putString(MediaMetadata.METADATA_KEY_ALBUM, labels.guide)
                .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, (current.index + 1).toLong())
                .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, current.items.size.toLong())
                .build()
        )
        runCatching { notificationManager.notify(AppNotifications.AUDIO_NOTIFICATION_ID, buildNotification()) }
    }

    private fun startService() {
        // Allowed while the app is visible or from a notification; during background navigation
        // the navigation service (also mediaPlayback) keeps playback going instead.
        runCatching {
            context.startForegroundService(Intent(context, AudioGuideService::class.java).setAction(AudioGuideService.ACTION_START))
        }.onFailure { Log.i(TAG, "Audio guide continues without its own service", it) }
    }

    private fun action(icon: Int, title: String, serviceAction: String, requestCode: Int): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(context, icon), title, serviceIntent(serviceAction, requestCode)).build()

    private fun serviceIntent(serviceAction: String, requestCode: Int): PendingIntent = PendingIntent.getForegroundService(
        context,
        REQUEST_BASE + requestCode,
        Intent(context, AudioGuideService::class.java).setAction(serviceAction),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        private const val TAG = "AudioGuide"
        private const val TTS_INIT_TIMEOUT_MILLIS = 8_000L
        private const val MAX_CHUNK_CHARS = 380
        private const val REQUEST_BASE = 5000
        private const val REQUEST_OPEN = 5100

        /** Sentence-sized pieces: pausing resumes from the current sentence, not from the start. */
        fun splitIntoChunks(text: String, maxChars: Int = MAX_CHUNK_CHARS): List<String> {
            val sentences = Regex("""(?<=[.!?…])\s+|\n+""").split(text.trim()).map { it.trim() }.filter { it.isNotEmpty() }
            val chunks = mutableListOf<String>()
            val current = StringBuilder()
            sentences.forEach { sentence ->
                var rest = sentence
                while (rest.length > maxChars) {
                    val cut = rest.lastIndexOf(' ', maxChars).takeIf { it > maxChars / 2 } ?: maxChars
                    if (current.isNotEmpty()) {
                        chunks += current.toString()
                        current.clear()
                    }
                    chunks += rest.substring(0, cut).trim()
                    rest = rest.substring(cut).trim()
                }
                if (current.isNotEmpty() && current.length + rest.length + 1 > maxChars) {
                    chunks += current.toString()
                    current.clear()
                }
                if (current.isNotEmpty()) current.append(' ')
                current.append(rest)
            }
            if (current.isNotEmpty()) chunks += current.toString()
            return chunks
        }
    }
}
