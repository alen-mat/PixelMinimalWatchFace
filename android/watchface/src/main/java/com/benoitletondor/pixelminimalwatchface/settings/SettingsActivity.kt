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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.wear.phone.interactions.PhoneTypeHelper
import androidx.wear.remote.interactions.RemoteActivityHelper
import androidx.wear.watchface.editor.EditorSession
import androidx.wear.widget.ConfirmationOverlay
import com.benoitletondor.pixelminimalwatchface.BuildConfig
import com.benoitletondor.pixelminimalwatchface.BuildConfig.COMPANION_APP_PLAYSTORE_URL
import com.benoitletondor.pixelminimalwatchface.Injection
import com.benoitletondor.pixelminimalwatchface.R
import com.benoitletondor.pixelminimalwatchface.databinding.ActivitySettingsBinding
import com.benoitletondor.pixelminimalwatchface.getWeatherProviderInfo
import com.benoitletondor.pixelminimalwatchface.helper.await
import com.benoitletondor.pixelminimalwatchface.helper.openActivity
import com.benoitletondor.pixelminimalwatchface.model.ComplicationColor
import com.benoitletondor.pixelminimalwatchface.model.ComplicationLocation
import com.benoitletondor.pixelminimalwatchface.model.Storage
import com.benoitletondor.pixelminimalwatchface.rating.FeedbackActivity
import com.benoitletondor.pixelminimalwatchface.settings.WidgetConfigurationActivity.Companion.RESULT_RELAUNCH
import com.benoitletondor.pixelminimalwatchface.settings.phonebattery.PhoneBatteryConfigurationActivity
import com.google.android.gms.wearable.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    private lateinit var adapter: ComplicationConfigRecyclerViewAdapter
    private lateinit var storage: Storage

    private lateinit var binding: ActivitySettingsBinding

    private lateinit var remoteActivityHelper: RemoteActivityHelper
    private lateinit var capabilityClient: CapabilityClient
    private lateinit var nodeClient: NodeClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storage = Injection.storage(this)

        lifecycleScope.launch {
            currentEditorSession = EditorSession.createOnWatchEditorSession(this@SettingsActivity)

            currentEditorSession
                ?.complicationsDataSourceInfo
                ?.collect {
                    if (storage.useAndroid12Style()) {
                        adapter.updateAndroid12Complications()
                    } else {
                        adapter.updateRegularComplications()
                    }
                }
        }

        lifecycleScope.launch {
            storage.watchComplicationColors()
                .drop(1)
                .collect {
                    if (storage.useAndroid12Style()) {
                        adapter.updateAndroid12Complications()
                    } else {
                        adapter.updateRegularComplications()
                    }
                }
        }

        remoteActivityHelper = RemoteActivityHelper(this, Dispatchers.IO.asExecutor())
        capabilityClient = Wearable.getCapabilityClient(this)
        nodeClient = Wearable.getNodeClient(this)

        adapter = ComplicationConfigRecyclerViewAdapter(this, storage, {
            openAppOnPhone()
        }, { use24hTimeFormat ->
            storage.setUse24hTimeFormat(use24hTimeFormat)
        }, {
            storage.setRatingDisplayed(true)
            startActivity(Intent(this, FeedbackActivity::class.java))
        }, { showWearOSLogo ->
            storage.setShowWearOSLogo(showWearOSLogo)
        }, { showComplicationsAmbient ->
            storage.setShowComplicationsInAmbientMode(showComplicationsAmbient)
        }, { useNormalTimeStyleInAmbientMode ->
            storage.setUseNormalTimeStyleInAmbientMode(useNormalTimeStyleInAmbientMode)
        }, { useThinTimeStyleInRegularMode ->
            storage.setUseThinTimeStyleInRegularMode(useThinTimeStyleInRegularMode)
        }, { timeSize ->
            storage.setTimeSize(timeSize)
        }, { dateAndBatterySize ->
            storage.setDateAndBatterySize(dateAndBatterySize)
        }, { showSecondsRing ->
            storage.setShowSecondsRing(showSecondsRing)
        }, { showWeather ->
            storage.setShowWeather(showWeather)
        }, {
            getWeatherProviderInfo()?.let { weatherProviderInfo ->
                openActivity(weatherProviderInfo.appPackage, weatherProviderInfo.weatherActivityName)
            }
        }, { showBattery ->
            storage.setShowWatchBattery(showBattery)
        }, { showBatteryInAmbient ->
            storage.setHideBatteryInAmbient(!showBatteryInAmbient)
        }, { useShortDateFormat ->
            storage.setUseShortDateFormat(useShortDateFormat)
        }, { showDateAmbient ->
            storage.setShowDateInAmbient(showDateAmbient)
        }, {
            openAppForDonationOnPhone()
        }, {
            startActivityForResult(
                Intent(this, PhoneBatteryConfigurationActivity::class.java),
                COMPLICATION_PHONE_BATTERY_SETUP_REQUEST_CODE,
            )
        }, {
            startActivityForResult(
                ColorSelectionActivity.createIntent(
                    this,
                    ComplicationColor(getColor(R.color.white), getString(R.string.color_default), true)
                ),
                TIME_AND_DATE_COLOR_REQUEST_CODE
            )
        }, {
            startActivityForResult(
                ColorSelectionActivity.createIntent(
                    this,
                    ComplicationColor(getColor(R.color.white), getString(R.string.color_default), true)
                ),
                BATTERY_COLOR_REQUEST_CODE
            )
        }, { useAndroid12Style ->
            storage.setUseAndroid12Style(useAndroid12Style)
        }, {
            startActivityForResult(
                ColorSelectionActivity.createIntent(
                    this,
                    ComplicationColor(getColor(R.color.white), getString(R.string.color_default), true)
                ),
                SECONDS_RING_COLOR_REQUEST_CODE
            )
        }, { widgetsSize ->
            storage.setWidgetsSize(widgetsSize)
        })

        binding.wearableRecyclerView.apply {
            isEdgeItemsCenteringEnabled = true
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            setHasFixedSize(true)
            adapter = this@SettingsActivity.adapter
        }
    }

    override fun onDestroy() {
        adapter.onDestroy()
        currentEditorSession = null

        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if( requestCode == COMPLICATION_WEATHER_PERMISSION_REQUEST_CODE ) {
            adapter.weatherComplicationPermissionFinished()
        } else if( requestCode == COMPLICATION_BATTERY_PERMISSION_REQUEST_CODE ) {
            adapter.batteryComplicationPermissionFinished()
        } else if ( requestCode == COMPLICATION_PHONE_BATTERY_SETUP_REQUEST_CODE ) {
            if (storage.useAndroid12Style()) {
                adapter.updateAndroid12Complications()
            } else {
                adapter.updateRegularComplications()
            }
            adapter.notifyDataSetChanged()
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
        } else if (requestCode == WIDGET_ACTIVITY_REQUEST_CODE && resultCode == RESULT_RELAUNCH) {
            data?.getParcelableExtra<ComplicationLocation>(WidgetConfigurationActivity.EXTRA_COMPLICATION_LOCATION)?.let { complicationLocation ->
                startActivityForResult(
                    WidgetConfigurationActivity.createIntent(this@SettingsActivity, complicationLocation),
                    WIDGET_ACTIVITY_REQUEST_CODE,
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

                        Log.e("SettingsActivity", "Error opening app for donation on phone", e)
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

                            Log.e("SettingsActivity", "Error opening app in PlayStore on phone", e)
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

            Log.e("SettingsActivity", "Error finding companion node", e)
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

            Log.e("SettingsActivity", "Error finding phone node", e)
            null
        }
    }

    companion object {
        const val COMPLICATION_WEATHER_PERMISSION_REQUEST_CODE = 1003
        const val COMPLICATION_BATTERY_PERMISSION_REQUEST_CODE = 1004
        const val COMPLICATION_PHONE_BATTERY_SETUP_REQUEST_CODE = 1006
        const val TIME_AND_DATE_COLOR_REQUEST_CODE = 1007
        const val BATTERY_COLOR_REQUEST_CODE = 1008
        const val SECONDS_RING_COLOR_REQUEST_CODE = 1009
        const val WIDGET_ACTIVITY_REQUEST_CODE = 1010

        var currentEditorSession: EditorSession? = null
    }
}
