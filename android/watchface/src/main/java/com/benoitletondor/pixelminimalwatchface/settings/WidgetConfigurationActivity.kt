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
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.*
import androidx.wear.watchface.complications.ComplicationDataSourceInfo
import com.benoitletondor.pixelminimalwatchface.ComplicationsSlots
import com.benoitletondor.pixelminimalwatchface.Injection
import com.benoitletondor.pixelminimalwatchface.R
import com.benoitletondor.pixelminimalwatchface.compose.WearTheme
import com.benoitletondor.pixelminimalwatchface.compose.component.RotatoryAwareLazyColumn
import com.benoitletondor.pixelminimalwatchface.compose.component.SettingComplicationSlot
import com.benoitletondor.pixelminimalwatchface.model.ComplicationColor
import com.benoitletondor.pixelminimalwatchface.model.ComplicationColorsProvider
import com.benoitletondor.pixelminimalwatchface.model.ComplicationLocation
import com.benoitletondor.pixelminimalwatchface.model.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class WidgetConfigurationActivity : ComponentActivity() {
    private lateinit var complicationLocation: ComplicationLocation
    private lateinit var storage: Storage

    private val complicationProviderMutableFlow = MutableStateFlow<ComplicationDataSourceInfo?>(null)
    private lateinit var complicationColorMutableFlow: MutableStateFlow<ComplicationColor>

    private var finishWithComplicationChangesOnResume = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storage = Injection.storage(this)
        complicationLocation = intent.getParcelableExtra(EXTRA_COMPLICATION_LOCATION)!!
        title = when(complicationLocation) {
            ComplicationLocation.LEFT -> getString(R.string.config_left_complication)
            ComplicationLocation.MIDDLE -> getString(R.string.config_middle_complication)
            ComplicationLocation.RIGHT -> getString(R.string.config_right_complication)
            ComplicationLocation.BOTTOM -> getString(R.string.config_bottom_complication)
            ComplicationLocation.ANDROID_12_TOP_LEFT -> getString(R.string.config_android_12_top_left_complication)
            ComplicationLocation.ANDROID_12_TOP_RIGHT -> getString(R.string.config_android_12_top_right_complication)
            ComplicationLocation.ANDROID_12_BOTTOM_LEFT -> getString(R.string.config_android_12_bottom_left_complication)
            ComplicationLocation.ANDROID_12_BOTTOM_RIGHT -> getString(R.string.config_android_12_bottom_right_complication)
        }
        complicationColorMutableFlow = MutableStateFlow(when(complicationLocation) {
            ComplicationLocation.LEFT -> storage.getComplicationColors().leftColor
            ComplicationLocation.MIDDLE -> storage.getComplicationColors().middleColor
            ComplicationLocation.RIGHT -> storage.getComplicationColors().rightColor
            ComplicationLocation.BOTTOM -> storage.getComplicationColors().bottomColor
            ComplicationLocation.ANDROID_12_TOP_LEFT -> storage.getComplicationColors().android12TopLeftColor
            ComplicationLocation.ANDROID_12_TOP_RIGHT -> storage.getComplicationColors().android12TopRightColor
            ComplicationLocation.ANDROID_12_BOTTOM_LEFT -> storage.getComplicationColors().android12BottomLeftColor
            ComplicationLocation.ANDROID_12_BOTTOM_RIGHT -> storage.getComplicationColors().android12BottomRightColor
        })

        lifecycleScope.launch {
            SettingsActivity.currentEditorSession
                ?.complicationsDataSourceInfo
                ?.collect { infos ->
                    val infosPerLocation = ComplicationsSlots.getComplicationsDataSources(infos)
                    complicationProviderMutableFlow.value = infosPerLocation[complicationLocation]
                }
        }

        setContent {
            val complicationProvider by complicationProviderMutableFlow.collectAsState()
            val complicationColor by complicationColorMutableFlow.collectAsState()

            WearTheme {
                RotatoryAwareLazyColumn {
                    item {
                        Text(
                            text = title as String,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }

                    item {
                        Chip(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 70.dp),
                            label = {
                                Text(
                                    text = "Widget",
                                    fontWeight = FontWeight.Normal,
                                )
                            },
                            secondaryLabel = {
                                Text(
                                    text = if (complicationProvider != null) {
                                        "Tap to change/remove"
                                    } else {
                                        "Tap to setup"
                                    },
                                    fontWeight = FontWeight.Normal,
                                    color = Color.LightGray,
                                )
                            },
                            icon = {
                                SettingComplicationSlot(
                                    providerInfo = complicationProvider,
                                    color = null,
                                    onClick = null,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .width(40.dp)
                                        .height(40.dp)
                                )
                            },
                            onClick = {
                                SettingsActivity.currentEditorSession?.let { editorSession ->
                                    lifecycleScope.launch {
                                        finishWithComplicationChangesOnResume = true
                                        ComplicationsSlots.startComplicationChooser(
                                            editorSession,
                                            complicationLocation,
                                        )
                                    }
                                }
                            },
                            colors = ChipDefaults.primaryChipColors(
                                backgroundColor = MaterialTheme.colors.surface,
                            ),
                        )
                    }

                    if (complicationProvider != null) {
                        item {
                            Chip(
                                modifier = Modifier.fillMaxWidth(),
                                label = {
                                    Text(
                                        text = "Accent color",
                                        fontWeight = FontWeight.Normal,
                                    )
                                },
                                secondaryLabel = {
                                    Text(
                                        text = "Tap to change",
                                        fontWeight = FontWeight.Normal,
                                        color = Color.LightGray,
                                    )
                                },
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .width(40.dp)
                                            .height(40.dp)
                                            .background(Color(complicationColor.color))
                                    )
                                },
                                onClick = {
                                    val defaultColor = when(complicationLocation) {
                                        ComplicationLocation.LEFT -> ComplicationColorsProvider.getDefaultComplicationColors(this@WidgetConfigurationActivity).leftColor
                                        ComplicationLocation.MIDDLE -> ComplicationColorsProvider.getDefaultComplicationColors(this@WidgetConfigurationActivity).middleColor
                                        ComplicationLocation.RIGHT -> ComplicationColorsProvider.getDefaultComplicationColors(this@WidgetConfigurationActivity).rightColor
                                        ComplicationLocation.BOTTOM -> ComplicationColorsProvider.getDefaultComplicationColors(this@WidgetConfigurationActivity).bottomColor
                                        ComplicationLocation.ANDROID_12_TOP_LEFT -> ComplicationColorsProvider.getDefaultComplicationColors(this@WidgetConfigurationActivity).android12TopLeftColor
                                        ComplicationLocation.ANDROID_12_TOP_RIGHT -> ComplicationColorsProvider.getDefaultComplicationColors(this@WidgetConfigurationActivity).android12TopRightColor
                                        ComplicationLocation.ANDROID_12_BOTTOM_LEFT -> ComplicationColorsProvider.getDefaultComplicationColors(this@WidgetConfigurationActivity).android12BottomLeftColor
                                        ComplicationLocation.ANDROID_12_BOTTOM_RIGHT -> ComplicationColorsProvider.getDefaultComplicationColors(this@WidgetConfigurationActivity).android12BottomRightColor
                                    }

                                    startActivityForResult(ColorSelectionActivity.createIntent(this@WidgetConfigurationActivity, defaultColor), UPDATE_COLORS_CONFIG_REQUEST_CODE)
                                },
                                colors = ChipDefaults.primaryChipColors(
                                    backgroundColor = MaterialTheme.colors.surface,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (finishWithComplicationChangesOnResume) {
            finishWithComplicationChangesOnResume = false

            setResult(RESULT_RELAUNCH, Intent().apply {
                putExtra(EXTRA_COMPLICATION_LOCATION, complicationLocation as Parcelable)
            })
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == UPDATE_COLORS_CONFIG_REQUEST_CODE && resultCode == RESULT_OK) {
            val selectedColor = data?.getParcelableExtra<ComplicationColor>(ColorSelectionActivity.RESULT_SELECTED_COLOR)
                ?: return

            val storage = Injection.storage(this)
            val colors = storage.getComplicationColors()
            storage.setComplicationColors(when(complicationLocation) {
                ComplicationLocation.LEFT -> colors.copy(leftColor = selectedColor)
                ComplicationLocation.MIDDLE -> colors.copy(middleColor = selectedColor)
                ComplicationLocation.RIGHT -> colors.copy(rightColor = selectedColor)
                ComplicationLocation.BOTTOM -> colors.copy(bottomColor = selectedColor)
                ComplicationLocation.ANDROID_12_TOP_LEFT -> colors.copy(android12TopLeftColor = selectedColor)
                ComplicationLocation.ANDROID_12_TOP_RIGHT -> colors.copy(android12TopRightColor = selectedColor)
                ComplicationLocation.ANDROID_12_BOTTOM_LEFT -> colors.copy(android12BottomLeftColor = selectedColor)
                ComplicationLocation.ANDROID_12_BOTTOM_RIGHT -> colors.copy(android12BottomRightColor = selectedColor)
            })

            complicationColorMutableFlow.value = selectedColor
            setResult(RESULT_OK)
        }
    }

    companion object {
        private const val UPDATE_COLORS_CONFIG_REQUEST_CODE = 1002
        const val EXTRA_COMPLICATION_LOCATION = "extra:complicationLocation"
        const val RESULT_RELAUNCH = 589

        fun createIntent(
            context: Context,
            complicationLocation: ComplicationLocation
        ): Intent {
            return Intent(context, WidgetConfigurationActivity::class.java).apply {
                putExtra(EXTRA_COMPLICATION_LOCATION, complicationLocation as Parcelable)
            }
        }
    }
}
