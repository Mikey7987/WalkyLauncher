package net.kdt.pojavlaunch.customcontrols.mouse;

import androidx.annotation.Nullable;

import org.lwjgl.glfw.CallbackBridge;

/**
 * Tracks whether a virtual mouse button is held, so the cursor can react to clicks.
 * CallbackBridge reports every mouse button event here.
 */
public final class CursorState {
    private CursorState() {}

    public interface Listener {
        void onPressedChanged(boolean pressed);
    }

    private static volatile int sButtonsDown = 0;
    private static volatile @Nullable Listener sListener;

    public static void setListener(@Nullable Listener listener) {
        sListener = listener;
    }

    public static boolean isPressed() {
        return sButtonsDown != 0;
    }

    /** @param button GLFW mouse button (0 left, 1 right, 2 middle) */
    public static void onMouseButton(int button, boolean isDown) {
        if (button < 0 || button > 7) return;
        boolean before = sButtonsDown != 0;
        if (isDown) sButtonsDown |= (1 << button);
        else sButtonsDown &= ~(1 << button);
        boolean after = sButtonsDown != 0;
        Listener l = sListener;
        if (before != after && l != null) l.onPressedChanged(after);
    }
}
