package com.github.guidohu.clockshift.autotrack

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.github.guidohu.clockshift.domain.GeoPoint
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Location permission and settings, as far as automatic check-in needs them. */
object LocationAccess {
    fun hasForeground(context: Context): Boolean = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    /** "Allow all the time": needed to notice arrivals and departures while the app is closed. */
    fun hasBackground(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasForeground(context) && granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            hasForeground(context)
        }

    fun isLocationEnabled(context: Context): Boolean =
        LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java))

    fun hasPlayServices(context: Context): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/** Reads the name of the Wi-Fi network the phone is connected to. Android only reveals it with location access. */
object WifiProbe {
    private const val TIMEOUT_MS = 2_000L
    private const val UNKNOWN_SSID = "<unknown ssid>"

    suspend fun currentName(context: Context): String? {
        if (!LocationAccess.hasForeground(context)) return null
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) nameFromCallback(context) else legacyName(context)
        return normalize(raw)
    }

    /** WifiInfo wraps the name in quotes and uses a placeholder when it is hidden. */
    fun normalize(raw: String?): String? {
        val name = raw?.trim()?.removeSurrounding("\"") ?: return null
        return name.takeUnless { it.isBlank() || it == UNKNOWN_SSID }
    }

    /**
     * Since Android 12 the name is only included for callbacks that ask for location information.
     * A callback for Wi-Fi reports the current network right away; without Wi-Fi it never fires.
     */
    private suspend fun nameFromCallback(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        val name = CompletableDeferred<String?>()
        val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                name.complete((capabilities.transportInfo as? WifiInfo)?.ssid)
            }
        }
        connectivity.registerNetworkCallback(request, callback)
        return try {
            withTimeoutOrNull(TIMEOUT_MS) { name.await() }
        } finally {
            connectivity.unregisterNetworkCallback(callback)
        }
    }

    @Suppress("DEPRECATION") // The supported way before Android 12; replaced by nameFromCallback there.
    private fun legacyName(context: Context): String? =
        context.applicationContext.getSystemService(WifiManager::class.java).connectionInfo?.ssid
}

/** One-off location fix for "use my current location". */
object LocationProbe {
    @SuppressLint("MissingPermission") // Checked first.
    suspend fun current(context: Context): GeoPoint? {
        if (!LocationAccess.hasForeground(context) || !LocationAccess.hasPlayServices(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cancellation = CancellationTokenSource()
        return suspendCancellableCoroutine { continuation ->
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                .addOnCompleteListener { task ->
                    val location = if (task.isSuccessful) task.result else null
                    continuation.resume(location?.let { GeoPoint(it.latitude, it.longitude) })
                }
            continuation.invokeOnCancellation { cancellation.cancel() }
        }
    }
}
