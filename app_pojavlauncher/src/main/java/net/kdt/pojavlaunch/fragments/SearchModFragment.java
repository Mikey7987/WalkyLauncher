package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.math.MathUtils;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.modloaders.modpacks.ContentTarget;
import net.kdt.pojavlaunch.modloaders.modpacks.ContentTargetDialog;
import net.kdt.pojavlaunch.modloaders.modpacks.ModItemAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ContentType;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

public class SearchModFragment extends Fragment implements ModItemAdapter.SearchResultCallback {

    public static final String TAG = "SearchModFragment";
    /** Optional int argument: the {@link ContentType} to open the screen on. Defaults to modpacks. */
    public static final String ARG_CONTENT_TYPE = "content_type";

    private static final int[] TYPE_CHIP_IDS = {
            R.id.search_mod_type_modpack,
            R.id.search_mod_type_mod,
            R.id.search_mod_type_resourcepack,
            R.id.search_mod_type_shader
    };
    /* Same order as the chips */
    private static final int[] TYPE_ORDER = {
            ContentType.MODPACK,
            ContentType.MOD,
            ContentType.RESOURCE_PACK,
            ContentType.SHADER
    };

    private View mOverlay;
    private float mOverlayTopCache; // Padding cache reduce resource lookup

    private final RecyclerView.OnScrollListener mOverlayPositionListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            mOverlay.setY(MathUtils.clamp(mOverlay.getY() - dy, -mOverlay.getHeight(), mOverlayTopCache));
        }
    };

    private EditText mSearchEditText;
    private ImageButton mFilterButton;
    private RecyclerView mRecyclerview;
    private ModItemAdapter mModItemAdapter;
    private ProgressBar mSearchProgressBar;
    private TextView mStatusTextView;
    private TextView[] mTypeChips;
    private TextView mTargetText;
    private TextView mTargetChange;
    private ColorStateList mDefaultTextColor;

    private ModpackApi modpackApi;

    /* What the controls currently show. Searches get their own copy, see searchMods() */
    private final SearchFilters mSearchFilters;

    public SearchModFragment(){
        super(R.layout.fragment_mod_search);
        mSearchFilters = new SearchFilters();
        mSearchFilters.contentType = ContentType.MODPACK;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        modpackApi = new CommonApi(context.getString(R.string.curseforge_api_key));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // You can only access resources after attaching to current context
        mModItemAdapter = new ModItemAdapter(getResources(), modpackApi, this);
        ProgressKeeper.addTaskCountListener(mModItemAdapter);
        mOverlayTopCache = getResources().getDimension(R.dimen.fragment_padding_medium);

        mOverlay = view.findViewById(R.id.search_mod_overlay);
        mSearchEditText = view.findViewById(R.id.search_mod_edittext);
        mSearchProgressBar = view.findViewById(R.id.search_mod_progressbar);
        mRecyclerview = view.findViewById(R.id.search_mod_list);
        mStatusTextView = view.findViewById(R.id.search_mod_status_text);
        mFilterButton = view.findViewById(R.id.search_mod_filter);
        mTargetText = view.findViewById(R.id.search_mod_target_text);
        mTargetChange = view.findViewById(R.id.search_mod_target_change);

        mDefaultTextColor = mStatusTextView.getTextColors();

        mRecyclerview.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerview.setAdapter(mModItemAdapter);

        mRecyclerview.addOnScrollListener(mOverlayPositionListener);

        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            searchMods(mSearchEditText.getText().toString());
            mSearchEditText.clearFocus();
            return false;
        });

        mOverlay.post(()->{
           int overlayHeight = mOverlay.getHeight();
           mRecyclerview.setPadding(mRecyclerview.getPaddingLeft(),
                   mRecyclerview.getPaddingTop() + overlayHeight,
                   mRecyclerview.getPaddingRight(),
                   mRecyclerview.getPaddingBottom());
        });
        mFilterButton.setOnClickListener(v -> displayFilterDialog());

        mTypeChips = new TextView[TYPE_CHIP_IDS.length];
        for (int i = 0; i < TYPE_CHIP_IDS.length; i++) {
            final int contentType = TYPE_ORDER[i];
            mTypeChips[i] = view.findViewById(TYPE_CHIP_IDS[i]);
            mTypeChips[i].setOnClickListener(v -> {
                if (mSearchFilters.contentType == contentType) return;
                applyContentType(contentType);
                searchMods(mSearchEditText.getText().toString());
            });
        }
        mTargetChange.setOnClickListener(v -> displayTargetDialog());

        Bundle arguments = getArguments();
        applyContentType(arguments != null
                ? arguments.getInt(ARG_CONTENT_TYPE, ContentType.MODPACK)
                : ContentType.MODPACK);
        searchMods(null);
    }

    @Override
    public void onResume() {
        super.onResume();
        // The target profile may have changed while we were away
        if (mTargetText != null) updateTargetBar();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ProgressKeeper.removeTaskCountListener(mModItemAdapter);
        mRecyclerview.removeOnScrollListener(mOverlayPositionListener);
    }

    @Override
    public void onSearchFinished() {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.GONE);
    }

    @Override
    public void onSearchError(int error) {
        boolean modpacks = mSearchFilters.contentType == ContentType.MODPACK;
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.VISIBLE);
        switch (error) {
            case ERROR_INTERNAL:
                mStatusTextView.setTextColor(Color.RED);
                mStatusTextView.setText(modpacks ? R.string.search_modpack_error : R.string.search_content_error);
                break;
            case ERROR_NO_RESULTS:
                mStatusTextView.setTextColor(mDefaultTextColor);
                mStatusTextView.setText(modpacks ? R.string.search_modpack_no_result : R.string.search_content_no_result);
                break;
        }
    }

    /** Switch what the controls show for a new kind of content. Does not search. */
    private void applyContentType(int contentType) {
        mSearchFilters.contentType = contentType;
        // The loader only means something for mods and modpacks
        if (!ContentType.usesLoaderFilter(contentType)) mSearchFilters.loader = null;

        for (int i = 0; i < mTypeChips.length; i++) {
            mTypeChips[i].setSelected(TYPE_ORDER[i] == contentType);
        }
        mSearchEditText.setHint(getSearchHint(contentType));
        updateTargetBar();
    }

    private int getSearchHint(int contentType) {
        switch (contentType) {
            case ContentType.MOD: return R.string.hint_search_mods;
            case ContentType.RESOURCE_PACK: return R.string.hint_search_resource_packs;
            case ContentType.SHADER: return R.string.hint_search_shaders;
            case ContentType.MODPACK:
            default: return R.string.hint_search_modpack;
        }
    }

    private void updateTargetBar() {
        if (ContentType.isSingleFile(mSearchFilters.contentType)) {
            mTargetText.setText(getString(R.string.content_target_install_to,
                    ContentTarget.getLabel(ContentTarget.getTargetKey())));
            mTargetChange.setVisibility(View.VISIBLE);
        } else {
            mTargetText.setText(R.string.content_target_modpack_hint);
            // INVISIBLE and not GONE, to keep the overlay the same height
            mTargetChange.setVisibility(View.INVISIBLE);
        }
    }

    private void searchMods(String name) {
        mSearchProgressBar.setVisibility(View.VISIBLE);
        // Give the search its own copy. The controls may change while it is still running.
        SearchFilters filters = new SearchFilters();
        filters.contentType = mSearchFilters.contentType;
        filters.name = name == null ? "" : name;
        filters.mcVersion = mSearchFilters.mcVersion;
        filters.loader = mSearchFilters.loader;
        mModItemAdapter.performSearchQuery(filters);
    }

    private void displayTargetDialog() {
        ContentTargetDialog.show(requireContext(), this::updateTargetBar);
    }

    private void displayFilterDialog() {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(R.layout.dialog_mod_filters)
                .create();

        // setup the view behavior
        dialog.setOnShowListener(dialogInterface -> {
            TextView mSelectedVersion = dialog.findViewById(R.id.search_mod_selected_mc_version_textview);
            Button mSelectVersionButton = dialog.findViewById(R.id.search_mod_mc_version_button);
            Button mApplyButton = dialog.findViewById(R.id.search_mod_apply_filters);
            View mLoaderLabel = dialog.findViewById(R.id.search_mod_loader_label);
            Spinner mLoaderSpinner = dialog.findViewById(R.id.search_mod_loader_spinner);

            assert mSelectVersionButton != null;
            assert mSelectedVersion != null;
            assert mApplyButton != null;
            assert mLoaderLabel != null;
            assert mLoaderSpinner != null;

            // Setup the expendable list behavior
            mSelectVersionButton.setOnClickListener(v -> VersionSelectorDialog.open(v.getContext(), true, (id, snapshot)-> mSelectedVersion.setText(id)));

            // Apply visually all the current settings
            mSelectedVersion.setText(mSearchFilters.mcVersion);

            boolean usesLoader = ContentType.usesLoaderFilter(mSearchFilters.contentType);
            mLoaderLabel.setVisibility(usesLoader ? View.VISIBLE : View.GONE);
            mLoaderSpinner.setVisibility(usesLoader ? View.VISIBLE : View.GONE);
            String[] loaderValues = getResources().getStringArray(R.array.content_loader_values);
            for (int i = 0; i < loaderValues.length; i++) {
                if (loaderValues[i].equals(mSearchFilters.loader)) {
                    mLoaderSpinner.setSelection(i);
                    break;
                }
            }

            // Apply the new settings
            mApplyButton.setOnClickListener(v -> {
                mSearchFilters.mcVersion = mSelectedVersion.getText().toString();
                String loader = loaderValues[mLoaderSpinner.getSelectedItemPosition()];
                mSearchFilters.loader = usesLoader && !loader.isEmpty() ? loader : null;
                searchMods(mSearchEditText.getText().toString());
                dialogInterface.dismiss();
            });
        });


        dialog.show();
    }
}
