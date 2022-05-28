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
package com.benoitletondor.pixelminimalwatchface.settings

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.*
import androidx.wear.phone.interactions.PhoneTypeHelper
import androidx.wear.remote.interactions.RemoteActivityHelper
import androidx.wear.watchface.ComplicationHelperActivity
import androidx.wear.watchface.complications.ComplicationDataSourceInfo
import androidx.wear.watchface.editor.EditorSession
import androidx.wear.widget.ConfirmationOverlay
import com.benoitletondor.pixelminimalwatchface.*
import com.benoitletondor.pixelminimalwatchface.BuildConfig.COMPANION_APP_PLAYSTORE_URL
import com.benoitletondor.pixelminimalwatchface.R
import com.benoitletondor.pixelminimalwatchface.compose.*
import com.benoitletondor.pixelminimalwatchface.compose.component.*
import com.benoitletondor.pixelminimalwatchface.helper.*
import com.benoitletondor.pixelminimalwatchface.model.ComplicationColor
import com.benoitletondor.pixelminimalwatchface.model.ComplicationLocation
import com.benoitletondor.pixelminimalwatchface.model.Storage
import com.benoitletondor.pixelminimalwatchface.rating.FeedbackActivity
import com.benoitletondor.pixelminimalwatchface.settings.WidgetConfigurationActivity.Companion.RESULT_RELAUNCH
import com.benoitletondor.pixelminimalwatchface.settings.phonebattery.PhoneBatteryConfigurationActivity
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await

class SettingsActivity : ComponentActivity() {
    private lateinit var storage: Storage

    private lateinit var watchFaceComponentName: ComponentName
    private lateinit var remoteActivityHelper: RemoteActivityHelper
    private lateinit var capabilityClient: CapabilityClient
    private lateinit var nodeClient: NodeClient

