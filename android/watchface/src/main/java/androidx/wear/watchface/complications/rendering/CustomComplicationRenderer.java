package androidx.wear.watchface.complications.rendering;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationText;
import android.text.Layout;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.wear.watchface.complications.data.ImageKt;
import androidx.wear.watchface.complications.data.RangedValueComplicationData;
import androidx.wear.watchface.complications.rendering.custom.CustomLayoutUtils;
import androidx.wear.watchface.complications.rendering.utils.IconLayoutHelper;
import androidx.wear.watchface.complications.rendering.utils.LargeImageLayoutHelper;
import androidx.wear.watchface.complications.rendering.utils.LayoutHelper;
import androidx.wear.watchface.complications.rendering.utils.LayoutUtils;
import androidx.wear.watchface.complications.rendering.utils.LongTextLayoutHelper;
import androidx.wear.watchface.complications.rendering.utils.RangedValueLayoutHelper;
import androidx.wear.watchface.complications.rendering.utils.ShortTextLayoutHelper;
import androidx.wear.watchface.complications.rendering.utils.SmallImageLayoutHelper;

import java.time.Instant;
import java.util.Objects;

/*
 * Copy/Paste of the "ComplicationRenderer" class with 1 main difference: it uses "CustomLayoutUtils.getInnerBounds"
 * instead of "LayoutUtils.getInnerBounds"
 */

@SuppressLint({"RestrictedApi", "VisibleForTests"})
public class CustomComplicationRenderer extends ComplicationRenderer {
    private static final float ICON_SIZE_FRACTION = 1.0f;

    /**
     * Size fraction used for drawing small image. 0.95 here means image will be 0.95 the size of
     * its container.
     */
    private static final float SMALL_IMAGE_SIZE_FRACTION = 0.95f;

    /** Size fraction used for drawing large image. */
    private static final float LARGE_IMAGE_SIZE_FRACTION = 1.0f;

    /** Used to apply padding to the beginning of the text when it's left aligned. */
    private static final float TEXT_PADDING_HEIGHT_FRACTION = 0.1f;

    /** Used to apply a grey color to a placeholder. */
    @VisibleForTesting
    static final Paint PLACEHOLDER_PAINT = createPlaceHolderPaint();

    private static Paint createPlaceHolderPaint() {
        Paint paint = new Paint();
        paint.setColor(Color.LTGRAY);
        paint.setAntiAlias(true);
        return paint;
    }

    /** Used to apply a grey color to a placeholder ranged value arc. */
    private static final Paint PLACEHOLDER_PROGRESS_PAINT = createPlaceHolderProgressPaint();

    private static Paint createPlaceHolderProgressPaint() {
        Paint paint = new Paint();
        paint.setColor(Color.LTGRAY);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
        return paint;
    }

    /** Used to apply a grey tint to a placeholder icon. */
    @VisibleForTesting
    static final ColorFilter PLACEHOLDER_COLOR_FILTER = new PorterDuffColorFilter(
            Color.LTGRAY, PorterDuff.Mode.SRC_IN);

    /** Context is required for localization. */
    private final Context mContext;

    private ComplicationData mComplicationData;
    private final Rect mBounds = new Rect();

    /** Used to render {@link ComplicationData#TYPE_NO_DATA}. */
    private CharSequence mNoDataText = "";

    private boolean mRangedValueProgressHidden;

    private boolean mHasNoData;

    // Below drawables will be null until they are fully loaded.
    @Nullable
    Drawable mIcon;
    @Nullable
    Drawable mBurnInProtectionIcon;
    @Nullable
    Drawable mSmallImage;
    @Nullable
    Drawable mBurnInProtectionSmallImage;
    @Nullable
    Drawable mLargeImage;

    @VisibleForTesting
    boolean mIsPlaceholderIcon;
    @VisibleForTesting
    boolean mIsPlaceholderSmallImage;
    @VisibleForTesting
    boolean mIsPlaceholderLargeImage;
    @VisibleForTesting
    boolean mIsPlaceholderRangedValue;
    @VisibleForTesting
    boolean mIsPlaceholderTitle;
    @VisibleForTesting
    boolean mIsPlaceholderText;

    // Drawables for rendering rounded images
    private RoundedDrawable mRoundedBackgroundDrawable = null;
    private RoundedDrawable mRoundedLargeImage = null;
    private RoundedDrawable mRoundedSmallImage = null;

    // Text renderers
    @VisibleForTesting
    TextRenderer mMainTextRenderer = new TextRenderer();

    @VisibleForTesting
    TextRenderer mSubTextRenderer = new TextRenderer();

    // Bounds for components. NB we want to avoid allocations in watch face rendering code to
    // reduce GC pressure.
    private final Rect mBackgroundBounds = new Rect();
    private final RectF mBackgroundBoundsF = new RectF();
    private final Rect mIconBounds = new Rect();
    private final Rect mSmallImageBounds = new Rect();
    private final Rect mLargeImageBounds = new Rect();
    private final Rect mMainTextBounds = new Rect();
    private final Rect mSubTextBounds = new Rect();
    private final Rect mRangedValueBounds = new Rect();
    private final RectF mRangedValueBoundsF = new RectF();

