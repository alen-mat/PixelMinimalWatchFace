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
import android.util.Log
import com.benoitletondor.pixelminimalwatchface.BuildConfig
import com.benoitletondor.pixelminimalwatchface.PixelMinimalWatchFace.Companion.HALF_HOUR_MS
import com.benoitletondor.pixelminimalwatchface.model.PhoneBatteryStatus
import com.benoitletondor.pixelminimalwatchface.model.Storage
import com.benoitletondor.pixelminimalwatchface.settings.phonebattery.findBestNode
import com.benoitletondor.pixelminimalwatchface.settings.phonebattery.startPhoneBatterySync
import com.benoitletondor.pixelminimalwatchface.settings.phonebattery.stopPhoneBatterySync
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class BatteryPhoneSyncHelper(
    private val context: Context,
    private val storage: Storage,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var phoneBatteryStatus: PhoneBatteryStatus = PhoneBatteryStatus.Unknown
        private set

    private var lastPhoneSyncRequestTimestamp: Long? = null

    private var job: Job? = null

    private val invalidateRendererMutableEventFlow = MutableSharedFlow<Unit>()
    val invalidateRendererEventFlow: Flow<Unit> = invalidateRendererMutableEventFlow

    fun start() {
        lastPhoneSyncRequestTimestamp = null

        job = scope.launch {
            context.syncPhoneBatteryStatus(storage)

            while (isActive) {
                delay(60000)
                Log.d(TAG, "awake")

                val lastRequestTimestamp = lastPhoneSyncRequestTimestamp
                if( storage.showPhoneBattery() &&
                    phoneBatteryStatus.isStale(System.currentTimeMillis()) &&
                    (lastRequestTimestamp == null || System.currentTimeMillis() - lastRequestTimestamp > HALF_HOUR_MS) ) {
                    lastPhoneSyncRequestTimestamp = System.currentTimeMillis()

                    context.syncPhoneBatteryStatus(storage)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun onPhoneBatteryStatusReceived(phoneBatteryStatus: PhoneBatteryStatus.DataReceived) {
        val previousPhoneBatteryStatus = this.phoneBatteryStatus as? PhoneBatteryStatus.DataReceived
        this.phoneBatteryStatus = phoneBatteryStatus

        if (storage.showPhoneBattery() &&
            (phoneBatteryStatus.batteryPercentage != previousPhoneBatteryStatus?.batteryPercentage || previousPhoneBatteryStatus.isStale(System.currentTimeMillis()))) {

            scope.launch {
                invalidateRendererMutableEventFlow.emit(Unit)
            }
        }
    }

    private suspend fun Context.syncPhoneBatteryStatus(storage: Storage) {
        try {
            Log.d(TAG, "syncPhoneBatteryStatus. showPhoneBattery: ${storage.showPhoneBattery()}")
            
            val capabilityInfo = withTimeout(5000) {
                Wearable.getCapabilityClient(this@syncPhoneBatteryStatus).getCapability(BuildConfig.COMPANION_APP_CAPABILITY, CapabilityClient.FILTER_REACHABLE).await()
            }

            if (storage.showPhoneBattery()) {
                capabilityInfo.nodes.findBestNode()?.startPhoneBatterySync(this@syncPhoneBatteryStatus)
            } else {
                capabilityInfo.nodes.findBestNode()?.stopPhoneBatterySync(this@syncPhoneBatteryStatus)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "Error while sending phone battery sync signal", t)
        }
    }

    companion object {
        private const val TAG = "BatteryPhoneSyncHelper"
    }
}