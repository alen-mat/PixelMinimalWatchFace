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

import android.app.*
import android.content.*
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.wear.watchface.*
import androidx.wear.watchface.style.*
import com.benoitletondor.pixelminimalwatchface.drawer.WatchFaceDrawer
import com.benoitletondor.pixelminimalwatchface.drawer.digital.android12.Android12DigitalWatchFaceDrawer
import com.benoitletondor.pixelminimalwatchface.drawer.digital.regular.RegularDigitalWatchFaceDrawer
import com.benoitletondor.pixelminimalwatchface.helper.*
import com.benoitletondor.pixelminimalwatchface.model.DEFAULT_APP_VERSION
import com.benoitletondor.pixelminimalwatchface.model.PhoneBatteryStatus
import com.benoitletondor.pixelminimalwatchface.model.Storage
import com.benoitletondor.pixelminimalwatchface.model.getBatteryText
import com.benoitletondor.pixelminimalwatchface.model.getValue
import com.benoitletondor.pixelminimalwatchface.settings.notificationssync.NotificationsSyncConfigurationActivity
import com.benoitletondor.pixelminimalwatchface.settings.phonebattery.*
import com.google.android.gms.wearable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.ZonedDateTime

private const val DATA_KEY_PREMIUM = "premium"
private const val DATA_KEY_BATTERY_STATUS_PERCENT = "/batterySync/batteryStatus"
private const val THREE_DAYS_MS: Long = 1000 * 60 * 60 * 24 * 3L
val DEBUG_LOGS = BuildConfig.DEBUG
private const val TAG = "PixelMinimalWatchFace"

