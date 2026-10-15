package com.ashuchat.app.webrtc

import android.util.Log
import com.ashuchat.app.data.model.DataChannelPacket
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

enum class SignalingMode {
    NTFY_SERVERLESS,
    WEBSOCKET,
    FIREBASE
}

/**
 * Universal Signaling Client
 *
 * Supports 3 signaling modes:
 * 1. Public Serverless Relay (ntfy.sh - 100% free, zero configuration, zero servers to host)
 * 2. Dedicated WebSocket Relay (ws:// or wss://, e.g. local Wi-Fi or hosted broker)
 * 3. Firebase Realtime Database (REST SSE for custom user Firebase projects)
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
        fun onOfflinePacketReceived(packet: DataChannelPacket)
        fun onOfflineAckReceived(messageId: String, status: String)
        fun onError(message: String)
    }

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val textMediaType = "text/plain; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Infinite for streaming / websockets
        .retryOnConnectionFailure(true)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var streamJob: Job? = null
    private var webSocket: WebSocket? = null

    private var currentRoomId: String? = null
    private var currentClientId: String? = null
    private var currentServerUrl: String = ""
    private var activeMode: SignalingMode = SignalingMode.NTFY_SERVERLESS

    private val processedCandidateKeys = hashSetOf<String>()

    companion object {
        private const val TAG = "SignalingClient"

        /**
         * Test whether a signaling server or relay URL is reachable.
         */
        fun testConnection(serverUrl: String, onResult: (Boolean, String) -> Unit) {
            val url = serverUrl.trim()
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .build()

            val testScope = CoroutineScope(Dispatchers.IO)
            testScope.launch {
                try {
                    when {
                        url.startsWith("ws://") || url.startsWith("wss://") -> {
                            val request = Request.Builder().url(url).build()
                            var replied = false
                            val ws = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                                override fun onOpen(webSocket: WebSocket, response: Response) {
                                    if (!replied) {
                                        replied = true
                                        onResult(true, "WebSocket connected successfully (HTTP 101)")
                                        webSocket.close(1000, "Test complete")
                                    }
                                }

                                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                                    if (!replied) {
                                        replied = true
                                        val code = response?.code
                                        val msg = if (code != null) "HTTP $code" else t.localizedMessage ?: "Connection refused"
                                        onResult(false, "WebSocket failed: $msg")
                                    }
                                }
                            })
                            delay(5000)
                            if (!replied) {
                                replied = true
                                ws.cancel()
                                onResult(false, "WebSocket connection timed out (5s)")
                            }
                        }

                        url.contains("firebaseio.com") || url.contains("firebasedatabase.app") -> {
                            val clean = url.trimEnd('/')
                            val req = Request.Builder().url("$clean/.json?shallow=true").build()
                            val resp = okHttpClient.newCall(req).execute()
                            if (resp.isSuccessful) {
                                onResult(true, "Firebase Realtime DB reachable (HTTP ${resp.code})")
                            } else {
                                onResult(false, "Firebase returned HTTP ${resp.code}: ${resp.message}")
                            }
                            resp.close()
                        }

                        else -> {
                            val clean = url.trimEnd('/')
                            val testTopic = "ashuchat_ping_${System.currentTimeMillis() % 1000}"
                            val req = Request.Builder()
                                .url("$clean/$testTopic/json?poll=1")
                                .header("User-Agent", "AshuChat-Android/1.0")
                                .build()
                            val resp = okHttpClient.newCall(req).execute()
                            if (resp.isSuccessful) {
                                onResult(true, "Serverless Cloud Relay Active (HTTP ${resp.code} OK)")
                            } else {
                                onResult(false, "Relay returned HTTP ${resp.code}: ${resp.message}")
                            }
                            resp.close()
                        }
                    }
                } catch (e: Exception) {
                    onResult(false, "Cannot connect: ${e.localizedMessage ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    fun connect(serverUrl: String, roomId: String, clientId: String) {
        currentRoomId = roomId
        currentClientId = clientId
        currentServerUrl = serverUrl.trim()
        processedCandidateKeys.clear()

        disconnect()

        activeMode = when {
            currentServerUrl.contains("firebaseio.com") || currentServerUrl.contains("firebasedatabase.app") -> SignalingMode.FIREBASE
            currentServerUrl.startsWith("ws://") || currentServerUrl.startsWith("wss://") -> SignalingMode.WEBSOCKET
            else -> SignalingMode.NTFY_SERVERLESS
        }

        Log.d(TAG, "Connecting using mode: $activeMode to $currentServerUrl for room: $roomId")

        when (activeMode) {
            SignalingMode.NTFY_SERVERLESS -> connectNtfyServerless(currentServerUrl, roomId, clientId)
            SignalingMode.WEBSOCKET -> connectWebSocket(currentServerUrl, roomId, clientId)
            SignalingMode.FIREBASE -> connectFirebaseServerless(currentServerUrl, roomId, clientId)
        }
    }

    // =========================================================================
    // Mode 1: Public Serverless HTTP SSE Relay (Default: ntfy.sh)
    // =========================================================================

    private fun sanitizeNtfyTopic(roomId: String): String {
        val clean = roomId.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_")
        return "ashuchat_$clean"
    }

    private fun connectNtfyServerless(baseUrl: String, roomId: String, clientId: String) {
        val cleanBase = baseUrl.trimEnd('/')
        val topic = sanitizeNtfyTopic(roomId)
        val sseUrl = "$cleanBase/$topic/sse"
        val publishUrl = "$cleanBase/$topic"

        Log.d(TAG, "Connecting to Serverless NTFY Relay: $sseUrl")

        streamJob = scope.launch {
            while (isActive) {
                try {
                    val request = Request.Builder()
                        .url(sseUrl)
                        .header("Accept", "text/event-stream")
                        .header("User-Agent", "AshuChat-Android/1.0")
                        .build()

                    val call = client.newCall(request)
                    val response = call.execute()

                    if (!response.isSuccessful) {
                        listener.onError("Relay stream error: HTTP ${response.code}")
                        response.close()
                        delay(3000)
                        continue
                    }

                    listener.onSignalingConnected()

                    // Broadcast peer presence in room
                    sendNtfyMessage(
                        publishUrl,
                        SignalingPayload(
                            type = "join",
                            roomId = roomId,
                            clientId = clientId
                        )
                    )

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
                            if (currentEvent == "message" && dataBuffer.isNotEmpty()) {
                                handleNtfyEvent(dataBuffer.toString(), publishUrl)
                            }
                            currentEvent = null
                            dataBuffer.setLength(0)
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    if (isActive) {
                        Log.w(TAG, "Relay SSE stream interrupted: ${e.localizedMessage}. Reconnecting in 2s...")
                        listener.onSignalingDisconnected()
                        delay(2000)
                    }
                }
            }
        }
    }

    private fun handleNtfyEvent(jsonStr: String, publishUrl: String) {
        try {
            val root = gson.fromJson(jsonStr, JsonObject::class.java) ?: return
            val innerMsg = root.get("message")?.asString ?: return
            val payload = gson.fromJson(innerMsg, SignalingPayload::class.java) ?: return

            val sender = payload.clientId
            if (sender == null || sender == currentClientId) {
                return // Ignore our own messages
            }

            Log.d(TAG, "NTFY received payload: ${payload.type} from $sender")

            when (payload.type) {
                "join" -> {
                    // Send join_ack so the joining peer knows we are already here
                    sendNtfyMessage(
                        publishUrl,
                        SignalingPayload(
                            type = "join_ack",
                            roomId = currentRoomId,
                            clientId = currentClientId
                        )
                    )
                    listener.onPeerJoined(sender)
                }
                "join_ack" -> {
                    listener.onPeerJoined(sender)
                }
                "offer" -> {
                    payload.sdp?.let { listener.onOfferReceived(it) }
                }
                "answer" -> {
                    payload.sdp?.let { listener.onAnswerReceived(it) }
                }
                "ice_candidate" -> {
                    if (payload.sdp != null && payload.sdpMid != null && payload.sdpMLineIndex != null) {
                        val key = "${payload.sdpMid}_${payload.sdpMLineIndex}_${payload.sdp.hashCode()}"
                        if (!processedCandidateKeys.contains(key)) {
                            processedCandidateKeys.add(key)
                            listener.onIceCandidateReceived(
                                IceCandidate(payload.sdpMid, payload.sdpMLineIndex, payload.sdp)
                            )
                        }
                    }
                }
                "peer_left" -> {
                    listener.onPeerLeft(sender)
                }
                "mailbox_message" -> {
                    val json = payload.message
                    if (json != null) {
                        try {
                            val packet = gson.fromJson(json, DataChannelPacket::class.java)
                            if (packet != null) {
                                listener.onOfflinePacketReceived(packet)
                                sendOfflineAck(packet.messageId ?: "", "DELIVERED")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed parsing NTFY mailbox message: ${e.localizedMessage}")
                        }
                    }
                }
                "mailbox_ack" -> {
                    val json = payload.message
                    if (json != null) {
                        try {
                            val obj = gson.fromJson(json, JsonObject::class.java)
                            val id = obj.get("messageId")?.asString
                            val status = obj.get("status")?.asString ?: "DELIVERED"
                            if (id != null) {
                                listener.onOfflineAckReceived(id, status)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed parsing NTFY mailbox ack: ${e.localizedMessage}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling NTFY event: ${e.localizedMessage}", e)
        }
    }

    private fun sendNtfyMessage(publishUrl: String, payload: SignalingPayload) {
        try {
            val json = gson.toJson(payload)
            val body = json.toRequestBody(textMediaType)
            val req = Request.Builder()
                .url(publishUrl)
                .post(body)
                .build()
            client.newCall(req).enqueue(NoOpCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish NTFY message: ${e.localizedMessage}")
        }
    }

    // =========================================================================
    // Mode 2: Dedicated WebSocket Relay (ws:// or wss://)
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
                "mailbox_message" -> {
                    val json = payload.message
                    if (json != null) {
                        try {
                            val packet = gson.fromJson(json, DataChannelPacket::class.java)
                            if (packet != null) {
                                listener.onOfflinePacketReceived(packet)
                                sendOfflineAck(packet.messageId ?: "", "DELIVERED")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed parsing WS mailbox message: ${e.localizedMessage}")
                        }
                    }
                }
                "mailbox_ack" -> {
                    val json = payload.message
                    if (json != null) {
                        try {
                            val obj = gson.fromJson(json, JsonObject::class.java)
                            val id = obj.get("messageId")?.asString
                            val status = obj.get("status")?.asString ?: "DELIVERED"
                            if (id != null) {
                                listener.onOfflineAckReceived(id, status)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed parsing WS mailbox ack: ${e.localizedMessage}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing WebSocket message: ${e.localizedMessage}")
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

    // =========================================================================
    // Mode 3: Firebase Realtime Database (Serverless SSE Streaming)
    // =========================================================================

    private fun connectFirebaseServerless(baseUrl: String, roomId: String, clientId: String) {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val roomUrl = "$normalizedBaseUrl/rooms/$roomId.json"

        Log.d(TAG, "Connecting to Firebase RTDB at: $roomUrl")

        streamJob = scope.launch {
            while (isActive) {
                try {
                    announceFirebasePeerPresence(normalizedBaseUrl, roomId, clientId)

                    val request = Request.Builder()
                        .url(roomUrl)
                        .header("Accept", "text/event-stream")
                        .build()

                    val call = client.newCall(request)
                    val response = call.execute()

                    if (!response.isSuccessful) {
                        listener.onError("Firebase connection failed: HTTP ${response.code}")
                        response.close()
                        delay(3000)
                        continue
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
                    response.close()
                } catch (e: Exception) {
                    if (isActive) {
                        Log.w(TAG, "Firebase SSE error: ${e.localizedMessage}. Reconnecting in 2s...")
                        listener.onSignalingDisconnected()
                        delay(2000)
                    }
                }
            }
        }
    }

    private fun announceFirebasePeerPresence(baseUrl: String, roomId: String, clientId: String) {
        val body = JsonObject().apply {
            addProperty("online", true)
            addProperty("joinedAt", System.currentTimeMillis())
        }.toString().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("$baseUrl/rooms/$roomId/peers/$clientId.json")
            .put(body)
            .build()

        client.newCall(request).enqueue(NoOpCallback)
    }

    private fun handleFirebaseSseData(jsonStr: String) {
        try {
            val root = gson.fromJson(jsonStr, JsonObject::class.java) ?: return
            val path = root.get("path")?.asString ?: "/"
            val dataElem = root.get("data")

            if (dataElem == null || dataElem.isJsonNull) return

            when {
                path == "/" && dataElem.isJsonObject -> {
                    val roomObj = dataElem.asJsonObject
                    if (roomObj.has("peers")) {
                        val peers = roomObj.getAsJsonObject("peers")
                        for (entry in peers.entrySet()) {
                            if (entry.key != currentClientId) {
                                listener.onPeerJoined(entry.key)
                            }
                        }
                    }
                    if (roomObj.has("offer")) {
                        val offer = roomObj.getAsJsonObject("offer")
                        val sender = offer.get("senderId")?.asString
                        val timestamp = if (offer.has("timestamp")) offer.get("timestamp").asLong else 0L
                        val isFresh = timestamp == 0L || (System.currentTimeMillis() - timestamp) < 120_000L
                        if (sender != currentClientId && offer.has("sdp") && isFresh) {
                            listener.onOfferReceived(offer.get("sdp").asString)
                        }
                    }
                    if (roomObj.has("answer")) {
                        val answer = roomObj.getAsJsonObject("answer")
                        val sender = answer.get("senderId")?.asString
                        val timestamp = if (answer.has("timestamp")) answer.get("timestamp").asLong else 0L
                        val isFresh = timestamp == 0L || (System.currentTimeMillis() - timestamp) < 120_000L
                        if (sender != currentClientId && answer.has("sdp") && isFresh) {
                            listener.onAnswerReceived(answer.get("sdp").asString)
                        }
                    }
                    if (roomObj.has("candidates")) {
                        parseFirebaseCandidates(roomObj.getAsJsonObject("candidates"))
                    }
                    if (roomObj.has("mailbox") && roomObj.get("mailbox").isJsonObject) {
                        val mailbox = roomObj.getAsJsonObject("mailbox")
                        for (entry in mailbox.entrySet()) {
                            if (entry.value.isJsonObject) {
                                processFirebaseMailboxItem(entry.key, entry.value.asJsonObject, currentServerUrl, currentRoomId)
                            }
                        }
                    }
                    if (roomObj.has("mailbox_acks") && roomObj.get("mailbox_acks").isJsonObject) {
                        val acks = roomObj.getAsJsonObject("mailbox_acks")
                        for (entry in acks.entrySet()) {
                            if (entry.value.isJsonObject) {
                                processFirebaseMailboxAck(entry.key, entry.value.asJsonObject, currentServerUrl, currentRoomId)
                            }
                        }
                    }
                }
                path.startsWith("/peers/") -> {
                    val peerId = path.removePrefix("/peers/")
                    if (peerId != currentClientId) {
                        listener.onPeerJoined(peerId)
                    }
                }
                path == "/offer" && dataElem.isJsonObject -> {
                    val offer = dataElem.asJsonObject
                    val sender = offer.get("senderId")?.asString
                    val timestamp = if (offer.has("timestamp")) offer.get("timestamp").asLong else 0L
                    val isFresh = timestamp == 0L || (System.currentTimeMillis() - timestamp) < 120_000L
                    if (sender != currentClientId && offer.has("sdp") && isFresh) {
                        listener.onOfferReceived(offer.get("sdp").asString)
                    }
                }
                path == "/answer" && dataElem.isJsonObject -> {
                    val answer = dataElem.asJsonObject
                    val sender = answer.get("senderId")?.asString
                    val timestamp = if (answer.has("timestamp")) answer.get("timestamp").asLong else 0L
                    val isFresh = timestamp == 0L || (System.currentTimeMillis() - timestamp) < 120_000L
                    if (sender != currentClientId && answer.has("sdp") && isFresh) {
                        listener.onAnswerReceived(answer.get("sdp").asString)
                    }
                }
                path.startsWith("/candidates") -> {
                    if (dataElem.isJsonObject) {
                        parseFirebaseCandidateObject(dataElem.asJsonObject, path)
                    }
                }
                path.startsWith("/mailbox/") -> {
                    val msgId = path.removePrefix("/mailbox/").substringBefore("/")
                    if (dataElem.isJsonObject) {
                        processFirebaseMailboxItem(msgId, dataElem.asJsonObject, currentServerUrl, currentRoomId)
                    }
                }
                path == "/mailbox" && dataElem.isJsonObject -> {
                    val mailbox = dataElem.asJsonObject
                    for (entry in mailbox.entrySet()) {
                        if (entry.value.isJsonObject) {
                            processFirebaseMailboxItem(entry.key, entry.value.asJsonObject, currentServerUrl, currentRoomId)
                        }
                    }
                }
                path.startsWith("/mailbox_acks/") -> {
                    val msgId = path.removePrefix("/mailbox_acks/").substringBefore("/")
                    if (dataElem.isJsonObject) {
                        processFirebaseMailboxAck(msgId, dataElem.asJsonObject, currentServerUrl, currentRoomId)
                    }
                }
                path == "/mailbox_acks" && dataElem.isJsonObject -> {
                    val acks = dataElem.asJsonObject
                    for (entry in acks.entrySet()) {
                        if (entry.value.isJsonObject) {
                            processFirebaseMailboxAck(entry.key, entry.value.asJsonObject, currentServerUrl, currentRoomId)
                        }
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
    // Public Sending API
    // =========================================================================

    fun sendOffer(sdp: String) {
        when (activeMode) {
            SignalingMode.NTFY_SERVERLESS -> {
                val cleanBase = currentServerUrl.trimEnd('/')
                val topic = sanitizeNtfyTopic(currentRoomId ?: return)
                sendNtfyMessage(
                    "$cleanBase/$topic",
                    SignalingPayload(
                        type = "offer",
                        roomId = currentRoomId,
                        clientId = currentClientId,
                        sdp = sdp
                    )
                )
            }
            SignalingMode.WEBSOCKET -> {
                sendMessageWs(
                    SignalingPayload(
                        type = "offer",
                        roomId = currentRoomId,
                        clientId = currentClientId,
                        sdp = sdp
                    )
                )
            }
            SignalingMode.FIREBASE -> {
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
            }
        }
    }

    fun sendAnswer(sdp: String) {
        when (activeMode) {
            SignalingMode.NTFY_SERVERLESS -> {
                val cleanBase = currentServerUrl.trimEnd('/')
                val topic = sanitizeNtfyTopic(currentRoomId ?: return)
                sendNtfyMessage(
                    "$cleanBase/$topic",
                    SignalingPayload(
                        type = "answer",
                        roomId = currentRoomId,
                        clientId = currentClientId,
                        sdp = sdp
                    )
                )
            }
            SignalingMode.WEBSOCKET -> {
                sendMessageWs(
                    SignalingPayload(
                        type = "answer",
                        roomId = currentRoomId,
                        clientId = currentClientId,
                        sdp = sdp
                    )
                )
            }
            SignalingMode.FIREBASE -> {
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
            }
        }
    }

    fun sendIceCandidate(candidate: IceCandidate) {
        when (activeMode) {
            SignalingMode.NTFY_SERVERLESS -> {
                val cleanBase = currentServerUrl.trimEnd('/')
                val topic = sanitizeNtfyTopic(currentRoomId ?: return)
                sendNtfyMessage(
                    "$cleanBase/$topic",
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
            SignalingMode.WEBSOCKET -> {
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
            SignalingMode.FIREBASE -> {
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
            }
        }
    }

    fun disconnect() {
        try {
            streamJob?.cancel()
            streamJob = null

            if (activeMode == SignalingMode.NTFY_SERVERLESS && currentRoomId != null && currentClientId != null) {
                val cleanBase = currentServerUrl.trimEnd('/')
                val topic = sanitizeNtfyTopic(currentRoomId!!)
                sendNtfyMessage(
                    "$cleanBase/$topic",
                    SignalingPayload(
                        type = "peer_left",
                        roomId = currentRoomId,
                        clientId = currentClientId
                    )
                )
            }

            if (activeMode == SignalingMode.FIREBASE && currentRoomId != null && currentClientId != null) {
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

    private fun processFirebaseMailboxItem(
        messageId: String,
        msgObj: JsonObject,
        baseUrl: String,
        roomId: String?
    ) {
        val sender = msgObj.get("senderId")?.asString
        if (sender == null || sender == currentClientId) {
            return
        }
        val cleanBase = baseUrl.trimEnd('/')
        try {
            val packet = gson.fromJson(msgObj, DataChannelPacket::class.java)
            if (packet != null) {
                listener.onOfflinePacketReceived(packet)

                // Delete processed item from cloud mailbox (ephemeral - zero permanent cloud storage)
                if (roomId != null) {
                    val delReq = Request.Builder()
                        .url("$cleanBase/rooms/$roomId/mailbox/$messageId.json")
                        .delete()
                        .build()
                    client.newCall(delReq).enqueue(NoOpCallback)

                    sendOfflineAck(messageId, "DELIVERED")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Firebase mailbox item: ${e.localizedMessage}", e)
        }
    }

    private fun processFirebaseMailboxAck(
        messageId: String,
        ackObj: JsonObject,
        baseUrl: String,
        roomId: String?
    ) {
        val sender = ackObj.get("senderId")?.asString
        if (sender == null || sender == currentClientId) {
            return
        }
        val cleanBase = baseUrl.trimEnd('/')
        val status = ackObj.get("status")?.asString ?: "DELIVERED"
        listener.onOfflineAckReceived(messageId, status)

        if (roomId != null) {
            val delReq = Request.Builder()
                .url("$cleanBase/rooms/$roomId/mailbox_acks/$messageId.json")
                .delete()
                .build()
            client.newCall(delReq).enqueue(NoOpCallback)
        }
    }

    fun sendOfflineMessage(packet: DataChannelPacket, onResult: ((Boolean) -> Unit)? = null) {
        val roomId = currentRoomId ?: run {
            onResult?.invoke(false)
            return
        }
        val messageId = packet.messageId ?: run {
            onResult?.invoke(false)
            return
        }

        when (activeMode) {
            SignalingMode.FIREBASE -> {
                val cleanBase = currentServerUrl.trimEnd('/')
                val root = gson.toJsonTree(packet).asJsonObject
                root.addProperty("senderId", currentClientId)
                val body = root.toString().toRequestBody(jsonMediaType)

                val req = Request.Builder()
                    .url("$cleanBase/rooms/$roomId/mailbox/$messageId.json")
                    .put(body)
                    .build()

                client.newCall(req).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Failed sending offline message to Firebase: ${e.localizedMessage}")
                        onResult?.invoke(false)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val success = response.isSuccessful
                        response.close()
                        onResult?.invoke(success)
                    }
                })
            }

            SignalingMode.NTFY_SERVERLESS -> {
                val cleanBase = currentServerUrl.trimEnd('/')
                val topic = sanitizeNtfyTopic(roomId)
                sendNtfyMessage(
                    "$cleanBase/$topic",
                    SignalingPayload(
                        type = "mailbox_message",
                        roomId = roomId,
                        clientId = currentClientId,
                        message = gson.toJson(packet)
                    )
                )
                onResult?.invoke(true)
            }

            SignalingMode.WEBSOCKET -> {
                sendMessageWs(
                    SignalingPayload(
                        type = "mailbox_message",
                        roomId = roomId,
                        clientId = currentClientId,
                        message = gson.toJson(packet)
                    )
                )
                onResult?.invoke(true)
            }
        }
    }

    fun sendOfflineAck(messageId: String, status: String) {
        val roomId = currentRoomId ?: return
        if (messageId.isBlank()) return

        when (activeMode) {
            SignalingMode.FIREBASE -> {
                val cleanBase = currentServerUrl.trimEnd('/')
                val body = JsonObject().apply {
                    addProperty("messageId", messageId)
                    addProperty("senderId", currentClientId)
                    addProperty("status", status)
                    addProperty("timestamp", System.currentTimeMillis())
                }.toString().toRequestBody(jsonMediaType)

                val req = Request.Builder()
                    .url("$cleanBase/rooms/$roomId/mailbox_acks/$messageId.json")
                    .put(body)
                    .build()

                client.newCall(req).enqueue(NoOpCallback)
            }

            SignalingMode.NTFY_SERVERLESS -> {
                val cleanBase = currentServerUrl.trimEnd('/')
                val topic = sanitizeNtfyTopic(roomId)
                val ackJson = JsonObject().apply {
                    addProperty("messageId", messageId)
                    addProperty("status", status)
                }.toString()

                sendNtfyMessage(
                    "$cleanBase/$topic",
                    SignalingPayload(
                        type = "mailbox_ack",
                        roomId = roomId,
                        clientId = currentClientId,
                        message = ackJson
                    )
                )
            }

            SignalingMode.WEBSOCKET -> {
                val ackJson = JsonObject().apply {
                    addProperty("messageId", messageId)
                    addProperty("status", status)
                }.toString()

                sendMessageWs(
                    SignalingPayload(
                        type = "mailbox_ack",
                        roomId = roomId,
                        clientId = currentClientId,
                        message = ackJson
                    )
                )
            }
        }
    }

    private object NoOpCallback : Callback {
        override fun onFailure(call: Call, e: IOException) {}
        override fun onResponse(call: Call, response: Response) {
            response.close()
        }
    }
}
