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
import android.util.Log
import android.util.SparseArray
import androidx.wear.watchface.complications.ComplicationDataSourceInfo
import androidx.wear.watchface.complications.ComplicationDataSourceInfoRetriever
import androidx.wear.watchface.style.UserStyleData
import com.benoitletondor.pixelminimalwatchface.helper.isSamsungCalendarBuggyProvider
import com.benoitletondor.pixelminimalwatchface.helper.isSamsungHeartRateProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ComplicationsProviders(
    private val context: Context,
    private val complicationIds: IntArray,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var initialized = false

    private val complicationProviderSparseArray: SparseArray<ComplicationProvider> = SparseArray(complicationIds.size)
    private val complicationDataSourceInfoRetriever = ComplicationDataSourceInfoRetriever(context)

    private val galaxyWatch4HeartRateComplicationsIdsMutableFlow = MutableStateFlow<Set<Int>>(emptySet())
    val galaxyWatch4HeartRateComplicationsIdsFlow: StateFlow<Set<Int>> = galaxyWatch4HeartRateComplicationsIdsMutableFlow

    private val calendarBuggyComplicationsIdsMutableFlow = MutableStateFlow<Set<Int>>(emptySet())
    val calendarBuggyComplicationsIdsFlow: StateFlow<Set<Int>> = calendarBuggyComplicationsIdsMutableFlow

    fun initIfNeeded(watchFaceId: String, userStyle: UserStyleData) {
        if (initialized) {
            return
        }

        initialized = true

        scope.launch {
            fetchFromDataSourceInfoRetriever()
        }
    }

    fun complicationDataSourceInfoUpdated(slotId: Int, complicationDataSourceInfo: ComplicationDataSourceInfo?) {
        if (DEBUG_LOGS) Log.d(TAG, "complicationDataSourceInfoUpdated: $slotId -> $complicationDataSourceInfo")

        val info = complicationDataSourceInfo ?: return
        val componentName = info.componentName

        val complicationProvider = if (componentName != null) {
            ComplicationProvider.Component(componentName)
        } else {
            ComplicationProvider.AppName(info.appName, info.name)
        }

        updateGalaxyWatch4ComplicationSlots(complicationProvider, slotId)

        complicationProviderSparseArray.put(slotId, complicationProvider)
    }

    fun getComplicationProvider(slotId: Int): ComplicationProvider? {
        return complicationProviderSparseArray.get(slotId)
    }

    private suspend fun fetchFromDataSourceInfoRetriever() {
        if (DEBUG_LOGS) Log.d(TAG, "fetchFromDataSourceInfoRetriever")

        try {
            val results = complicationDataSourceInfoRetriever.retrieveComplicationDataSourceInfo(
                ComponentName(context, PixelMinimalWatchFace::class.java),
                complicationIds,
            )

            if (results == null) {
                Log.e(TAG, "fetchFromDataSourceInfoRetriever.retrieveComplicationDataSourceInfo returned null")
                return
            }

            for(result in results) {
                complicationDataSourceInfoUpdated(result.slotId, result.info)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            Log.e(TAG, "Error in fetchFromDataSourceInfoRetriever.retrieveComplicationDataSourceInfo", e)
        }
    }

    private fun updateGalaxyWatch4ComplicationSlots(
        complicationProvider: ComplicationProvider,
        complicationId: Int
    ) {
        updateGalaxyWatch4HRComplicationSlots(
            complicationProvider,
            complicationId,
        )

        updateGalaxyWatch4CalendarComplicationSlots(
            complicationProvider,
            complicationId,
        )
    }

    private fun updateGalaxyWatch4HRComplicationSlots(
        complicationProvider: ComplicationProvider,
        complicationSlotId: Int,
    ) {
        val isGalaxyWatch4HeartRateComplication = complicationProvider.isSamsungHeartRateProvider()
        if (isGalaxyWatch4HeartRateComplication && complicationSlotId !in galaxyWatch4HeartRateComplicationsIdsMutableFlow.value) {
            if (DEBUG_LOGS) Log.d(TAG, "watchComplicationSlotsData, GW4 HR complication detected, id: $complicationSlotId")

            galaxyWatch4HeartRateComplicationsIdsMutableFlow.value = HashSet(galaxyWatch4HeartRateComplicationsIdsMutableFlow.value).apply {
                add(complicationSlotId)
            }
        } else {
            if (complicationSlotId in galaxyWatch4HeartRateComplicationsIdsMutableFlow.value) {
                if (DEBUG_LOGS) Log.d(TAG, "watchComplicationSlotsData, GW4 HR complication removed, id: $complicationSlotId")

                galaxyWatch4HeartRateComplicationsIdsMutableFlow.value = HashSet(galaxyWatch4HeartRateComplicationsIdsMutableFlow.value).apply {
                    remove(complicationSlotId)
                }
            }
        }
    }

    private fun updateGalaxyWatch4CalendarComplicationSlots(
        complicationProvider: ComplicationProvider,
        complicationSlotId: Int,
    ) {
        val isGalaxyWatch4CalendarComplication = complicationProvider.isSamsungCalendarBuggyProvider()
        if (isGalaxyWatch4CalendarComplication && complicationSlotId !in calendarBuggyComplicationsIdsMutableFlow.value) {
            if (DEBUG_LOGS) Log.d(TAG, "watchComplicationSlotsData, GW4 buggy calendar complication detected, id: $complicationSlotId")

            calendarBuggyComplicationsIdsMutableFlow.value = HashSet(calendarBuggyComplicationsIdsMutableFlow.value).apply {
                add(complicationSlotId)
            }
        } else {
            if (complicationSlotId in calendarBuggyComplicationsIdsMutableFlow.value) {
                if (DEBUG_LOGS) Log.d(TAG, "watchComplicationSlotsData, GW4 buggy calendar complication removed, id: $complicationSlotId")

                calendarBuggyComplicationsIdsMutableFlow.value = HashSet(calendarBuggyComplicationsIdsMutableFlow.value).apply {
                    remove(complicationSlotId)
                }
            }
        }
    }


    companion object {
        private const val TAG = "ComplicationsProviders"

        @SuppressLint("StaticFieldLeak")
        private var instance: ComplicationsProviders? = null

        fun init(context: Context, complicationIds: IntArray) {
            instance = ComplicationsProviders(context, complicationIds)
        }

        fun getInstance(): ComplicationsProviders {
            return instance ?: throw IllegalStateException("ComplicationsProviders not initialized")
        }
    }
}

sealed class ComplicationProvider {
    data class Component(val component: ComponentName) : ComplicationProvider()
    data class AppName(val appName: String, val providerName: String) : ComplicationProvider()
}