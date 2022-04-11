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
package com.benoitletondor.pixelminimalwatchface.drawer.digital.android12

import android.content.Context
import android.graphics.*
import android.support.wearable.complications.ComplicationData
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.wear.watchface.TapEvent
import androidx.wear.watchface.WatchState
import com.benoitletondor.pixelminimalwatchface.ComplicationsSlots
import com.benoitletondor.pixelminimalwatchface.R
import com.benoitletondor.pixelminimalwatchface.drawer.WatchFaceDrawer
import com.benoitletondor.pixelminimalwatchface.helper.*
import com.benoitletondor.pixelminimalwatchface.model.ComplicationLocation
import com.benoitletondor.pixelminimalwatchface.model.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.min

class Android12DigitalWatchFaceDrawer(
    private val context: Context,
    private val storage: Storage,
    private val watchState: WatchState,
    private val complicationsSlots: ComplicationsSlots,
    private val invalidateRenderer: () -> Unit,
) : WatchFaceDrawer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var drawingState: Android12DrawingState =  kotlin.run {
        val screenSize = context.getScreenSize()

        Android12DrawingState.NoCacheAvailable(
            screenSize.width,
            screenSize.height,
            screenSize.width / 2f,
            screenSize.height / 2f,
        )
    }

    private val hourFormatter24H = DateTimeFormatter.ofPattern("HH", Locale.getDefault())
    private val hourFormatter12H = DateTimeFormatter.ofPattern("hh", Locale.getDefault())
    private val minFormatter = DateTimeFormatter.ofPattern("mm", Locale.getDefault())

    private val productSansRegularFont: Typeface = ResourcesCompat.getFont(context, R.font.product_sans_regular)!!
    private val productSansThinFont: Typeface = ResourcesCompat.getFont(context, R.font.product_sans_thin)!!

    private val wearOSLogoPaint = Paint()
    private val timePaint = Paint().apply {
        typeface = productSansRegularFont
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
    private val distanceBetweenPhoneAndWatchBattery: Int = context.dpToPx(3)
    private val distanceBetweenHourAndMin: Int = context.dpToPx(4)
    private var chinSize: Int = 0
    private var isRound: Boolean = false
    private val weatherAndBatteryIconColorFilterDimmed: ColorFilter = PorterDuffColorFilter(dateAndBatteryColorDimmed, PorterDuff.Mode.SRC_IN)
    private val timeOffsetX = context.dpToPx(-2)
    private val timeCharPaddingX = context.dpToPx(1)
    private var timePaddingY = 0
    private val topAndBottomMargins = context.getTopAndBottomMargins()
    private val verticalPaddingBetweenElements = context.dpToPx(7)

    init {
        watchSizesChanges()
        watchShowBatteryIndicatorsChanges()
        watchShowWearOSLogoChanges()
    }

    override fun onDestroy() {
        scope.cancel()
    }

    override fun getActiveComplicationLocations(): Set<ComplicationLocation> = setOf(
        ComplicationLocation.ANDROID_12_TOP_LEFT,
        ComplicationLocation.ANDROID_12_TOP_RIGHT,
        ComplicationLocation.ANDROID_12_BOTTOM_LEFT,
        ComplicationLocation.ANDROID_12_BOTTOM_RIGHT,
    )

    override fun isTapOnWeather(tapEvent: TapEvent): Boolean {
        val drawingState = drawingState
        if( !storage.showWeather() ||
            !storage.isUserPremium() ||
            drawingState !is Android12DrawingState.CacheAvailable ) {
            return false
        }

        val displayRect = drawingState.getWeatherDisplayRect() ?: return false
        return displayRect.contains(tapEvent.xPos, tapEvent.yPos)
    }

    override fun isTapOnCenterOfScreen(tapEvent: TapEvent): Boolean {
        val drawingState = drawingState as? Android12DrawingState.CacheAvailable ?: return false

        val centerRect = Rect(
            (drawingState.screenWidth * 0.33f).toInt(),
            (drawingState.screenHeight * 0.25f).toInt(),
            (drawingState.screenWidth * 0.66f).toInt(),
            (drawingState.screenHeight * 0.75f).toInt()
        )

        return centerRect.contains(tapEvent.xPos, tapEvent.yPos)
    }

    override fun isTapOnBattery(tapEvent: TapEvent): Boolean {
        val drawingState = drawingState as? Android12DrawingState.CacheAvailable ?: return false

        return drawingState.tapIsOnBattery(tapEvent.xPos, tapEvent.yPos)
    }

    override fun draw(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        weatherComplicationData: ComplicationData?,
        phoneBatteryValue: String?,
        watchBatteryValue: Int?
    ) {
        val ambient = watchState.isAmbient.value == true

        setPaintVariables(ambient, watchState.hasLowBitAmbient)
        canvas.drawColor(backgroundColor)

        val currentDrawingState = drawingState
        if( currentDrawingState is Android12DrawingState.NoCacheAvailable ) {
            drawingState = currentDrawingState.buildCache()
        }

        val drawingState = drawingState
        if( drawingState is Android12DrawingState.CacheAvailable ){
            drawingState.draw(
                canvas,
                zonedDateTime,
                ambient,
                storage.isUserPremium(),
                storage.showSecondsRing(),
                !ambient || storage.getShowDateInAmbient(),
                weatherComplicationData,
                watchBatteryValue,
                phoneBatteryValue,
            )
        }
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

    private fun watchShowBatteryIndicatorsChanges() {
        scope.launch {
            combine(
                storage.watchShowWatchBattery(),
                storage.watchShowPhoneBattery(),
            ) { _, _ ->
                true // We don't care about the value here
            }
                .drop(1) // Ignore first value
                .collect {
                    recomputeDrawingStateAndReRender()
                }
        }
    }

    private fun watchShowWearOSLogoChanges() {
        scope.launch {
            storage.watchShowWearOSLogo()
                .drop(1) // Ignore first value
                .collect {
                    recomputeDrawingStateAndReRender()
                }
        }
    }

    private fun recomputeDrawingStateAndReRender() {
        drawingState = when(val currentDrawingState = drawingState) {
            is Android12DrawingState.CacheAvailable -> currentDrawingState.buildCache()
            is Android12DrawingState.NoCacheAvailable -> currentDrawingState.buildCache()
        }
        invalidateRenderer()
    }

    private fun setPaintVariables(
        ambient:Boolean,
        lowBitAmbient: Boolean,
    ) {
        wearOSLogoPaint.isAntiAlias = !ambient

        val shouldUseThinFont = (ambient && !storage.useNormalTimeStyleInAmbientMode()) || (!ambient && storage.useThinTimeStyleInRegularMode())
        timePaint.apply {
            isAntiAlias = !(ambient && lowBitAmbient)
            color = if( ambient ) { timeColorDimmed } else { storage.getTimeAndDateColor() }
            typeface = if( shouldUseThinFont ) { productSansThinFont } else { productSansRegularFont }
        }

        datePaint.apply {
            isAntiAlias = !(ambient && lowBitAmbient)
            color = if( ambient ) { dateAndBatteryColorDimmed } else { storage.getTimeAndDateColor() }
        }

        weatherIconPaint.apply {
            isAntiAlias = !ambient
            colorFilter = if( ambient ) { weatherAndBatteryIconColorFilterDimmed } else { storage.getTimeAndDateColorFilter() }
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

    private fun Android12DrawingState.NoCacheAvailable.buildCache(): Android12DrawingState.CacheAvailable {
        timePaddingY = if (storage.showWearOSLogo()) {
            context.dpToPx(-5)
        } else {
            0
        }

        val timeSize = storage.getTimeSize()
        val dateAndBatterySize = storage.getDateAndBatterySize()
        setScaledSizes(timeSize, dateAndBatterySize)

        val timeText = "0"
        val timeTextBounds = Rect().apply {
            timePaint.getTextBounds(timeText, 0, timeText.length, this)
        }
        val timeHeight = timeTextBounds.height()
        val timeWidth = timeTextBounds.width() * 2 + timeCharPaddingX * 2

        val timeX = centerX - timeWidth / 2f + timeOffsetX

        val dateText = "May, 15"
        val dateTextHeight = Rect().apply {
            datePaint.getTextBounds(dateText, 0, dateText.length, this)
        }.height()
        val timeTopY = (centerY - timeHeight - distanceBetweenHourAndMin + timePaddingY)
        val timeBottomY = (centerY + timeHeight + distanceBetweenHourAndMin + timePaddingY)
        val dateBottomY = timeTopY - verticalPaddingBetweenElements

        val batteryHeight = Rect().apply {
            batteryLevelPaint.getTextBounds("22%", 0, 3, this)
        }.height()
        val batteryBottomY = if (storage.showWearOSLogo()) {
            screenHeight - chinSize - topAndBottomMargins.toInt()
        } else {
            (timeBottomY + batteryHeight + context.dpToPx(1) + verticalPaddingBetweenElements).toInt()
        }

        val batteryTopY = batteryBottomY - batteryHeight

        val complicationsDrawingCache = buildComplicationDrawingCache(
            timeX = timeX,
            timeHeight = timeHeight,
            timeBottomY = timeBottomY,
            batteryTopY = if(storage.showWatchBattery() || storage.showPhoneBattery()) { batteryTopY } else { batteryBottomY },
        )

        return Android12DrawingState.CacheAvailable(
            context,
            batteryIconSize,
            batteryBottomY,
            batteryBottomY + context.dpToPx(1),
            dateTextHeight,
            dateBottomY,
            screenWidth,
            screenHeight,
            centerX,
            centerY,
            timeHeight,
            timeX,
            timeTextBounds.width() + timeCharPaddingX,
            complicationsDrawingCache,
        )
    }

    private fun Android12DrawingState.CacheAvailable.buildCache(): Android12DrawingState.CacheAvailable {
        return Android12DrawingState.NoCacheAvailable(screenWidth, screenHeight, centerX, centerY).buildCache()
    }

    private fun Android12DrawingState.NoCacheAvailable.buildComplicationDrawingCache(
        timeX: Float,
        timeHeight: Int,
        timeBottomY: Float,
        batteryTopY: Int,
    ): ComplicationsDrawingCache {
        val wearOsImage = ContextCompat.getDrawable(context, R.drawable.ic_wear_os_logo)!!.toBitmap()

        val currentWidgetsSize = storage.getWidgetsSize()
        val widgetsScaleFactor = fontDisplaySizeToScaleFactor(currentWidgetsSize, android12Layout = true)

        val complicationSize = (((screenWidth - timeX) * 0.35f) * widgetsScaleFactor)
        val wearOSLogoWidth = wearOsImage.width.toFloat()
        val wearOSLogoHeight = wearOsImage.height.toFloat()

        val leftX = (timeX / (if (isRound) 1.7f else 2f) - complicationSize / 2f)
        val rightX = (screenWidth - timeX / (if (isRound) 1.7f else 2f) - complicationSize / 2f)
        val topY = (centerY - distanceBetweenHourAndMin - timeHeight / 2f + complicationSize / 2f + context.dpToPx(3)) + timePaddingY
        val bottomY = (centerY + distanceBetweenHourAndMin + timeHeight / 2f - complicationSize / 2f + context.dpToPx(3)) + timePaddingY

        complicationsSlots.updateComplicationBounds(
            ComplicationLocation.ANDROID_12_TOP_LEFT,
            RectF(leftX, topY - complicationSize, leftX + complicationSize, topY),
        )

        complicationsSlots.updateComplicationBounds(
            ComplicationLocation.ANDROID_12_TOP_RIGHT,
            RectF(rightX, topY - complicationSize, rightX + complicationSize, topY),
        )

        complicationsSlots.updateComplicationBounds(
            ComplicationLocation.ANDROID_12_BOTTOM_LEFT,
            RectF(leftX, bottomY, leftX + complicationSize, bottomY + complicationSize),
        )

        complicationsSlots.updateComplicationBounds(
            ComplicationLocation.ANDROID_12_BOTTOM_RIGHT,
            RectF(rightX, bottomY, rightX + complicationSize, bottomY + complicationSize),
        )

        val paddingBetweenBottomLogoAndBattery = if(storage.showWatchBattery() || storage.showPhoneBattery()) {
            verticalPaddingBetweenElements
        } else {
            0
        }

        val targetWearOSLogoHeight = min(wearOSLogoHeight, (batteryTopY - paddingBetweenBottomLogoAndBattery) - (timeBottomY + verticalPaddingBetweenElements))
        val targetWearOSLogoWidth = if( targetWearOSLogoHeight < wearOSLogoHeight ) {
            wearOSLogoWidth * (targetWearOSLogoHeight / wearOSLogoHeight)
        } else {
            wearOSLogoWidth
        }

        val wearOSLogo: Bitmap = ContextCompat.getDrawable(context, R.drawable.ic_wear_os_logo)!!.toBitmap(targetWearOSLogoWidth.toInt(), targetWearOSLogoHeight.toInt())
        val wearOSLogoAmbient: Bitmap = ContextCompat.getDrawable(context, R.drawable.ic_wear_os_logo_ambient)!!.toBitmap(targetWearOSLogoWidth.toInt(), targetWearOSLogoHeight.toInt())

        return ComplicationsDrawingCache(
            wearOSLogo = wearOSLogo,
            wearOSLogoAmbient = wearOSLogoAmbient,
            wearOSLogoRect = Rect(
                (centerX - targetWearOSLogoWidth / 2f).toInt(),
                (timeBottomY + verticalPaddingBetweenElements).toInt(),
                (centerX + targetWearOSLogoWidth / 2f).toInt(),
                (timeBottomY + verticalPaddingBetweenElements + targetWearOSLogoHeight).toInt(),
            )
        )
    }

    private fun setScaledSizes(timeSize: Int, dateAndBatterySize: Int) {
        val scaleFactor = fontDisplaySizeToScaleFactor(timeSize, android12Layout = true)
        val dateAndBatteryScaleFactor = fontDisplaySizeToScaleFactor(dateAndBatterySize, android12Layout = true)

        timePaint.textSize = context.resources.getDimension(R.dimen.android_12_time_text_size) * scaleFactor
        val dateSize = context.resources.getDimension(R.dimen.android_12_date_text_size) * dateAndBatteryScaleFactor
        datePaint.textSize = dateSize
        batteryLevelPaint.textSize = context.resources.getDimension(R.dimen.android_12_battery_level_size) * dateAndBatteryScaleFactor
        batteryIconSize = (context.resources.getDimension(R.dimen.android_12_battery_icon_size) * dateAndBatteryScaleFactor).toInt()
    }

    private fun Android12DrawingState.CacheAvailable.draw(
        canvas: Canvas,
        date: ZonedDateTime,
        ambient:Boolean,
        isUserPremium: Boolean,
        drawSecondsRing: Boolean,
        drawDate: Boolean,
        weatherComplicationData: ComplicationData?,
        watchBatteryValue: Int?,
        phoneBatteryValue: String?,
    ) {
        val hourText = if( storage.getUse24hTimeFormat()) {
            hourFormatter24H.format(date)
        } else {
            hourFormatter12H.format(date)
        }
        val minText = minFormatter.format(date)

        val hourFirstChar = hourText.substring(0, 1)
        val hourSecondChar = hourText.substring(1, 2)
        val minFirstChar = minText.substring(0, 1)
        val minSecondChar = minText.substring(1, 2)

        val hourFirstCharWidth = Rect().apply {
            timePaint.getTextBounds(hourFirstChar, 0, 1, this)
        }.width()

        val hourSecondCharWidth = Rect().apply {
            timePaint.getTextBounds(hourSecondChar, 0, 1, this)
        }.width()

        val minFirstCharWidth = Rect().apply {
            timePaint.getTextBounds(minFirstChar, 0, 1, this)
        }.width()

        val minSecondCharWidth = Rect().apply {
            timePaint.getTextBounds(minSecondChar, 0, 1, this)
        }.width()

        canvas.drawText(hourFirstChar, timeX + (timeCharWidth - hourFirstCharWidth) / 2.5f, centerY - distanceBetweenHourAndMin + timePaddingY, timePaint)
        canvas.drawText(hourSecondChar, timeX + timeCharWidth + (timeCharWidth - hourSecondCharWidth) / 2.5f, centerY - distanceBetweenHourAndMin + timePaddingY, timePaint)
        canvas.drawText(minFirstChar, timeX + (timeCharWidth - minFirstCharWidth) / 2.5f, centerY + timeHeight + distanceBetweenHourAndMin + timePaddingY, timePaint)
        canvas.drawText(minSecondChar, timeX + timeCharWidth + (timeCharWidth - minSecondCharWidth) / 2.5f, centerY + timeHeight + distanceBetweenHourAndMin + timePaddingY, timePaint)

        if( drawDate ) {
            drawDateAndWeather(
                canvas,
                weatherComplicationData,
                storage.getUseShortDateFormat(),
                isUserPremium,
                date,
                datePaint,
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
    }
}

