package org.northwindinteractive.project

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform