package com.example.cipherview.data.network

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

/**
 * Handles direct offline peer-to-peer data transport over local Wi-Fi TCP sockets and Bluetooth RFCOMM.
 * Zero reliance on internet or external servers.
 */
class LocalTransferEngine(private val context: Context) {
    companion object {
        const val DEFAULT_PORT = 8998
        val BT_UUID: UUID = UUID.fromString("b2f4c1e0-8a71-4a3d-9d41-e947bc63d001")
        private const val BUFFER_SIZE = 16384
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var serverSocket: ServerSocket? = null
    private var btServerSocket: BluetoothServerSocket? = null
    private var isListening = false

    private val _transferProgress = MutableStateFlow(TransferProgress())
    val transferProgress: StateFlow<TransferProgress> = _transferProgress.asStateFlow()

    var onPackageReceived: ((ByteArray) -> Unit)? = null

    /**
     * Starts listening for incoming document packages over local Wi-Fi and Bluetooth.
     */
    fun startReceivingServer(port: Int = DEFAULT_PORT): Int {
        if (isListening) return serverSocket?.localPort ?: port

        var boundPort = port
        try {
            serverSocket = try {
                ServerSocket(port)
            } catch (e: Exception) {
                // If default port is taken, bind to any available dynamic port
                ServerSocket(0)
            }
            boundPort = serverSocket?.localPort ?: port
            isListening = true

            // Launch Wi-Fi listener loop
            scope.launch {
                listenForTcpConnections()
            }

            // Launch Bluetooth listener loop if Bluetooth is available
            startBluetoothServer()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return boundPort
    }

    private suspend fun listenForTcpConnections() = withContext(Dispatchers.IO) {
        while (isListening && serverSocket != null && !serverSocket!!.isClosed) {
            try {
                val client = serverSocket?.accept() ?: break
                handleIncomingStream(client.getInputStream(), client.getOutputStream(), "Wi-Fi Peer")
                client.close()
            } catch (e: Exception) {
                if (!isListening) break
            }
        }
    }

    private fun startBluetoothServer() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!adapter.isEnabled) return

        scope.launch(Dispatchers.IO) {
            try {
                btServerSocket = adapter.listenUsingRfcommWithServiceRecord("CipherView", BT_UUID)
                while (isListening && btServerSocket != null) {
                    val socket = btServerSocket?.accept() ?: break
                    handleIncomingStream(socket.inputStream, socket.outputStream, "Bluetooth Peer")
                    socket.close()
                }
            } catch (e: Exception) {
                // Bluetooth permissions or disabled
            }
        }
    }

    private fun handleIncomingStream(input: InputStream, output: OutputStream, peerName: String) {
        try {
            val dis = DataInputStream(input)
            val dos = DataOutputStream(output)

            val totalBytes = dis.readLong()
            if (totalBytes <= 0 || totalBytes > 250 * 1024 * 1024) { // 250 MB safeguard
                dos.writeByte(0)
                dos.flush()
                return
            }

            _transferProgress.value = TransferProgress(
                status = TransferStatus.TRANSFERRING,
                bytesTransferred = 0,
                totalBytes = totalBytes,
                peerNickname = peerName
            )

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesReadTotal = 0L
            val outputBuffer = java.io.ByteArrayOutputStream()

            while (bytesReadTotal < totalBytes) {
                val toRead = Math.min(buffer.size.toLong(), totalBytes - bytesReadTotal).toInt()
                val read = dis.read(buffer, 0, toRead)
                if (read == -1) break
                outputBuffer.write(buffer, 0, read)
                bytesReadTotal += read

                _transferProgress.value = _transferProgress.value.copy(
                    bytesTransferred = bytesReadTotal
                )
            }

            if (bytesReadTotal == totalBytes) {
                dos.writeByte(1) // ACK success
                dos.flush()

                _transferProgress.value = TransferProgress(
                    status = TransferStatus.SUCCESS,
                    bytesTransferred = totalBytes,
                    totalBytes = totalBytes,
                    peerNickname = peerName
                )

                val packageBytes = outputBuffer.toByteArray()
                onPackageReceived?.invoke(packageBytes)
            } else {
                dos.writeByte(0)
                dos.flush()
                _transferProgress.value = TransferProgress(
                    status = TransferStatus.FAILED,
                    errorMessage = "Incomplete transfer ($bytesReadTotal / $totalBytes bytes)"
                )
            }
        } catch (e: Exception) {
            _transferProgress.value = TransferProgress(
                status = TransferStatus.FAILED,
                errorMessage = e.localizedMessage ?: "Connection error"
            )
        }
    }