    // Paint sets for active and ambient modes.
    @VisibleForTesting
    ComplicationRenderer.PaintSet mActivePaintSet = null;
    ComplicationRenderer.PaintSet mActivePaintSetLostTapAction = null;
    @VisibleForTesting
    ComplicationRenderer.PaintSet mAmbientPaintSet = null;
    ComplicationRenderer.PaintSet mAmbientPaintSetLostTapAction = null;

    // Paints for texts
    @Nullable
    private TextPaint mMainTextPaint = null;
    @Nullable
    private TextPaint mSubTextPaint = null;

    // Styles for active and ambient modes.
    private ComplicationStyle mActiveStyle;
    private ComplicationStyle mAmbientStyle;

    @Nullable
    private Paint mDebugPaint;

    @Nullable
    private OnInvalidateListener mInvalidateListener;

    /**
     * Initializes complication renderer.
     *
     * @param context      Current [Context].
     * @param activeStyle  ComplicationSlot style to be used when in active mode.
     * @param ambientStyle ComplicationSlot style to be used when in ambient mode.
     */
    public CustomComplicationRenderer(Context context, ComplicationStyle activeStyle, ComplicationStyle ambientStyle) {
        super(context, activeStyle, ambientStyle);

        mContext = context;
        updateStyle(activeStyle, ambientStyle);
    }

    /**
     * Updates the complication styles in active and ambient modes
     *
     * @param activeStyle  complication style in active mode
     * @param ambientStyle complication style in ambient mode
     */
    @Override
    public void updateStyle(
            @NonNull ComplicationStyle activeStyle, @NonNull ComplicationStyle ambientStyle) {
        mActiveStyle = activeStyle;
        mAmbientStyle = ambientStyle;
        // Reset paint sets
        mActivePaintSet = new ComplicationRenderer.PaintSet(activeStyle, false, false, false);
        mActivePaintSetLostTapAction =
                new ComplicationRenderer.PaintSet(activeStyle.asTinted(Color.DKGRAY), false, false, false);
        mAmbientPaintSet = new ComplicationRenderer.PaintSet(ambientStyle, true, false, false);
        mAmbientPaintSetLostTapAction =
                new ComplicationRenderer.PaintSet(activeStyle.asTinted(Color.DKGRAY), true, false, false);
        calculateBounds();
    }

    /**
     * Sets the complication data to be rendered.
     *
     * @param data               ComplicationSlot data to be rendered. If this is null, nothing
     *                           is drawn.
     * @param loadDrawablesAsync If true any drawables will be loaded asynchronously, otherwise
     *                           they will be loaded synchronously.
     */
    @Override
    public void setComplicationData(@Nullable ComplicationData data, boolean loadDrawablesAsync) {
        if (Objects.equals(mComplicationData, data)) {
            return;
        }
        if (data == null) {
            mComplicationData = null;
            // Free unnecessary RoundedDrawables.
            mRoundedBackgroundDrawable = null;
            mRoundedLargeImage = null;
            mRoundedSmallImage = null;
            return;
        }

        mIsPlaceholderIcon = false;
        mIsPlaceholderSmallImage = false;
        mIsPlaceholderLargeImage = false;
        mIsPlaceholderRangedValue = false;
        mIsPlaceholderTitle = false;
        mIsPlaceholderText = false;

        if (data.getType() == ComplicationData.TYPE_NO_DATA) {
            if (data.hasPlaceholderType()) {
                mIsPlaceholderIcon = data.hasIcon() && ImageKt.isPlaceholder(data.getIcon());
                mIsPlaceholderSmallImage =
                        data.hasSmallImage() && ImageKt.isPlaceholder(data.getSmallImage());
                mIsPlaceholderLargeImage =
                        data.hasLargeImage() && ImageKt.isPlaceholder(data.getLargeImage());
                mIsPlaceholderRangedValue = data.hasRangedValue()
                        && data.getRangedValue()
                        == RangedValueComplicationData.PLACEHOLDER;
                if (data.getPlaceholderType() == ComplicationData.TYPE_LONG_TEXT) {
                    mIsPlaceholderTitle =
                            data.hasLongTitle() && data.getLongTitle().isPlaceholder();
                    mIsPlaceholderText =
                            data.hasLongText() && data.getLongText().isPlaceholder();
                } else {
                    mIsPlaceholderTitle =
                            data.hasShortTitle() && data.getShortTitle().isPlaceholder();
                    mIsPlaceholderText =
                            data.hasShortText() && data.getShortText().isPlaceholder();
                }
                mComplicationData = data;
                mHasNoData = false;
            } else {
                if (!mHasNoData) {
                    // Render TYPE_NO_DATA as a short text complication with a predefined string
                    mHasNoData = true;
                    mComplicationData =
                            new ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                                    .setShortText(ComplicationText.plainText(mNoDataText))
                                    .build();
                } else {
                    // This return prevents recalculating bounds if renderer already has
                    // TYPE_NO_DATA
                    return;
                }
            }
        } else {
            mComplicationData = data;
            mHasNoData = false;
        }
        if (loadDrawablesAsync) {
            if (!loadDrawableIconAndImagesAsync()) {
                invalidate();
            }
        } else {
            loadDrawableIconAndImages();
        }
        calculateBounds();

        // Based on the results of calculateBounds we know if mRoundedLargeImage or
        // mSmallImageBounds are needed for rendering and can null the references if not required.
        // NOTE mRoundedBackgroundDrawable has a different lifecycle which is based on the current
        // paint mode so it doesn't make sense to clear it's reference here.
        mRoundedLargeImage = null;
        mRoundedSmallImage = null;
    }

