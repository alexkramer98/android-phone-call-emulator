package com.example.telefoon

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
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
import eu.buney.kopus.OpusApplication
import eu.buney.kopus.OpusDecoder
import eu.buney.kopus.OpusEncoder
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class MyConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "MyConnectionService"
        private const val NOTIFICATION_CHANNEL_ID = "MyConnectionServiceChannel"
        private const val SERVER_HOST = "192.168.2.87" // Change to your server IP
        private const val SERVER_PORT = 9000
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE = 320 // 20ms at 16kHz
        private const val CHANNELS = 1

        init {
            System.loadLibrary("opus_jni")
        }
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ): Connection {
        val connection = object : Connection() {
            private val handler = Handler(Looper.getMainLooper())
            private var audioRecord: AudioRecord? = null
            private var audioTrack: AudioTrack? = null
            private var udpSocket: DatagramSocket? = null
            private var isActive = false
            private lateinit var receiveThread: Thread
            private lateinit var sendThread: Thread

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
                connectToServer()
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
            private fun connectToServer() {
                thread(start = true) {
                    try {
                        udpSocket = DatagramSocket()
                        isActive = true

                        // Send initial handshake
                        val handshake = "CONNECT".toByteArray()
                        val packet = DatagramPacket(
                            handshake,
                            handshake.size,
                            InetAddress.getByName(SERVER_HOST),
                            SERVER_PORT
                        )
                        udpSocket?.send(packet)
                        Log.d(TAG, "Sent handshake to server")

                        // Start receiving audio from server
                        startAudioPlayback()

                        // After a delay, start sending mic audio
                        handler.postDelayed({
                            if (isActive) {
                                startAudioRecording()
                            }
                        }, 3000) // Wait 3 seconds before sending mic

                    } catch (e: Exception) {
                        Log.e(TAG, "Error connecting to server", e)
                    }
                }
            }

            private fun startAudioPlayback() {
                val bufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()

                receiveThread = thread(start = true) {
                    try {
                        val decoder = OpusDecoder(sampleRate = SAMPLE_RATE, channels = CHANNELS)
                        val opusBuffer = ByteArray(1024) // Encoded opus data
                        val pcmBuffer = ShortArray(FRAME_SIZE) // Decoded PCM samples
                        val packet = DatagramPacket(opusBuffer, opusBuffer.size)

                        while (isActive) {
                            try {
                                udpSocket?.receive(packet)
                                val opusData = packet.data.copyOfRange(0, packet.length)

                                // Decode Opus to PCM
                                val decodedSamples = decoder.decode(
                                    opusData,
                                    0,
                                    opusData.size,
                                    pcmBuffer,
                                    0,
                                    FRAME_SIZE,
                                    false
                                )

                                if (decodedSamples > 0) {
                                    // Write decoded PCM to AudioTrack
                                    audioTrack?.write(pcmBuffer, 0, decodedSamples)
                                    Log.d(TAG, "Received ${opusData.size} Opus bytes, decoded to $decodedSamples PCM samples")
                                }
                            } catch (e: Exception) {
                                if (isActive) {
                                    Log.e(TAG, "Error receiving/decoding audio", e)
                                }
                            }
                        }
                        decoder.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error initializing Opus decoder", e)
                    }
                }
                Log.d(TAG, "Started audio playback with Opus decoding")
            }

            @RequiresPermission(Manifest.permission.RECORD_AUDIO)
            private fun startAudioRecording() {
                val bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                audioRecord?.startRecording()

                sendThread = thread(start = true) {
                    try {
                        val encoder = OpusEncoder(
                            sampleRate = SAMPLE_RATE,
                            channels = CHANNELS,
                            application = OpusApplication.Voip
                        )
                        val pcmBuffer = ShortArray(FRAME_SIZE) // PCM samples
                        val opusBuffer = ByteArray(1024) // Encoded opus data
                        val serverAddress = InetAddress.getByName(SERVER_HOST)

                        while (isActive) {
                            try {
                                val readSamples = audioRecord?.read(pcmBuffer, 0, FRAME_SIZE) ?: 0
                                if (readSamples == FRAME_SIZE) {
                                    // Encode PCM to Opus
                                    val encodedBytes = encoder.encode(pcmBuffer, 0, FRAME_SIZE, opusBuffer, 0, opusBuffer.size)

                                    if (encodedBytes > 0) {
                                        val packet = DatagramPacket(
                                            opusBuffer,
                                            encodedBytes,
                                            serverAddress,
                                            SERVER_PORT
                                        )
                                        udpSocket?.send(packet)
                                        Log.d(TAG, "Sent $encodedBytes Opus bytes (from $readSamples PCM samples)")
                                    }
                                }
                            } catch (e: Exception) {
                                if (isActive) {
                                    Log.e(TAG, "Error encoding/sending audio", e)
                                }
                            }
                        }
                        encoder.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error initializing Opus encoder", e)
                    }
                }
                Log.d(TAG, "Started audio recording and Opus encoding")
            }

            private fun cleanup() {
                isActive = false

                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null

                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null

                udpSocket?.close()
                udpSocket = null

                Log.d(TAG, "Cleaned up all audio resources")
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

        startForeground(1, notification)
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