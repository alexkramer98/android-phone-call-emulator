package com.example.telefoon

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log

class MyConnectionService : ConnectionService() {
    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ): Connection {
        val connection = object : Connection() {
            private var toneGenerator: ToneGenerator? = null
            private val handler = Handler(Looper.getMainLooper())
            private var beepRunnable: Runnable? = null

            override fun onAnswer() {
                Log.d("MyConnectionService", "Call answered")
                setActive()

                // Initialize the ToneGenerator to play beeps on the voice call audio stream.
                toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 100) // 100 is max volume

                // Define a runnable that plays a beep and then schedules itself to run again.
                beepRunnable = object : Runnable {
                    override fun run() {
                        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200) // Play a 200ms beep
                        handler.postDelayed(this, 1000) // Schedule to run again in 1 second
                    }
                }

                // Start the beeping loop.
                handler.post(beepRunnable!!)
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
                // IMPORTANT: Stop the handler from running again.
                beepRunnable?.let { handler.removeCallbacks(it) }
                beepRunnable = null

                // Stop any playing tone and release the generator.
                toneGenerator?.stopTone()
                toneGenerator?.release()
                toneGenerator = null
            }
        }

        val address = request.address
        connection.setAddress(address, TelecomManager.PRESENTATION_ALLOWED)
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