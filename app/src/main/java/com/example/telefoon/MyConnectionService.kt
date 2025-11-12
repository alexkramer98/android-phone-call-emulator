package com.example.telefoon

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import kotlin.math.abs

@SuppressLint("MissingPermission")
class MyConnectionService : ConnectionService() {
    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ): Connection {
        val connection = object : Connection() {
            private val handler = Handler(Looper.getMainLooper())

            private var toneGenerator: ToneGenerator? = null
            private var beepRunnable: Runnable? = null
            private var audioRecord: AudioRecord? = null
            private var isRecording = false
            private var recordingThread: Thread? = null
            private var isAudioStarted = false // Flag to ensure we only start once

            override fun onAnswer() {
                Log.d("MyConnectionService", "Call answered. Setting state and waiting for audio callback.")
                setAudioModeIsVoip(true)
                setActive()
            }

            // This is the crucial callback from the system
            override fun onCallAudioStateChanged(state: CallAudioState?) {
                super.onCallAudioStateChanged(state)
                Log.d("MyConnectionService", "onCallAudioStateChanged received. Muted: ${state?.isMuted}, Route: ${state?.route}")

                // Start audio processing only once, after the state is confirmed.
                if (!isAudioStarted) {
                    isAudioStarted = true
                    Log.d("MyConnectionService", "Audio state confirmed. Starting beeps and mic capture.")
                    startBeeping()
                    startMicCapture()
                }
            }

            private fun startBeeping() {
                toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 100)
                beepRunnable = object : Runnable {
                    override fun run() {
                        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                        handler.postDelayed(this, 1000)
                    }
                }
                handler.post(beepRunnable!!)
            }

            private fun startMicCapture() {
                val sampleRate = 8000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

                if (bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e("MyConnectionService", "AudioRecord: Invalid parameters.")
                    return
                }

                try {
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize * 2 // Use a slightly larger buffer for safety
                    )

                    if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                        Log.e("MyConnectionService", "AudioRecord failed to initialize.")
                        return
                    }

                    isRecording = true
                    recordingThread = Thread {
                        val buffer = ShortArray(bufferSize)
                        audioRecord?.startRecording()
                        Log.d("MyConnectionService", "AudioRecord started successfully.")

                        while (isRecording) {
                            val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                            if (readSize > 0) {
                                var sum = 0L
                                for (i in 0 until readSize) {
                                    sum += abs(buffer[i].toInt())
                                }
                                val amplitude = if (readSize > 0) (sum / readSize) else 0
                                // Only log if amplitude is not zero to reduce log spam
                                if (amplitude > 0) {
                                    Log.d("MyConnectionService", "Mic amplitude: $amplitude")
                                }
                            }
                        }
                        Log.d("MyConnectionService", "Recording loop finished.")
                    }.apply { start() } // Initialize and start the thread

                } catch (e: Exception) {
                    Log.e("MyConnectionService", "Failed to create AudioRecord", e)
                }
            }

            override fun onReject() {
                Log.d("MyConnectionService", "Call rejected")
                cleanup()
                setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.REJECTED))
                destroy()
            }

            override fun onDisconnect() {
                Log.d("MyConnectionService", "Call disconnected")
                cleanup()
                setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.LOCAL))
                destroy()
            }

            private fun cleanup() {
                handler.removeCallbacksAndMessages(null)
                toneGenerator?.release()
                toneGenerator = null

                isRecording = false
                try {
                    recordingThread?.join(200) // Wait a bit for the thread to finish
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null

                Log.d("MyConnectionService", "Cleanup complete.")
            }
        }

        connection.setAddress(request.address, TelecomManager.PRESENTATION_ALLOWED)
        connection.setCallerDisplayName("Robot", TelecomManager.PRESENTATION_ALLOWED)
        connection.setRinging()
        Log.d("MyConnectionService", "Connection set to ringing")

        return connection
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection? {
        return null
    }
}