class PixelMinimalWatchFace : WatchFaceService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var storage: Storage
    private lateinit var complicationsSlots: ComplicationsSlots

    override fun onCreate() {
        super.onCreate()

        if (DEBUG_LOGS) Log.d(TAG, "onCreate, security Patch: ${Build.VERSION.SECURITY_PATCH}, OS version : ${Build.VERSION.INCREMENTAL}")

        storage = Injection.storage(this)
        ComplicationsProviders.init(this, ComplicationsSlots.COMPLICATION_IDS)

        // Set app version to the current one if not set yet (first launch)
        if (storage.getAppVersion() == DEFAULT_APP_VERSION) {
            storage.setAppVersion(BuildConfig.VERSION_CODE)
        }
    }

    override fun onDestroy() {
        if (DEBUG_LOGS) Log.d(TAG, "onDestroy")
        
        scope.cancel()
        if(this::complicationsSlots.isInitialized) {
            complicationsSlots.onDestroy()
        }

        super.onDestroy()
    }

    override fun createUserStyleSchema(): UserStyleSchema = UserStyleSchema(
        listOf(
            ComplicationsSlots.complicationSetting,
        )
    )

    override fun createComplicationSlotsManager(currentUserStyleRepository: CurrentUserStyleRepository): ComplicationSlotsManager {
        if (DEBUG_LOGS) Log.d(TAG, "createComplicationSlotsManager")

        // It will be called without calling onCreate before in headless mode (for settings)
        if (!this::storage.isInitialized) {
            if (DEBUG_LOGS) Log.d(TAG, "createComplicationSlotsManager, returning empty slots manager")

            val complicationsSlots = ComplicationsSlots(this, Injection.storage(this), currentUserStyleRepository)
            val slots = complicationsSlots.createComplicationsSlots()
            complicationsSlots.onDestroy()

            return ComplicationSlotsManager(
                slots,
                currentUserStyleRepository,
            )
        }

        if (this::complicationsSlots.isInitialized) {
            complicationsSlots.onDestroy()
        }

        complicationsSlots = ComplicationsSlots(this, storage, currentUserStyleRepository)

        return ComplicationSlotsManager(
            complicationsSlots.createComplicationsSlots(),
            currentUserStyleRepository,
        ).apply {
            complicationsSlots.onCreate(this)
        }
    }

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository,
    ): WatchFace {
        if (DEBUG_LOGS) Log.d(TAG, "createWatchFace. Headless? ${watchState.isHeadless}")

        if (watchState.isHeadless) {
            return WatchFace(
                watchFaceType = WatchFaceType.DIGITAL,
                renderer = PreviewEditorSessionWatchFaceRenderer(
                    context = this,
                    surfaceHolder = surfaceHolder,
                    watchState = watchState,
                    currentUserStyleRepository = currentUserStyleRepository,
                    canvasType = CanvasType.SOFTWARE,
                ),
            )
        }

        val renderer = WatchFaceRenderer(
            context = applicationContext,
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            currentUserStyleRepository = currentUserStyleRepository,
            canvasType = CanvasType.SOFTWARE,
            storage = storage,
            complicationsSlots = complicationsSlots,
        )

        return WatchFace(
            watchFaceType = WatchFaceType.DIGITAL,
            renderer = renderer,
        ).apply {
            setTapListener(renderer)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private class WatchFaceRenderer(
        private val context: Context,
        surfaceHolder: SurfaceHolder,
        private val watchState: WatchState,
        private val currentUserStyleRepository: CurrentUserStyleRepository,
        canvasType: Int,
        private val storage: Storage,
        private val complicationsSlots: ComplicationsSlots,
    ) : Renderer.CanvasRenderer2<Renderer.SharedAssets>(
        surfaceHolder = surfaceHolder,
        currentUserStyleRepository = currentUserStyleRepository,
        watchState = watchState,
        canvasType = canvasType,
        interactiveDrawModeUpdateDelayMillis = 60000L,
        clearWithBackgroundTintBeforeRenderingHighlightLayer = false,
    ), DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener, WatchFace.TapListener {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        private var watchFaceDrawer: WatchFaceDrawer

        private var galaxyWatch4CalendarWatcherJob: Job? = null
        private var galaxyWatch4HeartRateWatcherJob: Job? = null

        private val batteryPhoneSyncHelper = BatteryPhoneSyncHelper(context, storage)
        private val batteryWatchSyncHelper = BatteryWatchSyncHelper(context, complicationsSlots)
        private val phoneNotifications = PhoneNotifications(context, storage)

        private var lastTapOnCenterOfScreenEventTimestamp: Long = 0

        init {
            watchFaceDrawer = createWatchFaceDrawer(storage.useAndroid12Style())

            watchWatchFaceDrawerChanges()
            watchGalaxyWatch4HRComplications()
            watchGalaxyWatch4CalendarComplications()
            watchComplicationSlotsRendererInvalidate()
            watchPhoneBatteryHelperRendererInvalidate()
            watchWatchBatteryHelperRendererInvalidate()
            watchNotificationIconsSyncChanges()
            watchWeatherDataUpdates()
            watchSecondsRingDisplayChanges()

            Wearable.getDataClient(context).addListener(this)
            Wearable.getMessageClient(context).addListener(this)

            batteryPhoneSyncHelper.start()
            batteryWatchSyncHelper.start()
            phoneNotifications.sync()
        }

        override fun onDestroy() {
            batteryPhoneSyncHelper.stop()
            batteryWatchSyncHelper.stop()
            watchFaceDrawer.onDestroy()
            phoneNotifications.onDestroy()
            Wearable.getDataClient(context).removeListener(this)
            Wearable.getMessageClient(context).removeListener(this)
            scope.cancel()

            super.onDestroy()
        }

        override suspend fun createSharedAssets(): SharedAssets = object : SharedAssets {
            override fun onDestroy() {}
        }

        override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: SharedAssets) {
            if (DEBUG_LOGS) Log.d(TAG, "render")

            showRatingNotificationIfNeeded()

            watchFaceDrawer.draw(
                canvas,
                bounds,
                zonedDateTime,
                if (storage.showWeather()) { complicationsSlots.weatherComplicationDataFlow.value } else { null },
                if (storage.showPhoneBattery()) { batteryPhoneSyncHelper.phoneBatteryStatus.getBatteryText(System.currentTimeMillis()) } else { null },
                if (storage.showWatchBattery()) { batteryWatchSyncHelper.watchBatteryStatus.getValue() } else { null },
                if (storage.isNotificationsSyncActivated()) { phoneNotifications.notificationsStateFlow.value } else { null },
            )

            if (storage.isUserPremium()) {
                if (storage.showComplicationsInAmbientMode() || watchState.isAmbient.value != true) {
                    complicationsSlots.render(canvas, zonedDateTime, renderParameters)
                }
            }
        }

        override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: SharedAssets) {
            if (DEBUG_LOGS) Log.d(TAG, "renderHighlightLayer")
        }

        override fun onTapEvent(
            tapType: Int,
            tapEvent: TapEvent,
            complicationSlot: ComplicationSlot?
        ) {
            if (tapType != TapType.UP) {
                return
            }

            if (DEBUG_LOGS) Log.d(TAG, "onTapEvent: $tapEvent")

            when {
                complicationSlot != null -> {
                    lastTapOnCenterOfScreenEventTimestamp = 0
                }
                watchFaceDrawer.isTapOnWeather(tapEvent) -> {
                    val weatherProviderInfo = context.getWeatherProviderInfo() ?: return
                    context.openActivity(weatherProviderInfo.appPackage, weatherProviderInfo.weatherActivityName)
                    lastTapOnCenterOfScreenEventTimestamp = 0
                }
                watchFaceDrawer.isTapOnBattery(tapEvent) -> {
                    lastTapOnCenterOfScreenEventTimestamp = 0
                    if (storage.showPhoneBattery() &&
                        batteryPhoneSyncHelper.phoneBatteryStatus.isStale(System.currentTimeMillis())) {
                        context.startActivity(Intent(context, PhoneBatteryConfigurationActivity::class.java).apply {
                            flags = FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                }
                watchFaceDrawer.isTapOnCenterOfScreen(tapEvent) -> {
                    val eventTime = System.currentTimeMillis()
                    if( lastTapOnCenterOfScreenEventTimestamp == 0L || eventTime - lastTapOnCenterOfScreenEventTimestamp > 400 ) {
                        lastTapOnCenterOfScreenEventTimestamp = eventTime
                    } else {
                        lastTapOnCenterOfScreenEventTimestamp = 0
                        context.startActivity(Intent(context, FullBrightnessActivity::class.java).apply {
                            flags = FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                }
                storage.isUserPremium() &&
                    storage.showPhoneBattery() &&
                    batteryPhoneSyncHelper.phoneBatteryStatus.isStale(System.currentTimeMillis()) &&
                    watchFaceDrawer.isTapOnBattery(tapEvent) -> {
                    context.startActivity(Intent(context, PhoneBatteryConfigurationActivity::class.java).apply {
                        flags = FLAG_ACTIVITY_NEW_TASK
                    })
                }
                storage.isUserPremium() &&
                    storage.isNotificationsSyncActivated() &&
                    watchFaceDrawer.isTapOnNotifications(tapEvent) -> {
                    when(val currentState = phoneNotifications.notificationsStateFlow.value) {
                        is PhoneNotifications.NotificationState.DataReceived -> {
                            if (currentState.icons.isNotEmpty()) {
                                Toast.makeText(
                                    context,
                                    if (Device.isSamsungGalaxyWatch) {
                                        "Swipe from left to go to notifications"
                                    } else {
                                        "Swipe from bottom to go to notifications"
                                    },
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                        is PhoneNotifications.NotificationState.Unknown -> {
                            if (currentState.isStale(System.currentTimeMillis())) {
                                context.startActivity(Intent(context, NotificationsSyncConfigurationActivity::class.java).apply {
                                    flags = FLAG_ACTIVITY_NEW_TASK
                                })
                            }
                        }
                    }
                }
            }
        }

        private fun handleIsPremiumCallback(isPremium: Boolean) {
            val wasPremium = storage.isUserPremium()
            storage.setUserPremium(isPremium)

            if( !wasPremium && isPremium ) {
                Toast.makeText(context, R.string.premium_confirmation, Toast.LENGTH_LONG).show()
            }

            invalidate()
        }

        private fun createWatchFaceDrawer(useAndroid12Style: Boolean): WatchFaceDrawer {
            if (DEBUG_LOGS) Log.d(TAG, "createWatchFaceDrawer, a12? $useAndroid12Style")

            val drawer = if (useAndroid12Style) {
                Android12DigitalWatchFaceDrawer(context, storage, watchState, complicationsSlots, currentUserStyleRepository, ::invalidate)
            } else {
                RegularDigitalWatchFaceDrawer(context, storage, watchState, complicationsSlots, currentUserStyleRepository, ::invalidate)
            }

            complicationsSlots.setActiveComplicationLocations(drawer.getActiveComplicationLocations())

            return drawer
        }

        private fun watchWatchFaceDrawerChanges() {
            scope.launch {
                storage.watchUseAndroid12Style()
                    .drop(1)
                    .collect { useAndroid12Style ->
                        watchFaceDrawer.onDestroy()
                        watchFaceDrawer = createWatchFaceDrawer(useAndroid12Style)
                    }
            }
        }

        private fun watchGalaxyWatch4HRComplications() {
            scope.launch {
                complicationsSlots.galaxyWatch4HeartRateComplicationsLocationsFlow
                    .map { it.isNotEmpty() }
                    .distinctUntilChanged()
                    .collect { hasGalaxyWatch4HRComplication ->
                        if (hasGalaxyWatch4HRComplication) {
                            onGalaxyWatch4HeartRateComplicationActive()
                        } else {
                            onGalaxyWatch4HeartRateComplicationInactive()
                        }
                    }
            }
        }

        private fun watchGalaxyWatch4CalendarComplications() {
            scope.launch {
                complicationsSlots.calendarBuggyComplicationsLocationsFlow
                    .map { it.isNotEmpty() }
                    .distinctUntilChanged()
                    .collect { hasCalendarBuggyComplication ->
                        if (hasCalendarBuggyComplication) {
                            onGalaxyWatch4CalendarComplicationActive()
                        } else {
                            onGalaxyWatch4CalendarComplicationInactive()
                        }
                    }
            }
        }

        private fun watchComplicationSlotsRendererInvalidate() {
            scope.launch {
                complicationsSlots.invalidateRendererEventFlow
                    .collect {
                        invalidate()
                    }
            }
        }

        private fun watchPhoneBatteryHelperRendererInvalidate() {
            scope.launch {
                batteryPhoneSyncHelper.invalidateRendererEventFlow
                    .collect {
                        invalidate()
                    }
            }
        }

        private fun watchWatchBatteryHelperRendererInvalidate() {
            scope.launch {
                batteryWatchSyncHelper.invalidateDrawerEventFlow
                    .collect {
                        watchFaceDrawer.onDestroy()
                        watchFaceDrawer = createWatchFaceDrawer(storage.useAndroid12Style())
                    }
            }
        }

        private fun watchWeatherDataUpdates() {
            scope.launch {
                storage.watchShowWeather()
                    .collect { showWeather ->
                        complicationsSlots.setWeatherComplicationEnabled(showWeather)
                    }
            }

            scope.launch {
                complicationsSlots.weatherComplicationDataFlow
                    .filterNotNull()
                    .collect {
                        invalidate()
                    }
            }
        }

        private fun watchSecondsRingDisplayChanges() {
            scope.launch {
                storage.watchShowSecondsRing()
                    .collect { showSecondsRing ->
                        interactiveDrawModeUpdateDelayMillis = if (showSecondsRing) 1000 else 60000
                    }
            }
        }

        private fun watchNotificationIconsSyncChanges() {
            scope.launch {
                storage.watchIsNotificationsSyncActivated()
                    .collectLatest { activated ->
                        if (!activated) {
                            Log.d(TAG, "Notifications from phone deactivated: invalidate")
                            invalidate()
                        } else {
                            phoneNotifications.notificationsStateFlow
                                .collect { state ->
                                    Log.d(TAG, "Notifications from phone received, invalidate: $state")
                                    invalidate()
                                }
                        }
                    }

            }
        }

        private fun onGalaxyWatch4HeartRateComplicationInactive() {
            if (DEBUG_LOGS) Log.d(TAG, "onGalaxyWatch4HeartRateComplicationInactive")

            galaxyWatch4HeartRateWatcherJob?.cancel()
            galaxyWatch4HeartRateWatcherJob = null
        }

        private fun onGalaxyWatch4HeartRateComplicationActive() {
            if (DEBUG_LOGS) Log.d(TAG, "onGalaxyWatch4HeartRateComplicationActive")

            galaxyWatch4HeartRateWatcherJob?.cancel()
            galaxyWatch4HeartRateWatcherJob = scope.launch {
                complicationsSlots.galaxyWatch4HeartRateComplicationsLocationsFlow
                    .flatMapLatest { complicationLocations ->
                        context.watchSamsungHeartRateUpdates()
                            .map { complicationLocations }
                    }
                    .collect { complicationLocations ->
                        if (DEBUG_LOGS) Log.d(TAG, "galaxyWatch4HeartRateWatcher, new value received")

                        for(complicationLocation in complicationLocations) {
                            if (DEBUG_LOGS) Log.d(TAG, "galaxyWatch4HeartRateWatcher, refreshing for complication $complicationLocation")

                            complicationsSlots.refreshDataAtLocation(complicationLocation)
                        }
                    }
            }
        }

        private fun onGalaxyWatch4CalendarComplicationInactive() {
            if (DEBUG_LOGS) Log.d(TAG, "onGalaxyWatch4CalendarComplicationInactive")

            galaxyWatch4CalendarWatcherJob?.cancel()
            galaxyWatch4CalendarWatcherJob = null
        }

        private fun onGalaxyWatch4CalendarComplicationActive() {
            if (DEBUG_LOGS) Log.d(TAG, "onGalaxyWatch4CalendarComplicationActive")

            galaxyWatch4CalendarWatcherJob?.cancel()
            galaxyWatch4CalendarWatcherJob = scope.launch {
                while(isActive) {
                    complicationsSlots.calendarBuggyComplicationsLocationsFlow.value.forEach { location ->
                        complicationsSlots.refreshDataAtLocation(location)
                    }

                    delay(HALF_HOUR_MS)
                }
            }
        }

        private fun showRatingNotificationIfNeeded() {
            if( !storage.hasRatingBeenDisplayed() &&
                System.currentTimeMillis() - storage.getInstallTimestamp() > THREE_DAYS_MS ) {
                storage.setRatingDisplayed(true)
                context.showRatingNotification()
            }
        }

        override fun onDataChanged(dataEvents: DataEventBuffer) {
            for (event in dataEvents) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

                    when(event.dataItem.uri.path) {
                        "/premium" -> {
                            if (dataMap.containsKey(DATA_KEY_PREMIUM)) {
                                handleIsPremiumCallback(dataMap.getBoolean(DATA_KEY_PREMIUM))
                            }
                        }
                        "/notifications" -> {
                            phoneNotifications.onNewData(dataMap)
                        }
                    }

                }
            }
        }

        override fun onMessageReceived(messageEvent: MessageEvent) {
            if (messageEvent.path == DATA_KEY_BATTERY_STATUS_PERCENT) {
                try {
                    val phoneBatteryPercentage: Int = messageEvent.data[0].toInt()
                    if (phoneBatteryPercentage in 0..100) {
                        batteryPhoneSyncHelper.onPhoneBatteryStatusReceived(PhoneBatteryStatus.DataReceived(phoneBatteryPercentage, System.currentTimeMillis()))
                    }
                } catch (t: Throwable) {
                    Log.e("PixelWatchFace", "Error while parsing phone battery percentage from phone", t)
                }
            } else if (messageEvent.path == DATA_KEY_PREMIUM) {
                try {
                    handleIsPremiumCallback(messageEvent.data[0].toInt() == 1)
                } catch (t: Throwable) {
                    Log.e("PixelWatchFace", "Error while parsing premium status from phone", t)
                    Toast.makeText(context, R.string.premium_error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val HALF_HOUR_MS: Long = 1000*60*30

        fun isActive(context: Context): Boolean {
            val wallpaperManager = WallpaperManager.getInstance(context)
            return wallpaperManager.wallpaperInfo?.packageName == context.packageName
        }
    }
}