    /**
     * Sets bounds for the complication data to be drawn within. Returns true if the boundaries are
     * recalculated for components.
     *
     * @param bounds Bounds for the complication data to be drawn within.
     */
    @Override
    public boolean setBounds(@NonNull Rect bounds) {
        // Calculations can be avoided if size didn't change
        boolean shouldCalculateBounds = true;
        if (mBounds.width() == bounds.width() && mBounds.height() == bounds.height()) {
            shouldCalculateBounds = false;
        }
        mBounds.set(bounds);
        if (shouldCalculateBounds) {
            calculateBounds();
        }
        return shouldCalculateBounds;
    }

    /**
     * Sets the text to be rendered when {@link ComplicationData} is of type {@link
     * ComplicationData#TYPE_NO_DATA}. If no data text is null, an empty string will be rendered.
     */
    @Override
    public void setNoDataText(@Nullable CharSequence noDataText) {
        if (noDataText == null) {
            noDataText = "";
        }
        // Making a copy of the CharSequence because mutable CharSequences may cause undefined
        // behavior
        mNoDataText = noDataText.subSequence(0, noDataText.length());
        // Create complication data with new text.
        if (mHasNoData) {
            mHasNoData = false;
            setComplicationData(
                    new ComplicationData.Builder(ComplicationData.TYPE_NO_DATA).build(), true);
        }
    }

    /** Sets if the ranged value progress should be hidden. */
    @Override
    public void setRangedValueProgressHidden(boolean hidden) {
        if (mRangedValueProgressHidden != hidden) {
            mRangedValueProgressHidden = hidden;
            calculateBounds();
        }
    }

    /** Returns {@code true} if the ranged value progress should be hidden. */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    @Override
    public boolean isRangedValueProgressHidden() {
        return mRangedValueProgressHidden;
    }

    /**
     * Renders complication data on a canvas. Does nothing if the current data is null, has type
     * 'empty' or 'not configured', or is not active.
     *
     * @param canvas           canvas to be drawn on.
     * @param currentTime      current time as an {@link Instant}
     * @param inAmbientMode    true if the device is in ambient mode.
     * @param lowBitAmbient    true if the screen supports fewer bits for each color in ambient
     *                         mode.
     * @param burnInProtection true if burn-in protection is required.
     * @param showTapHighlight true if the complication should be drawn with a highlighted effect,
     *                         to provide visual feedback after a tap.
     */
    @Override
    public void draw(
            @NonNull Canvas canvas,
            Instant currentTime,
            boolean inAmbientMode,
            boolean lowBitAmbient,
            boolean burnInProtection,
            boolean showTapHighlight) {
        // If complication data is not available or empty, or is not active, don't draw
        if (mComplicationData == null
                || mComplicationData.getType() == ComplicationData.TYPE_EMPTY
                || mComplicationData.getType() == ComplicationData.TYPE_NOT_CONFIGURED
                || !mComplicationData.isActiveAt(currentTime.toEpochMilli())
                || mBounds.isEmpty()) {
            return;
        }
        // If in ambient mode but paint set is not usable with current ambient properties,
        // reinitialize.
        if (inAmbientMode
                && (mAmbientPaintSet.mLowBitAmbient != lowBitAmbient
                || mAmbientPaintSet.mBurnInProtection != burnInProtection)) {
            mAmbientPaintSet = new ComplicationRenderer.PaintSet(mAmbientStyle, true, lowBitAmbient, burnInProtection);
        }
        // Choose the correct paint set to use
        ComplicationRenderer.PaintSet currentPaintSet = mComplicationData.getTapActionLostDueToSerialization()
                ? (inAmbientMode ? mAmbientPaintSetLostTapAction : mActivePaintSetLostTapAction) :
                (inAmbientMode ? mAmbientPaintSet : mActivePaintSet);
        // Update complication texts
        updateComplicationTexts(currentTime.toEpochMilli());
        canvas.save();
        canvas.translate(mBounds.left, mBounds.top);
        // Draw background first
        drawBackground(canvas, currentPaintSet);
        // Draw content
        drawIcon(canvas, currentPaintSet, mIsPlaceholderIcon);
        drawSmallImage(canvas, currentPaintSet, mIsPlaceholderSmallImage);
        drawLargeImage(canvas, currentPaintSet, mIsPlaceholderLargeImage);
        drawRangedValue(canvas, currentPaintSet, mIsPlaceholderRangedValue);
        drawMainText(canvas, currentPaintSet, mIsPlaceholderText);
        drawSubText(canvas, currentPaintSet, mIsPlaceholderTitle);
        // Draw highlight if highlighted
        if (showTapHighlight) {
            drawHighlight(canvas, currentPaintSet);
        }
        // Draw borders last (to ensure that they are always visible)
        drawBorders(canvas, currentPaintSet);
        canvas.restore();
    }

