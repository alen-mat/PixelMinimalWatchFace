/*
 *   Copyright 2021 Benoit LETONDOR
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
package com.benoitletondor.pixelminimalwatchface

import android.content.Context
import com.benoitletondor.pixelminimalwatchface.helper.isServiceAvailable

private const val WEAR_OS_APP_PACKAGE = "com.google.android.wearable.app"
private const val WEAR_OS_WEATHER_PROVIDER_SERVICE = "com.google.android.clockwork.home.weather.WeatherProviderService"
private const val WEAR_OS_WEATHER_ACTIVITY_NAME = "com.google.android.clockwork.home.weather.WeatherActivity"

private const val HEY_WEATHER_PACKAGE = "com.heytap.wearable.weather"
private const val HEY_WEATHER_PROVIDER_SERVICE = "com.heytap.wearable.weather.complication.WeatherProviderService"
private const val HEY_WEATHER_ACTIVITY_NAME = "com.heytap.wearable.weather.view.WeatherCityActivity"

data class WeatherProviderInfo(
    val appPackage: String,
    val weatherProviderService: String,
    val weatherActivityName: String,
)

private enum class WeatherProviders(val info: WeatherProviderInfo) {
    GOOGLE(WeatherProviderInfo(
        WEAR_OS_APP_PACKAGE,
        WEAR_OS_WEATHER_PROVIDER_SERVICE,
        WEAR_OS_WEATHER_ACTIVITY_NAME,
    )),
    HEY(WeatherProviderInfo(
        HEY_WEATHER_PACKAGE,
        HEY_WEATHER_PROVIDER_SERVICE,
        HEY_WEATHER_ACTIVITY_NAME,
    )),
}

fun Context.getWeatherProviderInfo(): WeatherProviderInfo? {
    return WeatherProviders.values().map { it.info }.firstOrNull { weatherProviderInfo ->
        isServiceAvailable(weatherProviderInfo.appPackage, weatherProviderInfo.weatherProviderService)
    }
}