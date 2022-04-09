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
package com.benoitletondor.pixelminimalwatchface.model

import android.content.Context
import android.graphics.ColorFilter
import androidx.annotation.ColorInt
import com.benoitletondor.pixelminimalwatchface.R
import com.benoitletondor.pixelminimalwatchface.helper.DEFAULT_TIME_SIZE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

const val DEFAULT_APP_VERSION = -1

private const val SHARED_PREFERENCES_NAME = "pixelMinimalSharedPref"

private const val DEFAULT_COMPLICATION_COLOR = -147282
private const val KEY_COMPLICATION_COLORS = "complicationColors"
private const val KEY_LEFT_COMPLICATION_COLOR = "leftComplicationColor"
private const val KEY_MIDDLE_COMPLICATION_COLOR = "middleComplicationColor"
private const val KEY_RIGHT_COMPLICATION_COLOR = "rightComplicationColor"
private const val KEY_BOTTOM_COMPLICATION_COLOR = "bottomComplicationColor"
private const val KEY_ANDROID_12_TOP_LEFT_COMPLICATION_COLOR = "android12TopLeftComplicationColor"
private const val KEY_ANDROID_12_TOP_RIGHT_COMPLICATION_COLOR = "android12TopRightComplicationColor"
private const val KEY_ANDROID_12_BOTTOM_LEFT_COMPLICATION_COLOR = "android12BottomLeftComplicationColor"
private const val KEY_ANDROID_12_BOTTOM_RIGHT_COMPLICATION_COLOR = "android12BottomRightComplicationColor"
private const val KEY_USER_PREMIUM = "user_premium"
private const val KEY_USE_24H_TIME_FORMAT = "use24hTimeFormat"
private const val KEY_INSTALL_TIMESTAMP = "installTS"
private const val KEY_RATING_NOTIFICATION_SENT = "ratingNotificationSent"
private const val KEY_APP_VERSION = "appVersion"
private const val KEY_SHOW_WEAR_OS_LOGO = "showWearOSLogo"
private const val KEY_SHOW_COMPLICATIONS_AMBIENT = "showComplicationsAmbient"
private const val KEY_USE_NORMAL_TIME_STYLE_IN_AMBIENT = "filledTimeAmbient"
private const val KEY_USE_THIN_TIME_STYLE_IN_REGULAR = "thinTimeRegularMode"
private const val KEY_TIME_SIZE = "timeSize"
private const val KEY_DATE_AND_BATTERY_SIZE = "dateSize"
private const val KEY_SECONDS_RING = "secondsRing"
private const val KEY_SHOW_WEATHER = "showWeather"
private const val KEY_SHOW_WATCH_BATTERY = "showBattery"
private const val KEY_SHOW_PHONE_BATTERY = "showPhoneBattery"
private const val KEY_FEATURE_DROP_2021_NOTIFICATION = "featureDrop2021Notification_5"
private const val KEY_USE_SHORT_DATE_FORMAT = "useShortDateFormat"
private const val KEY_SHOW_DATE_AMBIENT = "showDateAmbient"
private const val KEY_TIME_AND_DATE_COLOR = "timeAndDateColor"
private const val KEY_BATTERY_COLOR = "batteryColor"
private const val KEY_USE_ANDROID_12_STYLE = "useAndroid12Style"
private const val KEY_HIDE_BATTERY_IN_AMBIENT = "hideBatteryInAmbient"
private const val KEY_SECONDS_RING_COLOR = "secondsRingColor"
private const val KEY_WIDGETS_SIZE = "widgetSize"

