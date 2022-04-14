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

import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import android.view.SurfaceHolder
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import java.time.ZonedDateTime

class NoOpEditorSessionWatchFaceRenderer(
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
        // No-op
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
        private const val TAG = "NoOpWatchFaceRenderer"
    }
}