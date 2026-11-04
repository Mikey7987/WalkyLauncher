package net.kdt.pojavlaunch.customcontrols.mouse;

import static net.kdt.pojavlaunch.Tools.currentDisplayMetrics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.GrabListener;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import org.lwjgl.glfw.CallbackBridge;

/**
 * Class dealing with the virtual mouse
 */
public class Touchpad extends View implements GrabListener, AbstractTouchpad {
    /* Whether the Touchpad should be displayed */
    private boolean mDisplayState;
    /* Mouse pointer icon used by the touchpad */
    private Drawable mMousePointerDrawable;
    private float mMouseX, mMouseY;
    /* Dynamic cursor state */
    private static final long IDLE_FADE_DELAY_MS = 2500;
    private static final int PRESSED_TINT = 0xFF4ADE80;
    private boolean mDynamic;
    private boolean mCentered;
    private int mCursorW, mCursorH;
    private boolean mPressed;
    private boolean mMoving;
    private final Runnable mIdleRunnable = () -> {
        mMoving = false;
        animate().alpha(0.45f).setDuration(400).start();
        invalidate();
    };
    private final Runnable mStopMovingRunnable = () -> {
        mMoving = false;
        invalidate();
    };
    public Touchpad(@NonNull Context context) {
        this(context, null);
    }

    public Touchpad(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /** Enable the touchpad */
    private void _enable(){
        setVisibility(VISIBLE);
        if (mDynamic) wake();
        placeMouseAt(currentDisplayMetrics.widthPixels / 2f, currentDisplayMetrics.heightPixels / 2f);
    }

    /** Disable the touchpad and hides the mouse */
    private void _disable(){
        setVisibility(GONE);
    }

    /** @return The new state, enabled or disabled */
    public boolean switchState(){
        mDisplayState = !mDisplayState;
        if(!CallbackBridge.isGrabbing()) {
            if(mDisplayState) _enable();
            else _disable();
        }
        return mDisplayState;
    }

    public void placeMouseAt(float x, float y) {
        mMouseX = x;
        mMouseY = y;
        updateMousePosition();
    }

    private void sendMousePosition() {
        CallbackBridge.sendCursorPos((mMouseX * LauncherPreferences.PREF_SCALE_FACTOR), (mMouseY * LauncherPreferences.PREF_SCALE_FACTOR));
    }

    private void updateMousePosition() {
        sendMousePosition();
        // I wanted to implement a dirty rect for this, but it is ignored since API level 21
        // (which is our min API)
        // Let's hope the "internally calculated area" is good enough.
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.translate(mMouseX, mMouseY);
        // Centered cursors have their hotspot in the middle, pointers at the top left
        if (mCentered) canvas.translate(-mCursorW / 2f, -mCursorH / 2f);
        if (mDynamic) {
            float scale = mPressed ? 0.8f : (mMoving ? 1.12f : 1f);
            canvas.scale(scale, scale, mCentered ? mCursorW / 2f : 0f, mCentered ? mCursorH / 2f : 0f);
        }
        mMousePointerDrawable.draw(canvas);
    }

    /** Called by CursorState, may come from any thread */
    private void onPressedChanged(boolean pressed) {
        post(() -> {
            mPressed = pressed;
            if (mDynamic) {
                if (pressed) mMousePointerDrawable.setColorFilter(PRESSED_TINT, PorterDuff.Mode.SRC_ATOP);
                else mMousePointerDrawable.clearColorFilter();
                wake();
            }
            invalidate();
        });
    }

    /** Bring the cursor back to full opacity and restart the idle timer */
    private void wake() {
        removeCallbacks(mIdleRunnable);
        animate().cancel();
        setAlpha(1f);
        postDelayed(mIdleRunnable, IDLE_FADE_DELAY_MS);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        CursorState.setListener(this::onPressedChanged);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        CursorState.setListener(null);
        removeCallbacks(mIdleRunnable);
        removeCallbacks(mStopMovingRunnable);
    }

    private void init(){
        // Setup mouse pointer
        String style = LauncherPreferences.PREF_CURSOR_STYLE;
        mDynamic = LauncherPreferences.PREF_CURSOR_DYNAMIC;
        mCentered = CursorStyle.isCentered(style);
        mMousePointerDrawable = CursorStyle.load(getContext(), style);
        int[] size = CursorStyle.baseSize(style);
        mCursorW = (int) (size[0] * LauncherPreferences.PREF_MOUSESCALE);
        mCursorH = (int) (size[1] * LauncherPreferences.PREF_MOUSESCALE);
        mMousePointerDrawable.setBounds(0, 0, mCursorW, mCursorH);
        setFocusable(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setDefaultFocusHighlightEnabled(false);
        }

        // When the game is grabbing, we should not display the mouse
        disable();
        mDisplayState = false;
    }

    @Override
    public void onGrabState(boolean isGrabbing) {
        post(()->updateGrabState(isGrabbing));
    }
    private void updateGrabState(boolean isGrabbing) {
        if(!isGrabbing) {
            if(mDisplayState && getVisibility() != VISIBLE) _enable();
            if(!mDisplayState && getVisibility() == VISIBLE) _disable();
        }else{
            if(getVisibility() != View.GONE) _disable();
        }
    }

    @Override
    public boolean getDisplayState() {
        return mDisplayState;
    }

    @Override
    public void applyMotionVector(float x, float y) {
        mMouseX = Math.max(0, Math.min(currentDisplayMetrics.widthPixels, mMouseX + x * LauncherPreferences.PREF_MOUSESPEED));
        mMouseY = Math.max(0, Math.min(currentDisplayMetrics.heightPixels, mMouseY + y * LauncherPreferences.PREF_MOUSESPEED));
        if (mDynamic) {
            mMoving = true;
            wake();
            removeCallbacks(mStopMovingRunnable);
            postDelayed(mStopMovingRunnable, 150);
        }
        updateMousePosition();
    }

    @Override
    public void enable(boolean supposed) {
        if(mDisplayState) return;
        mDisplayState = true;
        if(supposed && CallbackBridge.isGrabbing()) return;
        _enable();
    }

    @Override
    public void disable() {
        if(!mDisplayState) return;
        mDisplayState = false;
        _disable();
    }
}
