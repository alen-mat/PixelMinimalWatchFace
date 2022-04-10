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
package androidx.wear.watchface.complications.rendering.custom;

import android.graphics.Rect;

import androidx.annotation.NonNull;

public class CustomLayoutUtils {
    private final static float customRatio = 1.3f;

    public static void getInnerBounds(@NonNull Rect outRect, @NonNull Rect inRect, float radius) {
        outRect.set(inRect);
        int padding = (int) Math.ceil((Math.sqrt(2.0f) - 1.0f) * radius);
        outRect.inset((int) (padding / customRatio), (int) (padding / customRatio));
    }
}
