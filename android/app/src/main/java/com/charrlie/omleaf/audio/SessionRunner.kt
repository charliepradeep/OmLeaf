package com.charrlie.omleaf.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.charrlie.omleaf.domain.Plan
import com.charrlie.omleaf.domain.PlanSynth
import com.charrlie.omleaf.domain.SoundSettings

/**
 * Plays one session. The audio is made on the fly and the audio clock is also the session clock,
 * so what you hear and what the screen shows cannot drift apart. When every sound is switched off it
 * still plays silence, which keeps the timing identical.
 */
class SessionRunner(context: Context, private val plan: Plan, sound: SoundSettings) {
    private val synth = PlanSynth(plan, sound)
    private val sampleRate = synth.sampleRate
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    private var focus: AudioFocusRequest? = null

    @Volatile private var stopped = false
    @Volatile var paused = false
        private set

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
    }

    fun start() {
        if (track != null) return
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bytes = maxOf(minBytes, sampleRate / 2 * 2)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t

        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        audioManager.requestAudioFocus(focus!!)

        t.play()
        thread = Thread({ writeLoop(t) }, "omleaf-audio").also { it.start() }
    }

    private fun writeLoop(t: AudioTrack) {
        val chunk = ShortArray(sampleRate / 10)
        val end = synth.totalFrames + synth.tailFrames
        var frame = 0L
        try {
            while (!stopped && frame < end) {
                val n = minOf(chunk.size.toLong(), end - frame).toInt()
                synth.render(frame, chunk, n)
                var off = 0
                while (off < n && !stopped) {
                    val w = t.write(chunk, off, n - off)
                    if (w < 0) return
                    off += w
                }
                frame += n
            }
            // Let the last chime finish before the track is released.
            while (!stopped && headFrames(t) < end) Thread.sleep(50)
        } catch (_: Exception) {
            // The track was released while we were writing. Nothing to do.
        }
    }

    private fun headFrames(t: AudioTrack): Long = t.playbackHeadPosition.toLong() and 0xFFFFFFFFL

    /** Milliseconds of the session played so far, never more than the plan's length. */
    fun elapsedMs(): Long {
        val t = track ?: return 0L
        return (headFrames(t) * 1000 / sampleRate).coerceIn(0L, plan.totalMs)
    }

    fun pause() {
        if (paused || stopped) return
        paused = true
        runCatching { track?.pause() }
    }

    fun resume() {
        if (!paused || stopped) return
        paused = false
        runCatching { track?.play() }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        val t = track
        runCatching { t?.pause() }
        runCatching { t?.flush() }
        runCatching { thread?.join(500) }
        runCatching { t?.release() }
        track = null
        focus?.let { audioManager.abandonAudioFocusRequest(it) }
        focus = null
    }
}
