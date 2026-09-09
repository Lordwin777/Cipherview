package com.example.cipherview

import android.app.Application
import com.example.cipherview.data.network.LocalTransferEngine
import com.example.cipherview.data.network.NsdDiscoveryManager
import com.example.cipherview.data.repository.LocalVaultRepository

class CipherViewApp : Application() {
    companion object {
        lateinit var instance: CipherViewApp
            private set
    }

    val vaultRepository by lazy { LocalVaultRepository(this) }
    val nsdDiscoveryManager by lazy { NsdDiscoveryManager(this) }
    val transferEngine by lazy { LocalTransferEngine(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
