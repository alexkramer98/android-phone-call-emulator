package com.example.telefoon

import android.content.Context
import android.media.AudioManager
import android.util.Log
import io.getstream.webrtc.StreamWebRtc
import io.getstream.webrtc.audio.StreamAudioTrack
import io.getstream.webrtc.config.StreamWebRtcConfig
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI

class SimpleWebRTCClient(private val context: Context) {

    private lateinit var webrtc: StreamWebRtc
    private lateinit var ws: WebSocketClient
    private lateinit var localAudio: StreamAudioTrack

    fun start() {
        // Force speakerphone
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = true

        // Initialize Stream WebRTC
        val config = StreamWebRtcConfig(audio = true, video = false)
        webrtc = StreamWebRtc(context, config)

        // Create local audio track
        localAudio = webrtc.createAudioTrack("local_audio")
        webrtc.addTrack(localAudio)

        // Listen for remote audio tracks
        webrtc.onRemoteAudioTrack { remoteTrack ->
            remoteTrack.play() // automatically plays through speaker
            Log.d("WebRTC", "Remote audio track added")
        }

        // Connect to signaling server
        ws = object : WebSocketClient(URI("ws://YOUR_SERVER_IP:8080")) {
            override fun onOpen(handshake: ServerHandshake?) {
                Log.d("WebRTC", "WebSocket connected")
                webrtc.createOffer { sdp ->
                    val json = """{"type":"offer","offer":{"type":"offer","sdp":"$sdp"}}"""
                    send(json)
                }
            }

            override fun onMessage(message: String) {
                val data = JSONObject(message)
                when (data.getString("type")) {
                    "answer" -> {
                        val sdp = data.getJSONObject("answer").getString("sdp")
                        webrtc.setRemoteDescription(sdp)
                    }
                    "candidate" -> {
                        val c = data.getJSONObject("candidate")
                        webrtc.addIceCandidate(
                            sdpMid = c.getString("sdpMid"),
                            sdpMLineIndex = c.getInt("sdpMLineIndex"),
                            candidate = c.getString("candidate")
                        )
                    }
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.d("WebRTC", "WebSocket closed: $reason")
            }

            override fun onError(ex: Exception) {
                Log.e("WebRTC", "WebSocket error", ex)
            }
        }
        ws.connect()
    }

    fun close() {
        try {
            ws.close()
            webrtc.release()
        } catch (_: Exception) {}
    }
}
