package org.northwindinteractive.project

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.northwindinteractive.project.platform.AndroidPermissionBridge
import org.northwindinteractive.project.platform.handleDiscreetAlertIntent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidPermissionBridge.register(this)
        if (savedInstanceState == null) handleDiscreetAlertIntent(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDiscreetAlertIntent(intent)
    }
}
