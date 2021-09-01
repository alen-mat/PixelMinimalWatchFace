package com.benoitletondor.pixelminimalwatchface

import android.os.Build

object Device {
    val isSamsungGalaxy get(): Boolean = Build.BRAND.equals("samsung", ignoreCase = true)
    val isOppoWatch get(): Boolean = Build.BRAND == "OPPO" && Build.MODEL == "OPPO Watch"
}