    @Override
    public void setOnInvalidateListener(@Nullable OnInvalidateListener listener) {
        mInvalidateListener = listener;
    }

    private void invalidate() {
        if (mInvalidateListener != null) {
            mInvalidateListener.onInvalidate();
        }
    }

    private void updateComplicationTexts(long currentTimeMillis) {
        if (mComplicationData.hasShortText()) {
            mMainTextRenderer.setMaxLines(1);
            mMainTextRenderer.setText(
                    mComplicationData.getShortText().getTextAt(
                            mContext.getResources(), currentTimeMillis));
            if (mComplicationData.getShortTitle() != null) {
                mSubTextRenderer.setText(
                        mComplicationData.getShortTitle().getTextAt(
                                mContext.getResources(), currentTimeMillis));
            } else {
                mSubTextRenderer.setText("");
            }
        }
        if (mComplicationData.hasLongText()) {
            mMainTextRenderer.setText(
                    mComplicationData.getLongText().getTextAt(
                            mContext.getResources(), currentTimeMillis));
            if (mComplicationData.getLongTitle() != null) {
                mSubTextRenderer.setText(
                        mComplicationData.getLongTitle().getTextAt(
                                mContext.getResources(), currentTimeMillis));
                // If long text has title, only show one line from each
                mMainTextRenderer.setMaxLines(1);
            } else {
                mSubTextRenderer.setText("");
                // If long text doesn't have a title, show two lines from text
                mMainTextRenderer.setMaxLines(2);
            }
        }
    }

    private void drawBackground(Canvas canvas, ComplicationRenderer.PaintSet paintSet) {
        int radius = getBorderRadius(paintSet.mStyle);
        canvas.drawRoundRect(mBackgroundBoundsF, radius, radius, paintSet.mBackgroundPaint);
        if (paintSet.mStyle.getBackgroundDrawable() != null
                && !paintSet.isInBurnInProtectionMode()) {
            if (mRoundedBackgroundDrawable == null) {
                mRoundedBackgroundDrawable = new RoundedDrawable();
            }
            mRoundedBackgroundDrawable.setDrawable(paintSet.mStyle.getBackgroundDrawable());
            mRoundedBackgroundDrawable.setRadius(radius);
            mRoundedBackgroundDrawable.setBounds(mBackgroundBounds);
            mRoundedBackgroundDrawable.draw(canvas);
        } else {
            mRoundedBackgroundDrawable = null;
        }
    }

    private void drawBorders(Canvas canvas, ComplicationRenderer.PaintSet paintSet) {
        if (paintSet.mStyle.getBorderStyle() != ComplicationStyle.BORDER_STYLE_NONE) {
            int radius = getBorderRadius(paintSet.mStyle);
            canvas.drawRoundRect(mBackgroundBoundsF, radius, radius, paintSet.mBorderPaint);
        }
    }

    private void drawHighlight(Canvas canvas, ComplicationRenderer.PaintSet paintSet) {
        if (!paintSet.mIsAmbientStyle) {
            // Don't draw the highlight in ambient mode
            int radius = getBorderRadius(paintSet.mStyle);
            canvas.drawRoundRect(mBackgroundBoundsF, radius, radius, paintSet.mHighlightPaint);
        }
    }

    private void drawMainText(Canvas canvas, ComplicationRenderer.PaintSet paintSet, boolean isPlaceholder) {
        if (mMainTextBounds.isEmpty()) {
            return;
        }
        if (DEBUG_MODE) {
            canvas.drawRect(mMainTextBounds, mDebugPaint);
        }
        if (mMainTextPaint != paintSet.mPrimaryTextPaint) {
            mMainTextPaint = paintSet.mPrimaryTextPaint;
            mMainTextRenderer.setPaint(mMainTextPaint);
            mMainTextRenderer.setInAmbientMode(paintSet.mIsAmbientStyle);
        }
        if (isPlaceholder) {
            float width;
            float height;
            // Avoid drawing two placeholder text fields of the same length.
            if (!mSubTextBounds.isEmpty()
                    && (mComplicationData.getPlaceholderType() == ComplicationData.TYPE_SHORT_TEXT
                    || mComplicationData.getPlaceholderType() == ComplicationData.TYPE_LONG_TEXT)) {
                width = mMainTextBounds.width() * 0.4f;
                height = mMainTextBounds.height() * 0.9f;
            } else {
                width = mMainTextBounds.width();
                height = mMainTextBounds.height() * 0.75f;
            }
            canvas.drawRoundRect(mMainTextBounds.left,
                    mMainTextBounds.top + height * 0.1f,
                    mMainTextBounds.left + width,
                    mMainTextBounds.top + height,
                    mMainTextBounds.width() * 0.05f,
                    mMainTextBounds.height() * 0.1f, PLACEHOLDER_PAINT);
        } else {
            mMainTextRenderer.draw(canvas, mMainTextBounds);
        }
    }

