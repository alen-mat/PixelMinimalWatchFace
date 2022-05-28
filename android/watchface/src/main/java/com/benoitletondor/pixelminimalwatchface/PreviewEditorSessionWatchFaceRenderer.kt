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

import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import java.time.ZonedDateTime

class PreviewEditorSessionWatchFaceRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int,
) : Renderer.CanvasRenderer2<Renderer.SharedAssets>(
    surfaceHolder = surfaceHolder,
    currentUserStyleRepository = currentUserStyleRepository,
    watchState = watchState,
    canvasType = canvasType,
    interactiveDrawModeUpdateDelayMillis = 60000L,
    clearWithBackgroundTintBeforeRenderingHighlightLayer = false,
) {
    private val previewPaint = Paint().apply {
        isAntiAlias = true
    }
    private var previewBitmap: Bitmap? = null
    
    init {
        if (DEBUG_LOGS) Log.d(TAG, "init")
    }

    override fun onDestroy() {
        if (DEBUG_LOGS) Log.d(TAG, "onDestroy")
        super.onDestroy()
    }

    override suspend fun createSharedAssets(): SharedAssets = object : SharedAssets {
        override fun onDestroy() {}
    }

    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: SharedAssets
    ) {
        if (DEBUG_LOGS) Log.d(TAG, "render")

        val previewBitmap = this.previewBitmap ?: kotlin.run {
            if (DEBUG_LOGS) Log.d(TAG, "creating bitmap ${bounds.width()}x${bounds.height()}")
            val bitmap = ContextCompat.getDrawable(context, R.drawable.preview)!!.toBitmap(bounds.width(), bounds.height())
            this.previewBitmap = bitmap
            bitmap
        }

        canvas.drawBitmap(previewBitmap, null, bounds, previewPaint)
    }

    override fun renderHighlightLayer(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: SharedAssets
    ) {
        if (DEBUG_LOGS) Log.d(TAG, "renderHighlightLayer")
        // No-op
    }

    companion object {
        private const val TAG = "PreviewWatchFaceRenderer"
    }
}