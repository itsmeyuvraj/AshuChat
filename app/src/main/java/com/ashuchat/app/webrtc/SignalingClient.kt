package com.ashuchat.app.webrtc

import android.util.Log
import com.ashuchat.app.data.model.SignalingPayload
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.webrtc.IceCandidate
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Serverless Signaling Client
 *
 * Supports two serverless modes with ZERO user server hosting required:
 * 1. Firebase Realtime Database (Serverless Cloud via REST SSE stream)
 * 2. Public Cloud WebSocket Broker (e.g. wss://...)
 */
class SignalingClient(
    private val listener: Listener
) {
    interface Listener {
        fun onSignalingConnected()
        fun onSignalingDisconnected()
        fun onPeerJoined(clientId: String)
        fun onPeerLeft(clientId: String)
        fun onOfferReceived(sdp: String)
        fun onAnswerReceived(sdp: String)
        fun onIceCandidateReceived(candidate: IceCandidate)
        fun onError(message: String)
    }

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Infinite for streaming / websockets
        .retryOnConnectionFailure(true)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var sseJob: Job? = null
    private var webSocket: WebSocket? = null

    private var currentRoomId: String? = null
    private var currentClientId: String? = null
    private var currentServerUrl: String = ""
    private var isFirebaseMode = false

    // Keep track of received candidate IDs to prevent duplicate processing
    private val processedCandidateKeys = hashSetOf<String>()

    companion object {
        private const val TAG = "SignalingClient"
    }

    fun connect(serverUrl: String, roomId: String, clientId: String) {
        currentRoomId = roomId
        currentClientId = clientId
        currentServerUrl = serverUrl.trim()
        processedCandidateKeys.clear()

        disconnect()

        isFirebaseMode = currentServerUrl.contains("firebaseio.com") ||
                currentServerUrl.startsWith("http://") ||
                currentServerUrl.startsWith("https://")

        if (isFirebaseMode) {
            connectFirebaseServerless(currentServerUrl, roomId, clientId)
        } else {
            connectWebSocket(currentServerUrl, roomId, clientId)
        }
    }

    // =========================================================================
    // Mode 1: Firebase Realtime Database Serverless (REST SSE Streaming)
    // =========================================================================

    private fun connectFirebaseServerless(baseUrl: String, roomId: String, clientId: String) {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val roomUrl = "$normalizedBaseUrl/rooms/$roomId.json"

        Log.d(TAG, "Connecting to Serverless Firebase Realtime DB at: $roomUrl")

        sseJob = scope.launch {
            try {
                // 1. Announce peer presence
                announcePeerPresence(normalizedBaseUrl, roomId, clientId)

                // 2. Open SSE stream with Accept: text/event-stream
                val request = Request.Builder()
                    .url(roomUrl)
                    .header("Accept", "text/event-stream")
                    .build()

                val call = client.newCall(request)
                val response = call.execute()

                if (!response.isSuccessful) {
                    listener.onError("Serverless Firebase connection failed: ${response.code}")
                    return@launch
                }

                listener.onSignalingConnected()

                val reader = BufferedReader(response.body?.charStream())
                var line: String? = null

                var currentEvent: String? = null
                val dataBuffer = StringBuilder()

                while (isActive && reader.readLine().also { line = it } != null) {
                    val l = line ?: break
                    if (l.startsWith("event:")) {
                        currentEvent = l.substring(6).trim()
                    } else if (l.startsWith("data:")) {
                        dataBuffer.append(l.substring(5).trim())
                    } else if (l.isEmpty()) {
                        // End of SSE message block
                        if (currentEvent != null && dataBuffer.isNotEmpty()) {
                            val dataStr = dataBuffer.toString()
                            if (currentEvent == "put" || currentEvent == "patch") {
                                handleFirebaseSseData(dataStr)
                            }
                        }
                        currentEvent = null
                        dataBuffer.setLength(0)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Serverless Firebase SSE error: ${e.localizedMessage}")
                    listener.onSignalingDisconnected()
                }
            }
        }
    }

    private fun announcePeerPresence(baseUrl: String, roomId: String, clientId: String) {
        val body = JsonObject().apply {
            addProperty("online", true)
            addProperty("joinedAt", System.currentTimeMillis())
        }.toString().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("$baseUrl/rooms/$roomId/peers/$clientId.json")
            .put(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to announce peer presence: ${e.localizedMessage}")
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    private fun handleFirebaseSseData(jsonStr: String) {
        try {
            val root = gson.fromJson(jsonStr, JsonObject::class.java) ?: return
            val path = root.get("path")?.asString ?: "/"
            val dataElem = root.get("data")

            if (dataElem == null || dataElem.isJsonNull) return

            when {
                // Full room snapshot
                path == "/" && dataElem.isJsonObject -> {
                    val roomObj = dataElem.asJsonObject
                    // Check other peers
                    if (roomObj.has("peers")) {
                        val peers = roomObj.getAsJsonObject("peers")
                        for (entry in peers.entrySet()) {
                            if (entry.key != currentClientId) {
                                listener.onPeerJoined(entry.key)
                            }
                        }
                    }
                    // Check Offer
                    if (roomObj.has("offer")) {
                        val offer = roomObj.getAsJsonObject("offer")
                        val sender = offer.get("senderId")?.asString
                        if (sender != currentClientId && offer.has("sdp")) {
                            listener.onOfferReceived(offer.get("sdp").asString)
                        }
                    }
                    // Check Answer
                    if (roomObj.has("answer")) {
                        val answer = roomObj.getAsJsonObject("answer")
                        val sender = answer.get("senderId")?.asString
                        if (sender != currentClientId && answer.has("sdp")) {
                            listener.onAnswerReceived(answer.get("sdp").asString)
                        }
                    }
                    // Check Candidates
                    if (roomObj.has("candidates")) {
                        parseFirebaseCandidates(roomObj.getAsJsonObject("candidates"))
                    }
                }

                // Peer joined
                path.startsWith("/peers/") -> {
                    val peerId = path.removePrefix("/peers/")
                    if (peerId != currentClientId) {
                        listener.onPeerJoined(peerId)
                    }
                }

                // Offer arrived
                path == "/offer" && dataElem.isJsonObject -> {
                    val offer = dataElem.asJsonObject
                    val sender = offer.get("senderId")?.asString
                    if (sender != currentClientId && offer.has("sdp")) {
                        listener.onOfferReceived(offer.get("sdp").asString)
                    }
                }

                // Answer arrived
                path == "/answer" && dataElem.isJsonObject -> {
                    val answer = dataElem.asJsonObject
                    val sender = answer.get("senderId")?.asString
                    if (sender != currentClientId && answer.has("sdp")) {
                        listener.onAnswerReceived(answer.get("sdp").asString)
                    }
                }

                // Candidate arrived
                path.startsWith("/candidates") -> {
                    if (dataElem.isJsonObject) {
                        parseFirebaseCandidateObject(dataElem.asJsonObject, path)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Firebase SSE data: ${e.localizedMessage}", e)
        }
    }

    private fun parseFirebaseCandidates(candidatesObj: JsonObject) {
        for (entry in candidatesObj.entrySet()) {
            val key = entry.key
            val candElem = entry.value
            if (candElem.isJsonObject) {
                parseFirebaseCandidateObject(candElem.asJsonObject, key)
            }
        }
    }

    private fun parseFirebaseCandidateObject(candObj: JsonObject, key: String) {
        val sender = candObj.get("senderId")?.asString
        if (sender != currentClientId && !processedCandidateKeys.contains(key)) {
            processedCandidateKeys.add(key)
            val sdp = candObj.get("sdp")?.asString
            val sdpMid = candObj.get("sdpMid")?.asString
            val sdpMLineIndex = candObj.get("sdpMLineIndex")?.asInt

            if (sdp != null && sdpMid != null && sdpMLineIndex != null) {
                val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                listener.onIceCandidateReceived(candidate)
            }
        }
    }

    // =========================================================================
    // Mode 2: Public Cloud WebSocket Broker Mode
    // =========================================================================

    private fun connectWebSocket(serverUrl: String, roomId: String, clientId: String) {
        val normalizedUrl = if (serverUrl.startsWith("http://")) {
            serverUrl.replace("http://", "ws://")
        } else if (serverUrl.startsWith("https://")) {
            serverUrl.replace("https://", "wss://")
        } else if (!serverUrl.startsWith("ws://") && !serverUrl.startsWith("wss://")) {
            "ws://$serverUrl"
        } else {
            serverUrl
        }

        val request = Request.Builder().url(normalizedUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Signaling WebSocket opened.")
                listener.onSignalingConnected()

                val joinMessage = SignalingPayload(
                    type = "join",
                    roomId = roomId,
                    clientId = clientId
                )
                sendMessageWs(joinMessage)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleWebSocketMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onSignalingDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Signaling WebSocket error: ${t.localizedMessage}", t)
                listener.onError("Connection error: ${t.localizedMessage}")
                listener.onSignalingDisconnected()
            }
        })
    }

    private fun handleWebSocketMessage(text: String) {
        try {
            val payload = gson.fromJson(text, SignalingPayload::class.java) ?: return
            when (payload.type) {
                "peer_joined" -> listener.onPeerJoined(payload.clientId ?: "partner")
                "peer_left" -> listener.onPeerLeft(payload.clientId ?: "partner")
                "offer" -> payload.sdp?.let { listener.onOfferReceived(it) }
                "answer" -> payload.sdp?.let { listener.onAnswerReceived(it) }
                "ice_candidate" -> {
                    if (payload.sdp != null && payload.sdpMid != null && payload.sdpMLineIndex != null) {
                        listener.onIceCandidateReceived(
                            IceCandidate(payload.sdpMid, payload.sdpMLineIndex, payload.sdp)
                        )
                    }
                }
                "error" -> listener.onError(payload.message ?: "Signaling error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing WebSocket message: ${e.localizedMessage}")
        }
    }

    // =========================================================================
    // Public Sending API (Handles both Firebase & WebSocket automatically)
    // =========================================================================

    fun sendOffer(sdp: String) {
        if (isFirebaseMode) {
            val baseUrl = currentServerUrl.trimEnd('/')
            val roomId = currentRoomId ?: return
            val body = JsonObject().apply {
                addProperty("sdp", sdp)
                addProperty("senderId", currentClientId)
                addProperty("timestamp", System.currentTimeMillis())
            }.toString().toRequestBody(jsonMediaType)

            val req = Request.Builder()
                .url("$baseUrl/rooms/$roomId/offer.json")
                .put(body)
                .build()
            client.newCall(req).enqueue(NoOpCallback)
        } else {
            sendMessageWs(
                SignalingPayload(
                    type = "offer",
                    roomId = currentRoomId,
                    clientId = currentClientId,
                    sdp = sdp
                )
            )
        }
    }

    fun sendAnswer(sdp: String) {
        if (isFirebaseMode) {
            val baseUrl = currentServerUrl.trimEnd('/')
            val roomId = currentRoomId ?: return
            val body = JsonObject().apply {
                addProperty("sdp", sdp)
                addProperty("senderId", currentClientId)
                addProperty("timestamp", System.currentTimeMillis())
            }.toString().toRequestBody(jsonMediaType)

            val req = Request.Builder()
                .url("$baseUrl/rooms/$roomId/answer.json")
                .put(body)
                .build()
            client.newCall(req).enqueue(NoOpCallback)
        } else {
            sendMessageWs(
                SignalingPayload(
                    type = "answer",
                    roomId = currentRoomId,
                    clientId = currentClientId,
                    sdp = sdp
                )
            )
        }
    }

    fun sendIceCandidate(candidate: IceCandidate) {
        if (isFirebaseMode) {
            val baseUrl = currentServerUrl.trimEnd('/')
            val roomId = currentRoomId ?: return
            val body = JsonObject().apply {
                addProperty("sdp", candidate.sdp)
                addProperty("sdpMid", candidate.sdpMid)
                addProperty("sdpMLineIndex", candidate.sdpMLineIndex)
                addProperty("senderId", currentClientId)
                addProperty("timestamp", System.currentTimeMillis())
            }.toString().toRequestBody(jsonMediaType)

            val req = Request.Builder()
                .url("$baseUrl/rooms/$roomId/candidates.json")
                .post(body)
                .build()
            client.newCall(req).enqueue(NoOpCallback)
        } else {
            sendMessageWs(
                SignalingPayload(
                    type = "ice_candidate",
                    roomId = currentRoomId,
                    clientId = currentClientId,
                    sdp = candidate.sdp,
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex
                )
            )
        }
    }

    private fun sendMessageWs(payload: SignalingPayload) {
        try {
            val json = gson.toJson(payload)
            webSocket?.send(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send WS message: ${e.localizedMessage}")
        }
    }

    fun disconnect() {
        try {
            sseJob?.cancel()
            sseJob = null

            if (isFirebaseMode && currentRoomId != null && currentClientId != null) {
                // Delete peer presence on exit
                val baseUrl = currentServerUrl.trimEnd('/')
                val roomId = currentRoomId
                val clientId = currentClientId
                val req = Request.Builder()
                    .url("$baseUrl/rooms/$roomId/peers/$clientId.json")
                    .delete()
                    .build()
                client.newCall(req).enqueue(NoOpCallback)
            }

            webSocket?.close(1000, "Normal Closure")
            webSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting: ${e.localizedMessage}")
        }
    }

    private object NoOpCallback : Callback {
        override fun onFailure(call: Call, e: IOException) {}
        override fun onResponse(call: Call, response: Response) {
            response.close()
        }
    }
}