    private void drawSubText(Canvas canvas, ComplicationRenderer.PaintSet paintSet, boolean isPlaceholder) {
        if (mSubTextBounds.isEmpty()) {
            return;
        }
        if (DEBUG_MODE) {
            canvas.drawRect(mSubTextBounds, mDebugPaint);
        }
        if (mSubTextPaint != paintSet.mSecondaryTextPaint) {
            mSubTextPaint = paintSet.mSecondaryTextPaint;
            mSubTextRenderer.setPaint(mSubTextPaint);
            mSubTextRenderer.setInAmbientMode(paintSet.mIsAmbientStyle);
        }

        if (isPlaceholder) {
            canvas.drawRoundRect(mSubTextBounds.left,
                    mSubTextBounds.bottom - mSubTextBounds.height() * 0.9f,
                    mSubTextBounds.right, mSubTextBounds.bottom, mSubTextBounds.width() * 0.05f,
                    mSubTextBounds.height() * 0.1f, PLACEHOLDER_PAINT);
        } else {
            mSubTextRenderer.draw(canvas, mSubTextBounds);
        }
    }

    private void drawRangedValue(Canvas canvas, ComplicationRenderer.PaintSet paintSet, boolean isPlaceholder) {
        if (mRangedValueBoundsF.isEmpty()) {
            return;
        }
        if (DEBUG_MODE) {
            canvas.drawRect(mRangedValueBoundsF, mDebugPaint);
        }

        float rangedMinValue = mComplicationData.getRangedMinValue();
        float rangedMaxValue = mComplicationData.getRangedMaxValue();
        float rangedValue = mComplicationData.getRangedValue();

        if (isPlaceholder) {
            rangedMinValue = 0.0f;
            rangedMaxValue = 100.0f;
            rangedValue = 75.0f;
        }

        float value =
                Math.min(rangedMaxValue, Math.max(rangedMinValue, rangedValue)) - rangedMinValue;
        float interval = rangedMaxValue - rangedMinValue;
        float progress = interval > 0 ? value / interval : 0;

        // do not need to draw gap in the cases of full circle
        float gap = (progress > 0.0f && progress < 1.0f) ? STROKE_GAP_IN_DEGREES : 0.0f;
        float inProgressAngle = Math.max(0, 360.0f * progress - gap);
        float remainderAngle = Math.max(0, 360.0f * (1.0f - progress) - gap);

        int insetAmount = (int) Math.ceil(paintSet.mInProgressPaint.getStrokeWidth());
        mRangedValueBoundsF.inset(insetAmount, insetAmount);

        if (isPlaceholder) {
            PLACEHOLDER_PROGRESS_PAINT.setStrokeWidth(paintSet.mInProgressPaint.getStrokeWidth());
        }

        // Draw the progress arc.
        canvas.drawArc(
                mRangedValueBoundsF,
                RANGED_VALUE_START_ANGLE + gap / 2,
                inProgressAngle,
                false,
                isPlaceholder ? PLACEHOLDER_PROGRESS_PAINT : paintSet.mInProgressPaint);

        // Draw the remain arc.
        if (!isPlaceholder) {
            canvas.drawArc(
                    mRangedValueBoundsF,
                    RANGED_VALUE_START_ANGLE
                            + gap / 2.0f
                            + inProgressAngle
                            + gap,
                    remainderAngle,
                    false,
                    paintSet.mRemainingPaint);
        }
        mRangedValueBoundsF.inset(-insetAmount, -insetAmount);
    }

    private void drawIcon(Canvas canvas, ComplicationRenderer.PaintSet paintSet, boolean isPlaceholder) {
        if (mIconBounds.isEmpty()) {
            return;
        }
        if (DEBUG_MODE) {
            canvas.drawRect(mIconBounds, mDebugPaint);
        }
        Drawable icon = mIcon;
        if (icon != null) {
            if (paintSet.isInBurnInProtectionMode() && mBurnInProtectionIcon != null) {
                icon = mBurnInProtectionIcon;
            }
            icon.setColorFilter(mComplicationData.hasPlaceholderType() ? PLACEHOLDER_COLOR_FILTER :
                    paintSet.mIconColorFilter);
            drawIconOnCanvas(canvas, mIconBounds, icon);
        } else if (isPlaceholder) {
            canvas.drawRect(mIconBounds, PLACEHOLDER_PAINT);
        }
    }

    private void drawSmallImage(Canvas canvas, ComplicationRenderer.PaintSet paintSet, boolean isPlaceholder) {
        if (mSmallImageBounds.isEmpty()) {
            return;
        }
        if (DEBUG_MODE) {
            canvas.drawRect(mSmallImageBounds, mDebugPaint);
        }
        if (mRoundedSmallImage == null) {
            mRoundedSmallImage = new RoundedDrawable();
        }
        if (!paintSet.isInBurnInProtectionMode()) {
            mRoundedSmallImage.setDrawable(mSmallImage);
            if (mSmallImage == null) {
                if (isPlaceholder) {
                    canvas.drawRect(mSmallImageBounds, PLACEHOLDER_PAINT);
                }
                return;
            }
        } else {
            mRoundedSmallImage.setDrawable(mBurnInProtectionSmallImage);
            if (mBurnInProtectionSmallImage == null) {
                return;
            }
        }
        if (mComplicationData.getSmallImageStyle() == ComplicationData.IMAGE_STYLE_ICON) {
            // Don't apply radius or color filter on icon style images
            mRoundedSmallImage.setColorFilter(null);
            mRoundedSmallImage.setRadius(0);
        } else {
            mRoundedSmallImage.setColorFilter(paintSet.mStyle.getImageColorFilter());
            mRoundedSmallImage.setRadius(getImageBorderRadius(paintSet.mStyle, mSmallImageBounds));
        }
        mRoundedSmallImage.setBounds(mSmallImageBounds);
        mRoundedSmallImage.draw(canvas);
    }

