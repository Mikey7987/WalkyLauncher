package net.kdt.pojavlaunch.modloaders.modpacks;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import net.kdt.pojavlaunch.R;

import java.util.List;

/**
 * The "which profile should content go to" picker, shared by the content screens.
 */
public final class ContentTargetDialog {
    private ContentTargetDialog() {}

    /**
     * @param context a context that can show an AppCompat dialog (an activity or a fragment's context)
     * @param onChanged called after the person picked a profile, the new target is already saved
     */
    public static void show(Context context, Runnable onChanged) {
        List<ContentTarget.Entry> entries = ContentTarget.getEntries();
        if (entries.isEmpty()) return;

        String currentKey = ContentTarget.getTargetKey();
        CharSequence[] labels = new CharSequence[entries.size()];
        int checked = -1;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = entries.get(i).label;
            if (entries.get(i).key.equals(currentKey)) checked = i;
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.content_target_dialog_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    ContentTarget.setTargetKey(entries.get(which).key);
                    onChanged.run();
                    dialog.dismiss();
                })
                .show();
    }
}
