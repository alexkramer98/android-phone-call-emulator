package com.example.telefoon

import android.net.Uri
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log

class MyConnectionService : ConnectionService() {

    // Hold the WebRTC client
    private var webrtcClient: SimpleWebRTCClient? = null

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ): Connection {
        val connection = object : Connection() {

            override fun onAnswer() {
                Log.d("MyConnectionService", "Call answered")
                setActive()

                // Start WebRTC audio connection
                webrtcClient = SimpleWebRTCClient(applicationContext)
                webrtcClient?.start()
            }

            override fun onReject() {
                Log.d("MyConnectionService", "Call rejected")
                stopWebRTC()
                setDisconnected(
                    android.telecom.DisconnectCause(
                        android.telecom.DisconnectCause.REJECTED
                    )
                )
                destroy()
            }

            override fun onDisconnect() {
                Log.d("MyConnectionService", "Call disconnected")
                stopWebRTC()
                setDisconnected(
                    android.telecom.DisconnectCause(
                        android.telecom.DisconnectCause.LOCAL
                    )
                )
                destroy()
            }
        }

        val address = request.address
        connection.setAddress(address, TelecomManager.PRESENTATION_ALLOWED)
        connection.setCallerDisplayName("Robot", TelecomManager.PRESENTATION_ALLOWED)
        connection.setRinging()

        return connection
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection? {
        // We are only handling incoming calls in this example
        return null
    }

    private fun stopWebRTC() {
        webrtcClient?.close()
        webrtcClient = null
    }
}
