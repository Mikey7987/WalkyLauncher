package net.kdt.pojavlaunch.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.ContentTarget;
import net.kdt.pojavlaunch.modloaders.modpacks.ContentTargetDialog;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ContentInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ContentType;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lists what is installed in a profile (mods, resource packs, shaders), and lets the person
 * enable/disable, delete, or add files from the device.
 */
public class InstalledContentFragment extends Fragment {
    public static final String TAG = "InstalledContentFragment";
    private static final String DISABLED_SUFFIX = ".disabled";

    private static final int[] CHIP_IDS = {
            R.id.installed_type_mod, R.id.installed_type_resourcepack, R.id.installed_type_shader};
    private static final int[] CHIP_TYPES = {
            ContentType.MOD, ContentType.RESOURCE_PACK, ContentType.SHADER};

    private int mContentType = ContentType.MOD;
    private TextView[] mChips;
    private TextView mTargetText;
    private TextView mEmptyText;
    private RecyclerView mList;
    private final ItemAdapter mAdapter = new ItemAdapter();

    private final ActivityResultLauncher<String[]> mPicker =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), this::importUris);

    public InstalledContentFragment() {
        super(R.layout.fragment_installed_content);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mTargetText = view.findViewById(R.id.installed_target_text);
        mEmptyText = view.findViewById(R.id.installed_empty);
        mList = view.findViewById(R.id.installed_list);
        mList.setLayoutManager(new LinearLayoutManager(requireContext()));
        mList.setAdapter(mAdapter);

        mChips = new TextView[CHIP_IDS.length];
        for (int i = 0; i < CHIP_IDS.length; i++) {
            final int type = CHIP_TYPES[i];
            mChips[i] = view.findViewById(CHIP_IDS[i]);
            mChips[i].setOnClickListener(v -> {
                mContentType = type;
                refresh();
            });
        }
        view.findViewById(R.id.installed_target_change)
                .setOnClickListener(v -> ContentTargetDialog.show(requireContext(), this::refresh));
        view.findViewById(R.id.installed_add_button)
                .setOnClickListener(v -> mPicker.launch(new String[]{"*/*"}));
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private File getFolder() {
        return ContentInstaller.getDestinationDirectory(mContentType);
    }

    private void refresh() {
        for (int i = 0; i < mChips.length; i++) mChips[i].setSelected(CHIP_TYPES[i] == mContentType);
        mTargetText.setText(getString(R.string.content_installed_profile,
                ContentTarget.getLabel(ContentTarget.getTargetKey())));

        File[] files = getFolder().listFiles();
        List<File> entries = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (!f.isFile() || f.getName().endsWith(".part")) continue;
                if (isContentFile(f.getName())) entries.add(f);
            }
        }
        Collections.sort(entries, (a, b) -> baseName(a).compareToIgnoreCase(baseName(b)));
        mAdapter.setFiles(entries);
        mEmptyText.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static boolean isDisabled(File f) {
        return f.getName().endsWith(DISABLED_SUFFIX);
    }

    private static String enabledName(File f) {
        String n = f.getName();
        return isDisabled(f) ? n.substring(0, n.length() - DISABLED_SUFFIX.length()) : n;
    }

    private static String baseName(File f) {
        return enabledName(f);
    }

    private boolean isContentFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(DISABLED_SUFFIX)) lower = lower.substring(0, lower.length() - DISABLED_SUFFIX.length());
        return mContentType == ContentType.MOD ? lower.endsWith(".jar") : lower.endsWith(".zip");
    }

    private void toggle(File file) {
        File target = isDisabled(file)
                ? new File(file.getParentFile(), enabledName(file))
                : new File(file.getParentFile(), file.getName() + DISABLED_SUFFIX);
        if (target.exists() || !file.renameTo(target)) {
            Toast.makeText(requireContext(),
                    getString(R.string.content_installed_toggle_failed, enabledName(file)),
                    Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    private void confirmDelete(File file) {
        String name = enabledName(file);
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.content_installed_delete_title, name))
                .setMessage(R.string.content_installed_delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.content_installed_delete, (d, w) -> {
                    if (file.delete()) {
                        Toast.makeText(requireContext(),
                                getString(R.string.content_installed_deleted, name),
                                Toast.LENGTH_SHORT).show();
                    }
                    refresh();
                })
                .show();
    }

    /** Copy the files the person picked into the current folder, on a background thread */
    private void importUris(@Nullable List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        final File folder = getFolder();
        final int type = mContentType;
        final android.content.Context appContext = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            int added = 0, failed = 0;
            for (Uri uri : uris) {
                if (copyOne(appContext, uri, folder, type)) added++; else failed++;
            }
            final int a = added, f = failed;
            Tools.runOnUiThread(() -> {
                if (a > 0) Toast.makeText(appContext,
                        appContext.getString(R.string.content_installed_added, a), Toast.LENGTH_SHORT).show();
                if (f > 0) Toast.makeText(appContext,
                        appContext.getString(R.string.content_installed_add_failed, f), Toast.LENGTH_LONG).show();
                if (isAdded()) refresh();
            });
        });
    }

    private static boolean copyOne(android.content.Context ctx, Uri uri, File folder, int type) {
        String displayName = null;
        try (android.database.Cursor c = ctx.getContentResolver()
                .query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) displayName = c.getString(0);
        } catch (RuntimeException ignored) {}
        if (displayName == null) displayName = uri.getLastPathSegment();
        if (displayName == null) return false;

        String name = ContentInstaller.sanitizeFileName(displayName);
        String lower = name.toLowerCase(Locale.ROOT);
        if (!(type == ContentType.MOD ? lower.endsWith(".jar") : lower.endsWith(".zip"))) return false;

        File part = new File(folder, name + ".part");
        try {
            FileUtils.ensureDirectory(folder);
            try (InputStream in = ctx.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(part)) {
                if (in == null) return false;
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
            File dest = new File(folder, name);
            if (dest.exists() && !dest.delete()) return false;
            return part.renameTo(dest);
        } catch (IOException | RuntimeException e) {
            return false;
        } finally {
            if (part.exists()) //noinspection ResultOfMethodCallIgnored
                part.delete();
        }
    }

    private class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.Holder> {
        private List<File> mFiles = Collections.emptyList();

        void setFiles(List<File> files) {
            mFiles = files;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_installed_content, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            File file = mFiles.get(position);
            boolean disabled = isDisabled(file);
            h.name.setText(enabledName(file));
            String size = Formatter.formatShortFileSize(h.itemView.getContext(), file.length());
            h.meta.setText(disabled ? size + " · " + getString(R.string.content_installed_disabled) : size);
            h.name.setAlpha(disabled ? 0.5f : 1f);
            // Detach the listener first, so that rebinding doesn't trigger a toggle
            h.toggle.setOnCheckedChangeListener(null);
            h.toggle.setChecked(!disabled);
            // Resource packs and shaders are listed by the game itself, a ".disabled" file just hides them
            h.toggle.setOnCheckedChangeListener((b, checked) -> toggle(file));
            h.delete.setOnClickListener(v -> confirmDelete(file));
        }

        @Override
        public int getItemCount() {
            return mFiles.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView name, meta;
            final SwitchCompat toggle;
            final ImageButton delete;

            Holder(View v) {
                super(v);
                name = v.findViewById(R.id.installed_item_name);
                meta = v.findViewById(R.id.installed_item_meta);
                toggle = v.findViewById(R.id.installed_item_switch);
                delete = v.findViewById(R.id.installed_item_delete);
            }
        }
    }
}
