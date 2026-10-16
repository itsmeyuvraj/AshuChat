package com.ashuchat.app.webrtc

import android.content.Context
import android.net.Uri
import android.util.Log
import com.ashuchat.app.data.model.DataChannelPacket
import com.ashuchat.app.utils.FileUtils
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FileTransferManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val listener: Listener
) {
    interface Listener {
        fun onFileTransferProgress(fileId: String, progress: Float)
        fun onFileTransferComplete(messageId: String, fileId: String, localPath: String, isIncoming: Boolean)
        fun onFileTransferFailed(fileId: String, error: String)
        fun sendControlPacket(packet: DataChannelPacket)
    }

    companion object {
        private const val TAG = "FileTransferManager"
        const val CHUNK_SIZE = 16 * 1024 // 16 KB chunks
        const val HEADER_FILE_ID_LEN = 36 // UUID string length
        const val MAX_BUFFERED_AMOUNT = 512 * 1024L // 512 KB threshold for backpressure
    }

    private val gson = Gson()

    // Holds incoming transfer metadata keyed by fileId
    data class IncomingTransfer(
        val messageId: String,
        val fileId: String,
        val fileName: String,
        val fileSize: Long,
        val mimeType: String?,
        val totalChunks: Int,
        val tempFile: File,
        val randomAccessFile: RandomAccessFile,
        var receivedChunks: Int = 0,
        var receivedBytes: Long = 0L
    )

    private val incomingTransfers = ConcurrentHashMap<String, IncomingTransfer>()
    private val activeProgress = ConcurrentHashMap<String, Float>()

    /**
     * Called when a file_header control packet is received over DataChannel.
     */
    fun handleFileHeader(packet: DataChannelPacket) {
        val fileId = packet.fileId ?: return
        val messageId = packet.messageId ?: UUID.randomUUID().toString()
        val fileName = packet.fileName ?: "received_file_${System.currentTimeMillis()}"
        val fileSize = packet.fileSize
        val mimeType = packet.mimeType
        val totalChunks = packet.totalChunks

        Log.d(TAG, "Incoming file: $fileName, size: $fileSize bytes, total chunks: $totalChunks")

        try {
            val storageDir = FileUtils.getAttachmentsDir(context)
            val tempFile = File(storageDir, "transfer_$fileId.tmp")
            if (tempFile.exists()) tempFile.delete()
            tempFile.createNewFile()

            val raf = RandomAccessFile(tempFile, "rw")
            raf.setLength(fileSize)

            val transfer = IncomingTransfer(
                messageId = messageId,
                fileId = fileId,
                fileName = fileName,
                fileSize = fileSize,
                mimeType = mimeType,
                totalChunks = totalChunks,
                tempFile = tempFile,
                randomAccessFile = raf
            )

            incomingTransfers[fileId] = transfer
            activeProgress[fileId] = 0f
            listener.onFileTransferProgress(messageId, 0f)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing incoming file transfer: ${e.localizedMessage}", e)
            listener.onFileTransferFailed(fileId, e.localizedMessage ?: "File init failed")
        }
    }

    /**
     * Called when binary bytes arrive over DataChannel.
     */
    fun handleBinaryChunk(buffer: DataChannel.Buffer) {
        val byteBuffer = buffer.data
        val totalLen = byteBuffer.remaining()

        if (totalLen < HEADER_FILE_ID_LEN + 8) {
            Log.w(TAG, "Received binary packet too short: $totalLen bytes")
            return
        }

        val fileIdBytes = ByteArray(HEADER_FILE_ID_LEN)
        byteBuffer.get(fileIdBytes)
        val fileId = String(fileIdBytes, StandardCharsets.UTF_8)

        val chunkIndex = byteBuffer.getInt()
        val totalChunks = byteBuffer.getInt()

        val transfer = incomingTransfers[fileId]
        if (transfer == null) {
            Log.w(TAG, "No active incoming transfer for fileId: $fileId")
            return
        }

        try {
            val payloadLen = byteBuffer.remaining()
            val chunkBytes = ByteArray(payloadLen)
            byteBuffer.get(chunkBytes)

            val offset = chunkIndex.toLong() * CHUNK_SIZE
            synchronized(transfer) {
                transfer.randomAccessFile.seek(offset)
                transfer.randomAccessFile.write(chunkBytes)
                transfer.receivedChunks++
                transfer.receivedBytes += payloadLen

                val progress = if (transfer.fileSize > 0) {
                    (transfer.receivedBytes.toFloat() / transfer.fileSize.toFloat()).coerceIn(0f, 1f)
                } else {
                    1f
                }
                activeProgress[fileId] = progress
                listener.onFileTransferProgress(transfer.messageId, progress)

                if (transfer.receivedChunks >= transfer.totalChunks || transfer.receivedBytes >= transfer.fileSize) {
                    Log.d(TAG, "File transfer completed for fileId: $fileId")
                    transfer.randomAccessFile.close()

                    // Move file to permanent app storage
                    val storageDir = FileUtils.getAttachmentsDir(context)
                    val safeFileName = FileUtils.sanitizeFileName(transfer.fileName)
                    val finalFile = File(storageDir, "${System.currentTimeMillis()}_$safeFileName")
                    if (!transfer.tempFile.renameTo(finalFile)) {
                        transfer.tempFile.copyTo(finalFile, overwrite = true)
                        transfer.tempFile.delete()
                    }

                    incomingTransfers.remove(fileId)
                    activeProgress.remove(fileId)

                    // Send ack back to peer
                    listener.sendControlPacket(
                        DataChannelPacket(
                            type = "file_ack",
                            fileId = fileId,
                            messageId = transfer.messageId,
                            status = "COMPLETED"
                        )
                    )

                    listener.onFileTransferComplete(
                        messageId = transfer.messageId,
                        fileId = fileId,
                        localPath = finalFile.absolutePath,
                        isIncoming = true
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing binary chunk: ${e.localizedMessage}", e)
            listener.onFileTransferFailed(fileId, e.localizedMessage ?: "Chunk write error")
        }
    }

    /**
     * Sends a file to the peer over the WebRTC DataChannel with backpressure flow control.
     */
    fun sendFile(
        dataChannel: DataChannel,
        messageId: String,
        uri: Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String?,
        senderName: String? = null
    ) {
        scope.launch(Dispatchers.IO) {
            val fileId = UUID.randomUUID().toString()
            try {
                val totalChunks = ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt().coerceAtLeast(1)

                // 1. Send file_header metadata control packet
                val headerPacket = DataChannelPacket(
                    type = "file_header",
                    messageId = messageId,
                    senderName = senderName,
                    fileId = fileId,
                    fileName = fileName,
                    fileSize = fileSize,
                    mimeType = mimeType,
                    totalChunks = totalChunks,
                    chunkSize = CHUNK_SIZE
                )
                listener.sendControlPacket(headerPacket)

                // Small pause to allow peer to allocate file buffer
                delay(100)

                // 2. Open input stream from content resolver or local file
                val inputStream = if (uri.scheme == "content") {
                    context.contentResolver.openInputStream(uri)
                } else {
                    val filePath = if (uri.scheme == "file") uri.path ?: uri.toString().removePrefix("file://") else uri.toString()
                    File(filePath).inputStream()
                } ?: throw IllegalStateException("Could not open input stream for $uri")

                val buffer = ByteArray(CHUNK_SIZE)
                var chunkIndex = 0
                var bytesSent = 0L

                inputStream.use { stream ->
                    var bytesRead: Int
                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        // Apply backpressure if DataChannel internal buffer is filling up
                        while (dataChannel.bufferedAmount() > MAX_BUFFERED_AMOUNT) {
                            delay(15)
                        }

                        // Prepare binary frame: [36 bytes fileId] + [4 bytes chunkIndex] + [4 bytes totalChunks] + [chunk payload]
                        val packetBuffer = ByteBuffer.allocate(HEADER_FILE_ID_LEN + 8 + bytesRead)
                        packetBuffer.put(fileId.toByteArray(StandardCharsets.UTF_8))
                        packetBuffer.putInt(chunkIndex)
                        packetBuffer.putInt(totalChunks)
                        packetBuffer.put(buffer, 0, bytesRead)
                        packetBuffer.flip()

                        val dcBuffer = DataChannel.Buffer(packetBuffer, true)
                        val sent = dataChannel.send(dcBuffer)
                        if (!sent) {
                            Log.w(TAG, "DataChannel.send() returned false at chunk $chunkIndex")
                        }

                        bytesSent += bytesRead
                        chunkIndex++

                        val progress = (bytesSent.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f)
                        listener.onFileTransferProgress(messageId, progress)
                    }
                }

                Log.d(TAG, "Sent all $totalChunks chunks for file: $fileName")

                // Wait for final buffer drain
                while (dataChannel.bufferedAmount() > 0) {
                    delay(20)
                }

                listener.onFileTransferComplete(
                    messageId = messageId,
                    fileId = fileId,
                    localPath = uri.toString(),
                    isIncoming = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error sending file: ${e.localizedMessage}", e)
                listener.onFileTransferFailed(fileId, e.localizedMessage ?: "Send file failed")
            }
        }
    }
}
