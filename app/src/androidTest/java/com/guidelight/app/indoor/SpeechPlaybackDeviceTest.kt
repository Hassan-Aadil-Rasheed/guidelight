package com.guidelight.app.indoor

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** No user audio is recorded. Synthesizes a fixed diagnostic phrase in an isolated cache file. */
@RunWith(AndroidJUnit4::class)
class SpeechPlaybackDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<HomeActivity>()
    private fun report(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("speech_check", message) })
    }
    @Test fun inspectSynthesisAndBothPlaybackPaths() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ready = CountDownLatch(1)
        val initialized = AtomicInteger(-99)
        val tts = TextToSpeech(context) { initialized.set(it); ready.countDown() }
        val file = File(context.cacheDir, "speech-check-${UUID.randomUUID()}.wav")
        var player: MediaPlayer? = null
        try {
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue()
            assertThat(initialized.get()).isEqualTo(TextToSpeech.SUCCESS)
            val language = tts.setLanguage(Locale.US)
            report("language=$language voice=${tts.voice?.name} network=${tts.voice?.isNetworkConnectionRequired} offlineEnglish=${tts.voices.count { it.locale.language == "en" && !it.isNetworkConnectionRequired }}")
            var completed = CountDownLatch(1)
            val error = AtomicInteger(0)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { report("start=$id") }
                override fun onDone(id: String?) { report("done=$id"); completed.countDown() }
                override fun onError(id: String?) { error.set(-99); completed.countDown() }
                override fun onError(id: String?, code: Int) { report("error=$code id=$id"); error.set(code); completed.countDown() }
            })
            assertThat(tts.synthesizeToFile("GuideLight sound test.", Bundle(), file, "synthesis-check")).isEqualTo(TextToSpeech.SUCCESS)
            assertThat(completed.await(20, TimeUnit.SECONDS)).isTrue()
            assertThat(error.get()).isEqualTo(0)
            val bytes = file.readBytes()
            var dataStart = -1
            for (i in 12 until bytes.size - 8) {
                if (bytes[i] == 100.toByte() && bytes[i + 1] == 97.toByte() && bytes[i + 2] == 116.toByte() && bytes[i + 3] == 97.toByte()) {
                    dataStart = i + 8; break
                }
            }
            assertThat(dataStart).isAtLeast(20)
            var peak = 0
            for (i in dataStart until bytes.size - 1 step 2) {
                val sample = ((bytes[i].toInt() and 255) or (bytes[i + 1].toInt() shl 8)).toShort().toInt()
                peak = maxOf(peak, kotlin.math.abs(sample))
            }
            report("wavBytes=${bytes.size} pcmPeak=$peak")
            assertThat(peak).isGreaterThan(256)
            completed = CountDownLatch(1)
            report("direct TTS playback")
            assertThat(tts.speak("GuideLight sound test.", TextToSpeech.QUEUE_FLUSH, null, "direct-playback")).isEqualTo(TextToSpeech.SUCCESS)
            assertThat(completed.await(15, TimeUnit.SECONDS)).isTrue()
            assertThat(error.get()).isEqualTo(0)
            report("file playback")
            val played = CountDownLatch(1)
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setOnCompletionListener { played.countDown() }
                setOnErrorListener { _, what, extra -> report("player error=$what/$extra"); played.countDown(); true }
                setDataSource(file.absolutePath)
                prepare(); start()
            }
            assertThat(played.await(15, TimeUnit.SECONDS)).isTrue()
        } finally { player?.release(); tts.shutdown(); if (file.exists()) file.delete() }
    }
}
