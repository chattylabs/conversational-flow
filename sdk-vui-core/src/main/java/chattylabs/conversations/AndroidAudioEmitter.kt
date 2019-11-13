/*
 * Copyright 2019 ChattyLabs
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chattylabs.conversations

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private const val TAG = "Audio"
private const val AMPLITUDE_THRESHOLD = 1500

private val SAMPLE_RATE_CANDIDATES = intArrayOf(44100, 22050, 16000, 11025)

/**
 * Emits microphone audio using a background [ScheduledExecutorService].
 */
@SuppressLint("NewApi")
class AudioEmitter {

    private val isLowerThan25 = Build.VERSION.SDK_INT <= Build.VERSION_CODES.M
    private val audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION
    private val encoding = AudioFormat.ENCODING_PCM_16BIT
    private val channel = AudioFormat.CHANNEL_IN_MONO
    private var mAudioRecorder: AudioRecord? = null
    private var mAudioExecutor: ScheduledExecutorService? = null
    private lateinit var mBuffer: ByteArray

    /** Start streaming  */
    fun start(subscriber: (buffer: ByteArray, size: Int) -> Unit) {
        mAudioExecutor = Executors.newSingleThreadScheduledExecutor()

        // start!
        Log.d(TAG, "Recording audio with buffer size of: ${mBuffer.size} bytes")
        getAudioRecorder().startRecording()

        var hasStartedSpeaking = false

        // stream bytes as they become available in chunks equal to the buffer size
        mAudioExecutor!!.scheduleAtFixedRate({
            // read audio data
            val read = if (isLowerThan25) getAudioRecorder().read(
                    mBuffer, 0, mBuffer.size) else getAudioRecorder().read(
                    mBuffer, 0, mBuffer.size, AudioRecord.READ_BLOCKING)

            if (!hasStartedSpeaking) hasStartedSpeaking = isHearingVoice(mBuffer, read)

            // send next chunk
            if (hasStartedSpeaking && read > 0) {
                subscriber(mBuffer, read)
            }
        }, 0, 50, TimeUnit.MILLISECONDS)
    }

    /** Stop Streaming  */
    fun stop() {
        // stop recording
        mAudioRecorder?.stop()
        mAudioRecorder?.release()
        mAudioRecorder = null

        // stop events
        mAudioExecutor?.shutdown()
        mAudioExecutor = null
    }

    /**
     * Retrieves the sample rate currently used to record audio.
     *
     * @return The sample rate of recorded audio.
     */
    fun getSampleRate() = getAudioRecorder().sampleRate

    private fun isHearingVoice(buffer: ByteArray, size: Int): Boolean {
        var i = 0
        while (i < size - 1) {
            // The buffer has LINEAR16 in little endian.
            var s = buffer[i + 1].toInt()
            if (s < 0) s *= -1
            s = s shl 8
            s += Math.abs(buffer[i].toInt())
            if (s > AMPLITUDE_THRESHOLD) {
                return true
            }
            i += 2
        }
        return false
    }

    private fun getAudioRecorder(): AudioRecord {
        if (mAudioRecorder == null) {
            for (sampleRate in SAMPLE_RATE_CANDIDATES) {
                val sizeInBytes = AudioRecord.getMinBufferSize(sampleRate, channel, encoding)
                if (sizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
                    continue
                }

                mBuffer = ByteArray(4 * sizeInBytes)

                // create and configure recorder
                // Note: ensure settings are match the speech recognition config

                mAudioRecorder = if (isLowerThan25) {
                    AudioRecord(audioSource, sampleRate, channel, encoding, sizeInBytes)
                } else {
                    AudioRecord.Builder()
                            .setAudioSource(audioSource)
                            .setAudioFormat(AudioFormat.Builder()
                                    .setEncoding(encoding)
                                    .setSampleRate(sampleRate)
                                    .setChannelMask(channel)
                                    .build())
                            .setBufferSizeInBytes(sizeInBytes)
                            .build()
                }

                if (mAudioRecorder!!.state != AudioRecord.STATE_INITIALIZED) {
                    mAudioRecorder!!.release()
                } else break
            }
        }

        return mAudioRecorder!!
    }
}
