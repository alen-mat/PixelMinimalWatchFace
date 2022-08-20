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

import android.graphics.Canvas
import android.graphics.Rect
import android.support.wearable.complications.ComplicationData
import androidx.wear.watchface.TapEvent
import com.benoitletondor.pixelminimalwatchface.PhoneNotifications
import com.benoitletondor.pixelminimalwatchface.model.ComplicationLocation
import java.time.ZonedDateTime

interface WatchFaceDrawer {
    fun onDestroy()

    fun getActiveComplicationLocations(): Set<ComplicationLocation>

    fun isTapOnWeather(tapEvent: TapEvent): Boolean
    fun isTapOnCenterOfScreen(tapEvent: TapEvent): Boolean
    fun isTapOnBattery(tapEvent: TapEvent): Boolean
    fun isTapOnNotifications(x: Int, y: Int): Boolean

    fun draw(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        weatherComplicationData: ComplicationData?,
        phoneBatteryValue: String?,
        watchBatteryValue: Int?,
        notificationsState: PhoneNotifications.NotificationState?,
    )
}
