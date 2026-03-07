package com.pragyan.frontendandroid.posture

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.PI

object AngleUtils {

    fun calculateAngle(a: List<Float>, b: List<Float>, c: List<Float>): Int {

        val radians =
            atan2((c[1] - b[1]).toDouble(), (c[0] - b[0]).toDouble()) -
                    atan2((a[1] - b[1]).toDouble(), (a[0] - b[0]).toDouble())

        var angle = abs(radians * 180.0 / PI)

        if (angle > 180) {
            angle = 360 - angle
        }

        return angle.toInt()
    }
}