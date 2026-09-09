package com.example.cipherview.data.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

/**
 * Handles automatic discovery of nearby CipherView devices over local Wi-Fi or personal hotspot
 * using Android Network Service Discovery (NSD / mDNS).
 */
class NsdDiscoveryManager(private val context: Context) {
    companion object {
        const val SERVICE_TYPE = "_cipherview._tcp."
        const val SERVICE_PREFIX = "CVIEW"
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscovering = false
    private var isRegistered = false

    private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val peers: StateFlow<List<DiscoveredPeer>> = _peers.asStateFlow()

    private val peerMap = mutableMapOf<String, DiscoveredPeer>()

    /**
     * Broadcasts this device's presence and receiving port to nearby devices on the Wi-Fi network.
     */
    fun registerService(nickname: String, port: Int) {
        if (isRegistered || nsdManager == null) return

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "$SERVICE_PREFIX-$nickname"
            serviceType = SERVICE_TYPE
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                isRegistered = true
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                isRegistered = false
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                isRegistered = false
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                isRegistered = false
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unregisterService() {
        if (!isRegistered || nsdManager == null) return
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        registrationListener = null
        isRegistered = false
    }

    /**
     * Starts listening for other CipherView devices on the local Wi-Fi.
     */
    fun startDiscovery(myNickname: String) {
        if (isDiscovering || nsdManager == null) return

        // Acquire multicast lock for reliable mDNS packet reception
        try {
            if (multicastLock == null) {
                multicastLock = wifiManager?.createMulticastLock("CipherViewMulticastLock")?.apply {
                    setReferenceCounted(true)
                }
            }
            multicastLock?.acquire()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        peerMap.clear()
        _peers.value = emptyList()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                isDiscovering = true
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains("_cipherview._tcp")) {
                    val serviceName = serviceInfo.serviceName
                    if (serviceName.startsWith(SERVICE_PREFIX)) {
                        val peerNickname = serviceName.removePrefix("$SERVICE_PREFIX-")
                        // Don't list ourselves as a peer
                        if (!peerNickname.equals(myNickname, ignoreCase = true)) {
                            resolveService(serviceInfo, peerNickname)
                        }
                    }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val serviceName = serviceInfo.serviceName
                peerMap.remove(serviceName)
                _peers.value = peerMap.values.toList()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                isDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                isDiscovering = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                isDiscovering = false
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo, peerNickname: String) {
        nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                val host: InetAddress? = resolvedInfo.host
                val port = resolvedInfo.port
                if (host != null && port > 0) {
                    val peer = DiscoveredPeer(
                        id = resolvedInfo.serviceName,
                        nickname = peerNickname,
                        transportType = TransportType.WIFI_NSD,
                        ipAddress = host.hostAddress,
                        port = port
                    )
                    peerMap[resolvedInfo.serviceName] = peer
                    _peers.value = peerMap.values.toList()
                }
            }
        })
    }

    fun stopDiscovery() {
        if (!isDiscovering || nsdManager == null) return
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        discoveryListener = null
        isDiscovering = false

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
