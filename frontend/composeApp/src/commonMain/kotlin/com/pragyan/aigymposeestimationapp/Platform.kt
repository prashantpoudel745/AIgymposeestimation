package com.pragyan.aigymposeestimationapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform