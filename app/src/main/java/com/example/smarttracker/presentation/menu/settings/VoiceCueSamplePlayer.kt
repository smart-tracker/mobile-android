package com.example.smarttracker.presentation.menu.settings

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.smarttracker.data.location.TtsPhraseFormatter
import java.util.Locale

/**
 * Проигрыватель пробной фразы для слайдера громкости голосовых подсказок
 * (экран «Настройки»).
 *
 * Отдельный TTS-экземпляр, а не тот, что в LocationTrackingService: сервис
 * живёт только во время тренировки (нюанс 32), а настройки крутят и без неё.
 * Живёт в composition ([SettingsScreen]), освобождается в onDispose.
 *
 * Деградация как у сервиса: движок не поднялся или нет русского голоса —
 * лог и молчание, экран продолжает работать (фраза — доп. фидбек, не функция).
 */
class VoiceCueSamplePlayer(context: Context) {

    private var ready = false
    private var released = false

    /** Запрос отыгрыша пришёл до готовности движка — отыграется в init-колбэке. */
    private var pendingPlay = false

    // Nullable + init-блок: Kotlin запрещает читать поле внутри его же
    // инициализатора (колбэк ссылается на tts для setLanguage).
    // Колбэк придёт на main thread после конструктора — поле уже присвоено.
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (released || engine == null) return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "TTS init failed: status=$status, пробный отыгрыш недоступен")
                return@TextToSpeech
            }
            val lang = engine.setLanguage(Locale("ru"))
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Русский TTS-голос недоступен, пробный отыгрыш выключен")
                return@TextToSpeech
            }
            ready = true
            if (pendingPlay) {
                pendingPlay = false
                play()
            }
        }
    }

    /**
     * Произносит короткий шаблон подсказки. Громкость — текущая системная
     * громкость медиа (STREAM_MUSIC): слайдер выставляет её ДО вызова, фраза
     * звучит ровно так, как будут звучать реальные подсказки на тренировке.
     * До готовности движка запрос запоминается и отыгрывается после init.
     */
    fun play() {
        if (released) return
        if (!ready) {
            pendingPlay = true
            return
        }
        // QUEUE_FLUSH: быстрые повторные движения слайдера не копят очередь фраз —
        // звучит только последний выбор.
        tts?.speak(SAMPLE_PHRASE, TextToSpeech.QUEUE_FLUSH, null, "volume-sample")
    }

    /** Останавливает речь и освобождает движок. Повторные вызовы безопасны. */
    fun release() {
        if (released) return
        released = true
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }

    private companion object {
        const val TAG = "VoiceCueSamplePlayer"

        /** Очень короткий шаблон реальной подсказки: километровый рубеж без темпа. */
        val SAMPLE_PHRASE = TtsPhraseFormatter.kilometerCue(1, 0L)
    }
}
