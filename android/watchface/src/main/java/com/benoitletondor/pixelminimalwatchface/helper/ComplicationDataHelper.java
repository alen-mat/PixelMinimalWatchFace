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
