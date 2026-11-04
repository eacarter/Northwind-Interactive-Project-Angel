package org.northwindinteractive.project.data.ble.fake

import org.northwindinteractive.project.domain.models.Venue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import org.northwindinteractive.project.data.ble.BeaconReading
import org.northwindinteractive.project.data.ble.BleImpl

class FakeBleSource: BleImpl {
    private val flows = mutableMapOf<String, MutableSharedFlow<BeaconReading>>()
    var stopScanCalledFor: MutableList<String> = mutableListOf()
        private set

    override fun scan(venue: Venue): Flow<BeaconReading> {
        val flow = flows.getOrPut(venue.id) { MutableSharedFlow(replay = 0, extraBufferCapacity = 10) }
        return flow.onCompletion { stopScanCalledFor.add(venue.id) }
    }

    suspend fun emitReading(venueId: String, reading: BeaconReading) {
        flows[venueId]?.emit(reading)
    }
}