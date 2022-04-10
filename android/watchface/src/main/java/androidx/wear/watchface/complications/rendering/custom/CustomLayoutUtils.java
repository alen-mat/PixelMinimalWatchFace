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
