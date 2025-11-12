package com.example.telefoon

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlin.concurrent.thread
import kotlin.math.sqrt

class MyConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "MyConnectionService"
        private const val NOTIFICATION_CHANNEL_ID = "MyConnectionServiceChannel"
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ): Connection {
        val connection = object : Connection() {
            private var toneGenerator: ToneGenerator? = null
            private val handler = Handler(Looper.getMainLooper())
            private var beepRunnable: Runnable? = null
            private var audioRecord: AudioRecord? = null
            private var isRecording = false
            private lateinit var recordingThread: Thread

            init {
                setAudioModeIsVoip(true)
            }

            override fun onPlayDtmfTone(c: Char) {
                Log.d(TAG, "DTMF tones: $c")
            }

            @RequiresPermission(Manifest.permission.RECORD_AUDIO)
            override fun onAnswer() {
                Log.d(TAG, "Call answered")
                setActive()
                startForegroundService()
                startRecording()
                startToneGenerator();
            }

            override fun onReject() {
                Log.d(TAG, "Call rejected")
                cleanup()
                setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
                destroy()
            }

            override fun onDisconnect() {
                Log.d(TAG, "Call disconnected")
                cleanup()
                setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                destroy()
            }

            @RequiresPermission(Manifest.permission.RECORD_AUDIO)
            private fun startRecording() {
                val sampleRate = 8000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT

                val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                isRecording = true
                audioRecord?.startRecording()

                recordingThread = thread(start = true) {
                    val buffer = ShortArray(bufferSize / 2) // short is 2 bytes
                    while (isRecording) {
                        val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (readResult > 0) {
                            var sumOfSquares = 0.0
                            for (i in 0 until readResult) {
                                sumOfSquares += buffer[i] * buffer[i]
                            }
                            val amplitude = sqrt(sumOfSquares / readResult)
                            Log.d(TAG, "Mic amplitude: $amplitude, Bytes read: $readResult")
                        }
                    }
                }
                Log.d(TAG, "Started recording")
            }

            private fun startToneGenerator() {
                toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 100)

                beepRunnable = object : Runnable {
                    override fun run() {
                        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                        handler.postDelayed(this, 1000)
                    }
                }

                handler.post(beepRunnable!!)
            }

            private fun cleanup() {
                isRecording = false

                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null

                beepRunnable?.let { handler.removeCallbacks(it) }
                beepRunnable = null
                toneGenerator?.stopTone()
                toneGenerator?.release()
                toneGenerator = null

                Log.d(TAG, "Stopped recording and released resources")
                stopForegroundService()
            }
        }

        connection.setAddress(request.address, TelecomManager.PRESENTATION_ALLOWED)
        connection.setCallerDisplayName("Robot", TelecomManager.PRESENTATION_ALLOWED)
        connection.setRinging()

        Log.d(TAG, "Incoming call, connection set to ringing")

        return connection
    }

    private fun startForegroundService() {
        createNotificationChannel()

        val notificationBuilder = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        val notification = notificationBuilder.build()

        startForeground(
            1,
            notification,
        )
        Log.d(TAG, "Foreground service started.")
    }

    private fun stopForegroundService() {
        Log.d(TAG, "Stopping foreground service.")
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Connection Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection? {
        return null
    }
}