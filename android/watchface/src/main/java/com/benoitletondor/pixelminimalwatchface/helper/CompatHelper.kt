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
package com.benoitletondor.pixelminimalwatchface.helper

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.util.Log
import com.benoitletondor.pixelminimalwatchface.Device
import com.benoitletondor.pixelminimalwatchface.R
import org.json.JSONObject
import kotlin.math.roundToInt
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Intent
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.pm.PackageInfoCompat
import androidx.wear.watchface.complications.data.*
import com.benoitletondor.pixelminimalwatchface.model.ComplicationLocation
import com.benoitletondor.pixelminimalwatchface.model.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.text.SimpleDateFormat
import java.util.*

private val galaxyWatch4AODBuggyWearOSVersions = setOf(
    "EVA8",
    "EVA9",
)

val isGalaxyWatch4AODBuggyWearOSVersion = Device.isSamsungGalaxyWatch && Build.VERSION.INCREMENTAL.takeLast(4) in galaxyWatch4AODBuggyWearOSVersions
val isGalaxyWatch4CalendarBuggyWearOSVersion = Device.isSamsungGalaxyWatch && Build.VERSION.SECURITY_PATCH.startsWith("2022")

fun Context.getTopAndBottomMargins(): Float {
    return when {
        Device.isOppoWatch -> dpToPx(5).toFloat()
        Device.isSamsungGalaxyWatchBigScreen(this) -> dpToPx(29).toFloat()
        Device.isSamsungGalaxyWatch -> dpToPx(26).toFloat()
        else -> resources.getDimension(R.dimen.screen_top_and_bottom_margin)
    }
}

private val timeDateFormatter24h = SimpleDateFormat("HH:mm", Locale.US)
private val timeDateFormatter12h = SimpleDateFormat("h:mm", Locale.US)

private var heartRateIcon: Icon? = null
private var calendarIcon: Icon? = null

@SuppressLint("RestrictedApi")
fun ComplicationData.sanitizeForSamsungGalaxyWatchIfNeeded(
    context: Context,
    storage: Storage,
    complicationLocation: ComplicationLocation,
    dataSource: ComponentName?,
): ComplicationData? {
    try {
        if (!Device.isSamsungGalaxyWatch) {
            return this
        }

        if (dataSource == null) {
            return this
        }

        if (type == ComplicationType.EMPTY) {
            return this
        }

        return when {
            dataSource.isSamsungHeartRateProvider() -> {
                val shortText = context.getSamsungHeartRateData() ?: "?"

                val icon = heartRateIcon ?: kotlin.run {
                    val icon =  Icon.createWithResource(context, R.drawable.ic_heart_complication)
                    heartRateIcon = icon
                    icon
                }
                val builder = ShortTextComplicationData.Builder(PlainComplicationText.Builder(shortText).build(), ComplicationText.EMPTY)
                    .setTapAction(tapAction)
                    .setMonochromaticImage(MonochromaticImage.Builder(icon).setAmbientImage(icon).build())
                builder.build()
            }
            dataSource.isSamsungHealthBadComplicationData(context) -> {
                android.support.wearable.complications.ComplicationData.Builder(this.asWireComplicationData())
                    .setTapAction(
                        PendingIntent.getActivity(
                            context,
                            0,
                            Intent().apply {
                                component = getSamsungHealthHomeComponentName()
                            },
                            PendingIntent.FLAG_IMMUTABLE,
                        )
                    )
                    .build()
                    .toApiComplicationData()
            }
            dataSource.isSamsungCalendarBuggyProvider() -> {
                val nextEvent = context.getNextCalendarEvent() ?: return this
                val isLargeWidget = complicationLocation == ComplicationLocation.BOTTOM

                val icon = calendarIcon ?: kotlin.run {
                    val icon = Icon.createWithResource(context, R.drawable.ic_calendar_complication)
                    calendarIcon = icon
                    icon
                }

                val openCalendarIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent().apply {
                        component = getSamsungCalendarHomeComponentName()
                    },
                    PendingIntent.FLAG_IMMUTABLE,
                )

                if (isLargeWidget) {
                    LongTextComplicationData.Builder(PlainComplicationText.Builder(nextEvent.title).build(), ComplicationText.EMPTY)
                        .setTapAction(openCalendarIntent)
                        .setMonochromaticImage(MonochromaticImage.Builder(icon).setAmbientImage(icon).build())
                        .build()
                } else {
                    val eventDate = Date(nextEvent.startTimestamp)
                    val formattedTime = if (storage.getUse24hTimeFormat()) {
                        timeDateFormatter24h.format(eventDate)
                    } else {
                        timeDateFormatter12h.format(eventDate)
                    }

                    ShortTextComplicationData.Builder(PlainComplicationText.Builder(formattedTime).build(), ComplicationText.EMPTY)
                        .setTapAction(openCalendarIntent)
                        .setMonochromaticImage(MonochromaticImage.Builder(icon).setAmbientImage(icon).build())
                        .build()
                }
            }
            else -> null
        }
    } catch (t: Throwable) {
        Log.e("PixelWatchFace", "Error while sanitizing complication data", t)
        return null
    }
}

