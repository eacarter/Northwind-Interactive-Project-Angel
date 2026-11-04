package org.northwindinteractive.project.data.ble

import com.juul.kable.Advertisement
import com.juul.kable.Scanner
import org.northwindinteractive.project.domain.models.Venue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


class BleSource(
    private val scanner: Scanner<Advertisement>
): BleImpl {
    override fun scan(venue: Venue): Flow<BeaconReading> = flow {

    }

//    private fun checkIfMatchesVenue(result: ScanResult, venue: Venue): Boolean {
//        val serviceData = result.scanRecord?.getServiceData(
//            ParcelUuid(UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB"))
//        ) ?: return false
//
//        if (serviceData.size < 17 || serviceData[0] != 0x00.toByte()) return false
//
//        val namespace = serviceData.copyOfRange(1, 11).toHex()
//        val instance = serviceData.copyOfRange(11, 17).toHex()
//
//        return namespace.equals(venue.beaconIdentity.name, ignoreCase = true) &&
//                instance.equals(venue.beaconIdentity.instance, ignoreCase = true)
//    }
//
//    private fun ByteArray.toHex(): String =
//        joinToString("") { "%02X".format(it) }

}