package net.kdt.pojavlaunch.customcontrols.mouse;

import androidx.annotation.Nullable;

/**
 * What the game wants the cursor to look like right now: whether a mouse button is held,
 * and which GLFW cursor shape was requested (glfwSetCursor). CallbackBridge reports both here.
 */
public final class CursorState {
    private CursorState() {}

    public interface Listener {
        void onPressedChanged(boolean pressed);
        void onShapeChanged(int glfwShape);
    }

    private static volatile int sButtonsDown = 0;
    private static volatile int sShape = CursorStyle.SHAPE_ARROW;
    private static volatile @Nullable Listener sListener;

    public static void setListener(@Nullable Listener listener) {
        sListener = listener;
    }

    public static boolean isPressed() {
        return sButtonsDown != 0;
    }

    public static int getShape() {
        return sShape;
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

    /** @param glfwShape one of the GLFW_*_CURSOR constants */
    public static void onShape(int glfwShape) {
        if (glfwShape == sShape) return;
        sShape = glfwShape;
        Listener l = sListener;
        if (l != null) l.onShapeChanged(glfwShape);
    }
}
