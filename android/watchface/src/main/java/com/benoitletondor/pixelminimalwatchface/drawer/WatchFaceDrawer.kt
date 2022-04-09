/*
 *   Copyright 2022 Benoit LETONDOR
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.benoitletondor.pixelminimalwatchface.drawer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.support.wearable.complications.ComplicationData
import android.util.SparseArray
import android.view.WindowInsets
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.WatchState
import com.benoitletondor.pixelminimalwatchface.PhoneBatteryStatus
import com.benoitletondor.pixelminimalwatchface.model.ComplicationColors
import com.benoitletondor.pixelminimalwatchface.model.ComplicationLocation
import com.benoitletondor.pixelminimalwatchface.model.Storage
import java.time.ZonedDateTime
import java.util.*

interface WatchFaceDrawer {
    fun onCreate(context: Context, watchState: WatchState)
    fun onDestroy()

    fun getActiveComplicationLocations(): Set<ComplicationLocation>
    fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        weatherComplicationData: ComplicationData?,
    )

    fun initializeComplicationDrawables(drawableCallback: Drawable.Callback): IntArray
    fun onApplyWindowInsets(insets: WindowInsets)
    fun onSurfaceChanged(width: Int, height: Int)
    fun onComplicationColorsUpdate(complicationColors: ComplicationColors, complicationsData: SparseArray<ComplicationData>)
    fun onComplicationDataUpdate(complicationId: Int,
                                 data: ComplicationData?,
                                 complicationColors: ComplicationColors)
    fun tapIsOnComplication(x: Int, y: Int): Boolean
    fun tapIsOnWeather(x: Int, y: Int): Boolean
    fun tapIsInCenterOfScreen(x: Int, y: Int): Boolean
    fun tapIsOnBattery(x: Int, y: Int): Boolean

    fun draw(
        canvas: Canvas,
        calendar: Calendar,
        muteMode: Boolean,
        ambient:Boolean,
        lowBitAmbient: Boolean,
        burnInProtection: Boolean,
        weatherComplicationData: ComplicationData?,
        batteryComplicationData: ComplicationData?,
        phoneBatteryStatus: PhoneBatteryStatus?,
    )
}
