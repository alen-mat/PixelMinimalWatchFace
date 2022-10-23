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
package com.benoitletondor.pixelminimalwatchface.model

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.ColorInt

data class ComplicationColors(
    val leftColor: ComplicationColor,
    val middleColor: ComplicationColor,
    val rightColor: ComplicationColor,
    val bottomColor: ComplicationColor,
    val android12TopLeftColor: ComplicationColor,
    val android12TopRightColor: ComplicationColor,
    val android12BottomLeftColor: ComplicationColor,
    val android12BottomRightColor: ComplicationColor,
)

data class ComplicationColorCategory(
    val label: String,
    val colors: List<ComplicationColor>,
)

data class ComplicationColor(
    @ColorInt val color: Int,
    val label: String,
    val isDefault: Boolean
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readString()!!,
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(color)
        parcel.writeString(label)
        parcel.writeByte(if (isDefault) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ComplicationColor> {
        override fun createFromParcel(parcel: Parcel): ComplicationColor {
            return ComplicationColor(parcel)
        }

        override fun newArray(size: Int): Array<ComplicationColor?> {
            return arrayOfNulls(size)
        }
    }
}

@ColorInt
fun ComplicationColors.getPrimaryColorForComplication(complicationLocation: ComplicationLocation): Int = when (complicationLocation) {
    ComplicationLocation.LEFT -> { leftColor.color }
    ComplicationLocation.MIDDLE -> { middleColor.color }
    ComplicationLocation.BOTTOM -> { bottomColor.color }
    ComplicationLocation.RIGHT -> { rightColor.color }
    ComplicationLocation.ANDROID_12_TOP_LEFT -> { android12TopLeftColor.color }
    ComplicationLocation.ANDROID_12_TOP_RIGHT -> { android12TopRightColor.color }
    ComplicationLocation.ANDROID_12_BOTTOM_LEFT -> { android12BottomLeftColor.color }
    ComplicationLocation.ANDROID_12_BOTTOM_RIGHT -> { android12BottomRightColor.color }
}