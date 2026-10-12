package io.github.thelony97.orbit.geo

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.GeoPoint
import io.github.thelony97.orbit.data.OrbitRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Address → coordinates with Android's built-in geocoder (no API key), cached on the contact. */
object Geo {

    fun needsLookup(c: Contact): Boolean {
        val address = c.address.trim()
        return address.isNotEmpty() && c.deletedAt == null && c.geo?.query != address
    }

    /** Coordinates that belong to the current address, or null. */
    fun point(c: Contact): GeoPoint? = c.geo?.takeIf { it.found && it.query == c.address.trim() && c.address.isNotBlank() }

    /** null = lookup failed for technical reasons (offline …), try again later. */
    private suspend fun lookup(ctx: Context, address: String): GeoPoint? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(ctx, Locale.GERMAN)
        val result: List<Address>? = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(address, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (cont.isActive) cont.resume(addresses.toList())
                    }

                    override fun onError(errorMessage: String?) {
                        if (cont.isActive) cont.resume(null)
                    }
                })
            }
        }
        if (result == null) return null
        val a = result.firstOrNull() ?: return GeoPoint(query = address, found = false)
        return GeoPoint(a.latitude, a.longitude, address, true)
    }

    /** Geocodes every contact whose address has no fresh coordinates. Returns how many are still missing. */
    suspend fun refreshAll(ctx: Context, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }) {
        val todo = OrbitRepository.snapshot().contacts.filter { needsLookup(it) }
        todo.forEachIndexed { i, c ->
            onProgress(i, todo.size)
            val address = c.address.trim()
            val point = lookup(ctx, address)
            if (point != null) {
                OrbitRepository.update { d ->
                    d.copy(contacts = d.contacts.map { if (it.id == c.id && it.address.trim() == address) it.copy(geo = point) else it })
                }
            }
            delay(150)
        }
        onProgress(todo.size, todo.size)
    }

    /** Great-circle distance in kilometres. */
    fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    fun formatDistance(km: Double): String = when {
        km < 1.0 -> "${(km * 1000).toInt() / 10 * 10} m"
        km < 10.0 -> String.format(Locale.GERMAN, "%.1f km", km)
        else -> String.format(Locale.GERMAN, "%,d km", km.toInt())
    }

    fun googleMapsSearch(address: String): Uri =
        Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(address))

    fun googleMapsRoute(address: String): Uri =
        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(address))
}