interface Storage {
    fun getComplicationColors(): ComplicationColors
    fun setComplicationColors(complicationColors: ComplicationColors)
    fun watchComplicationColors(): StateFlow<ComplicationColors>
    fun isUserPremium(): Boolean
    fun setUserPremium(premium: Boolean)
    fun setUse24hTimeFormat(use: Boolean)
    fun getUse24hTimeFormat(): Boolean
    fun getInstallTimestamp(): Long
    fun hasRatingBeenDisplayed(): Boolean
    fun setRatingDisplayed(sent: Boolean)
    fun getAppVersion(): Int
    fun setAppVersion(version: Int)
    fun showWearOSLogo(): Boolean
    fun setShowWearOSLogo(shouldShowWearOSLogo: Boolean)
    fun showComplicationsInAmbientMode(): Boolean
    fun setShowComplicationsInAmbientMode(show: Boolean)
    fun useNormalTimeStyleInAmbientMode(): Boolean
    fun setUseNormalTimeStyleInAmbientMode(useNormalTime: Boolean)
    fun useThinTimeStyleInRegularMode(): Boolean
    fun setUseThinTimeStyleInRegularMode(useThinTime: Boolean)
    fun getTimeSize(): Int
    fun setTimeSize(timeSize: Int)
    fun getDateAndBatterySize(): Int
    fun setDateAndBatterySize(size: Int)
    fun showSecondsRing(): Boolean
    fun setShowSecondsRing(showSecondsRing: Boolean)
    fun showWeather(): Boolean
    fun setShowWeather(show: Boolean)
    fun watchShowWeather(): Flow<Boolean>
    fun showWatchBattery(): Boolean
    fun setShowWatchBattery(show: Boolean)
    fun hasFeatureDropSummer2021NotificationBeenShown(): Boolean
    fun setFeatureDropSummer2021NotificationShown()
    fun getUseShortDateFormat(): Boolean
    fun setUseShortDateFormat(useShortDateFormat: Boolean)
    fun setShowDateInAmbient(showDateInAmbient: Boolean)
    fun getShowDateInAmbient(): Boolean
    fun showPhoneBattery(): Boolean
    fun setShowPhoneBattery(show: Boolean)
    @ColorInt fun getTimeAndDateColor(): Int
    fun getTimeAndDateColorFilter(): ColorFilter
    fun setTimeAndDateColor(@ColorInt color: Int)
    @ColorInt fun getBatteryIndicatorColor(): Int
    fun getBatteryIndicatorColorFilter(): ColorFilter
    fun setBatteryIndicatorColor(@ColorInt color: Int)
    fun useAndroid12Style(): Boolean
    fun setUseAndroid12Style(useAndroid12Style: Boolean)
    fun watchUseAndroid12Style(): Flow<Boolean>
    fun hideBatteryInAmbient(): Boolean
    fun setHideBatteryInAmbient(hide: Boolean)
    fun getSecondRingColor(): ColorFilter
    fun setSecondRingColor(@ColorInt color: Int)
    fun getWidgetsSize(): Int
    fun setWidgetsSize(widgetsSize: Int)
}

