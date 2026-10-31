package com.guidelight.app.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*

/** A single fused stream on Play devices, with framework providers as a fallback. */
class WalkingLocationTracker(context: Context, private val onLocation: (Location) -> Unit) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val fused = LocationServices.getFusedLocationProviderClient(context)
    private val hasPlayServices = GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    private var active = false
    private var generation = 0
    private var callback: LocationCallback? = null
    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        stop()
        if (!listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .any { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }) return false
        active = true
        val run = generation
        fun deliver(location: Location) { if (active && generation == run) onLocation(location) }
        if (hasPlayServices) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                .setMinUpdateIntervalMillis(500L)
                .setMinUpdateDistanceMeters(0f)
                .setMaxUpdateDelayMillis(0L)
                .setWaitForAccurateLocation(true)
                .build()
            val updates = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) { result.locations.forEach(::deliver) }
            }
            callback = updates
            fused.lastLocation.addOnSuccessListener { it?.let(::deliver) }
            fused.requestLocationUpdates(request, updates, Looper.getMainLooper())
                .addOnFailureListener { if (active && generation == run) startFramework(run) }
        } else startFramework(run)
        return true
    }

    @SuppressLint("MissingPermission")
    private fun startFramework(run: Int) {
        if (listener != null) return
        val updates = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (active && generation == run) onLocation(location)
            }
        }
        listener = updates
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            runCatching {
                if (manager.isProviderEnabled(provider)) {
                    manager.getLastKnownLocation(provider)?.let(updates::onLocationChanged)
                    manager.requestLocationUpdates(provider, 1_000L, 0f, updates, Looper.getMainLooper())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        manager.getCurrentLocation(provider, null, { it.run() }) { it?.let(updates::onLocationChanged) }
                }
            }
        }
    }

    fun stop() {
        active = false
        generation++
        callback?.let { fused.removeLocationUpdates(it) }
        listener?.let { manager.removeUpdates(it) }
        callback = null
        listener = null
    }
}