    private void drawLargeImage(Canvas canvas, ComplicationRenderer.PaintSet paintSet, boolean isPlaceholder) {
        if (mLargeImageBounds.isEmpty()) {
            return;
        }
        if (DEBUG_MODE) {
            canvas.drawRect(mLargeImageBounds, mDebugPaint);
        }
        // Draw the image if not in burn in protection mode (in active mode or burn in not enabled)
        if (!paintSet.isInBurnInProtectionMode()) {
            if (mRoundedLargeImage == null) {
                mRoundedLargeImage = new RoundedDrawable();
            }
            mRoundedLargeImage.setDrawable(mLargeImage);
            // Large image is always treated as photo style
            mRoundedLargeImage.setRadius(getImageBorderRadius(paintSet.mStyle, mLargeImageBounds));
            mRoundedLargeImage.setBounds(mLargeImageBounds);
            mRoundedLargeImage.setColorFilter(paintSet.mStyle.getImageColorFilter());
            mRoundedLargeImage.draw(canvas);
        } else if (isPlaceholder) {
            canvas.drawRect(mLargeImageBounds, PLACEHOLDER_PAINT);
        }
    }

    private static void drawIconOnCanvas(Canvas canvas, Rect bounds, Drawable icon) {
        icon.setBounds(0, 0, bounds.width(), bounds.height());
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        icon.draw(canvas);
        canvas.restore();
    }

    private int getBorderRadius(ComplicationStyle currentStyle) {
        if (mBounds.isEmpty()) {
            return 0;
        } else {
            return Math.min(
                    Math.min(mBounds.height(), mBounds.width()) / 2,
                    currentStyle.getBorderRadius());
        }
    }

    @VisibleForTesting
    int getImageBorderRadius(ComplicationStyle currentStyle, Rect imageBounds) {
        if (mBounds.isEmpty()) {
            return 0;
        } else {
            return Math.max(
                    getBorderRadius(currentStyle)
                            - Math.min(
                            Math.min(imageBounds.left, mBounds.width() - imageBounds.right),
                            Math.min(
                                    imageBounds.top,
                                    mBounds.height() - imageBounds.bottom)),
                    0);
        }
    }

