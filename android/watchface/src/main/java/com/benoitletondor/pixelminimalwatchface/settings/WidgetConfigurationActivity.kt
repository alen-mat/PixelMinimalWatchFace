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
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.wear.widget.WearableLinearLayoutManager
import com.benoitletondor.pixelminimalwatchface.ComplicationsSlots
import com.benoitletondor.pixelminimalwatchface.Injection
import com.benoitletondor.pixelminimalwatchface.R
import com.benoitletondor.pixelminimalwatchface.databinding.ActivityWidgetConfigBinding
import com.benoitletondor.pixelminimalwatchface.model.ComplicationColor
import com.benoitletondor.pixelminimalwatchface.model.ComplicationColorsProvider
import com.benoitletondor.pixelminimalwatchface.model.ComplicationLocation
import kotlinx.coroutines.launch

class WidgetConfigurationActivity : ComponentActivity() {
    private lateinit var adapter: WidgetConfigRecyclerViewAdapter
    private lateinit var complicationLocation: ComplicationLocation

    private lateinit var binding: ActivityWidgetConfigBinding

    private var finishWithComplicationChangesOnResume = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        adapter = WidgetConfigRecyclerViewAdapter(
            complicationLocation = complicationLocation,
            context = this,
            title = title as String,
            onSelectColorClicked = {
                val defaultColor = when(complicationLocation) {
                    ComplicationLocation.LEFT -> ComplicationColorsProvider.getDefaultComplicationColors(this).leftColor
                    ComplicationLocation.MIDDLE -> ComplicationColorsProvider.getDefaultComplicationColors(this).middleColor
                    ComplicationLocation.RIGHT -> ComplicationColorsProvider.getDefaultComplicationColors(this).rightColor
                    ComplicationLocation.BOTTOM -> ComplicationColorsProvider.getDefaultComplicationColors(this).bottomColor
                    ComplicationLocation.ANDROID_12_TOP_LEFT -> ComplicationColorsProvider.getDefaultComplicationColors(this).android12TopLeftColor
                    ComplicationLocation.ANDROID_12_TOP_RIGHT -> ComplicationColorsProvider.getDefaultComplicationColors(this).android12TopRightColor
                    ComplicationLocation.ANDROID_12_BOTTOM_LEFT -> ComplicationColorsProvider.getDefaultComplicationColors(this).android12BottomLeftColor
                    ComplicationLocation.ANDROID_12_BOTTOM_RIGHT -> ComplicationColorsProvider.getDefaultComplicationColors(this).android12BottomRightColor
                }

                startActivityForResult(ColorSelectionActivity.createIntent(this, defaultColor), UPDATE_COLORS_CONFIG_REQUEST_CODE)
            },
            onSelectComplicationClicked = {
                SettingsActivity.currentEditorSession?.let { editorSession ->
                    lifecycleScope.launch {
                        finishWithComplicationChangesOnResume = true
                        ComplicationsSlots.startComplicationChooser(
                            editorSession,
                            complicationLocation,
                        )
                    }
                }
            }
        )

        binding.widgetConfigRecyclerView.apply {
            isEdgeItemsCenteringEnabled = true
            layoutManager = WearableLinearLayoutManager(this@WidgetConfigurationActivity)
            setHasFixedSize(true)
            adapter = this@WidgetConfigurationActivity.adapter
        }

        lifecycleScope.launch {
            val editorSession = SettingsActivity.currentEditorSession ?: return@launch

            editorSession.complicationsDataSourceInfo
                .collect {
                    adapter.updateComplication(
                        ComplicationsSlots.getComplicationDataSource(editorSession, complicationLocation)?.icon
                    )
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

    @SuppressLint("RestrictedApi")
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

            adapter.updatePreviewColors(selectedColor)
            setResult(RESULT_OK)
        }
    }

    override fun onDestroy() {
        adapter.onDestroy()
        super.onDestroy()
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