    /**
     * Sends an encrypted .cview package to a remote peer via Wi-Fi TCP socket.
     */
    suspend fun sendPackageViaWifi(
        ipAddress: String,
        port: Int,
        packageBytes: ByteArray,
        peerNickname: String,
        fileName: String
    ): Boolean = withContext(Dispatchers.IO) {
        _transferProgress.value = TransferProgress(
            status = TransferStatus.CONNECTING,
            totalBytes = packageBytes.size.toLong(),
            peerNickname = peerNickname,
            fileName = fileName
        )

        try {
            Socket(ipAddress, port).use { socket ->
                socket.soTimeout = 30000
                val output = socket.getOutputStream()
                val input = socket.getInputStream()
                val dos = DataOutputStream(output)
                val dis = DataInputStream(input)

                // Send payload size
                dos.writeLong(packageBytes.size.toLong())
                dos.flush()

                _transferProgress.value = _transferProgress.value.copy(
                    status = TransferStatus.TRANSFERRING
                )

                // Stream bytes with progress updates
                val chunkSize = BUFFER_SIZE
                var sent = 0L
                val total = packageBytes.size.toLong()

                while (sent < total) {
                    val remaining = (total - sent).toInt()
                    val toWrite = Math.min(chunkSize, remaining)
                    dos.write(packageBytes, sent.toInt(), toWrite)
                    sent += toWrite
                    _transferProgress.value = _transferProgress.value.copy(
                        bytesTransferred = sent
                    )
                }
                dos.flush()

                // Read ACK
                val ack = dis.readByte()
                if (ack.toInt() == 1) {
                    _transferProgress.value = TransferProgress(
                        status = TransferStatus.SUCCESS,
                        bytesTransferred = total,
                        totalBytes = total,
                        peerNickname = peerNickname,
                        fileName = fileName
                    )
                    return@withContext true
                } else {
                    _transferProgress.value = TransferProgress(
                        status = TransferStatus.FAILED,
                        errorMessage = "Recipient rejected package",
                        peerNickname = peerNickname
                    )
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            _transferProgress.value = TransferProgress(
                status = TransferStatus.FAILED,
                errorMessage = e.localizedMessage ?: "Failed to connect to $peerNickname",
                peerNickname = peerNickname
            )
            return@withContext false
        }
    }

    /**
     * Sends an encrypted package via Bluetooth RFCOMM.
     */
    suspend fun sendPackageViaBluetooth(
        deviceAddress: String,
        packageBytes: ByteArray,
        peerNickname: String,
        fileName: String
    ): Boolean = withContext(Dispatchers.IO) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext false
        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(deviceAddress)
        } catch (e: Exception) {
            return@withContext false
        }

        _transferProgress.value = TransferProgress(
            status = TransferStatus.CONNECTING,
            totalBytes = packageBytes.size.toLong(),
            peerNickname = peerNickname,
            fileName = fileName
        )

        try {
            val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(BT_UUID)
            socket.connect()

            val dos = DataOutputStream(socket.outputStream)
            val dis = DataInputStream(socket.inputStream)

            dos.writeLong(packageBytes.size.toLong())
            dos.flush()

            _transferProgress.value = _transferProgress.value.copy(
                status = TransferStatus.TRANSFERRING
            )

            var sent = 0L
            val total = packageBytes.size.toLong()
            while (sent < total) {
                val remaining = (total - sent).toInt()
                val toWrite = Math.min(BUFFER_SIZE, remaining)
                dos.write(packageBytes, sent.toInt(), toWrite)
                sent += toWrite
                _transferProgress.value = _transferProgress.value.copy(
                    bytesTransferred = sent
                )
            }
            dos.flush()

            val ack = dis.readByte()
            socket.close()

            if (ack.toInt() == 1) {
                _transferProgress.value = TransferProgress(
                    status = TransferStatus.SUCCESS,
                    bytesTransferred = total,
                    totalBytes = total,
                    peerNickname = peerNickname,
                    fileName = fileName
                )
                true
            } else {
                _transferProgress.value = TransferProgress(
                    status = TransferStatus.FAILED,
                    errorMessage = "Recipient rejected package via Bluetooth"
                )
                false
            }
        } catch (e: Exception) {
            _transferProgress.value = TransferProgress(
                status = TransferStatus.FAILED,
                errorMessage = e.localizedMessage ?: "Bluetooth transfer failed"
            )
            false
        }
    }

    fun resetProgress() {
        _transferProgress.value = TransferProgress(status = TransferStatus.IDLE)
    }

    fun stopServer() {
        isListening = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null

        try {
            btServerSocket?.close()
        } catch (e: Exception) {}
        btServerSocket = null
    }

    /**
     * Resolves the current Wi-Fi/LAN IPv4 address of this device.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
