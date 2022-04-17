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
package com.benoitletondor.pixelminimalwatchface

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.util.SparseArray
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.wear.watchface.*
import androidx.wear.watchface.complications.*
import androidx.wear.watchface.complications.SystemDataSources.Companion.NO_DATA_SOURCE
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.complications.rendering.CustomCanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.CustomComplicationDrawable
import androidx.wear.watchface.editor.EditorSession
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.WatchFaceLayer
import com.benoitletondor.pixelminimalwatchface.helper.isSamsungCalendarBuggyProvider
import com.benoitletondor.pixelminimalwatchface.helper.isSamsungHeartRateProvider
import com.benoitletondor.pixelminimalwatchface.helper.sanitizeForSamsungGalaxyWatchIfNeeded
import com.benoitletondor.pixelminimalwatchface.model.ComplicationLocation
import com.benoitletondor.pixelminimalwatchface.model.Storage
import com.benoitletondor.pixelminimalwatchface.model.getPrimaryColorForComplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.ZonedDateTime
import java.util.*
import kotlin.collections.HashSet

class ComplicationsSlots(
    private val context: Context,
    private val storage: Storage,
    private val currentUserStyleRepository: CurrentUserStyleRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val complicationProviderSparseArray: SparseArray<ComplicationDataSourceInfo> = SparseArray(COMPLICATION_IDS.size)
    private val complicationDataSourceInfoRetriever = ComplicationDataSourceInfoRetriever(context)

    private val activeSlots: MutableSet<ComplicationLocation> = mutableSetOf()
    private val activeComplicationWatchingJobs: MutableList<Job> = mutableListOf()
    private val dirtyComplicationDataSlots: MutableSet<Int> = mutableSetOf()
    private val overrideComplicationData: SparseArray<ComplicationData> = SparseArray()

    private lateinit var complicationSlotsManager: ComplicationSlotsManager

    private val invalidateRendererMutableEventFlow = MutableSharedFlow<Unit>()
    val invalidateRendererEventFlow: Flow<Unit> = invalidateRendererMutableEventFlow

    private val titleSize = context.resources.getDimensionPixelSize(R.dimen.complication_title_size)
    private val textSize: Int = context.resources.getDimensionPixelSize(R.dimen.complication_text_size)
    private val complicationTitleColor = ContextCompat.getColor(context, R.color.complication_title_color)
    private val dateAndBatteryColorDimmed = ContextCompat.getColor(context, R.color.face_date_dimmed)
    private val productSansRegularFont = ResourcesCompat.getFont(context, R.font.product_sans_regular)!!
    private val transparentColor = ContextCompat.getColor(context, R.color.transparent)

    private val leftComplicationDrawable = CustomComplicationDrawable(context)
    private var leftComplicationOption = UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        complicationSlotId = LEFT_COMPLICATION_ID,
    )

    private val middleComplicationDrawable = CustomComplicationDrawable(context)
    private var middleComplicationOption = UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        complicationSlotId = MIDDLE_COMPLICATION_ID,
    )

    private val rightComplicationDrawable = CustomComplicationDrawable(context)
    private var rightComplicationOption = UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        complicationSlotId = RIGHT_COMPLICATION_ID,
    )

    private val bottomComplicationDrawable = CustomComplicationDrawable(context)
    private var bottomComplicationOption = UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        complicationSlotId = BOTTOM_COMPLICATION_ID,
    )

    private val android12TopLeftComplicationDrawable = CustomComplicationDrawable(context)
    private var android12TopLeftComplicationOption = UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        complicationSlotId = ANDROID_12_TOP_LEFT_COMPLICATION_ID,
    )

    private val android12TopRightComplicationDrawable = CustomComplicationDrawable(context)
    private var android12TopRightComplicationOption = UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        complicationSlotId = ANDROID_12_TOP_RIGHT_COMPLICATION_ID,
    )

    private val android12BottomLeftComplicationDrawable = CustomComplicationDrawable(context)
    private var android12BottomLeftComplicationOption = UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        complicationSlotId = ANDROID_12_BOTTOM_LEFT_COMPLICATION_ID,
    )

    private val android12BottomRightComplicationDrawable = CustomComplicationDrawable(context)
    private var android12BottomRightComplicationOption = UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        complicationSlotId = ANDROID_12_BOTTOM_RIGHT_COMPLICATION_ID,
    )

    private var weatherDataWatcherJob: Job? = null
    private var weatherComplicationOption = UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        complicationSlotId = WEATHER_COMPLICATION_ID,
    )
    private val weatherComplicationDataMutableFlow = MutableStateFlow<android.support.wearable.complications.ComplicationData?>(null)
    val weatherComplicationDataFlow: StateFlow<android.support.wearable.complications.ComplicationData?> = weatherComplicationDataMutableFlow

    private var batteryDataWatcherJob: Job? = null
    private val watchBatteryLevelMutableFlow = MutableStateFlow<Int?>(null)
    val watchBatteryLevelFlow: StateFlow<Int?> = watchBatteryLevelMutableFlow

    private val galaxyWatch4HeartRateComplicationsLocationsMutableFlow = MutableStateFlow<Set<ComplicationLocation>>(emptySet())
    val galaxyWatch4HeartRateComplicationsLocationsFlow: Flow<Set<ComplicationLocation>> = galaxyWatch4HeartRateComplicationsLocationsMutableFlow

    private val calendarBuggyComplicationsLocationsMutableFlow = MutableStateFlow<Set<ComplicationLocation>>(emptySet())
    val calendarBuggyComplicationsLocationsFlow: StateFlow<Set<ComplicationLocation>> = calendarBuggyComplicationsLocationsMutableFlow

    fun onCreate(complicationSlotsManager: ComplicationSlotsManager) {
        if (DEBUG_LOGS) Log.d(TAG, "onCreate")

        this.complicationSlotsManager = complicationSlotsManager

        watchComplicationDataAndColorChanges()
    }

    fun onDestroy() {
        if (DEBUG_LOGS) Log.d(TAG, "onDestroy")

        scope.cancel()
        complicationDataSourceInfoRetriever.close()
    }

    fun createComplicationsSlots(): List<ComplicationSlot> {
        if (DEBUG_LOGS) Log.d(TAG, "createComplicationsSlots")

        val batteryComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = BATTERY_COMPLICATION_ID,
            canvasComplicationFactory = { watchState, listener ->
                CanvasComplicationDrawable(
                    ComplicationDrawable(context),
                    watchState,
                    listener
                )
            },
            supportedTypes = listOf(ComplicationType.SHORT_TEXT),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
                SystemDataSources.DATA_SOURCE_WATCH_BATTERY,
                ComplicationType.SHORT_TEXT,
            ),
            bounds = defaultComplicationSlotBounds,
        )
            .setEnabled(true)
            .setFixedComplicationDataSource(true)
            .build()

        val weatherProviderInfo = context.getWeatherProviderInfo()
        val weatherComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = WEATHER_COMPLICATION_ID,
            canvasComplicationFactory = { watchState, listener ->
                CanvasComplicationDrawable(
                    ComplicationDrawable(context),
                    watchState,
                    listener
                )
            },
            supportedTypes = listOf(ComplicationType.SHORT_TEXT),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
                primaryDataSource = ComponentName(weatherProviderInfo?.appPackage ?: "", weatherProviderInfo?.weatherProviderService ?: ""),
                primaryDataSourceDefaultType = ComplicationType.SHORT_TEXT,
                systemDataSourceFallback = NO_DATA_SOURCE,
                systemDataSourceFallbackDefaultType = ComplicationType.SHORT_TEXT,
            ),
            bounds = defaultComplicationSlotBounds,
        )
            .setFixedComplicationDataSource(true)
            .setEnabled(false)
            .build()

        val leftComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = LEFT_COMPLICATION_ID,
            canvasComplicationFactory = buildCanvasComplicationFactory(
                ComplicationLocation.LEFT.getComplicationDrawable(),
            ),
            supportedTypes = getSupportedComplicationTypes(ComplicationLocation.LEFT),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(),
            bounds = defaultComplicationSlotBounds,
        ).build()

        val middleComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = MIDDLE_COMPLICATION_ID,
            canvasComplicationFactory = buildCanvasComplicationFactory(
                ComplicationLocation.MIDDLE.getComplicationDrawable()
            ),
            supportedTypes = getSupportedComplicationTypes(ComplicationLocation.MIDDLE),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(),
            bounds = defaultComplicationSlotBounds,
        ).build()

        val rightComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = RIGHT_COMPLICATION_ID,
            canvasComplicationFactory = buildCanvasComplicationFactory(
                ComplicationLocation.RIGHT.getComplicationDrawable()
            ),
            supportedTypes = getSupportedComplicationTypes(ComplicationLocation.RIGHT),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(),
            bounds = defaultComplicationSlotBounds,
        ).build()

        val bottomComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = BOTTOM_COMPLICATION_ID,
            canvasComplicationFactory = buildCanvasComplicationFactory(
                ComplicationLocation.BOTTOM.getComplicationDrawable()
            ),
            supportedTypes = getSupportedComplicationTypes(ComplicationLocation.BOTTOM),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(),
            bounds = defaultComplicationSlotBounds,
        ).build()

        val android12TopLeftComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = ANDROID_12_TOP_LEFT_COMPLICATION_ID,
            canvasComplicationFactory = buildCanvasComplicationFactory(
                ComplicationLocation.ANDROID_12_TOP_LEFT.getComplicationDrawable(),
            ),
            supportedTypes = getSupportedComplicationTypes(ComplicationLocation.ANDROID_12_TOP_LEFT),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(),
            bounds = defaultComplicationSlotBounds,
        ).build()

        val android12TopRightComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = ANDROID_12_TOP_RIGHT_COMPLICATION_ID,
            canvasComplicationFactory = buildCanvasComplicationFactory(
                ComplicationLocation.ANDROID_12_TOP_RIGHT.getComplicationDrawable(),
            ),
            supportedTypes = getSupportedComplicationTypes(ComplicationLocation.ANDROID_12_TOP_RIGHT),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(),
            bounds = defaultComplicationSlotBounds,
        ).build()

        val android12BottomLeftComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = ANDROID_12_BOTTOM_LEFT_COMPLICATION_ID,
            canvasComplicationFactory = buildCanvasComplicationFactory(
                ComplicationLocation.ANDROID_12_BOTTOM_LEFT.getComplicationDrawable(),
            ),
            supportedTypes = getSupportedComplicationTypes(ComplicationLocation.ANDROID_12_BOTTOM_LEFT),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(),
            bounds = defaultComplicationSlotBounds,
        ).build()

        val android12BottomRightComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = ANDROID_12_BOTTOM_RIGHT_COMPLICATION_ID,
            canvasComplicationFactory = buildCanvasComplicationFactory(
                ComplicationLocation.ANDROID_12_BOTTOM_RIGHT.getComplicationDrawable(),
            ),
            supportedTypes = getSupportedComplicationTypes(ComplicationLocation.ANDROID_12_BOTTOM_RIGHT),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(),
            bounds = defaultComplicationSlotBounds,
        ).build()

        return listOf(
            batteryComplication,
            weatherComplication,
            leftComplication,
            middleComplication,
            rightComplication,
            bottomComplication,
            android12TopLeftComplication,
            android12TopRightComplication,
            android12BottomLeftComplication,
            android12BottomRightComplication,
        )
    }

    private fun buildCanvasComplicationFactory(complicationDrawable: CustomComplicationDrawable): CanvasComplicationFactory {
        return CanvasComplicationFactory { watchState, listener ->
            CustomCanvasComplicationDrawable(
                complicationDrawable,
                watchState,
                listener
            )
        }
    }

    @SuppressLint("RestrictedApi")
    private fun watchComplicationDataAndColorChanges() {
        complicationSlotsManager.complicationSlots.forEach { (_, slot) ->
            val location = slot.id.toComplicationLocation() ?: return@forEach
            val drawable = location.getComplicationDrawable()

            drawable.activeStyle.titleSize = titleSize
            drawable.ambientStyle.titleSize = titleSize
            drawable.activeStyle.titleColor = complicationTitleColor
            drawable.ambientStyle.titleColor = complicationTitleColor
            drawable.ambientStyle.iconColor = dateAndBatteryColorDimmed
            drawable.activeStyle.setTextTypeface(productSansRegularFont)
            drawable.ambientStyle.setTextTypeface(productSansRegularFont)
            drawable.activeStyle.setTitleTypeface(productSansRegularFont)
            drawable.ambientStyle.setTitleTypeface(productSansRegularFont)
            drawable.activeStyle.borderColor = transparentColor
            drawable.ambientStyle.borderColor = transparentColor

            scope.launch {
                slot.complicationData
                    .combine(
                        storage.watchComplicationColors()
                    ) { data, colors ->
                        Pair (data.asWireComplicationData(), colors)
                    }
                    .collect { (data, colors) ->
                        val primaryComplicationColor = colors.getPrimaryColorForComplication(location)
                        drawable.activeStyle.iconColor = primaryComplicationColor

                        if( data.icon != null ) {
                            if( location == ComplicationLocation.BOTTOM && ( data.longTitle != null ) ) {
                                drawable.activeStyle.textColor = primaryComplicationColor
                                drawable.ambientStyle.textColor = dateAndBatteryColorDimmed
                            } else {
                                drawable.activeStyle.textColor = complicationTitleColor
                                drawable.ambientStyle.textColor = complicationTitleColor
                            }

                            if( location != ComplicationLocation.BOTTOM && data.shortTitle == null ) {
                                drawable.activeStyle.textSize = titleSize
                                drawable.ambientStyle.textSize = titleSize
                            } else {
                                drawable.activeStyle.textSize = textSize
                                drawable.ambientStyle.textSize = textSize
                            }
                        } else {
                            drawable.activeStyle.textColor = primaryComplicationColor
                            drawable.ambientStyle.textColor = dateAndBatteryColorDimmed
                            drawable.activeStyle.textSize = textSize
                            drawable.ambientStyle.textSize = textSize
                        }
                    }
            }
        }
    }

    fun updateComplicationBounds(complicationLocation: ComplicationLocation, bounds: RectF) {
        if (DEBUG_LOGS) Log.d(TAG, "updateComplicationBounds at location: $complicationLocation, bounds: $bounds")

        complicationLocation.editComplicationOptions {
            it.setComplicationSlotBounds(ComplicationSlotBounds(bounds))
        }

        updateComplicationSetting()
    }

    fun setActiveComplicationLocations(activeLocations: Set<ComplicationLocation>) {
        if (DEBUG_LOGS) Log.d(TAG, "setActiveComplicationLocations: $activeLocations")

        for(location in ComplicationLocation.values()) {
            location.editComplicationOptions {
                it.setEnabled(location in activeLocations)
            }
        }

        galaxyWatch4HeartRateComplicationsLocationsMutableFlow.value = emptySet()
        calendarBuggyComplicationsLocationsMutableFlow.value = emptySet()
        overrideComplicationData.clear()
        dirtyComplicationDataSlots.clear()
        activeSlots.clear()
        activeSlots.addAll(activeLocations)

        updateComplicationSetting()
        updateComplicationProviders()
        watchComplicationSlotsData()
        watchWatchBatteryData()
    }

    fun refreshDataAtLocation(complicationLocation: ComplicationLocation) {
        if (DEBUG_LOGS) Log.d(TAG, "refreshDataAtLocation: $complicationLocation")

        scope.launch {
            synchronized(dirtyComplicationDataSlots) {
                dirtyComplicationDataSlots.add(complicationLocation.getComplicationId())
            }

            invalidateRendererMutableEventFlow.emit(Unit)
        }
    }

    fun setWeatherComplicationEnabled(enabled: Boolean) {
        if (DEBUG_LOGS) Log.d(TAG, "setWeatherComplicationEnabled: $enabled")

        weatherDataWatcherJob?.cancel()
        weatherComplicationOption = UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay.Builder(WEATHER_COMPLICATION_ID)
            .setEnabled(enabled)
            .build()
        updateComplicationSetting()

        if (enabled) {
            weatherDataWatcherJob = scope.launch {
                complicationSlotsManager.complicationSlots[WEATHER_COMPLICATION_ID]?.let { complicationSlot ->
                    complicationSlot.complicationData.collect { complicationData ->
                        try {
                            weatherComplicationDataMutableFlow.value = complicationData.asWireComplicationData()
                            if (DEBUG_LOGS) Log.d(TAG, "weatherComplicationData received: $complicationData")
                        } catch (e: Exception) {
                            Log.e(TAG, "onComplicationDataUpdate, error while parsing weather data from complication", e)
                        }
                    }
                }
            }
        }
    }

    fun getComplicationBounds(complicationLocation: ComplicationLocation): RectF? {
        val userStyle = currentUserStyleRepository.userStyle.value
        val option = userStyle[UserStyleSetting.Id(COMPLICATIONS_SETTING_ID)] as? UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotsOption
        val overlays = option?.complicationSlotOverlays

        return overlays
            ?.firstOrNull { it.complicationSlotId == complicationLocation.getComplicationId() }
            ?.complicationSlotBounds
            ?.perComplicationTypeBounds
            ?.get(ComplicationType.SHORT_TEXT)
    }

    fun updateComplicationProviders() {
        if (DEBUG_LOGS) Log.d(TAG, "updateComplicationProviders")

        scope.launch {
            val results = complicationDataSourceInfoRetriever.retrieveComplicationDataSourceInfo(
                ComponentName(context, PixelMinimalWatchFace::class.java),
                COMPLICATION_IDS,
            ) ?: return@launch

            for(result in results) {
                complicationProviderSparseArray.put(result.slotId, result.info)

                val location = result.slotId.toComplicationLocation() ?: continue
                if (location !in activeSlots) {
                    continue
                }

                updateGalaxyWatch4ComplicationSlots(
                    location,
                    result.info,
                    result.slotId,
                )
            }
        }
    }

    fun render(canvas: Canvas, zonedDateTime: ZonedDateTime, rendererParameters: RenderParameters) {
        for ((_, slot) in complicationSlotsManager.complicationSlots) {
            val slotLocation = slot.id.toComplicationLocation()

            if (slotLocation != null && slotLocation in activeSlots) {
                if (slotLocation == ComplicationLocation.MIDDLE && storage.showWearOSLogo()) {
                    continue
                }

                if (slotLocation == ComplicationLocation.BOTTOM && (storage.showWatchBattery() || storage.showPhoneBattery())) {
                    continue
                }

                if (slot.id in dirtyComplicationDataSlots) {
                    synchronized(dirtyComplicationDataSlots) {
                        dirtyComplicationDataSlots.remove(slot.id)
                    }

                    overrideComplicationData.put(
                        slot.id,
                        slot.complicationData.value.sanitizeForSamsungGalaxyWatchIfNeeded(
                            context,
                            storage,
                            slotLocation,
                            complicationProviderSparseArray[slot.id],
                        ),
                    )
                }

                val bounds = slot.computeBounds(Rect(0, 0, canvas.width, canvas.height))
                (slot.renderer as? CustomCanvasComplicationDrawable)?.render(
                    canvas,
                    bounds,
                    zonedDateTime,
                    rendererParameters,
                    slot.id,
                    overrideComplicationData[slot.id],
                )
            }
        }
    }

    private fun watchComplicationSlotsData() {
        activeComplicationWatchingJobs.forEach { it.cancel() }
        activeComplicationWatchingJobs.clear()

        complicationSlotsManager.complicationSlots.forEach { (_, slot) ->
            if (slot.id.toComplicationLocation() !in activeSlots) {
                return@forEach
            }

            val location = slot.id.toComplicationLocation() ?: return@forEach

            val job = scope.launch {
                slot.complicationData.collect { complicationData ->
                    if (DEBUG_LOGS) Log.d(TAG, "watchComplicationSlotsData: $location, data: $complicationData")

                    val newComplicationData = complicationData.sanitizeForSamsungGalaxyWatchIfNeeded(
                        context,
                        storage,
                        location,
                        complicationProviderSparseArray[slot.id],
                    )

                    if (newComplicationData != null) {
                        overrideComplicationData.put(slot.id, newComplicationData)
                        invalidateRendererMutableEventFlow.emit(Unit)
                    }
                }
            }

            activeComplicationWatchingJobs.add(job)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun watchWatchBatteryData() {
        batteryDataWatcherJob?.cancel()
        batteryDataWatcherJob = scope.launch {
            complicationSlotsManager.complicationSlots[BATTERY_COMPLICATION_ID]?.let { complicationSlot ->
                complicationSlot.complicationData.collect { complicationData ->
                    try {
                        complicationData.asWireComplicationData().shortText?.getTextAt(context.resources, System.currentTimeMillis())?.let { text ->
                            val batteryChargePercentage = text.substring(0, text.indexOf("%")).toInt()
                            watchBatteryLevelMutableFlow.emit(batteryChargePercentage)

                            if (DEBUG_LOGS) Log.d(TAG, "batteryComplicationData received: $batteryChargePercentage")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "onComplicationDataUpdate, error while parsing battery data from complication", e)
                    }
                }
            }
        }
    }

    private fun updateComplicationSetting() {
        if (DEBUG_LOGS) Log.d(TAG, "updateComplicationSetting")

        currentUserStyleRepository.updateUserStyle(
            currentUserStyleRepository.userStyle.value
                .toMutableUserStyle().apply {
                    set(
                        UserStyleSetting.Id(COMPLICATIONS_SETTING_ID),
                        UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotsOption(
                            id = UserStyleSetting.Option.Id(UUID.randomUUID().toString()),
                            displayName = "",
                            icon = null,
                            complicationSlotOverlays = listOf(
                                leftComplicationOption,
                                middleComplicationOption,
                                rightComplicationOption,
                                bottomComplicationOption,
                                android12TopLeftComplicationOption,
                                android12TopRightComplicationOption,
                                android12BottomLeftComplicationOption,
                                android12BottomRightComplicationOption,
                                weatherComplicationOption,
                            ),
                        ),
                    )
                }
                .toUserStyle()
        )
    }

    private fun ComplicationLocation.editComplicationOptions(action: (UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay.Builder) -> Unit) {
        val option = when(this) {
            ComplicationLocation.LEFT -> leftComplicationOption
            ComplicationLocation.MIDDLE -> middleComplicationOption
            ComplicationLocation.RIGHT -> rightComplicationOption
            ComplicationLocation.BOTTOM -> bottomComplicationOption
            ComplicationLocation.ANDROID_12_TOP_LEFT -> android12TopLeftComplicationOption
            ComplicationLocation.ANDROID_12_TOP_RIGHT -> android12TopRightComplicationOption
            ComplicationLocation.ANDROID_12_BOTTOM_LEFT -> android12BottomLeftComplicationOption
            ComplicationLocation.ANDROID_12_BOTTOM_RIGHT -> android12BottomRightComplicationOption
        }

        val builder = UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay.Builder(option.complicationSlotId).apply {
            option.enabled?.let { setEnabled(it) }
            option.complicationSlotBounds?.let { setComplicationSlotBounds(it) }
            action(this)
        }

        when(this) {
            ComplicationLocation.LEFT -> leftComplicationOption = builder.build()
            ComplicationLocation.MIDDLE -> middleComplicationOption = builder.build()
            ComplicationLocation.RIGHT -> rightComplicationOption = builder.build()
            ComplicationLocation.BOTTOM -> bottomComplicationOption = builder.build()
            ComplicationLocation.ANDROID_12_TOP_LEFT -> android12TopLeftComplicationOption = builder.build()
            ComplicationLocation.ANDROID_12_TOP_RIGHT -> android12TopRightComplicationOption = builder.build()
            ComplicationLocation.ANDROID_12_BOTTOM_LEFT -> android12BottomLeftComplicationOption = builder.build()
            ComplicationLocation.ANDROID_12_BOTTOM_RIGHT -> android12TopRightComplicationOption = builder.build()
        }
    }

    private fun updateGalaxyWatch4ComplicationSlots(
        location: ComplicationLocation,
        dataSourceInfo: ComplicationDataSourceInfo?,
        complicationId: Int
    ) {
        updateGalaxyWatch4HRComplicationSlots(
            location,
            dataSourceInfo,
            complicationId,
        )

        updateGalaxyWatch4CalendarComplicationSlots(
            location,
            dataSourceInfo,
            complicationId,
        )
    }

    private fun updateGalaxyWatch4HRComplicationSlots(
        location: ComplicationLocation,
        dataSourceInfo: ComplicationDataSourceInfo?,
        complicationSlotId: Int,
    ) {
        val isGalaxyWatch4HeartRateComplication = dataSourceInfo?.componentName?.isSamsungHeartRateProvider() == true
        if (isGalaxyWatch4HeartRateComplication && location !in galaxyWatch4HeartRateComplicationsLocationsMutableFlow.value) {
            if (DEBUG_LOGS) Log.d(TAG, "watchComplicationSlotsData, GW4 HR complication detected, id: $complicationSlotId")

            galaxyWatch4HeartRateComplicationsLocationsMutableFlow.value = HashSet(galaxyWatch4HeartRateComplicationsLocationsMutableFlow.value).apply {
                add(location)
            }
        } else {
            if (location in galaxyWatch4HeartRateComplicationsLocationsMutableFlow.value) {
                if (DEBUG_LOGS) Log.d(TAG, "watchComplicationSlotsData, GW4 HR complication removed, id: $complicationSlotId")

                galaxyWatch4HeartRateComplicationsLocationsMutableFlow.value = HashSet(galaxyWatch4HeartRateComplicationsLocationsMutableFlow.value).apply {
                    remove(location)
                }
            }
        }
    }

    private fun updateGalaxyWatch4CalendarComplicationSlots(
        location: ComplicationLocation,
        dataSourceInfo: ComplicationDataSourceInfo?,
        complicationSlotId: Int,
    ) {
        val isGalaxyWatch4CalendarComplication = dataSourceInfo?.componentName?.isSamsungCalendarBuggyProvider() == true
        if (isGalaxyWatch4CalendarComplication && location !in calendarBuggyComplicationsLocationsMutableFlow.value) {
            if (DEBUG_LOGS) Log.d(TAG, "watchComplicationSlotsData, GW4 buggy calendar complication detected, id: $complicationSlotId")

            calendarBuggyComplicationsLocationsMutableFlow.value = HashSet(calendarBuggyComplicationsLocationsMutableFlow.value).apply {
                add(location)
            }
        } else {
            if (location in calendarBuggyComplicationsLocationsMutableFlow.value) {
                if (DEBUG_LOGS) Log.d(TAG, "watchComplicationSlotsData, GW4 buggy calendar complication removed, id: $complicationSlotId")

                calendarBuggyComplicationsLocationsMutableFlow.value = HashSet(calendarBuggyComplicationsLocationsMutableFlow.value).apply {
                    remove(location)
                }
            }
        }
    }

    private fun ComplicationLocation.getComplicationDrawable(): CustomComplicationDrawable = when(this) {
        ComplicationLocation.LEFT -> leftComplicationDrawable
        ComplicationLocation.MIDDLE -> middleComplicationDrawable
        ComplicationLocation.RIGHT -> rightComplicationDrawable
        ComplicationLocation.BOTTOM -> bottomComplicationDrawable
        ComplicationLocation.ANDROID_12_TOP_LEFT -> android12TopLeftComplicationDrawable
        ComplicationLocation.ANDROID_12_TOP_RIGHT -> android12TopRightComplicationDrawable
        ComplicationLocation.ANDROID_12_BOTTOM_LEFT -> android12BottomLeftComplicationDrawable
        ComplicationLocation.ANDROID_12_BOTTOM_RIGHT -> android12BottomRightComplicationDrawable
    }

    companion object {
        private const val TAG = "ComplicationsSlot"

        private const val COMPLICATIONS_SETTING_ID = "complications:setting"

        private const val LEFT_COMPLICATION_ID = 100
        private const val RIGHT_COMPLICATION_ID = 101
        private const val MIDDLE_COMPLICATION_ID = 102
        private const val BOTTOM_COMPLICATION_ID = 103
        private const val WEATHER_COMPLICATION_ID = 104
        private const val BATTERY_COMPLICATION_ID = 105
        private const val ANDROID_12_TOP_LEFT_COMPLICATION_ID = 106
        private const val ANDROID_12_TOP_RIGHT_COMPLICATION_ID = 107
        private const val ANDROID_12_BOTTOM_LEFT_COMPLICATION_ID = 108
        private const val ANDROID_12_BOTTOM_RIGHT_COMPLICATION_ID = 109

        private val defaultComplicationSlotBounds = ComplicationSlotBounds(
            RectF(0f, 0f, 0f, 0f)
        )

        private val COMPLICATION_IDS = intArrayOf(
            LEFT_COMPLICATION_ID,
            MIDDLE_COMPLICATION_ID,
            RIGHT_COMPLICATION_ID,
            BOTTOM_COMPLICATION_ID,
            ANDROID_12_TOP_LEFT_COMPLICATION_ID,
            ANDROID_12_TOP_RIGHT_COMPLICATION_ID,
            ANDROID_12_BOTTOM_LEFT_COMPLICATION_ID,
            ANDROID_12_BOTTOM_RIGHT_COMPLICATION_ID,
        )

        private val normalComplicationDataTypes = listOf(
            ComplicationType.SHORT_TEXT,
            ComplicationType.RANGED_VALUE,
            ComplicationType.SMALL_IMAGE
        )

        private val largeComplicationDataTypes = listOf(
            ComplicationType.LONG_TEXT,
            ComplicationType.SHORT_TEXT,
            ComplicationType.SMALL_IMAGE,
        )

        @SuppressLint("RestrictedApi")
        suspend fun startComplicationChooser(
            editorSession: EditorSession,
            complicationLocation: ComplicationLocation,
        ) = editorSession.openComplicationDataSourceChooser(
            complicationLocation.getComplicationId()
        )

        fun getComplicationDataSource(
            editorSession: EditorSession,
            complicationLocation: ComplicationLocation
        ): ComplicationDataSourceInfo? = editorSession.complicationsDataSourceInfo.value[complicationLocation.getComplicationId()]

        private fun ComplicationLocation.getComplicationId(): Int {
            return when (this) {
                ComplicationLocation.LEFT -> LEFT_COMPLICATION_ID
                ComplicationLocation.MIDDLE -> MIDDLE_COMPLICATION_ID
                ComplicationLocation.RIGHT -> RIGHT_COMPLICATION_ID
                ComplicationLocation.BOTTOM -> BOTTOM_COMPLICATION_ID
                ComplicationLocation.ANDROID_12_TOP_LEFT -> ANDROID_12_TOP_LEFT_COMPLICATION_ID
                ComplicationLocation.ANDROID_12_TOP_RIGHT -> ANDROID_12_TOP_RIGHT_COMPLICATION_ID
                ComplicationLocation.ANDROID_12_BOTTOM_LEFT -> ANDROID_12_BOTTOM_LEFT_COMPLICATION_ID
                ComplicationLocation.ANDROID_12_BOTTOM_RIGHT -> ANDROID_12_BOTTOM_RIGHT_COMPLICATION_ID
            }
        }

        private fun Int.toComplicationLocation(): ComplicationLocation? {
            return when (this) {
                LEFT_COMPLICATION_ID -> ComplicationLocation.LEFT
                MIDDLE_COMPLICATION_ID -> ComplicationLocation.MIDDLE
                RIGHT_COMPLICATION_ID -> ComplicationLocation.RIGHT
                BOTTOM_COMPLICATION_ID -> ComplicationLocation.BOTTOM
                ANDROID_12_TOP_LEFT_COMPLICATION_ID -> ComplicationLocation.ANDROID_12_TOP_LEFT
                ANDROID_12_TOP_RIGHT_COMPLICATION_ID -> ComplicationLocation.ANDROID_12_TOP_RIGHT
                ANDROID_12_BOTTOM_LEFT_COMPLICATION_ID -> ComplicationLocation.ANDROID_12_BOTTOM_LEFT
                ANDROID_12_BOTTOM_RIGHT_COMPLICATION_ID -> ComplicationLocation.ANDROID_12_BOTTOM_RIGHT
                else -> null
            }
        }

        private fun getSupportedComplicationTypes(complicationLocation: ComplicationLocation): List<ComplicationType> {
            return when (complicationLocation) {
                ComplicationLocation.LEFT -> normalComplicationDataTypes
                ComplicationLocation.MIDDLE -> normalComplicationDataTypes
                ComplicationLocation.RIGHT -> normalComplicationDataTypes
                ComplicationLocation.BOTTOM -> largeComplicationDataTypes
                ComplicationLocation.ANDROID_12_TOP_LEFT -> normalComplicationDataTypes
                ComplicationLocation.ANDROID_12_TOP_RIGHT -> normalComplicationDataTypes
                ComplicationLocation.ANDROID_12_BOTTOM_LEFT -> normalComplicationDataTypes
                ComplicationLocation.ANDROID_12_BOTTOM_RIGHT -> normalComplicationDataTypes
            }
        }

        val complicationSetting = UserStyleSetting.ComplicationSlotsUserStyleSetting(
            UserStyleSetting.Id(COMPLICATIONS_SETTING_ID),
            displayName = "",
            description = "",
            icon = null,
            affectsWatchFaceLayers = listOf(
                WatchFaceLayer.COMPLICATIONS,
            ),
            complicationConfig = listOf(
                UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotsOption(
                    id = UserStyleSetting.Option.Id(UUID.randomUUID().toString()),
                    displayName = "",
                    icon = null,
                    complicationSlotOverlays = emptyList(),
                )
            )
        )
    }
}