private fun ComponentName.isSamsungHealthBadComplicationData(context: Context): Boolean {
    val sHealthVersion = try {
        context.getShealthAppVersion()
    } catch (e: Throwable) {
        return false
    }

    return when {
        sHealthVersion == S_HEALTH_6_20_0_016  -> isSamsungDailyActivityBuggyProvider() ||
            isSamsungStepsProvider() ||
            isSamsungSleepProvider() ||
            isSamsungWaterProvider()
        sHealthVersion >= S_HEALTH_6_21_0_051 -> isSamsungDailyActivityBuggyProvider()
        else -> false
    }
}

private fun ComponentName.isSamsungDailyActivityBuggyProvider(): Boolean {
    return packageName == S_HEALTH_PACKAGE_NAME &&
        className == "com.samsung.android.wear.shealth.complications.dailyactivity.DailyActivityComplicationProviderService"
}

fun ComponentName.isSamsungCalendarBuggyProvider(): Boolean {
    return isGalaxyWatch4CalendarBuggyWearOSVersion &&
        packageName == S_CALENDAR_PACKAGE_NAME &&
        className == "com.google.android.clockwork.sysui.experiences.calendar.NextEventProviderService"
}

private fun ComponentName.isSamsungStepsProvider(): Boolean {
    return packageName == S_HEALTH_PACKAGE_NAME &&
        className == "com.samsung.android.wear.shealth.complications.steps.StepsComplicationProviderService"
}

private fun ComponentName.isSamsungSleepProvider(): Boolean {
    return packageName == S_HEALTH_PACKAGE_NAME &&
        className == "com.samsung.android.wear.shealth.complications.sleep.SleepComplicationProviderService"
}

private fun ComponentName.isSamsungWaterProvider(): Boolean {
    return packageName == S_HEALTH_PACKAGE_NAME &&
        className == "com.samsung.android.wear.shealth.complications.water.WaterComplicationProviderService"
}

fun ComponentName.isSamsungHeartRateProvider(): Boolean {
    return packageName == S_HEALTH_PACKAGE_NAME &&
        className == "com.samsung.android.wear.shealth.complications.heartrate.HeartrateComplicationProviderService"
}

private fun Context.getShealthAppVersion(): Long {
    val packageInfo = packageManager.getPackageInfo(S_HEALTH_PACKAGE_NAME, 0)
    return PackageInfoCompat.getLongVersionCode(packageInfo)
}

@SuppressLint("NewApi", "Range")
private fun Context.getNextCalendarEvent(): CalendarEvent? {
    try {
        val uri = "content://$S_CALENDAR_PACKAGE_NAME.watch/nextEvents"

        contentResolver.query(
            Uri.parse(uri),
            null,
            Bundle().apply { putInt(ContentResolver.QUERY_ARG_LIMIT, 1) },
            null,
        )?.use { query ->
            if (query.count == 0) {
                return null
            }

            query.moveToFirst()
            val eventTitle = query.getString(query.getColumnIndex("title"))
            val eventStartTimestamp = query.getLong(query.getColumnIndex("begin"))

            return CalendarEvent(eventTitle, eventStartTimestamp)
        }

        return null
    } catch (e: Exception) {
        Log.e("CompatHelper", "Error while getting next event", e)
        return null
    }
}

private data class CalendarEvent(
    val title: String,
    val startTimestamp: Long,
)

private fun Context.getSamsungHeartRateData(): String? {
    val uri = "content://$S_HEALTH_PACKAGE_NAME.healthdataprovider"

    val bundle = contentResolver.call(Uri.parse(uri), "heart_rate", null, null)
    if (bundle != null) {
        val error = bundle.getString("error")
        if (error != null) {
            return null
        }

        val data = bundle.getString("data") ?: return null
        val json = JSONObject(data)
        val hr = json.optDouble("value", -1.0)
        return if (hr > 0) {
            hr.roundToInt().toString()
        } else {
            null
        }
    }

    return null
}

fun Context.watchSamsungHeartRateUpdates(): Flow<Unit> = callbackFlow {
    val uri = "content://$S_HEALTH_PACKAGE_NAME.healthdataprovider/"
    val heartRateMethod = "heart_rate"

    fun unregister(observer: ContentObserver) {
        contentResolver.unregisterContentObserver(observer)
        contentResolver.call(Uri.parse(uri), heartRateMethod, "unregister", null)
    }

    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)

            trySendBlocking(Unit)
                .onFailure { e ->
                    if (e == null) {
                        return@onFailure
                    }

                    if (e is CancellationException) {
                        unregister(this)
                    }

                    throw e
                }
        }
    }

    contentResolver.call(Uri.parse(uri), heartRateMethod, "register", null)
    contentResolver.registerContentObserver(Uri.parse(uri), true, observer)

    awaitClose { unregister(observer) }
}

private fun getSamsungHealthHomeComponentName() = ComponentName(
    S_HEALTH_PACKAGE_NAME,
    "com.samsung.android.wear.shealth.app.home.HomeActivity"
)

private fun getSamsungCalendarHomeComponentName() = ComponentName(
    S_CALENDAR_PACKAGE_NAME,
    "com.samsung.android.app.calendar.view.daily.DailyActivity"
)

private const val S_HEALTH_PACKAGE_NAME = "com.samsung.android.wear.shealth"
private const val S_CALENDAR_PACKAGE_NAME = "com.samsung.android.calendar"
private const val S_HEALTH_6_20_0_016 = 6200016L
private const val S_HEALTH_6_21_0_051 = 6210051L
