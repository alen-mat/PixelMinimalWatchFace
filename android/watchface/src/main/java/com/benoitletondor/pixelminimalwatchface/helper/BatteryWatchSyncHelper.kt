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

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.benoitletondor.pixelminimalwatchface.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull

class BatteryWatchSyncHelper(
    private val context: Context,
    private val complicationsSlots: ComplicationsSlots,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var watchBatteryStatus: WatchBatteryStatus = WatchBatteryStatus.Unknown
        private set

    private var watchBatteryLevelWatcherJob: Job? = null
    private var dataFreshnessJob: Job? = null

    private val invalidateDrawerMutableEventFlow = MutableSharedFlow<Unit>()
    val invalidateDrawerEventFlow: Flow<Unit> = invalidateDrawerMutableEventFlow

    fun start() {
        watchBatteryLevelWatcherJob = scope.launch {
            complicationsSlots.watchBatteryLevelFlow
                .filterNotNull()
                .collect { batteryLevel ->
                    onWatchBatteryStatusReceived(WatchBatteryStatus.DataReceived(batteryLevel))
                }
        }

        dataFreshnessJob = scope.launch {
            while (isActive) {
                delay(60000)
                Log.d(TAG, "awake")

                val lastWatchBatteryStatus = watchBatteryStatus
                if (Device.isSamsungGalaxyWatch &&
                    lastWatchBatteryStatus is WatchBatteryStatus.DataReceived) {
                    ensureBatteryDataIsUpToDateOrReload(lastWatchBatteryStatus)
                }
            }
        }
    }

    fun stop() {
        dataFreshnessJob?.cancel()
        watchBatteryLevelWatcherJob?.cancel()
    }

    private fun onWatchBatteryStatusReceived(watchBatteryStatus: WatchBatteryStatus.DataReceived) {
        this.watchBatteryStatus = watchBatteryStatus
    }

    private fun ensureBatteryDataIsUpToDateOrReload(lastWatchBatteryStatus: WatchBatteryStatus.DataReceived) {
        if (DEBUG_LOGS) Log.d(TAG, "ensureBatteryDataIsUpToDateOrReload comparing $lastWatchBatteryStatus")

        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val maybeBatteryStatus = context.registerReceiver(null, filter)
            val maybeCurrentBatteryPercentage = maybeBatteryStatus?.let { intent ->
                val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                level * 100 / scale.toFloat()
            }?.toInt()

            if (DEBUG_LOGS) Log.d(TAG, "ensureBatteryDataIsUpToDateOrReload current value $maybeCurrentBatteryPercentage")

            if (maybeCurrentBatteryPercentage != null &&
                maybeCurrentBatteryPercentage != lastWatchBatteryStatus.batteryPercentage) {

                if (lastWatchBatteryStatus.shouldRefresh(maybeCurrentBatteryPercentage)) {
                    watchBatteryStatus = WatchBatteryStatus.Unknown

                    if (DEBUG_LOGS) Log.d(TAG, "ensureBatteryDataIsUpToDateOrReload, refreshing")

                    scope.launch {
                        invalidateDrawerMutableEventFlow.emit(Unit)
                    }
                } else {
                    if (DEBUG_LOGS) Log.d(TAG, "ensureBatteryDataIsUpToDateOrReload ignoring cause not stale yet")
                    lastWatchBatteryStatus.markAsStale()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureBatteryDataIsUpToDateOrReload: Error while comparing data", e)
        }
    }

    companion object {
        private const val TAG = "BatteryWatchSyncHelper"
    }
}