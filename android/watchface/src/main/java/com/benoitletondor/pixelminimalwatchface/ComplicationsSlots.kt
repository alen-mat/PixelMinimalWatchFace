package com.benoitletondor.pixelminimalwatchface

import android.content.Context
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.wear.watchface.CanvasComplicationFactory
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.WatchFaceLayer
import com.benoitletondor.pixelminimalwatchface.model.ComplicationLocation
import com.benoitletondor.pixelminimalwatchface.model.Storage
import com.benoitletondor.pixelminimalwatchface.model.getPrimaryColorForComplicationId

class ComplicationsSlots(
    private val context: Context,
    private val storage: Storage,
    private val currentUserStyleRepository: CurrentUserStyleRepository,
) {
    private val titleSize = context.resources.getDimensionPixelSize(R.dimen.complication_title_size)
    private val complicationTitleColor = ContextCompat.getColor(context, R.color.complication_title_color)
    private val dateAndBatteryColorDimmed = ContextCompat.getColor(context, R.color.face_date_dimmed)
    private val productSansRegularFont = ResourcesCompat.getFont(context, R.font.product_sans_regular)!!
    private val transparentColor = ContextCompat.getColor(context, R.color.transparent)

    private val leftComplicationDrawable = ComplicationDrawable()
    private var leftComplicationOption = UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        complicationSlotId = LEFT_COMPLICATION_ID,
    )

    private val middleComplicationDrawable = ComplicationDrawable()
    private var middleComplicationOption = UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        complicationSlotId = MIDDLE_COMPLICATION_ID,
    )

    private val rightComplicationDrawable = ComplicationDrawable()
    private var rightComplicationOption = UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay(
        complicationSlotId = RIGHT_COMPLICATION_ID,
    )

    fun createComplicationsSlots(): List<ComplicationSlot> {
        // TODO other slots

        val leftComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = LEFT_COMPLICATION_ID,
            canvasComplicationFactory = buildCanvasComplicationFactory(
                ComplicationLocation.LEFT.getComplicationDrawable(),
            ),
            supportedTypes = getSupportedComplicationTypes(ComplicationLocation.LEFT),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(),
            bounds = ComplicationLocation.LEFT.buildDefaultComplicationBounds(),
        ).build()

        val middleComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = MIDDLE_COMPLICATION_ID,
            canvasComplicationFactory = buildCanvasComplicationFactory(
                ComplicationLocation.MIDDLE.getComplicationDrawable()
            ),
            supportedTypes = getSupportedComplicationTypes(ComplicationLocation.MIDDLE),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(),
            bounds = ComplicationLocation.MIDDLE.buildDefaultComplicationBounds(),
        ).build()

        val rightComplication = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = RIGHT_COMPLICATION_ID,
            canvasComplicationFactory = buildCanvasComplicationFactory(
                ComplicationLocation.RIGHT.getComplicationDrawable()
            ),
            supportedTypes = getSupportedComplicationTypes(ComplicationLocation.RIGHT),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(),
            bounds = ComplicationLocation.RIGHT.buildDefaultComplicationBounds(),
        ).build()

        return listOf(
            leftComplication,
            middleComplication,
            rightComplication,
        )
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

    fun updateComplicationDrawableStyles() {
        for(complicationLocation in ComplicationLocation.values()) {
            val drawable = complicationLocation.getComplicationDrawable()

            val colors = storage.getComplicationColors()
            val primaryComplicationColor = colors.getPrimaryColorForComplicationId(complicationLocation.getComplicationId())

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
        }
    }

    fun updateComplicationBounds(complicationLocation: ComplicationLocation, bounds: RectF) {
        complicationLocation.editComplicationOptions {
            it.setComplicationSlotBounds(ComplicationSlotBounds(bounds))
        }

        updateComplicationSetting()
    }

    fun setActiveComplicationLocations(activeLocations: Set<ComplicationLocation>) {
        for(location in ComplicationLocation.values()) {
            location.editComplicationOptions {
                it.setEnabled(location in activeLocations)
            }
        }

        updateComplicationSetting()
    }

    private fun updateComplicationSetting() {
        currentUserStyleRepository.updateUserStyle(
            currentUserStyleRepository.userStyle.value
                .toMutableUserStyle().apply {
                    set(
                        UserStyleSetting.Id(COMPLICATIONS_SETTING_ID),
                        UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotsOption(
                            id = UserStyleSetting.Option.Id(COMPLICATIONS_SLOT_OPTION_ID),
                            displayName = "",
                            icon = null,
                            complicationSlotOverlays = listOf(
                                leftComplicationOption,
                                middleComplicationOption,
                                rightComplicationOption,
                            ),
                        ),
                    )
                }
                .toUserStyle()
        )
    }

    private fun ComplicationLocation.buildDefaultComplicationBounds(): ComplicationSlotBounds = when(this) {
        ComplicationLocation.LEFT -> ComplicationSlotBounds(
            RectF(
                LEFT_COMPLICATION_LEFT_BOUND,
                LEFT_AND_RIGHT_COMPLICATIONS_TOP_BOUND,
                LEFT_COMPLICATION_RIGHT_BOUND,
                LEFT_AND_RIGHT_COMPLICATIONS_BOTTOM_BOUND,
            )
        )
        ComplicationLocation.MIDDLE -> ComplicationSlotBounds(
            RectF(
                MIDDLE_COMPLICATION_LEFT_BOUND,
                LEFT_AND_RIGHT_COMPLICATIONS_TOP_BOUND,
                MIDDLE_COMPLICATION_RIGHT_BOUND,
                LEFT_AND_RIGHT_COMPLICATIONS_BOTTOM_BOUND,
            )
        )
        ComplicationLocation.RIGHT -> ComplicationSlotBounds(
            RectF(
                RIGHT_COMPLICATION_LEFT_BOUND,
                LEFT_AND_RIGHT_COMPLICATIONS_TOP_BOUND,
                RIGHT_COMPLICATION_RIGHT_BOUND,
                LEFT_AND_RIGHT_COMPLICATIONS_BOTTOM_BOUND,
            )
        )
        ComplicationLocation.BOTTOM -> TODO()
        ComplicationLocation.ANDROID_12_TOP_LEFT -> TODO()
        ComplicationLocation.ANDROID_12_TOP_RIGHT -> TODO()
        ComplicationLocation.ANDROID_12_BOTTOM_LEFT -> TODO()
        ComplicationLocation.ANDROID_12_BOTTOM_RIGHT -> TODO()
    }

    private fun ComplicationLocation.getComplicationDrawable(): ComplicationDrawable = when(this) {
        ComplicationLocation.LEFT -> leftComplicationDrawable
        ComplicationLocation.MIDDLE -> middleComplicationDrawable
        ComplicationLocation.RIGHT -> rightComplicationDrawable
        ComplicationLocation.BOTTOM -> TODO()
        ComplicationLocation.ANDROID_12_TOP_LEFT -> TODO()
        ComplicationLocation.ANDROID_12_TOP_RIGHT -> TODO()
        ComplicationLocation.ANDROID_12_BOTTOM_LEFT -> TODO()
        ComplicationLocation.ANDROID_12_BOTTOM_RIGHT -> TODO()
    }

    private fun ComplicationLocation.editComplicationOptions(action: (UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay.Builder) -> Unit) {
        val option = when(this) {
            ComplicationLocation.LEFT -> leftComplicationOption
            ComplicationLocation.MIDDLE -> middleComplicationOption
            ComplicationLocation.RIGHT -> rightComplicationOption
            ComplicationLocation.BOTTOM -> TODO()
            ComplicationLocation.ANDROID_12_TOP_LEFT -> TODO()
            ComplicationLocation.ANDROID_12_TOP_RIGHT -> TODO()
            ComplicationLocation.ANDROID_12_BOTTOM_LEFT -> TODO()
            ComplicationLocation.ANDROID_12_BOTTOM_RIGHT -> TODO()
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
            ComplicationLocation.BOTTOM -> TODO()
            ComplicationLocation.ANDROID_12_TOP_LEFT -> TODO()
            ComplicationLocation.ANDROID_12_TOP_RIGHT -> TODO()
            ComplicationLocation.ANDROID_12_BOTTOM_LEFT -> TODO()
            ComplicationLocation.ANDROID_12_BOTTOM_RIGHT -> TODO()
        }
    }

    companion object {
        private const val COMPLICATIONS_SETTING_ID = "complications:setting"
        private const val COMPLICATIONS_SLOT_OPTION_ID = "complications:slotOption"

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

        private const val LEFT_AND_RIGHT_COMPLICATIONS_TOP_BOUND = 0.15f
        private const val LEFT_AND_RIGHT_COMPLICATIONS_BOTTOM_BOUND = 0.4f

        private const val LEFT_COMPLICATION_LEFT_BOUND = 0.15f
        private const val LEFT_COMPLICATION_RIGHT_BOUND = 0.35f

        private const val MIDDLE_COMPLICATION_LEFT_BOUND = 0.40f
        private const val MIDDLE_COMPLICATION_RIGHT_BOUND = 0.60f

        private const val RIGHT_COMPLICATION_LEFT_BOUND = 0.65f
        private const val RIGHT_COMPLICATION_RIGHT_BOUND = 0.85f

        val COMPLICATION_IDS = intArrayOf(
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
                    id = UserStyleSetting.Option.Id(COMPLICATIONS_SLOT_OPTION_ID),
                    displayName = "",
                    icon = null,
                    complicationSlotOverlays = emptyList(),
                )
            )
        )
    }
}

