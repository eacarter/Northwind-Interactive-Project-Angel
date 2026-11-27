package org.northwindinteractive.project.core.platform

import co.touchlab.kermit.Logger
import com.juul.kable.Scanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapNotNull
import org.northwindinteractive.project.core.domain.model.BeaconObservation
import org.northwindinteractive.project.core.domain.model.VenueSummary
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Eddystone-UID scanner for the provisioned ESP32 venue beacons (milestone M5).
 *
 * Eddystone service data (0xFEAA) is readable through CoreBluetooth on iOS and BluetoothLeScanner on
 * Android, so a single shared implementation covers both platforms. Frame layout:
 * `[0]=0x00 frame type, [1]=tx power, [2..11]=namespace, [12..17]=instance`.
 * The instance id is used as the zone id; the backend owns the zone registry.
 */
class KableBeaconScanner(
    private val clock: Clock,
) : BeaconScanner {

    override val isSupported: Boolean = true

    override fun scan(venue: VenueSummary): Flow<BeaconObservation> {
        val namespace = venue.beaconNamespace ?: return kotlinx.coroutines.flow.emptyFlow()
        val scanner = Scanner {
            filters {
                match { services = listOf(EDDYSTONE_SERVICE) }
            }
        }
        return scanner.advertisements
            .mapNotNull { advertisement ->
                val data = advertisement.serviceData(EDDYSTONE_SERVICE) ?: return@mapNotNull null
                if (data.size < 18 || data[0] != 0x00.toByte()) return@mapNotNull null
                val frameNamespace = data.copyOfRange(2, 12).toHex()
                if (!frameNamespace.equals(namespace, ignoreCase = true)) return@mapNotNull null
                val instance = data.copyOfRange(12, 18).toHex()
                BeaconObservation(
                    beaconId = "$frameNamespace:$instance",
                    zoneId = instance,
                    rssi = advertisement.rssi,
                    observedAt = clock.now(),
                )
            }
            .catch { error ->
                // BLE is optional supplementary evidence; failures fall back to geofence-only.
                Logger.w(error) { "Beacon scan unavailable" }
            }
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()
    }

    private companion object {
        val EDDYSTONE_SERVICE: Uuid = Uuid.parse("0000feaa-0000-1000-8000-00805f9b34fb")
    }
}

/** Used when the venue has no beacon zones or the platform/user disallows BLE. */
object NoBeaconScanner : BeaconScanner {
    override val isSupported: Boolean = false
    override fun scan(venue: VenueSummary): Flow<BeaconObservation> = kotlinx.coroutines.flow.emptyFlow()
}
