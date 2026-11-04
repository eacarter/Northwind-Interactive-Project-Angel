package com.northwindinteractive.northwindinteractivepapertrader.di

import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import org.northwindinteractive.project.di.bluetoothModule
import org.northwindinteractive.project.di.firebaseModule
import org.northwindinteractive.project.di.viewModelModule

fun initKoin() {
    val koinInstance = KoinPlatform.getKoinOrNull()

    if(koinInstance != null){
        print("Instance already started")
    }
    else {
        startKoin {
            modules(
                firebaseModule,
                viewModelModule,
                bluetoothModule
            )
        }
    }
}