    private val complicationsMutableFlow = MutableStateFlow(emptyMap<ComplicationLocation, ComplicationDataSourceInfo?>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        watchFaceComponentName = ComponentName(this, PixelMinimalWatchFace::class.java)
        storage = Injection.storage(this)

        lifecycleScope.launch {
            currentEditorSession = EditorSession.createOnWatchEditorSession(this@SettingsActivity)

            currentEditorSession
                ?.complicationsDataSourceInfo
                ?.collect { infos ->
                    try {
                        infos.forEach { (slotId, complicationDataSourceInfo) ->
                            ComplicationsProviders.getInstance().complicationDataSourceInfoUpdated(slotId, complicationDataSourceInfo)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e(TAG, "Error while updating data sources", e)
                    }

                    complicationsMutableFlow.value = ComplicationsSlots.getComplicationsDataSources(infos)
                }
        }

        remoteActivityHelper = RemoteActivityHelper(this, Dispatchers.IO.asExecutor())
        capabilityClient = Wearable.getCapabilityClient(this)
        nodeClient = Wearable.getNodeClient(this)

        setContent {
            SettingsScreen()
        }
    }

    override fun onDestroy() {
        currentEditorSession = null

        super.onDestroy()
    }

    @SuppressLint("RestrictedApi")
    @Composable
    private fun SettingsScreen() {
        val context = LocalContext.current
        val isScreenRound = remember { context.isScreenRound() }
        val weatherProviderInfo = remember { context.getWeatherProviderInfo() }
        val useAndroid12 by storage.watchUseAndroid12Style().collectAsState(storage.useAndroid12Style())
        val isUserPremium by storage.watchIsUserPremium().collectAsState(storage.isUserPremium())
        val showWatchBattery by storage.watchShowWatchBattery().collectAsState(storage.showWatchBattery())
        val showPhoneBattery by storage.watchShowPhoneBattery().collectAsState(storage.showPhoneBattery())

        WearTheme {
            RotatoryAwareScalingLazyColumn {
                item(key = "Title") {
                    Text(
                        text = "Pixel Minimal Watch Face",
                        fontFamily = productSansFontFamily,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillParentMaxWidth(),
                    )
                }
                item(key = "Android 12 style") {
                    SettingToggleChip(
                        label = "Android 12 style",
                        checked = useAndroid12,
                        onCheckedChange = { storage.setUseAndroid12Style(it) },
                        iconDrawable = R.drawable.ic_baseline_android,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                item(key = "WidgetsSection") { SettingSectionItem(label = "Widgets") }

                if (isUserPremium) {
                    if (useAndroid12) {
                        item(key = "Android12Complications") {
                            Android12Complications()
                        }
                    } else {
                        item(key = "RegularComplications") {
                            RegularComplications(
                                showBattery = showPhoneBattery || showWatchBattery,
                            )
                        }
                    }

                    item(key = "WidgetSize") {
                        val widgetsSize by storage.watchWidgetsSize().collectAsState(storage.getWidgetsSize())

                        SettingSlider(
                            iconDrawable = R.drawable.ic_baseline_photo_size_select_small_24,
                            onValueChange = { newValue ->
                                storage.setWidgetsSize(newValue)
                            },
                            value = widgetsSize,
                            title = "Size of widgets: ${context.fontDisplaySizeToHumanReadableString(widgetsSize)}",
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                } else {
                    item(key = "PremiumCompanion") {
                        Column {
                            Text(
                                text = "To setup widgets, display weather info and battery indicators you have to become a premium user.",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillParentMaxWidth(),
                            )

                            Text(
                                text = "Install the companion app on your phone to continue",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillParentMaxWidth(),
                            )

                            Chip(
                                label = { Text("Open companion app on phone") },
                                onClick = ::openAppOnPhone,
                                icon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_phonelink_setup_white),
                                        contentDescription = null,
                                    )
                                }
                            )
                        }
                    }
                }

                item(key = "ShowWearOSLogo") {
                    val showWearOSLogo by storage.watchShowWearOSLogo().collectAsState(storage.showWearOSLogo())

                    SettingToggleChip(
                        label = if (useAndroid12 || !isUserPremium) { "Show WearOS logo" } else { "WearOS logo as middle widget" },
                        checked = showWearOSLogo,
                        onCheckedChange = { storage.setShowWearOSLogo(it) },
                        iconDrawable = R.drawable.ic_wear_os_logo_white,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }

                if (isUserPremium) {
                    item(key = "BatteryIndicatorSection") {
                        SettingSectionItem(
                            label = "Display battery status",
                            includeBottomPadding = false,
                        )
                    }

                    if (!useAndroid12) {
                        item(key = "BatteryIndicatorBottomWidgetWarning") {
                            Text(
                                text = "Activating any battery indicator replaces the bottom widget",
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                            )
                        }
                    }

                    item(key = "WatchBatteryIndicator") {
                        SettingToggleChip(
                            label = "Watch battery indicator",
                            checked = showWatchBattery,
                            onCheckedChange = { showBattery ->
                                if (showBattery) {
                                    startActivityForResult(
                                        ComplicationHelperActivity.createPermissionRequestHelperIntent(
                                            context,
                                            watchFaceComponentName,
                                            null,
                                            null,
                                        ),
                                        COMPLICATION_BATTERY_PERMISSION_REQUEST_CODE,
                                    )
                                } else {
                                    storage.setShowWatchBattery(false)
                                }
                            },
                            iconDrawable = R.drawable.ic_baseline_battery_charging_full,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }

                    item(key = "PhoneBatteryIndicator") {
                        SettingChip(
                            label = "(Beta) Phone battery indicator setup",
                            onClick = {
                                startActivityForResult(
                                    Intent(this@SettingsActivity, PhoneBatteryConfigurationActivity::class.java),
                                    COMPLICATION_PHONE_BATTERY_SETUP_REQUEST_CODE,
                                )
                            },
                            iconDrawable = R.drawable.ic_phone,
                        )
                    }

                    item(key = "BatteryIndicatorsColor") {
                        SettingChip(
                            label = "Battery indicators colors (doesn't affect ambient mode)",
                            onClick = {
                                startActivityForResult(
                                    ColorSelectionActivity.createIntent(
                                        this@SettingsActivity,
                                        ComplicationColor(getColor(R.color.white), getString(R.string.color_default), true)
                                    ),
                                    BATTERY_COLOR_REQUEST_CODE
                                )
                            },
                            iconDrawable = R.drawable.ic_palette_24,
                            modifier = Modifier.heightIn(min = 70.dp),
                        )
                    }
                }

                item(key = "DateTimeSection") {
                    SettingSectionItem(
                        label = "Time and date",
                        includeBottomPadding = false,
                    )
                }

                item(key = "ShortDateFormat") {
                    val useShortDateFormat by storage.watchUseShortDateFormat().collectAsState(storage.getUseShortDateFormat())

                    SettingToggleChip(
                        label = "Use short date format",
                        checked = useShortDateFormat,
                        onCheckedChange = { storage.setUseShortDateFormat(it) },
                        iconDrawable = R.drawable.ic_baseline_short_text,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }

                if( isUserPremium && weatherProviderInfo != null ) {
                    item(key = "ShowWeather") {
                        val showWeather by storage.watchShowWeather().collectAsState(storage.showWeather())

                        Column {
                            SettingToggleChip(
                                label = "Show weather after date",
                                checked = showWeather,
                                onCheckedChange = { showWeather ->
                                    if (showWeather) {
                                        startActivityForResult(
                                            ComplicationHelperActivity.createPermissionRequestHelperIntent(
                                                context,
                                                watchFaceComponentName,
                                                null,
                                                null,
                                            ),
                                            COMPLICATION_WEATHER_PERMISSION_REQUEST_CODE
                                        )
                                    } else {
                                        storage.setShowWeather(false)
                                    }
                                },
                                iconDrawable = R.drawable.ic_weather_partly_cloudy,
                            )

                            if (showWeather) {
                                Text(
                                    text = "Temperature scale (°F or °C) is controlled by the Weather app.",
                                    fontSize = 12.sp,
                                    lineHeight = 14.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(start = 40.dp, bottom = 4.dp),
                                )

                                SettingChip(
                                    label = "Open Weather app for setup",
                                    onClick = {
                                        weatherProviderInfo.let { weatherProviderInfo ->
                                            openActivity(weatherProviderInfo.appPackage, weatherProviderInfo.weatherActivityName)
                                        }
                                    },
                                    iconDrawable = null,
                                    modifier = Modifier.padding(start = 40.dp, bottom = 8.dp),
                                )
                            }
                        }

                    }
                }

                item(key = "24hTimeFormatSetting") {
                    val use24hTimeFormat by storage.watchUse24hTimeFormat().collectAsState(storage.getUse24hTimeFormat())

                    SettingToggleChip(
                        label = "Use 24h time format",
                        checked = use24hTimeFormat,
                        onCheckedChange = { storage.setUse24hTimeFormat(it) },
                        iconDrawable = R.drawable.ic_access_time,
                    )
                }

                item(key = "TimeSize") {
                    val timeSize by storage.watchTimeSize().collectAsState(storage.getTimeSize())

                    SettingSlider(
                        iconDrawable = R.drawable.ic_baseline_format_size,
                        onValueChange = { newValue ->
                            storage.setTimeSize(newValue)
                        },
                        value = timeSize,
                        title = "Size of time: ${context.fontDisplaySizeToHumanReadableString(timeSize)}",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                item(key = "DateAndBatterySize") {
                    val dateAndBatterySize by storage.watchDateAndBatterySize().collectAsState(storage.getDateAndBatterySize())

                    SettingSlider(
                        iconDrawable = R.drawable.ic_baseline_format_size,
                        onValueChange = { newValue ->
                            storage.setDateAndBatterySize(newValue)
                        },
                        value = dateAndBatterySize,
                        title = "Size of date & battery: ${context.fontDisplaySizeToHumanReadableString(dateAndBatterySize)}",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                item(key = "TimeDataColor") {
                    SettingChip(
                        label = "Time and date color (doesn't affect ambient mode)",
                        onClick = {
                            startActivityForResult(
                                ColorSelectionActivity.createIntent(
                                    this@SettingsActivity,
                                    ComplicationColor(getColor(R.color.white), getString(R.string.color_default), true)
                                ),
                                TIME_AND_DATE_COLOR_REQUEST_CODE
                            )
                        },
                        iconDrawable = R.drawable.ic_palette_24,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .heightIn(min = 70.dp),
                    )
                }

                if( isScreenRound ) {
                    item(key = "ShowSecondsRing") {
                        val showSecondsRing by storage.watchShowSecondsRing().collectAsState(storage.showSecondsRing())

                        Column {
                            SettingToggleChip(
                                label = "Show seconds ring",
                                checked = showSecondsRing,
                                onCheckedChange = { storage.setShowSecondsRing(it) },
                                iconDrawable = R.drawable.ic_baseline_panorama_fish_eye,
                            )

                            if (showSecondsRing) {
                                SettingChip(
                                    label = "Seconds ring color",
                                    onClick = {
                                        startActivityForResult(
                                            ColorSelectionActivity.createIntent(
                                                this@SettingsActivity,
                                                ComplicationColor(getColor(R.color.white), getString(R.string.color_default), true)
                                            ),
                                            SECONDS_RING_COLOR_REQUEST_CODE
                                        )
                                    },
                                    iconDrawable = R.drawable.ic_palette_24,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }

                item(key = "TimeStyleSection") {
                    SettingSectionItem(
                        label = "Time style",
                    )
                }

                item(key = "ThinTimeWatchOn") {
                    val useThinTimeStyleInRegularMode by storage.watchUseThinTimeStyleInRegularMode().collectAsState(storage.useThinTimeStyleInRegularMode())

                    SettingToggleChip(
                        label = "Use thin time style when watch is on",
                        checked = useThinTimeStyleInRegularMode,
                        onCheckedChange = { storage.setUseThinTimeStyleInRegularMode(it) },
                        iconDrawable = R.drawable.ic_baseline_invert_colors,
                        modifier = Modifier.heightIn(min = 70.dp),
                    )
                }

                item(key = "NormalTimeWatchOff") {
                    val useNormalTimeStyleInAmbientMode by storage.watchUseNormalTimeStyleInAmbientMode().collectAsState(storage.useNormalTimeStyleInAmbientMode())

                    SettingToggleChip(
                        label = "Use normal in place of thin time style in ambient mode",
                        checked = useNormalTimeStyleInAmbientMode,
                        onCheckedChange = { storage.setUseNormalTimeStyleInAmbientMode(it) },
                        iconDrawable = R.drawable.ic_baseline_invert_colors_off,
                        modifier = Modifier.heightIn(min = 70.dp),
                    )
                }

                item(key = "AmbientSection") {
                    SettingSectionItem(
                        label = "Ambient mode",
                    )
                }

                item(key = "ShowDateInAmbientMode") {
                    val showDateInAmbient by storage.watchShowDateInAmbient().collectAsState(storage.getShowDateInAmbient())

                    SettingToggleChip(
                        label = "Show date in ambient mode",
                        checked = showDateInAmbient,
                        onCheckedChange = { storage.setShowDateInAmbient(it) },
                        iconDrawable = R.drawable.ic_outline_calendar_today,
                    )
                }

                if (isUserPremium) {
                    item(key= "ComplicationsInAmbientMode") {
                        val showComplicationsInAmbient by storage.watchShowComplicationsInAmbientMode().collectAsState(storage.showComplicationsInAmbientMode())

                        SettingToggleChip(
                            label = "Widgets in ambient mode",
                            checked = showComplicationsInAmbient,
                            onCheckedChange = { storage.setShowComplicationsInAmbientMode(it) },
                            iconDrawable = R.drawable.ic_settings_power,
                        )
                    }
                }

                if (isUserPremium && (showWatchBattery || showPhoneBattery)) {
                    item(key = "ShowBatteryInAmbientMode") {
                        val hideBatteryInAmbient by storage.watchHideBatteryInAmbient().collectAsState(storage.hideBatteryInAmbient())

                        SettingToggleChip(
                            label = "Show battery indicators in ambient mode",
                            checked = !hideBatteryInAmbient,
                            onCheckedChange = { storage.setHideBatteryInAmbient(!it) },
                            iconDrawable = R.drawable.ic_settings_power,
                            modifier = Modifier.heightIn(min = 70.dp),
                        )
                    }
                }

                item(key = "SupportSection") {
                    SettingSectionItem(
                        label = "Support",
                    )
                }

                item(key = "GiveFeedback") {
                    SettingChip(
                        label = "Give your feedback",
                        onClick = {
                            storage.setRatingDisplayed(true)
                            startActivity(Intent(this@SettingsActivity, FeedbackActivity::class.java))
                        },
                        iconDrawable = R.drawable.ic_thumbs_up_down,
                    )
                }

                if (isUserPremium) {
                    item(key = "Donate") {
                        SettingChip(
                            label = "Donate to support development",
                            onClick = ::openAppForDonationOnPhone,
                            iconDrawable = R.drawable.ic_baseline_add_reaction,
                        )
                    }
                }

                item(key = "FooterVersion") {
                    Text(
                        text ="Version: ${BuildConfig.VERSION_NAME}",
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }

                item(key = "FooterCopyright") {
                    Text(
                        text ="Made by Benoit Letondor",
                    )
                }
            }
        }
    }

    @Composable
    private fun Android12Complications() {
        val complicationColors by storage.watchComplicationColors().collectAsState(storage.getComplicationColors())
        val complications by complicationsMutableFlow.collectAsState()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(fraction = 0.7f),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SettingComplicationSlotContainer {
                    SettingComplicationSlot(
                        providerInfo = complications[ComplicationLocation.ANDROID_12_TOP_LEFT],
                        color = complicationColors.android12TopLeftColor,
                        onClick = { startComplicationSelectionActivity(ComplicationLocation.ANDROID_12_TOP_LEFT) },
                    )
                }

                SettingComplicationSlotContainer {
                    SettingComplicationSlot(
                        providerInfo = complications[ComplicationLocation.ANDROID_12_TOP_RIGHT],
                        color = complicationColors.android12TopRightColor,
                        onClick = { startComplicationSelectionActivity(ComplicationLocation.ANDROID_12_TOP_RIGHT) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(fraction = 0.7f),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SettingComplicationSlotContainer {
                    SettingComplicationSlot(
                        providerInfo = complications[ComplicationLocation.ANDROID_12_BOTTOM_LEFT],
                        color = complicationColors.android12BottomLeftColor,
                        onClick = { startComplicationSelectionActivity(ComplicationLocation.ANDROID_12_BOTTOM_LEFT) },
                    )
                }

                SettingComplicationSlotContainer {
                    SettingComplicationSlot(
                        providerInfo = complications[ComplicationLocation.ANDROID_12_BOTTOM_RIGHT],
                        color = complicationColors.android12BottomRightColor,
                        onClick = { startComplicationSelectionActivity(ComplicationLocation.ANDROID_12_BOTTOM_RIGHT) },
                    )
                }
            }
        }
    }

    @Composable
    private fun RegularComplications(
        showBattery: Boolean,
    ) {
        val complicationColors by storage.watchComplicationColors().collectAsState(storage.getComplicationColors())
        val complications by complicationsMutableFlow.collectAsState()
        val showWearOSLogo by storage.watchShowWearOSLogo().collectAsState(storage.showWearOSLogo())

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SettingComplicationSlotContainer {
                    SettingComplicationSlot(
                        providerInfo = complications[ComplicationLocation.LEFT],
                        color = complicationColors.leftColor,
                        onClick = { startComplicationSelectionActivity(ComplicationLocation.LEFT) },
                    )
                }

                SettingComplicationSlotContainer {
                    if (showWearOSLogo) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_wear_os_logo),
                            contentDescription = "Wear OS logo",
                        )
                    } else {
                        SettingComplicationSlot(
                            providerInfo = complications[ComplicationLocation.MIDDLE],
                            color = complicationColors.middleColor,
                            onClick = { startComplicationSelectionActivity(ComplicationLocation.MIDDLE) },
                        )
                    }
                }

                SettingComplicationSlotContainer {
                    SettingComplicationSlot(
                        providerInfo = complications[ComplicationLocation.RIGHT],
                        color = complicationColors.rightColor,
                        onClick = { startComplicationSelectionActivity(ComplicationLocation.RIGHT) },
                    )
                }
            }

            SettingComplicationSlotContainer {
                if (showBattery) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_baseline_battery_charging_full),
                        contentDescription = "Battery indicator",
                    )
                } else {
                    SettingComplicationSlot(
                        providerInfo = complications[ComplicationLocation.BOTTOM],
                        color = complicationColors.bottomColor,
                        onClick = { startComplicationSelectionActivity(ComplicationLocation.BOTTOM) },
                    )
                }
            }
        }
    }

    private fun startComplicationSelectionActivity(location: ComplicationLocation) {
        startActivityForResult(
            WidgetConfigurationActivity.createIntent(this, location),
            COMPLICATION_CONFIG_REQUEST_CODE,
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if( requestCode == COMPLICATION_WEATHER_PERMISSION_REQUEST_CODE ) {
            val granted = isPermissionGranted("com.google.android.wearable.permission.RECEIVE_COMPLICATION_DATA")
            storage.setShowWeather(granted)
        } else if( requestCode == COMPLICATION_BATTERY_PERMISSION_REQUEST_CODE ) {
            val granted = isPermissionGranted("com.google.android.wearable.permission.RECEIVE_COMPLICATION_DATA")
            storage.setShowWatchBattery(granted)
        } else if ( requestCode == TIME_AND_DATE_COLOR_REQUEST_CODE && resultCode == RESULT_OK ) {
            val color = data?.getParcelableExtra<ComplicationColor>(ColorSelectionActivity.RESULT_SELECTED_COLOR)
            if (color != null) {
                storage.setTimeAndDateColor(color.color)
            }
        } else if ( requestCode == BATTERY_COLOR_REQUEST_CODE && resultCode == RESULT_OK ) {
            val color = data?.getParcelableExtra<ComplicationColor>(ColorSelectionActivity.RESULT_SELECTED_COLOR)
            if (color != null) {
                storage.setBatteryIndicatorColor(color.color)
            }
        } else if (requestCode == SECONDS_RING_COLOR_REQUEST_CODE && resultCode == RESULT_OK) {
            val color = data?.getParcelableExtra<ComplicationColor>(ColorSelectionActivity.RESULT_SELECTED_COLOR)
            if (color != null) {
                storage.setSecondRingColor(color.color)
            }
        } else if (requestCode == COMPLICATION_CONFIG_REQUEST_CODE && resultCode == RESULT_RELAUNCH) {
            data?.getParcelableExtra<ComplicationLocation>(WidgetConfigurationActivity.EXTRA_COMPLICATION_LOCATION)?.let { complicationLocation ->
                startActivityForResult(
                    WidgetConfigurationActivity.createIntent(this@SettingsActivity, complicationLocation),
                    COMPLICATION_CONFIG_REQUEST_CODE,
                )
                overridePendingTransition(0, 0)
            }
        }
    }

    private fun openAppOnPhone() {
        if ( PhoneTypeHelper.getPhoneDeviceType(applicationContext) == PhoneTypeHelper.DEVICE_TYPE_ANDROID ) {
            val intentAndroid = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse("pixelminimalwatchface://open"))
                .setPackage(BuildConfig.APPLICATION_ID)

            lifecycleScope.launch {
                val companionNode = capabilityClient.findCompanionNode()
                if (companionNode != null) {
                    try {
                        remoteActivityHelper.startRemoteActivity(
                            intentAndroid,
                            companionNode.id,
                        ).await()

                        ConfirmationOverlay()
                            .setOnAnimationFinishedListener {
                                finish()
                            }
                            .setType(ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION)
                            .setDuration(3000)
                            .setMessage(getString(R.string.open_phone_url_android_device) as CharSequence)
                            .showOn(this@SettingsActivity)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        openAppInStoreOnPhone()
                    }

                } else {
                    openAppInStoreOnPhone()
                }
            }
        } else {
            openAppInStoreOnPhone()
        }
    }

    private fun openAppForDonationOnPhone() {
        if ( PhoneTypeHelper.getPhoneDeviceType(applicationContext) == PhoneTypeHelper.DEVICE_TYPE_ANDROID ) {
            val intentAndroid = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse("pixelminimalwatchface://donate"))

            lifecycleScope.launch {
                val companionNode = capabilityClient.findCompanionNode()
                if (companionNode != null) {
                    try {
                        remoteActivityHelper.startRemoteActivity(
                            intentAndroid,
                            companionNode.id,
                        ).await()

                        ConfirmationOverlay()
                            .setType(ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION)
                            .setDuration(3000)
                            .setMessage(getString(R.string.open_phone_url_android_device) as CharSequence)
                            .showOn(this@SettingsActivity)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e

                        Log.e(TAG, "Error opening app for donation on phone", e)
                        openAppInStoreOnPhone(finish = false)
                    }
                } else {
                    openAppInStoreOnPhone(finish = false)
                }
            }
        } else {
            openAppInStoreOnPhone(finish = false)
        }
    }

    private fun openAppInStoreOnPhone(finish: Boolean = true) {
        when (PhoneTypeHelper.getPhoneDeviceType(applicationContext)) {
            PhoneTypeHelper.DEVICE_TYPE_ANDROID -> {
                // Create Remote Intent to open Play Store listing of app on remote device.
                val intentAndroid = Intent(Intent.ACTION_VIEW)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .setData(Uri.parse(COMPANION_APP_PLAYSTORE_URL))

                lifecycleScope.launch {
                    val phoneNode = nodeClient.findPhoneNode()
                    if (phoneNode != null) {
                        try {
                            remoteActivityHelper.startRemoteActivity(
                                intentAndroid,
                                phoneNode.id,
                            ).await()

                            ConfirmationOverlay()
                                .setOnAnimationFinishedListener {
                                    if (finish) {
                                        finish()
                                    }
                                }
                                .setType(ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION)
                                .setDuration(3000)
                                .setMessage(getString(R.string.open_phone_url_android_device) as CharSequence)
                                .showOn(this@SettingsActivity)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e

                            Log.e(TAG, "Error opening app in PlayStore on phone", e)
                            Toast.makeText(this@SettingsActivity, R.string.open_phone_url_android_device_failure, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this@SettingsActivity, R.string.open_phone_url_android_device_failure, Toast.LENGTH_LONG).show()
                    }
                }
            }
            PhoneTypeHelper.DEVICE_TYPE_IOS -> {
                Toast.makeText(this@SettingsActivity, R.string.open_phone_url_ios_device, Toast.LENGTH_LONG).show()
            }
            PhoneTypeHelper.DEVICE_TYPE_UNKNOWN, PhoneTypeHelper.DEVICE_TYPE_ERROR -> {
                Toast.makeText(this@SettingsActivity, R.string.open_phone_url_android_device_failure, Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun CapabilityClient.findCompanionNode(): Node? {
        return try {
            getCapability(BuildConfig.COMPANION_APP_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
                .firstOrNull { it.isNearby }
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            Log.e(TAG, "Error finding companion node", e)
            null
        }
    }

    private suspend fun NodeClient.findPhoneNode(): Node? {
        return try {
            connectedNodes
                .await()
                .firstOrNull { it.isNearby }
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            Log.e(TAG, "Error finding phone node", e)
            null
        }
    }

    companion object {
        private const val TAG = "SettingsActivity"

        private const val COMPLICATION_WEATHER_PERMISSION_REQUEST_CODE = 1003
        private const val COMPLICATION_BATTERY_PERMISSION_REQUEST_CODE = 1004
        private const val COMPLICATION_CONFIG_REQUEST_CODE = 1005
        private const val COMPLICATION_PHONE_BATTERY_SETUP_REQUEST_CODE = 1006
        private const val TIME_AND_DATE_COLOR_REQUEST_CODE = 1007
        private const val BATTERY_COLOR_REQUEST_CODE = 1008
        private const val SECONDS_RING_COLOR_REQUEST_CODE = 1009

        var currentEditorSession: EditorSession? = null
    }
}
