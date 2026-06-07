package com.helmlink.companion.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class OrcaDiscoveryService(context: Context) {

    companion object {
        private const val TAG = "OrcaDiscovery"
        private const val SERVICE_TYPE = "_http._tcp."
        private val ORCA_PATTERN = Regex("^orca-[a-zA-Z0-9]{6}(-\\d)? ORCA$")
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _discoveredHost = MutableStateFlow<String?>(null)
    val discoveredHost: StateFlow<String?> = _discoveredHost

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    fun startDiscovery() {
        if (discoveryListener != null) return
        _isSearching.value = true
        _discoveredHost.value = null
        Log.d(TAG, "Starting mDNS discovery for Orca Core...")

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Discovery started")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                if (ORCA_PATTERN.matches(serviceInfo.serviceName)) {
                    Log.d(TAG, "Orca Core found: ${serviceInfo.serviceName}")
                    resolveService(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                if (ORCA_PATTERN.matches(serviceInfo.serviceName)) {
                    _discoveredHost.value = null
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped")
                _isSearching.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
                _isSearching.value = false
                discoveryListener = null
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
            _isSearching.value = false
            discoveryListener = null
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress
                Log.d(TAG, "Orca Core resolved: $host:${serviceInfo.port}")
                if (host != null) {
                    _discoveredHost.value = host
                    _isSearching.value = false
                }
            }
        })
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping discovery", e)
            }
        }
        discoveryListener = null
        _isSearching.value = false
    }
}
