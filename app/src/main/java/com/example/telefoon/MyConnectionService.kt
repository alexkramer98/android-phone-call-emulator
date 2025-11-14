package com.example.telefoon

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlin.concurrent.thread
import kotlin.math.sqrt

class MyConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "MyConnectionService"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "MyConnectionServiceChannel"
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ): Connection {
        val connection = object : Connection() {
            private var audioRecord: AudioRecord? = null
            private var isRecording = false
            private lateinit var recordingThread: Thread

            private val sampleRate = 8000
            private val channelConfig = AudioFormat.CHANNEL_IN_MONO
            private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            private var bufferSize = 0

            // This init block is executed when the Connection object is created.
            init {
                // *** THIS IS THE CRITICAL CHANGE ***
                // Tell the Telecom framework that this is a VoIP call and our app will
                // be managing the audio stream. This is essential for the mic to activate.
                setAudioModeIsVoip(true)
            }

            override fun onAnswer() {
                Log.d(TAG, "Call answered")
                setActive()
                startForegroundService()
                startRecording()
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

            private fun startRecording() {
                if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "RECORD_AUDIO permission not granted.")
                    return
                }

                bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                if (bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "Invalid AudioRecord parameters.")
                    return
                }

                audioRecord = AudioRecord(
                    // Using MIC is often more reliable than VOICE_COMMUNICATION for direct access.
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord could not be initialized.")
                    return
                }

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

            private fun cleanup() {
                isRecording = false
                try {
                    if (this::recordingThread.isInitialized) {
                        recordingThread.join(100)
                    }
                } catch (e: InterruptedException) {
                    Log.e(TAG, "Interrupted while waiting for recording thread to finish", e)
                }

                if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord?.stop()
                }
                audioRecord?.release()
                audioRecord = null
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

    // The rest of the file (startForegroundService, createNotification, etc.) remains the same.
    // ...
    private fun startForegroundService() {
        createNotificationChannel()
        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "Foreground service started.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
        }
    }

    private fun stopForegroundService() {
        Log.d(TAG, "Stopping foreground service.")
        stopForeground(true)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Connection Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return notificationBuilder
            .setContentTitle("Active Call")
            .setContentText("Your call is currently in progress.")
            .setSmallIcon(android.R.drawable.ic_menu_call) // Replace with your own icon
            .setCategory(Notification.CATEGORY_CALL)
            .build()
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection? {
        // Not implemented for this example
        return null
    }
}