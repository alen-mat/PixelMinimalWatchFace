package com.benoitletondor.pixelminimalwatchface.helper

import android.content.Context
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.wear.watchface.CanvasComplicationFactory
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import com.benoitletondor.pixelminimalwatchface.PixelMinimalWatchFace
import com.benoitletondor.pixelminimalwatchface.R
import com.benoitletondor.pixelminimalwatchface.model.ComplicationLocation
import com.benoitletondor.pixelminimalwatchface.model.Storage
import com.benoitletondor.pixelminimalwatchface.model.getPrimaryColorForComplicationId

private const val LEFT_AND_RIGHT_COMPLICATIONS_TOP_BOUND = 0.15f
private const val LEFT_AND_RIGHT_COMPLICATIONS_BOTTOM_BOUND = 0.4f

private const val LEFT_COMPLICATION_LEFT_BOUND = 0.15f
private const val LEFT_COMPLICATION_RIGHT_BOUND = 0.35f

private const val MIDDLE_COMPLICATION_LEFT_BOUND = 0.40f
private const val MIDDLE_COMPLICATION_RIGHT_BOUND = 0.60f

private const val RIGHT_COMPLICATION_LEFT_BOUND = 0.65f
private const val RIGHT_COMPLICATION_RIGHT_BOUND = 0.85f

fun createComplicationsSlots(context: Context, storage: Storage): List<ComplicationSlot> {
    if (storage.useAndroid12Style()) {
        return emptyList() // TODO
    } else {
        val leftComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = PixelMinimalWatchFace.LEFT_COMPLICATION_ID,
            canvasComplicationFactory = buildCanvasComplicationFactory(
                buildComplicationDrawable(context, storage, PixelMinimalWatchFace.LEFT_COMPLICATION_ID)
            ),
            supportedTypes = PixelMinimalWatchFace.getSupportedComplicationTypes(ComplicationLocation.LEFT),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(),
            bounds = buildComplicationBounds(context, PixelMinimalWatchFace.LEFT_COMPLICATION_ID),
        ).build()

        val middleComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = PixelMinimalWatchFace.MIDDLE_COMPLICATION_ID,
            canvasComplicationFactory = buildCanvasComplicationFactory(
                buildComplicationDrawable(context, storage, PixelMinimalWatchFace.MIDDLE_COMPLICATION_ID)
            ),
            supportedTypes = PixelMinimalWatchFace.getSupportedComplicationTypes(ComplicationLocation.MIDDLE),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(),
            bounds = buildComplicationBounds(context, PixelMinimalWatchFace.MIDDLE_COMPLICATION_ID),
        ).build()

        val rightComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = PixelMinimalWatchFace.RIGHT_COMPLICATION_ID,
            canvasComplicationFactory = buildCanvasComplicationFactory(
                buildComplicationDrawable(context, storage, PixelMinimalWatchFace.RIGHT_COMPLICATION_ID)
            ),
            supportedTypes = PixelMinimalWatchFace.getSupportedComplicationTypes(ComplicationLocation.RIGHT),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(),
            bounds = buildComplicationBounds(context, PixelMinimalWatchFace.RIGHT_COMPLICATION_ID),
        ).build()

        return listOf(
            leftComplication,
            middleComplication,
            rightComplication,
        )
    }
}

private fun buildCanvasComplicationFactory(complicationDrawable: ComplicationDrawable): CanvasComplicationFactory {
    return CanvasComplicationFactory { watchState, listener ->
        CanvasComplicationDrawable(
            complicationDrawable,
            watchState,
            listener
        )
    }
}

private fun buildComplicationDrawable(
    context: Context,
    storage: Storage,
    complicationId: Int,
): ComplicationDrawable {
    // TODO caching per ID
    val drawable = ComplicationDrawable()

    val colors = storage.getComplicationColors()
    val primaryComplicationColor = colors.getPrimaryColorForComplicationId(complicationId)

    // TODO caching
    val titleSize = context.resources.getDimensionPixelSize(R.dimen.complication_title_size)
    val complicationTitleColor = ContextCompat.getColor(context, R.color.complication_title_color)
    val dateAndBatteryColorDimmed = ContextCompat.getColor(context, R.color.face_date_dimmed)
    val productSansRegularFont = ResourcesCompat.getFont(context, R.font.product_sans_regular)!!
    val transparentColor = ContextCompat.getColor(context, R.color.transparent)

    drawable.activeStyle.titleSize = titleSize
    drawable.ambientStyle.titleSize = titleSize
    drawable.activeStyle.titleColor = complicationTitleColor
    drawable.ambientStyle.titleColor = complicationTitleColor
    drawable.activeStyle.iconColor = primaryComplicationColor
    drawable.ambientStyle.iconColor = dateAndBatteryColorDimmed
    drawable.activeStyle.setTextTypeface(productSansRegularFont)
    drawable.ambientStyle.setTextTypeface(productSansRegularFont)
    drawable.activeStyle.setTitleTypeface(productSansRegularFont)
    drawable.ambientStyle.setTitleTypeface(productSansRegularFont)
    drawable.activeStyle.borderColor = transparentColor
    drawable.ambientStyle.borderColor = transparentColor

    return drawable
}

private fun buildComplicationBounds(context: Context, complicationId: Int): ComplicationSlotBounds {
    val screenWidth = context.resources.displayMetrics.widthPixels
    val screenHeight = context.resources.displayMetrics.heightPixels

    // TODO dynamic
    return when(complicationId) {
        PixelMinimalWatchFace.LEFT_COMPLICATION_ID -> ComplicationSlotBounds(
            RectF(
            LEFT_COMPLICATION_LEFT_BOUND,
            LEFT_AND_RIGHT_COMPLICATIONS_TOP_BOUND,
            LEFT_COMPLICATION_RIGHT_BOUND,
            LEFT_AND_RIGHT_COMPLICATIONS_BOTTOM_BOUND,
            )
        )
        PixelMinimalWatchFace.MIDDLE_COMPLICATION_ID -> ComplicationSlotBounds(
            RectF(
                MIDDLE_COMPLICATION_LEFT_BOUND,
                LEFT_AND_RIGHT_COMPLICATIONS_TOP_BOUND,
                MIDDLE_COMPLICATION_RIGHT_BOUND,
                LEFT_AND_RIGHT_COMPLICATIONS_BOTTOM_BOUND,
            )
        )
        PixelMinimalWatchFace.RIGHT_COMPLICATION_ID -> ComplicationSlotBounds(
            RectF(
                RIGHT_COMPLICATION_LEFT_BOUND,
                LEFT_AND_RIGHT_COMPLICATIONS_TOP_BOUND,
                RIGHT_COMPLICATION_RIGHT_BOUND,
                LEFT_AND_RIGHT_COMPLICATIONS_BOTTOM_BOUND,
            )
        )
        else -> throw IllegalArgumentException("Unknown complicationId: $complicationId")
    }
}