package net.kdt.pojavlaunch.customcontrols.mouse;

import static net.kdt.pojavlaunch.Tools.currentDisplayMetrics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.GrabListener;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import org.lwjgl.glfw.CallbackBridge;

/**
 * Class dealing with the virtual mouse
 */
public class Touchpad extends View implements GrabListener, AbstractTouchpad, CursorState.Listener {
    private static final int PRESSED_TINT = 0xFF4ADE80;
    private static final float PRESSED_SCALE = 0.85f;
    /* Size of the shape cursors, before applying the mouse scale setting */
    private static final int SHAPE_SIZE = 48;

    /* Whether the Touchpad should be displayed */
    private boolean mDisplayState;
    /* Mouse pointer icon used by the touchpad */
    private Drawable mMousePointerDrawable;
    private float mMouseX, mMouseY;

    /* Cursor appearance */
    private boolean mDynamic;
    private Drawable mBaseDrawable;
    private float mBaseHotX, mBaseHotY;
    private int mBaseW, mBaseH;
    private final SparseArray<Drawable> mShapeDrawables = new SparseArray<>();
    private int mCursorW, mCursorH;
    private float mHotX, mHotY;
    private boolean mPressed;

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
        // Put the click point of the cursor on the mouse position
        canvas.translate(-mHotX * mCursorW, -mHotY * mCursorH);
        if (mDynamic && mPressed) {
            canvas.scale(PRESSED_SCALE, PRESSED_SCALE, mHotX * mCursorW, mHotY * mCursorH);
        }
        mMousePointerDrawable.draw(canvas);
    }

    /** Use the cursor for the shape the game asked for (dynamic cursor), or the chosen style otherwise */
    private void applyShape(int shape) {
        Drawable shapeDrawable = null;
        if (mDynamic && shape != CursorStyle.SHAPE_ARROW) {
            shapeDrawable = mShapeDrawables.get(shape);
            if (shapeDrawable == null) {
                shapeDrawable = CursorStyle.loadShape(getContext(), shape);
                if (shapeDrawable != null) mShapeDrawables.put(shape, shapeDrawable);
            }
        }
        if (mMousePointerDrawable != null) mMousePointerDrawable.clearColorFilter();
        if (shapeDrawable != null) {
            float[] hot = CursorStyle.shapeHotspot(shape);
            mHotX = hot[0];
            mHotY = hot[1];
            mCursorW = mCursorH = (int) (SHAPE_SIZE * LauncherPreferences.PREF_MOUSESCALE);
            mMousePointerDrawable = shapeDrawable;
        } else {
            mHotX = mBaseHotX;
            mHotY = mBaseHotY;
            mCursorW = mBaseW;
            mCursorH = mBaseH;
            mMousePointerDrawable = mBaseDrawable;
        }
        mMousePointerDrawable.setBounds(0, 0, mCursorW, mCursorH);
        applyPressedTint();
        invalidate();
    }

    private void applyPressedTint() {
        if (mDynamic && mPressed) mMousePointerDrawable.setColorFilter(PRESSED_TINT, PorterDuff.Mode.SRC_ATOP);
        else mMousePointerDrawable.clearColorFilter();
    }

    /** From CursorState, may come from any thread */
    @Override
    public void onPressedChanged(boolean pressed) {
        post(() -> {
            mPressed = pressed;
            applyPressedTint();
            invalidate();
        });
    }

    /** From CursorState, may come from any thread */
    @Override
    public void onShapeChanged(int glfwShape) {
        post(() -> applyShape(glfwShape));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        CursorState.setListener(this);
        // The game may have asked for a shape before we existed
        applyShape(CursorState.getShape());
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        CursorState.setListener(null);
    }

    private void init(){
        // Setup mouse pointer
        String style = LauncherPreferences.PREF_CURSOR_STYLE;
        mDynamic = LauncherPreferences.PREF_CURSOR_DYNAMIC;
        mBaseDrawable = CursorStyle.load(getContext(), style);
        boolean centered = CursorStyle.isCentered(style);
        mBaseHotX = mBaseHotY = centered ? 0.5f : 0f;
        int[] size = CursorStyle.baseSize(style);
        mBaseW = (int) (size[0] * LauncherPreferences.PREF_MOUSESCALE);
        mBaseH = (int) (size[1] * LauncherPreferences.PREF_MOUSESCALE);
        mMousePointerDrawable = mBaseDrawable;
        mHotX = mBaseHotX;
        mHotY = mBaseHotY;
        mCursorW = mBaseW;
        mCursorH = mBaseH;
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
