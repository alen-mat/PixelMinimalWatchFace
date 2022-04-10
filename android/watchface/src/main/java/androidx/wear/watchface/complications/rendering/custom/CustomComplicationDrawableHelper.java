package androidx.wear.watchface.complications.rendering.custom;

import androidx.annotation.NonNull;
import androidx.wear.watchface.complications.rendering.ComplicationStyle;

@SuppressWarnings("KotlinInternalInJava")
public class CustomComplicationDrawableHelper {
    public static boolean isStyleDirty(@NonNull ComplicationStyle style) {
        return style.isDirty();
    }

    public static void clearDirtyFlag(@NonNull ComplicationStyle style) {
        style.clearDirtyFlag();
    }
}
