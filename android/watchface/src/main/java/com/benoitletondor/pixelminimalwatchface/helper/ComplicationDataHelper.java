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
package com.benoitletondor.pixelminimalwatchface.helper;

import androidx.annotation.NonNull;
import androidx.wear.watchface.ComplicationSlotsManager;
import androidx.wear.watchface.complications.data.ComplicationData;

import java.time.Instant;

public class ComplicationDataHelper {
    @SuppressWarnings("KotlinInternalInJava")
    public static void updateComplicationData(
        @NonNull ComplicationSlotsManager complicationSlotsManager,
        int complicationId,
        @NonNull ComplicationData data
    ) {
        complicationSlotsManager.onComplicationDataUpdate$watchface_release(
            complicationId,
            data,
            Instant.now()
        );
        complicationSlotsManager.onComplicationsUpdated$watchface_release();
    }
}