class StorageImpl(
    context: Context,
) : Storage {
    private val appContext = context.applicationContext
    private val sharedPreferences = appContext.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)

    // Those values will be called up to 60 times a minute when not in ambient mode
    // SharedPreferences uses a map so we cache the values to avoid map lookups
    private val timeSizeCache = StorageCachedIntValue(sharedPreferences, KEY_TIME_SIZE, DEFAULT_TIME_SIZE)
    private val dateAndBatterySizeCache = StorageCachedIntValue(sharedPreferences, KEY_DATE_AND_BATTERY_SIZE, getTimeSize())
    private val isPremiumUserCache = StorageCachedBoolValue(sharedPreferences, KEY_USER_PREMIUM, false)
    private val use24hFormatCache = StorageCachedBoolValue(sharedPreferences, KEY_USE_24H_TIME_FORMAT, true)
    private val showWearOSLogoCache = StorageCachedBoolValue(sharedPreferences, KEY_SHOW_WEAR_OS_LOGO, true)
    private val showComplicationsInAmbientModeCache = StorageCachedBoolValue(sharedPreferences, KEY_SHOW_COMPLICATIONS_AMBIENT, false)
    private val showSecondsRingCache = StorageCachedBoolValue(sharedPreferences, KEY_SECONDS_RING, false)
    private val showWeatherCache = StorageCachedBoolValue(sharedPreferences, KEY_SHOW_WEATHER, false)
    private val showWatchBattery = StorageCachedBoolValue(sharedPreferences, KEY_SHOW_WATCH_BATTERY, false)
    private val useShortDateFormatCache = StorageCachedBoolValue(sharedPreferences, KEY_USE_SHORT_DATE_FORMAT, false)
    private val showDateInAmbientCache = StorageCachedBoolValue(sharedPreferences, KEY_SHOW_DATE_AMBIENT, true)
    private val showPhoneBatteryCache = StorageCachedBoolValue(sharedPreferences, KEY_SHOW_PHONE_BATTERY, false)
    private val timeAndDateColorCache = StorageCachedColorValue(sharedPreferences, appContext, KEY_TIME_AND_DATE_COLOR, R.color.white)
    private val batteryIndicatorColorCache = StorageCachedColorValue(sharedPreferences, appContext, KEY_BATTERY_COLOR, R.color.white)
    private val cacheComplicationsColorMutableFlow = MutableStateFlow(loadComplicationColors())
    private val useAndroid12StyleCache = StorageCachedBoolValue(sharedPreferences, KEY_USE_ANDROID_12_STYLE, false)
    private val hideBatteryInAmbientCache = StorageCachedBoolValue(sharedPreferences, KEY_HIDE_BATTERY_IN_AMBIENT, false)
    private val secondRingColorCache = StorageCachedColorValue(sharedPreferences, appContext, KEY_SECONDS_RING_COLOR, R.color.white)
    private val widgetsSizeCache = StorageCachedIntValue(sharedPreferences, KEY_WIDGETS_SIZE, DEFAULT_TIME_SIZE)
    private val useNormalTimeStyleInAmbientModeCache = StorageCachedBoolValue(sharedPreferences, KEY_USE_NORMAL_TIME_STYLE_IN_AMBIENT, false)
    private val useThinTimeStyleInNormalModeCache = StorageCachedBoolValue(sharedPreferences, KEY_USE_THIN_TIME_STYLE_IN_REGULAR, false)
    private val hasRatingBeenDisplayedCache = StorageCachedBoolValue(sharedPreferences, KEY_RATING_NOTIFICATION_SENT, false)

    init {
        if( getInstallTimestamp() < 0 ) {
            sharedPreferences.edit().putLong(KEY_INSTALL_TIMESTAMP, System.currentTimeMillis()).apply()
        }
    }

    private fun loadComplicationColors(): ComplicationColors {
        val baseColor = sharedPreferences.getInt(
            KEY_COMPLICATION_COLORS,
            DEFAULT_COMPLICATION_COLOR
        )

        val leftColor = sharedPreferences.getInt(
            KEY_LEFT_COMPLICATION_COLOR,
            baseColor
        )

        val middleColor = sharedPreferences.getInt(
            KEY_MIDDLE_COMPLICATION_COLOR,
            baseColor
        )

        val rightColor = sharedPreferences.getInt(
            KEY_RIGHT_COMPLICATION_COLOR,
            baseColor
        )

        val bottomColor = sharedPreferences.getInt(
            KEY_BOTTOM_COMPLICATION_COLOR,
            baseColor
        )

        val android12TopLeftColor = sharedPreferences.getInt(
            KEY_ANDROID_12_TOP_LEFT_COMPLICATION_COLOR,
            baseColor
        )

        val android12TopRightColor = sharedPreferences.getInt(
            KEY_ANDROID_12_TOP_RIGHT_COMPLICATION_COLOR,
            baseColor
        )

        val android12BottomLeftColor = sharedPreferences.getInt(
            KEY_ANDROID_12_BOTTOM_LEFT_COMPLICATION_COLOR,
            baseColor
        )

        val android12BottomRightColor = sharedPreferences.getInt(
            KEY_ANDROID_12_BOTTOM_RIGHT_COMPLICATION_COLOR,
            baseColor
        )

        val defaultColors = ComplicationColorsProvider.getDefaultComplicationColors(appContext)

        val colors = ComplicationColors(
            if( leftColor == DEFAULT_COMPLICATION_COLOR ) { defaultColors.leftColor } else { ComplicationColor(leftColor, ComplicationColorsProvider.getLabelForColor(appContext, leftColor),false) },
            if( middleColor == DEFAULT_COMPLICATION_COLOR ) { defaultColors.middleColor } else { ComplicationColor(middleColor, ComplicationColorsProvider.getLabelForColor(appContext, middleColor),false) },
            if( rightColor == DEFAULT_COMPLICATION_COLOR ) { defaultColors.rightColor } else { ComplicationColor(rightColor, ComplicationColorsProvider.getLabelForColor(appContext, rightColor),false) },
            if( bottomColor == DEFAULT_COMPLICATION_COLOR ) { defaultColors.bottomColor } else { ComplicationColor(bottomColor, ComplicationColorsProvider.getLabelForColor(appContext, bottomColor),false) },
            if( android12TopLeftColor == DEFAULT_COMPLICATION_COLOR ) { defaultColors.android12TopLeftColor } else { ComplicationColor(android12TopLeftColor, ComplicationColorsProvider.getLabelForColor(appContext, android12TopLeftColor),false) },
            if( android12TopRightColor == DEFAULT_COMPLICATION_COLOR ) { defaultColors.android12TopRightColor } else { ComplicationColor(android12TopRightColor, ComplicationColorsProvider.getLabelForColor(appContext, android12TopRightColor),false) },
            if( android12BottomLeftColor == DEFAULT_COMPLICATION_COLOR ) { defaultColors.android12BottomLeftColor } else { ComplicationColor(android12BottomLeftColor, ComplicationColorsProvider.getLabelForColor(appContext, android12BottomLeftColor),false) },
            if( android12BottomRightColor == DEFAULT_COMPLICATION_COLOR ) { defaultColors.android12BottomRightColor } else { ComplicationColor(android12BottomRightColor, ComplicationColorsProvider.getLabelForColor(appContext, android12BottomRightColor),false) },
        )

        return colors
    }

    override fun getComplicationColors(): ComplicationColors = cacheComplicationsColorMutableFlow.value

    override fun setComplicationColors(complicationColors: ComplicationColors) {
        cacheComplicationsColorMutableFlow.value = complicationColors
        sharedPreferences.edit()
            .putInt(
                KEY_LEFT_COMPLICATION_COLOR,
                if( complicationColors.leftColor.isDefault ) {
                    DEFAULT_COMPLICATION_COLOR
                } else { complicationColors.leftColor.color }
            )
            .putInt(
                KEY_MIDDLE_COMPLICATION_COLOR,
                if( complicationColors.middleColor.isDefault ) {
                    DEFAULT_COMPLICATION_COLOR
                } else { complicationColors.middleColor.color }
            )
            .putInt(
                KEY_RIGHT_COMPLICATION_COLOR,
                if( complicationColors.rightColor.isDefault ) {
                    DEFAULT_COMPLICATION_COLOR
                } else { complicationColors.rightColor.color }
            )
            .putInt(
                KEY_BOTTOM_COMPLICATION_COLOR,
                if( complicationColors.bottomColor.isDefault ) {
                    DEFAULT_COMPLICATION_COLOR
                } else { complicationColors.bottomColor.color }
            )
            .putInt(
                KEY_ANDROID_12_BOTTOM_LEFT_COMPLICATION_COLOR,
                if( complicationColors.android12BottomLeftColor.isDefault ) {
                    DEFAULT_COMPLICATION_COLOR
                } else { complicationColors.android12BottomLeftColor.color }
            )
            .putInt(
                KEY_ANDROID_12_TOP_LEFT_COMPLICATION_COLOR,
                if( complicationColors.android12TopLeftColor.isDefault ) {
                    DEFAULT_COMPLICATION_COLOR
                } else { complicationColors.android12TopLeftColor.color }
            )
            .putInt(
                KEY_ANDROID_12_TOP_RIGHT_COMPLICATION_COLOR,
                if( complicationColors.android12TopRightColor.isDefault ) {
                    DEFAULT_COMPLICATION_COLOR
                } else { complicationColors.android12TopRightColor.color }
            )
            .putInt(
                KEY_ANDROID_12_BOTTOM_RIGHT_COMPLICATION_COLOR,
                if( complicationColors.android12BottomRightColor.isDefault ) {
                    DEFAULT_COMPLICATION_COLOR
                } else { complicationColors.android12BottomRightColor.color }
            )
            .apply()
    }

    override fun watchComplicationColors(): StateFlow<ComplicationColors> = cacheComplicationsColorMutableFlow

    override fun isUserPremium(): Boolean = isPremiumUserCache.get()

    override fun setUserPremium(premium: Boolean) = isPremiumUserCache.set(premium)

    override fun setUse24hTimeFormat(use: Boolean) = use24hFormatCache.set(use)

    override fun getUse24hTimeFormat(): Boolean = use24hFormatCache.get()

    override fun getInstallTimestamp(): Long {
        return sharedPreferences.getLong(KEY_INSTALL_TIMESTAMP, -1)
    }

    override fun hasRatingBeenDisplayed(): Boolean = hasRatingBeenDisplayedCache.get()

    override fun setRatingDisplayed(sent: Boolean) = hasRatingBeenDisplayedCache.set(sent)

    override fun getAppVersion(): Int {
        return sharedPreferences.getInt(KEY_APP_VERSION, DEFAULT_APP_VERSION)
    }

    override fun setAppVersion(version: Int) {
        sharedPreferences.edit().putInt(KEY_APP_VERSION, version).apply()
    }

    override fun showWearOSLogo(): Boolean = showWearOSLogoCache.get()

    override fun setShowWearOSLogo(shouldShowWearOSLogo: Boolean) = showWearOSLogoCache.set(shouldShowWearOSLogo)

    override fun showComplicationsInAmbientMode(): Boolean = showComplicationsInAmbientModeCache.get()

    override fun setShowComplicationsInAmbientMode(show: Boolean) = showComplicationsInAmbientModeCache.set(show)

    override fun useNormalTimeStyleInAmbientMode(): Boolean = useNormalTimeStyleInAmbientModeCache.get()

    override fun setUseNormalTimeStyleInAmbientMode(useNormalTime: Boolean) = useNormalTimeStyleInAmbientModeCache.set(useNormalTime)

    override fun useThinTimeStyleInRegularMode(): Boolean = useThinTimeStyleInNormalModeCache.get()

    override fun setUseThinTimeStyleInRegularMode(useThinTime: Boolean) = useThinTimeStyleInNormalModeCache.set(useThinTime)

    override fun getTimeSize(): Int = timeSizeCache.get()

    override fun setTimeSize(timeSize: Int) = timeSizeCache.set(timeSize)

    override fun getDateAndBatterySize(): Int = dateAndBatterySizeCache.get()

    override fun setDateAndBatterySize(size: Int) = dateAndBatterySizeCache.set(size)

    override fun showSecondsRing(): Boolean = showSecondsRingCache.get()

    override fun setShowSecondsRing(showSecondsRing: Boolean) = showSecondsRingCache.set(showSecondsRing)

    override fun showWeather(): Boolean = showWeatherCache.get()

    override fun setShowWeather(show: Boolean) = showWeatherCache.set(show)

    override fun watchShowWeather(): Flow<Boolean> = showWeatherCache.watchChanges()

    override fun showWatchBattery(): Boolean = showWatchBattery.get()

    override fun setShowWatchBattery(show: Boolean) = showWatchBattery.set(show)

    override fun showPhoneBattery(): Boolean = showPhoneBatteryCache.get()

    override fun setShowPhoneBattery(show: Boolean) = showPhoneBatteryCache.set(show)

    override fun getTimeAndDateColor(): Int = timeAndDateColorCache.get().color

    override fun getTimeAndDateColorFilter(): ColorFilter = timeAndDateColorCache.get().colorFilter

    override fun setTimeAndDateColor(color: Int) = timeAndDateColorCache.set(color)

    override fun getBatteryIndicatorColor(): Int = batteryIndicatorColorCache.get().color

    override fun getBatteryIndicatorColorFilter(): ColorFilter = batteryIndicatorColorCache.get().colorFilter

    override fun setBatteryIndicatorColor(color: Int) = batteryIndicatorColorCache.set(color)

    override fun useAndroid12Style(): Boolean = useAndroid12StyleCache.get()

    override fun setUseAndroid12Style(useAndroid12Style: Boolean) = useAndroid12StyleCache.set(useAndroid12Style)

    override fun watchUseAndroid12Style(): Flow<Boolean> = useAndroid12StyleCache.watchChanges()

    override fun hideBatteryInAmbient(): Boolean = hideBatteryInAmbientCache.get()

    override fun setHideBatteryInAmbient(hide: Boolean) = hideBatteryInAmbientCache.set(hide)

    override fun getSecondRingColor(): ColorFilter = secondRingColorCache.get().colorFilter

    override fun setSecondRingColor(@ColorInt color: Int) = secondRingColorCache.set(color)

    override fun getWidgetsSize(): Int = widgetsSizeCache.get()

    override fun setWidgetsSize(widgetsSize: Int) = widgetsSizeCache.set(widgetsSize)

    override fun hasFeatureDropSummer2021NotificationBeenShown(): Boolean {
        return sharedPreferences.getBoolean(KEY_FEATURE_DROP_2021_NOTIFICATION, false)
    }

    override fun setFeatureDropSummer2021NotificationShown() {
        sharedPreferences.edit().putBoolean(KEY_FEATURE_DROP_2021_NOTIFICATION, true).apply()
    }

    override fun getUseShortDateFormat(): Boolean = useShortDateFormatCache.get()

    override fun setUseShortDateFormat(useShortDateFormat: Boolean) = useShortDateFormatCache.set(useShortDateFormat)

    override fun setShowDateInAmbient(showDateInAmbient: Boolean) = showDateInAmbientCache.set(showDateInAmbient)

    override fun getShowDateInAmbient(): Boolean = showDateInAmbientCache.get()
}