    private void calculateBounds() {
        if (mComplicationData == null || mBounds.isEmpty()) {
            return;
        }
        mBackgroundBounds.set(0, 0, mBounds.width(), mBounds.height());
        mBackgroundBoundsF.set(0, 0, mBounds.width(), mBounds.height());
        LayoutHelper currentLayoutHelper;
        int type = mComplicationData.getType();
        if (type == ComplicationData.TYPE_NO_DATA && mComplicationData.hasPlaceholderType()) {
            type = mComplicationData.getPlaceholderType();
        }
        switch (type) {
            case ComplicationData.TYPE_ICON:
                currentLayoutHelper = new IconLayoutHelper();
                break;
            case ComplicationData.TYPE_SMALL_IMAGE:
                currentLayoutHelper = new SmallImageLayoutHelper();
                break;
            case ComplicationData.TYPE_LARGE_IMAGE:
                currentLayoutHelper = new LargeImageLayoutHelper();
                break;
            case ComplicationData.TYPE_SHORT_TEXT:
            case ComplicationData.TYPE_NO_PERMISSION:
                currentLayoutHelper = new ShortTextLayoutHelper();
                break;
            case ComplicationData.TYPE_LONG_TEXT:
                currentLayoutHelper = new LongTextLayoutHelper();
                break;
            case ComplicationData.TYPE_RANGED_VALUE:
                if (mRangedValueProgressHidden) {
                    if (mComplicationData.getShortText() == null) {
                        currentLayoutHelper = new IconLayoutHelper();
                    } else {
                        currentLayoutHelper = new ShortTextLayoutHelper();
                    }
                } else {
                    currentLayoutHelper = new RangedValueLayoutHelper();
                }
                break;
            case ComplicationData.TYPE_EMPTY:
            case ComplicationData.TYPE_NOT_CONFIGURED:
            case ComplicationData.TYPE_NO_DATA:
            default:
                currentLayoutHelper = new LayoutHelper();
                break;
        }
        currentLayoutHelper.update(mBounds.width(), mBounds.height(), mComplicationData);
        currentLayoutHelper.getRangedValueBounds(mRangedValueBounds);
        mRangedValueBoundsF.set(mRangedValueBounds);
        currentLayoutHelper.getIconBounds(mIconBounds);
        currentLayoutHelper.getSmallImageBounds(mSmallImageBounds);
        currentLayoutHelper.getLargeImageBounds(mLargeImageBounds);
        Layout.Alignment alignment;
        if (type == ComplicationData.TYPE_LONG_TEXT) {
            alignment = currentLayoutHelper.getLongTextAlignment();
            currentLayoutHelper.getLongTextBounds(mMainTextBounds);
            mMainTextRenderer.setAlignment(alignment);
            mMainTextRenderer.setGravity(currentLayoutHelper.getLongTextGravity());
            currentLayoutHelper.getLongTitleBounds(mSubTextBounds);
            mSubTextRenderer.setAlignment(currentLayoutHelper.getLongTitleAlignment());
            mSubTextRenderer.setGravity(currentLayoutHelper.getLongTitleGravity());
        } else {
            alignment = currentLayoutHelper.getShortTextAlignment();
            currentLayoutHelper.getShortTextBounds(mMainTextBounds);
            mMainTextRenderer.setAlignment(alignment);
            mMainTextRenderer.setGravity(currentLayoutHelper.getShortTextGravity());
            currentLayoutHelper.getShortTitleBounds(mSubTextBounds);
            mSubTextRenderer.setAlignment(currentLayoutHelper.getShortTitleAlignment());
            mSubTextRenderer.setGravity(currentLayoutHelper.getShortTitleGravity());
        }
        if (alignment != Layout.Alignment.ALIGN_CENTER) {
            float paddingAmount = TEXT_PADDING_HEIGHT_FRACTION * mBounds.height();
            mMainTextRenderer.setRelativePadding(paddingAmount / mMainTextBounds.width(), 0, 0, 0);
            mSubTextRenderer.setRelativePadding(paddingAmount / mMainTextBounds.width(), 0, 0, 0);
        } else {
            mMainTextRenderer.setRelativePadding(0, 0, 0, 0);
            mSubTextRenderer.setRelativePadding(0, 0, 0, 0);
        }
        Rect innerBounds = new Rect();
        CustomLayoutUtils.getInnerBounds(
                innerBounds,
                mBackgroundBounds,
                Math.max(getBorderRadius(mActiveStyle), getBorderRadius(mAmbientStyle)));
        // Intersect text bounds with inner bounds to avoid overflow
        if (!mMainTextBounds.intersect(innerBounds)) {
            mMainTextBounds.setEmpty();
        }
        if (!mSubTextBounds.intersect(innerBounds)) {
            mSubTextBounds.setEmpty();
        }
        // Intersect icon bounds with inner bounds and try to keep its center the same
        if (!mIconBounds.isEmpty()) {
            // Apply padding to icons
            LayoutUtils.scaledAroundCenter(mIconBounds, mIconBounds, ICON_SIZE_FRACTION);
            LayoutUtils.fitSquareToBounds(mIconBounds, innerBounds);
        }
        // Intersect small image with inner bounds and make it a square if image style is icon
        if (!mSmallImageBounds.isEmpty()) {
            // Apply padding to small images
            LayoutUtils.scaledAroundCenter(
                    mSmallImageBounds, mSmallImageBounds, SMALL_IMAGE_SIZE_FRACTION);
            if (mComplicationData.getSmallImageStyle() == ComplicationData.IMAGE_STYLE_ICON) {
                LayoutUtils.fitSquareToBounds(mSmallImageBounds, innerBounds);
            }
        }
        // Apply padding to large images
        if (!mLargeImageBounds.isEmpty()) {
            LayoutUtils.scaledAroundCenter(
                    mLargeImageBounds, mLargeImageBounds, LARGE_IMAGE_SIZE_FRACTION);
        }
    }

