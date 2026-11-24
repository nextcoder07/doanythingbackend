package com.hakdaar.app.platform.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AudioRecorder that records 16kHz, 16-bit PCM mono audio directly into RAM.
 * Stops automatically at 55 seconds or when stop() is called.
 * Produces standard WAV bytes in-memory. Never writes to disk.
 */
class AudioRecorder {

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val MAX_RECORDING_SECONDS = 55
        const val MAX_BYTES = MAX_RECORDING_SECONDS * SAMPLE_RATE * 2 // 1,760,000 bytes
    }

    @Volatile
    private var isRecording = false
    private var audioRecord: AudioRecord? = null

    @SuppressLint("MissingPermission")
    suspend fun recordAudio(
        onProgressSeconds: ((Int) -> Unit)? = null
    ): ByteArray = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(4096)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        audioRecord = record

        val pcmOutputStream = ByteArrayOutputStream()
        val tempBuffer = ByteArray(bufferSize)

        record.startRecording()
        isRecording = true

        val startTime = System.currentTimeMillis()
        var lastReportedSec = 0

        try {
            while (isRecording && pcmOutputStream.size() < MAX_BYTES) {
                val bytesRead = record.read(tempBuffer, 0, tempBuffer.size)
                if (bytesRead > 0) {
                    pcmOutputStream.write(tempBuffer, 0, bytesRead)
                }

                val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                if (elapsedSec != lastReportedSec) {
                    lastReportedSec = elapsedSec
                    onProgressSeconds?.invoke(elapsedSec)
                }

                if (elapsedSec >= MAX_RECORDING_SECONDS) {
                    break
                }
            }
        } finally {
            try {
                record.stop()
            } catch (e: Exception) {
                // Ignore stop error
            }
            record.release()
            audioRecord = null
            isRecording = false
        }

        val rawPcm = pcmOutputStream.toByteArray()
        val wavBytes = createWavBytes(rawPcm, SAMPLE_RATE, 1, 16)

        // Clear raw PCM buffer
        rawPcm.fill(0)

        return@withContext wavBytes
    }

    fun stop() {
        isRecording = false
    }

    private fun createWavBytes(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * (bitsPerSample / 8)

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            // RIFF chunk descriptor
            put('R'.code.toByte()); put('I'.code.toByte()); put('F'.code.toByte()); put('F'.code.toByte())
            putInt(totalDataLen)
            put('W'.code.toByte()); put('A'.code.toByte()); put('V'.code.toByte()); put('E'.code.toByte())

            // "fmt " sub-chunk
            put('f'.code.toByte()); put('m'.code.toByte()); put('t'.code.toByte()); put(' '.code.toByte())
            putInt(16) // Subchunk1Size for PCM
            putShort(1.toShort()) // AudioFormat 1 = PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort((channels * (bitsPerSample / 8)).toShort()) // BlockAlign
            putShort(bitsPerSample.toShort())

            // "data" sub-chunk
            put('d'.code.toByte()); put('a'.code.toByte()); put('t'.code.toByte()); put('a'.code.toByte())
            putInt(totalAudioLen)
        }.array()

        val wavStream = ByteArrayOutputStream(44 + totalAudioLen)
        wavStream.write(header)
        wavStream.write(pcmData)
        return wavStream.toByteArray()
    }
}
