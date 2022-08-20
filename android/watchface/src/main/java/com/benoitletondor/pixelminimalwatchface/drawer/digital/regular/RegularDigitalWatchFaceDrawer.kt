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
package com.benoitletondor.pixelminimalwatchface.drawer.digital.regular

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.support.wearable.complications.ComplicationData
import android.util.Log
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.wear.watchface.TapEvent
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import com.benoitletondor.pixelminimalwatchface.*
import com.benoitletondor.pixelminimalwatchface.drawer.WatchFaceDrawer
import com.benoitletondor.pixelminimalwatchface.helper.*
import com.benoitletondor.pixelminimalwatchface.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.*

@SuppressLint("RestrictedApi")
class RegularDigitalWatchFaceDrawer(
    private val context: Context,
    private val storage: Storage,
    private val watchState: WatchState,
    private val complicationsSlots: ComplicationsSlots,
    private val currentUserStyleRepository: CurrentUserStyleRepository,
    private val invalidateRenderer: () -> Unit,
) : WatchFaceDrawer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var drawingState: RegularDrawerDrawingState = kotlin.run {
        val screenSize = context.getScreenSize()

        RegularDrawerDrawingState.NoCacheAvailable(
            screenSize.width,
            screenSize.height,
            screenSize.width / 2f,
            screenSize.height / 2f,
        )
    }

    private val productSansRegularFont: Typeface = ResourcesCompat.getFont(context, R.font.product_sans_regular)!!
    private val wearOSLogoPaint = Paint()
    private val timePaint = Paint().apply {
        typeface = productSansRegularFont
        strokeWidth = 1.8f
    }
    private val datePaint = Paint().apply {
        typeface = productSansRegularFont
    }
    private val weatherIconPaint = Paint()
    @ColorInt
    private val backgroundColor: Int = ContextCompat.getColor(context, R.color.face_background)
    @ColorInt
    private val timeColorDimmed: Int = ContextCompat.getColor(context, R.color.face_time_dimmed)
    @ColorInt
    private val dateAndBatteryColorDimmed: Int = ContextCompat.getColor(context, R.color.face_date_dimmed)
    private val wearOSLogo: Bitmap = ContextCompat.getDrawable(context, R.drawable.ic_wear_os_logo)!!.toBitmap()
    private val wearOSLogoAmbient: Bitmap = ContextCompat.getDrawable(context, R.drawable.ic_wear_os_logo_ambient)!!.toBitmap()
    private val batteryIconPaint: Paint = Paint()
    private var batteryIconSize = 0
    private val batteryLevelPaint = Paint().apply {
        typeface = productSansRegularFont
    }
    private val secondsRingPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 10F
        isAntiAlias = true
    }
    private val notificationsPaint = Paint()
    private val distanceBetweenPhoneAndWatchBattery: Int = context.dpToPx(3)
    private val timeFormatter24H = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val timeFormatter12H = DateTimeFormatter.ofPattern("h:mm", Locale.getDefault())
    private val spaceBeforeWeather = context.dpToPx(5)
    private val topAndBottomMargins = context.getTopAndBottomMargins().toInt()
    private val weatherAndBatteryIconColorFilterDimmed: ColorFilter = PorterDuffColorFilter(dateAndBatteryColorDimmed, PorterDuff.Mode.SRC_IN)

    init {
        if (DEBUG_LOGS) Log.d(TAG, "init")

        watchSizesChanges()
        watchUserStyleChanges()
    }

    override fun onDestroy() {
        if (DEBUG_LOGS) Log.d(TAG, "onDestroy")

        scope.cancel()
    }

    private fun watchSizesChanges() {
        scope.launch {
            combine(
                storage.watchTimeSize(),
                storage.watchDateAndBatterySize(),
                storage.watchWidgetsSize()
            ) { _, _, _ ->
                true // We don't care about the value here
            }
            .drop(1) // Ignore first value
            .collect {
                recomputeDrawingStateAndReRender()
            }
        }
    }

    private fun watchUserStyleChanges() {
        scope.launch {
            currentUserStyleRepository.userStyle
                .drop(1)
                .collect {
                    val bounds = complicationsSlots.getComplicationBounds(getActiveComplicationLocations().first())
                    if (bounds == null || bounds.isEmpty) {
                        recomputeDrawingStateAndReRender()
                    }
                }
        }
    }

    private fun recomputeDrawingStateAndReRender() {
        if (DEBUG_LOGS) Log.d(TAG, "recomputeDrawingStateAndReRender")

        drawingState = when(val currentDrawingState = drawingState) {
            is RegularDrawerDrawingState.CacheAvailable -> currentDrawingState.buildCache()
            is RegularDrawerDrawingState.NoCacheAvailable -> currentDrawingState.buildCache()
        }

        invalidateRenderer()
    }

    override fun getActiveComplicationLocations(): Set<ComplicationLocation> = setOf(
        ComplicationLocation.LEFT,
        ComplicationLocation.MIDDLE,
        ComplicationLocation.RIGHT,
        ComplicationLocation.BOTTOM,
    )

    override fun isTapOnWeather(tapEvent: TapEvent): Boolean {
        val drawingState = drawingState
        if( !storage.showWeather() ||
            !storage.isUserPremium() ||
            drawingState !is RegularDrawerDrawingState.CacheAvailable ) {
            return false
        }

        val displayRect = drawingState.getWeatherDisplayRect() ?: return false
        return displayRect.contains(tapEvent.xPos, tapEvent.yPos)
    }

    override fun isTapOnCenterOfScreen(tapEvent: TapEvent): Boolean {
        val drawingState = drawingState as? RegularDrawerDrawingState.CacheAvailable ?: return false

        val centerRect = Rect(
            (drawingState.screenWidth * 0.25f).toInt(),
            (drawingState.screenHeight * 0.25f).toInt(),
            (drawingState.screenWidth * 0.75f).toInt(),
            (drawingState.screenHeight * 0.75f).toInt()
        )

        return centerRect.contains(tapEvent.xPos, tapEvent.yPos)
    }

    override fun isTapOnBattery(tapEvent: TapEvent): Boolean {
        val drawingState = drawingState as? RegularDrawerDrawingState.CacheAvailable ?: return false

        return drawingState.tapIsOnBattery(tapEvent.xPos, tapEvent.yPos)
    }

    override fun isTapOnNotifications(x: Int, y: Int): Boolean {
        val drawingState = drawingState as? RegularDrawerDrawingState.CacheAvailable ?: return false

        return drawingState.isTapOnNotifications(x, y)
    }

    override fun draw(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        weatherComplicationData: ComplicationData?,
        phoneBatteryValue: String?,
        watchBatteryValue: Int?,
        notificationsState: PhoneNotifications.NotificationState?,
    ) {
        val isAmbient = watchState.isAmbient.value == true
        setPaintVariables(
            ambient = isAmbient,
            lowBitAmbient = watchState.hasLowBitAmbient,
        )

        canvas.drawColor(backgroundColor)

        val currentDrawingState = drawingState
        if( currentDrawingState is RegularDrawerDrawingState.NoCacheAvailable ) {
            drawingState = currentDrawingState.buildCache()
        }

        val drawingState = drawingState
        if(drawingState is RegularDrawerDrawingState.CacheAvailable){
            drawingState.draw(
                canvas,
                zonedDateTime,
                watchState.isAmbient.value == true,
                storage.isUserPremium(),
                storage.showSecondsRing(),
                !isAmbient || storage.getShowDateInAmbient(),
                weatherComplicationData,
                watchBatteryValue,
                phoneBatteryValue,
                notificationsState,
            )
        }
    }

    private fun setPaintVariables(
        ambient: Boolean,
        lowBitAmbient: Boolean,
    ) {
        wearOSLogoPaint.isAntiAlias = !ambient

        val shouldUseStrokeStyle = (ambient && !storage.useNormalTimeStyleInAmbientMode()) || (!ambient && storage.useThinTimeStyleInRegularMode())
        timePaint.apply {
            isAntiAlias = !(ambient && lowBitAmbient)
            style = if(shouldUseStrokeStyle) { Paint.Style.STROKE } else { Paint.Style.FILL }
            color = if( ambient ) { timeColorDimmed } else { storage.getTimeColor() }
        }

        datePaint.apply {
            isAntiAlias = !(ambient && lowBitAmbient)
            color = if( ambient ) { dateAndBatteryColorDimmed } else { storage.getDateColor() }
        }

        weatherIconPaint.apply {
            isAntiAlias = !ambient
            colorFilter = if( ambient ) { weatherAndBatteryIconColorFilterDimmed } else { storage.getDateColorFilter() }
        }

        batteryLevelPaint.apply {
            isAntiAlias = !(ambient && lowBitAmbient)
            color = if( ambient ) { dateAndBatteryColorDimmed } else { storage.getBatteryIndicatorColor() }
        }

        batteryIconPaint.apply {
            isAntiAlias = !(ambient && lowBitAmbient)
            colorFilter = if( ambient ) { weatherAndBatteryIconColorFilterDimmed } else { storage.getBatteryIndicatorColorFilter() }
        }

        secondsRingPaint.apply {
            colorFilter = storage.getSecondRingColor()
        }
    }

    private fun RegularDrawerDrawingState.NoCacheAvailable.buildCache(): RegularDrawerDrawingState.CacheAvailable {
        val timeSize = storage.getTimeSize()
        val dateAndBatterySize = storage.getDateAndBatterySize()
        setScaledSizes(timeSize, dateAndBatterySize)

        val timeText = "22:13"
        val timeTextBounds = Rect().apply {
            timePaint.getTextBounds(timeText, 0, timeText.length, this)
        }
        val timeYOffset = centerY + (timeTextBounds.height() / 2.0f ) + context.resources.getDimensionPixelSize(R.dimen.time_y_offset)

        val dateText = "May, 15"
        val dateTextHeight = Rect().apply {
            datePaint.getTextBounds(dateText, 0, dateText.length, this)
        }.height()
        val dateYOffset = timeYOffset + (timeTextBounds.height() / 2) - (dateTextHeight / 2.0f ) + context.resources.getDimensionPixelSize(R.dimen.space_between_time_and_date)

        val complicationsDrawingCache = buildComplicationDrawingCache(
            timeYOffset - timeTextBounds.height(),
            dateYOffset + dateTextHeight / 2,
        )

        val batteryBottomY = screenHeight - watchState.chinHeight - topAndBottomMargins

        return RegularDrawerDrawingState.CacheAvailable(
            context,
            batteryIconSize,
            batteryBottomY,
            batteryBottomY + context.dpToPx(1),
            dateTextHeight,
            dateYOffset,
            screenWidth,
            screenHeight,
            centerX,
            centerY,
            timeYOffset,
            complicationsDrawingCache,
        )
    }

    private fun RegularDrawerDrawingState.NoCacheAvailable.buildComplicationDrawingCache(
        topBottom: Float,
        bottomTop: Float,
    ): ComplicationsDrawingCache {
        val wearOsImage = wearOSLogo

        val currentWidgetsSize = storage.getWidgetsSize()
        val widgetsScaleFactor = fontDisplaySizeToScaleFactor(currentWidgetsSize, android12Layout = false)

        val sizeOfComplication = if(context.resources.configuration.isScreenRound) { ((screenWidth / 4.5) * widgetsScaleFactor).toInt() } else { (min(topBottom.toInt() - topAndBottomMargins - context.dpToPx(2), (screenWidth / 3.5).toInt()) * widgetsScaleFactor).toInt() }
        // If watch is round, align top widgets with the top of the time, otherwise center them in the top space
        val verticalOffset = if (context.resources.configuration.isScreenRound) { topBottom.toInt() - sizeOfComplication - context.resources.getDimensionPixelSize(R.dimen.space_between_time_and_top_widgets) } else { topAndBottomMargins + ((topBottom.toInt() - topAndBottomMargins) / 2) - (sizeOfComplication / 2) }
        val distanceBetweenComplications = context.dpToPx(3)

        val maxWidth = max(sizeOfComplication, wearOsImage.width)

        val leftBounds = Rect(
            (centerX - (maxWidth / 2) - distanceBetweenComplications - sizeOfComplication).toInt(),
            verticalOffset,
            (centerX - (maxWidth / 2)  - distanceBetweenComplications).toInt(),
            (verticalOffset + sizeOfComplication)
        )

        complicationsSlots.updateComplicationBounds(ComplicationLocation.LEFT, context.convertAbsoluteBoundsToScreenBounds(leftBounds))

        val middleBounds = Rect(
            (centerX - (sizeOfComplication / 2)).toInt(),
            verticalOffset,
            (centerX + (sizeOfComplication / 2)).toInt(),
            (verticalOffset + sizeOfComplication)
        )

        complicationsSlots.updateComplicationBounds(ComplicationLocation.MIDDLE, context.convertAbsoluteBoundsToScreenBounds(middleBounds))

        val rightBounds = Rect(
            (centerX + (maxWidth / 2) + distanceBetweenComplications).toInt(),
            verticalOffset,
            (centerX + (maxWidth / 2)  + distanceBetweenComplications + sizeOfComplication).toInt(),
            (verticalOffset + sizeOfComplication)
        )

        complicationsSlots.updateComplicationBounds(ComplicationLocation.RIGHT, context.convertAbsoluteBoundsToScreenBounds(rightBounds))

        val availableBottomSpace = screenHeight - bottomTop - watchState.chinHeight - topAndBottomMargins
        val bottomComplicationHeight = min(availableBottomSpace, context.dpToPx(36).toFloat())
        val bottomComplicationTop = if(context.resources.configuration.isScreenRound) { bottomTop.toInt() + context.dpToPx(5) } else { (bottomTop + + context.dpToPx(5) + availableBottomSpace - bottomComplicationHeight).toInt() }
        val bottomComplicationBottom = if(context.resources.configuration.isScreenRound) { (bottomTop + bottomComplicationHeight).toInt() } else { (bottomTop + availableBottomSpace).toInt() }
        val bottomComplicationLeft = computeComplicationLeft(bottomComplicationBottom, screenHeight)
        val bottomComplicationWidth = (screenWidth - 2* bottomComplicationLeft) * 0.9

        val bottomBounds = Rect(
            (centerX - (bottomComplicationWidth / 2)).toInt(),
            bottomComplicationTop,
            (centerX + (bottomComplicationWidth / 2)).toInt(),
            bottomComplicationBottom
        )

        complicationsSlots.updateComplicationBounds(ComplicationLocation.BOTTOM, context.convertAbsoluteBoundsToScreenBounds(bottomBounds))

        val iconXOffset = centerX - (wearOsImage.width / 2.0f)
        val iconYOffset = leftBounds.top + (leftBounds.height() / 2) - (wearOsImage.height / 2)

        return ComplicationsDrawingCache(
            iconXOffset,
            iconYOffset.toFloat(),
            notificationsRect = Rect(
                if (context.resources.configuration.isScreenRound) { (screenWidth / 7f).toInt() } else { context.dpToPx(15) },
                bottomTop.toInt(),
                if (context.resources.configuration.isScreenRound) { screenWidth - (screenWidth / 7f).toInt() } else { screenWidth - context.dpToPx(15) },
                bottomTop.toInt() + availableBottomSpace.toInt() - batteryIconSize - context.dpToPx(2),
            ),
        )
    }

    private fun computeComplicationLeft(bottomY: Int, screenHeight: Int): Int {
        return if(context.resources.configuration.isScreenRound) {
            screenHeight / 2 - sqrt((screenHeight / 2).toDouble().pow(2) - ((bottomY - (screenHeight / 2)).toDouble().pow(2))).toInt()
        } else {
            context.dpToPx(10)
        }
    }

    private fun RegularDrawerDrawingState.CacheAvailable.draw(
        canvas: Canvas,
        date: ZonedDateTime,
        ambient:Boolean,
        isUserPremium: Boolean,
        drawSecondsRing: Boolean,
        drawDate: Boolean,
        weatherComplicationData: ComplicationData?,
        watchBatteryValue: Int?,
        phoneBatteryValue: String?,
        notificationsState: PhoneNotifications.NotificationState?,
    ) {
        val timeText = if( storage.getUse24hTimeFormat()) {
            timeFormatter24H.format(date)
        } else {
            timeFormatter12H.format(date)
        }
        val timeXOffset = centerX - (timePaint.measureText(timeText) / 2f)
        canvas.drawText(timeText, timeXOffset, timeYOffset, timePaint)

        if( storage.showWearOSLogo() && (!ambient || storage.getShowWearOSLogoInAmbient()) ) {
            val wearOsImage = if( ambient ) { wearOSLogoAmbient } else { wearOSLogo }
            canvas.drawBitmap(wearOsImage, complicationsDrawingCache.iconXOffset, complicationsDrawingCache.iconYOffset, wearOSLogoPaint)
        }

        if( drawDate ) {
            drawDateAndWeather(
                canvas,
                weatherComplicationData,
                storage.getUseShortDateFormat(),
                isUserPremium,
                date,
                datePaint,
                spaceBeforeWeather,
                weatherIconPaint,
            )
        }

        if( drawSecondsRing && !ambient ) {
            drawSecondRing(canvas, date, secondsRingPaint)
        }

        if( isUserPremium && (watchBatteryValue != null || phoneBatteryValue != null) && (!ambient || !storage.hideBatteryInAmbient()) ) {
            drawBattery(
                canvas,
                batteryLevelPaint,
                batteryIconPaint,
                distanceBetweenPhoneAndWatchBattery,
                date,
                watchBatteryValue,
                phoneBatteryValue,
            )
        }

        if (isUserPremium && notificationsState != null && (!ambient || storage.getShowNotificationsInAmbient())) {
            drawNotifications(canvas, notificationsPaint, notificationsState)
        }
    }

    private fun RegularDrawerDrawingState.CacheAvailable.buildCache(): RegularDrawerDrawingState.CacheAvailable {
        return RegularDrawerDrawingState.NoCacheAvailable(screenWidth, screenHeight, centerX, centerY).buildCache()
    }

    private fun setScaledSizes(timeSize: Int, dateAndBatterySize: Int) {
        val timeScaleFactor = fontDisplaySizeToScaleFactor(timeSize, android12Layout = false)
        val dateAndBatteryScaleFactor = fontDisplaySizeToScaleFactor(dateAndBatterySize, android12Layout = false)

        timePaint.textSize = context.resources.getDimension(
            if(context.resources.configuration.isScreenRound) {
                R.dimen.time_text_size_round
            } else {
                R.dimen.time_text_size
            }
        ) * timeScaleFactor

        val dateSize = context.resources.getDimension(
            if(context.resources.configuration.isScreenRound) {
                R.dimen.date_text_size_round
            } else {
                R.dimen.date_text_size
            }
        ) * dateAndBatteryScaleFactor

        datePaint.textSize = dateSize
        batteryLevelPaint.textSize = context.resources.getDimension(R.dimen.battery_level_size) * dateAndBatteryScaleFactor
        batteryIconSize = (context.resources.getDimension(R.dimen.battery_icon_size) * dateAndBatteryScaleFactor).toInt()
    }

    companion object {
        private const val TAG = "RegularRenderer"
    }
}