    /**
     * Returns true if the data contains images. If there are, the images will be loaded
     * asynchronously and the drawable will be invalidated when loading is complete.
     */
    private boolean loadDrawableIconAndImagesAsync() {
        Handler handler = new Handler(Looper.getMainLooper());
        Icon icon = null;
        Icon smallImage = null;
        Icon burnInProtectionSmallImage = null;
        Icon largeImage = null;
        Icon burnInProtectionIcon = null;
        mIcon = null;
        mSmallImage = null;
        mBurnInProtectionSmallImage = null;
        mLargeImage = null;
        mBurnInProtectionIcon = null;
        if (mComplicationData != null) {
            icon = mComplicationData.hasIcon() ? mComplicationData.getIcon() : null;
            burnInProtectionIcon = mComplicationData.hasBurnInProtectionIcon()
                    ? mComplicationData.getBurnInProtectionIcon() : null;
            burnInProtectionSmallImage =
                    mComplicationData.hasBurnInProtectionSmallImage()
                            ? mComplicationData.getBurnInProtectionSmallImage() : null;
            smallImage =
                    mComplicationData.hasSmallImage() ? mComplicationData.getSmallImage() : null;
            largeImage =
                    mComplicationData.hasLargeImage() ? mComplicationData.getLargeImage() : null;
        }

        boolean hasImage = false;
        if (icon != null) {
            hasImage = true;
            icon.loadDrawableAsync(
                    mContext,
                    new Icon.OnDrawableLoadedListener() {
                        @Override
                        @SuppressLint("SyntheticAccessor")
                        public void onDrawableLoaded(Drawable d) {
                            if (d == null) {
                                return;
                            }
                            mIcon = d;
                            mIcon.mutate();
                            invalidate();
                        }
                    },
                    handler);
        }

        if (burnInProtectionIcon != null) {
            hasImage = true;
            burnInProtectionIcon.loadDrawableAsync(
                    mContext,
                    new Icon.OnDrawableLoadedListener() {
                        @Override
                        @SuppressLint("SyntheticAccessor")
                        public void onDrawableLoaded(Drawable d) {
                            if (d == null) {
                                return;
                            }
                            mBurnInProtectionIcon = d;
                            mBurnInProtectionIcon.mutate();
                            invalidate();
                        }
                    },
                    handler);
        }

        if (smallImage != null) {
            hasImage = true;
            smallImage.loadDrawableAsync(
                    mContext,
                    new Icon.OnDrawableLoadedListener() {
                        @Override
                        @SuppressLint("SyntheticAccessor")
                        public void onDrawableLoaded(Drawable d) {
                            if (d == null) {
                                return;
                            }
                            mSmallImage = d;
                            invalidate();
                        }
                    },
                    handler);
        }

        if (burnInProtectionSmallImage != null) {
            hasImage = true;
            burnInProtectionSmallImage.loadDrawableAsync(
                    mContext,
                    new Icon.OnDrawableLoadedListener() {
                        @Override
                        @SuppressLint("SyntheticAccessor")
                        public void onDrawableLoaded(Drawable d) {
                            if (d == null) {
                                return;
                            }
                            mBurnInProtectionSmallImage = d;
                            invalidate();
                        }
                    },
                    handler);
        }

        if (largeImage != null) {
            hasImage = true;
            largeImage.loadDrawableAsync(
                    mContext,
                    new Icon.OnDrawableLoadedListener() {
                        @Override
                        @SuppressLint("SyntheticAccessor")
                        public void onDrawableLoaded(Drawable d) {
                            if (d == null) {
                                return;
                            }
                            mLargeImage = d;
                            invalidate();
                        }
                    },
                    handler);
        }
        return hasImage;
    }

    /** Synchronously loads any images. */
    private void loadDrawableIconAndImages() {
        Icon icon = null;
        Icon smallImage = null;
        Icon burnInProtectionSmallImage = null;
        Icon largeImage = null;
        Icon burnInProtectionIcon = null;
        mIcon = null;
        mSmallImage = null;
        mBurnInProtectionSmallImage = null;
        mLargeImage = null;
        mBurnInProtectionIcon = null;
        if (mComplicationData != null) {
            icon = mComplicationData.hasIcon() ? mComplicationData.getIcon() : null;
            burnInProtectionIcon = mComplicationData.hasBurnInProtectionIcon()
                    ? mComplicationData.getBurnInProtectionIcon() : null;
            burnInProtectionSmallImage =
                    mComplicationData.hasBurnInProtectionSmallImage()
                            ? mComplicationData.getBurnInProtectionSmallImage() : null;
            smallImage =
                    mComplicationData.hasSmallImage() ? mComplicationData.getSmallImage() : null;
            largeImage =
                    mComplicationData.hasLargeImage() ? mComplicationData.getLargeImage() : null;
        }

        if (icon != null) {
            mIcon = icon.loadDrawable(mContext);
        }

        if (burnInProtectionIcon != null) {
            mBurnInProtectionIcon = burnInProtectionIcon.loadDrawable(mContext);
        }

        if (smallImage != null) {
            mSmallImage = smallImage.loadDrawable(mContext);
        }

        if (burnInProtectionSmallImage != null) {
            mBurnInProtectionSmallImage = burnInProtectionSmallImage.loadDrawable(mContext);
        }

        if (largeImage != null) {
            mLargeImage = largeImage.loadDrawable(mContext);
        }
    }

    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Override
    public Rect getBounds() {
        return mBounds;
    }

    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Override
    public Rect getIconBounds() {
        return mIconBounds;
    }

    @Nullable
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Override
    public Drawable getIcon() {
        return mIcon;
    }

    @Nullable
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Override
    public Drawable getSmallImage() {
        return mSmallImage;
    }

    @Nullable
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Override
    public Drawable getBurnInProtectionIcon() {
        return mBurnInProtectionIcon;
    }

    @Nullable
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Override
    public Drawable getBurnInProtectionSmallImage() {
        return mBurnInProtectionSmallImage;
    }

    @Nullable
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Override
    public RoundedDrawable getRoundedSmallImage() {
        return mRoundedSmallImage;
    }

    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Override
    public Rect getMainTextBounds() {
        return mMainTextBounds;
    }

    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Override
    public Rect getSubTextBounds() {
        return mSubTextBounds;
    }

    /** @param outRect Object that receives the computation of the complication's inner bounds */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Override
    public void getComplicationInnerBounds(@NonNull Rect outRect) {
        LayoutUtils.getInnerBounds(
                outRect,
                mBounds,
                Math.max(getBorderRadius(mActiveStyle), getBorderRadius(mAmbientStyle)));
    }

    @Override
    ComplicationData getComplicationData() {
        return mComplicationData;
    }
}
