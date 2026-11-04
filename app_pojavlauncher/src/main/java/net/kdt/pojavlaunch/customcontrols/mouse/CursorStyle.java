package net.kdt.pojavlaunch.customcontrols.mouse;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

import java.io.File;

/** The cursor looks the person can choose from, and how to draw them */
public final class CursorStyle {
    private CursorStyle() {}

    public static final String DEFAULT = "default";
    public static final String ARROW = "arrow";
    public static final String DOT = "dot";
    public static final String RING = "ring";
    public static final String CROSSHAIR = "crosshair";
    public static final String CUSTOM = "custom";

    /** Where an imported custom cursor image is stored */
    public static File getCustomFile() {
        return new File(Tools.DIR_DATA, "custom_cursor.png");
    }

    /** Pointer-shaped cursors have their hotspot at the top left, the others at the middle */
    public static boolean isCentered(String style) {
        return DOT.equals(style) || RING.equals(style) || CROSSHAIR.equals(style);
    }

    /** @return the cursor drawable for the style, never null. Falls back to the default pointer. */
    @NonNull
    public static Drawable load(Context context, String style) {
        Drawable d = null;
        if (CUSTOM.equals(style)) {
            File f = getCustomFile();
            if (f.isFile()) {
                android.graphics.Bitmap bm = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (bm != null) d = new BitmapDrawable(context.getResources(), bm);
            }
        } else if (ARROW.equals(style)) {
            d = ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_cursor_arrow, context.getTheme());
        } else if (DOT.equals(style)) {
            d = ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_cursor_dot, context.getTheme());
        } else if (RING.equals(style)) {
            d = ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_cursor_ring, context.getTheme());
        } else if (CROSSHAIR.equals(style)) {
            d = ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_cursor_crosshair, context.getTheme());
        }
        if (d == null) d = ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_mouse_pointer, context.getTheme());
        assert d != null;
        return d.mutate();
    }

    /** Width and height (before scaling) that the cursor is drawn at, in pixels */
    public static int[] baseSize(String style) {
        if (isCentered(style) || CUSTOM.equals(style)) return new int[]{48, 48};
        return new int[]{36, 54};
    }

    /* GLFW standard cursor shapes, the values the game passes through glfwCreateStandardCursor */
    public static final int SHAPE_ARROW = 0x36001;
    public static final int SHAPE_IBEAM = 0x36002;
    public static final int SHAPE_CROSSHAIR = 0x36003;
    public static final int SHAPE_HAND = 0x36004;
    public static final int SHAPE_RESIZE_EW = 0x36005;
    public static final int SHAPE_RESIZE_NS = 0x36006;
    public static final int SHAPE_RESIZE_NWSE = 0x36007;
    public static final int SHAPE_RESIZE_NESW = 0x36008;
    public static final int SHAPE_RESIZE_ALL = 0x36009;
    public static final int SHAPE_NOT_ALLOWED = 0x3600A;

    /** @return the drawable resource for a requested shape, or 0 if the arrow (the chosen style) should be used */
    public static int shapeDrawable(int shape) {
        switch (shape) {
            case SHAPE_IBEAM: return R.drawable.ic_cursor_ibeam;
            case SHAPE_CROSSHAIR: return R.drawable.ic_cursor_crosshair;
            case SHAPE_HAND: return R.drawable.ic_cursor_hand;
            case SHAPE_RESIZE_EW: return R.drawable.ic_cursor_resize_ew;
            case SHAPE_RESIZE_NS: return R.drawable.ic_cursor_resize_ns;
            case SHAPE_RESIZE_NWSE: return R.drawable.ic_cursor_resize_nwse;
            case SHAPE_RESIZE_NESW: return R.drawable.ic_cursor_resize_nesw;
            case SHAPE_RESIZE_ALL: return R.drawable.ic_cursor_move;
            case SHAPE_NOT_ALLOWED: return R.drawable.ic_cursor_forbidden;
            case SHAPE_ARROW:
            default: return 0;
        }
    }

    /** @return the drawable for a requested shape, or null if the arrow (the chosen style) should be used */
    @androidx.annotation.Nullable
    public static Drawable loadShape(Context context, int shape) {
        int res = shapeDrawable(shape);
        if (res == 0) return null;
        Drawable d = ResourcesCompat.getDrawable(context.getResources(), res, context.getTheme());
        return d == null ? null : d.mutate();
    }

    /** @return where the click point of a shape is, as fractions {x, y} of the drawable's size */
    public static float[] shapeHotspot(int shape) {
        if (shape == SHAPE_HAND) return new float[]{0.425f, 0.10f}; // the tip of the finger
        return new float[]{0.5f, 0.5f};
    }
}
