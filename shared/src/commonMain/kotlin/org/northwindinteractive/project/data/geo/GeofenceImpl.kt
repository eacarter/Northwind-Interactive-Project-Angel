package com.northwindinteractive.penntestapp.data.geo

import org.northwindinteractive.project.domain.models.Venue

interface GeofenceImpl {
    suspend fun registerGeofence(venues: List<Venue>)
    suspend fun unRegisterGeofence()
    suspend fun checkInitialContainment(venues: List<Venue>): List<Venue>
}