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
}
