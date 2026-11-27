package org.northwindinteractive.project

import android.app.Application
import org.northwindinteractive.project.platform.AndroidAngel

class AngelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidAngel.start(this)
    }
}
