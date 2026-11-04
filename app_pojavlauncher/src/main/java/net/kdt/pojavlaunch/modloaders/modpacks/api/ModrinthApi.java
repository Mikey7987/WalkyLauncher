package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ContentType;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModrinthIndex;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;
import net.kdt.pojavlaunch.utils.GsonJsonUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

public class ModrinthApi implements ModpackApi{
    private static final String TAG = "ModrinthApi";
    /* How many levels of "mod needs mod needs mod" we are willing to follow */
    private static final int MAX_DEPENDENCY_DEPTH = 3;

    private final ApiHandler mApiHandler;
    public ModrinthApi(){
        mApiHandler = new ApiHandler("https://api.modrinth.com/v2");
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        ModrinthSearchResult modrinthSearchResult = (ModrinthSearchResult) previousPageResult;

        // Fixes an issue where the offset being equal or greater than total_hits is ignored
        if (modrinthSearchResult != null && modrinthSearchResult.previousOffset >= modrinthSearchResult.totalResultCount) {
            ModrinthSearchResult emptyResult = new ModrinthSearchResult();
            emptyResult.results = new ModItem[0];
            emptyResult.totalResultCount = modrinthSearchResult.totalResultCount;
            emptyResult.previousOffset = modrinthSearchResult.previousOffset;
            return emptyResult;
        }


        // Build the facets filters
        HashMap<String, Object> params = new HashMap<>();
        StringBuilder facetString = new StringBuilder();
        facetString.append("[");
        facetString.append(String.format("[\"project_type:%s\"]", ContentType.getModrinthProjectType(searchFilters.contentType)));
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            facetString.append(String.format(",[\"versions:%s\"]", searchFilters.mcVersion));
        if(searchFilters.loader != null && ContentType.usesLoaderFilter(searchFilters.contentType))
            facetString.append(String.format(",[\"categories:%s\"]", searchFilters.loader));
        facetString.append("]");
        params.put("facets", facetString.toString());
        params.put("query", searchFilters.name);
        params.put("limit", 50);
        params.put("index", "relevance");
        if(modrinthSearchResult != null)
            params.put("offset", modrinthSearchResult.previousOffset);

        JsonObject response = mApiHandler.get("search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray responseHits = response.getAsJsonArray("hits");
        if(responseHits == null) return null;

        ModItem[] items = new ModItem[responseHits.size()];
        for(int i=0; i<responseHits.size(); ++i){
            JsonObject hit = responseHits.get(i).getAsJsonObject();
            // Small projects often have no icon, and the API sends a null for them.
            // The empty string makes the icon loader fail cleanly instead of crashing.
            String iconUrl = GsonJsonUtils.getStringSafe(hit, "icon_url");
            ModItem item = new ModItem(
                    Constants.SOURCE_MODRINTH,
                    searchFilters.contentType,
                    hit.get("project_id").getAsString(),
                    hit.get("title").getAsString(),
                    hit.get("description").getAsString(),
                    iconUrl != null ? iconUrl : ""
            );
            item.filterMcVersion = searchFilters.mcVersion;
            item.filterLoader = searchFilters.loader;
            items[i] = item;
        }
        if(modrinthSearchResult == null) modrinthSearchResult = new ModrinthSearchResult();
        modrinthSearchResult.previousOffset += responseHits.size();
        modrinthSearchResult.results = items;
        modrinthSearchResult.totalResultCount = response.get("total_hits").getAsInt();
        return modrinthSearchResult;
    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        HashMap<String, Object> params = new HashMap<>();
        if(item.filterLoader != null && ContentType.usesLoaderFilter(item.contentType))
            params.put("loaders", "[\"" + item.filterLoader + "\"]");
        if(item.filterMcVersion != null && !item.filterMcVersion.isEmpty())
            params.put("game_versions", "[\"" + item.filterMcVersion + "\"]");

        String endpoint = String.format("project/%s/version", item.id);
        JsonArray response = params.isEmpty()
                ? mApiHandler.get(endpoint, JsonArray.class)
                : mApiHandler.get(endpoint, params, JsonArray.class);
        // Nothing here also means "nothing compatible with the filters"
        if(response == null || response.size() == 0) return null;

        ArrayList<String> names = new ArrayList<>(response.size());
        ArrayList<String> mcNames = new ArrayList<>(response.size());
        ArrayList<String> urls = new ArrayList<>(response.size());
        ArrayList<String> hashes = new ArrayList<>(response.size());
        ArrayList<String> ids = new ArrayList<>(response.size());

        for (JsonElement versionElement : response) {
            JsonObject version = GsonJsonUtils.getJsonObjectSafe(versionElement);
            if(version == null) continue;
            JsonObject file = getPrimaryFile(version);
            String url = GsonJsonUtils.getStringSafe(file, "url");
            if(url == null) continue;

            String name = GsonJsonUtils.getStringSafe(version, "name");
            if(name == null || name.isEmpty()) name = GsonJsonUtils.getStringSafe(version, "version_number");
            if(name == null) name = item.title;

            names.add(name);
            mcNames.add(pickGameVersion(version, item.filterMcVersion));
            urls.add(url);
            // Assume there may not be hashes, in case the API changes
            hashes.add(GsonJsonUtils.getStringSafe(GsonJsonUtils.getJsonObjectSafe(file, "hashes"), "sha1"));
            ids.add(GsonJsonUtils.getStringSafe(version, "id"));
        }
        if(names.isEmpty()) return null;

        ModDetail detail = new ModDetail(item,
                names.toArray(new String[0]),
                mcNames.toArray(new String[0]),
                urls.toArray(new String[0]),
                hashes.toArray(new String[0]));
        detail.versionIds = ids.toArray(new String[0]);
        return detail;
    }

    @Override
    public ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException{
        if(ContentType.isSingleFile(modDetail.contentType)) {
            installSingleFile(modDetail, selectedVersion);
            return null; // nothing else to install afterwards
        }
        return ModpackInstaller.installModpack(modDetail, selectedVersion, this::installMrpack);
    }

    /** The file marked as primary, or the first one if none is */
    private static @Nullable JsonObject getPrimaryFile(JsonObject version) {
        JsonArray files = GsonJsonUtils.getJsonArraySafe(version, "files");
        if(files == null || files.size() == 0) return null;
        for(JsonElement fileElement : files) {
            JsonObject file = GsonJsonUtils.getJsonObjectSafe(fileElement);
            if(file == null) continue;
            JsonElement primary = GsonJsonUtils.getElementSafe(file, "primary");
            if(primary != null && primary.isJsonPrimitive() && primary.getAsBoolean()) return file;
        }
        return GsonJsonUtils.getJsonObjectSafe(files.get(0));
    }

    /** The Minecraft version to show for a version: the one that was searched for, or the first listed */
    private static @Nullable String pickGameVersion(JsonObject version, @Nullable String preferred) {
        JsonArray gameVersions = GsonJsonUtils.getJsonArraySafe(version, "game_versions");
        if(gameVersions == null || gameVersions.size() == 0) return null;
        if(preferred != null && !preferred.isEmpty()) {
            for(JsonElement gameVersion : gameVersions) {
                if(preferred.equals(gameVersion.getAsString())) return preferred;
            }
        }
        return gameVersions.get(0).getAsString();
    }

    private void installSingleFile(ModDetail detail, int selectedVersion) throws IOException {
        File installed = ContentInstaller.installSelected(detail, selectedVersion);
        Log.i(TAG, "Installed " + installed);
        // Only mods depend on other mods
        if(detail.contentType != ContentType.MOD) return;
        if(detail.versionIds == null || detail.versionIds[selectedVersion] == null) return;

        Set<String> handledProjects = new HashSet<>();
        handledProjects.add(detail.id);
        try {
            installDependencies(detail.versionIds[selectedVersion], detail.filterMcVersion,
                    detail.filterLoader, ContentInstaller.getDestinationDirectory(ContentType.MOD),
                    handledProjects, 0);
        }catch (RuntimeException e) {
            // Dependencies are a best effort thing: surprising data from the API must not
            // make us report the whole installation as failed
            Log.w(TAG, "Unable to resolve dependencies", e);
        }
    }

    /**
     * Download the required dependencies of a mod version into the mods folder.
     * Optional and incompatible dependencies are ignored.
     */
    private void installDependencies(String versionId, @Nullable String preferredMcVersion,
                                     @Nullable String preferredLoader, File modsDir,
                                     Set<String> handledProjects, int depth) throws IOException {
        if(depth >= MAX_DEPENDENCY_DEPTH) return;
        JsonObject version = mApiHandler.get("version/" + versionId, JsonObject.class);
        JsonArray dependencies = GsonJsonUtils.getJsonArraySafe(version, "dependencies");
        if(dependencies == null) return;

        String mcVersion = preferredMcVersion;
        if(mcVersion == null || mcVersion.isEmpty()) mcVersion = pickGameVersion(version, null);
        String loadersFilter;
        if(preferredLoader != null) {
            loadersFilter = "[\"" + preferredLoader + "\"]";
        }else {
            JsonArray loaders = GsonJsonUtils.getJsonArraySafe(version, "loaders");
            loadersFilter = loaders != null && loaders.size() > 0 ? loaders.toString() : null;
        }

        for(JsonElement dependencyElement : dependencies) {
            JsonObject dependency = GsonJsonUtils.getJsonObjectSafe(dependencyElement);
            if(dependency == null) continue;
            if(!"required".equals(GsonJsonUtils.getStringSafe(dependency, "dependency_type"))) continue;

            String projectId = GsonJsonUtils.getStringSafe(dependency, "project_id");
            if(projectId != null && !handledProjects.add(projectId)) continue;

            String dependencyVersionId = GsonJsonUtils.getStringSafe(dependency, "version_id");
            JsonObject dependencyVersion = null;
            if(dependencyVersionId != null) {
                dependencyVersion = mApiHandler.get("version/" + dependencyVersionId, JsonObject.class);
            }else if(projectId != null) {
                dependencyVersion = findCompatibleVersion(projectId, mcVersion, loadersFilter);
            }
            if(dependencyVersion == null) {
                Log.w(TAG, "No compatible version found for the dependency " + projectId);
                continue;
            }

            JsonObject file = getPrimaryFile(dependencyVersion);
            String url = GsonJsonUtils.getStringSafe(file, "url");
            if(url == null) continue;
            String sha1 = GsonJsonUtils.getStringSafe(GsonJsonUtils.getJsonObjectSafe(file, "hashes"), "sha1");
            ContentInstaller.downloadInto(modsDir, url, sha1, ContentInstaller.getSafeFileName(url));

            String installedVersionId = GsonJsonUtils.getStringSafe(dependencyVersion, "id");
            if(installedVersionId != null) {
                installDependencies(installedVersionId, mcVersion, preferredLoader, modsDir,
                        handledProjects, depth + 1);
            }
        }
    }

    private @Nullable JsonObject findCompatibleVersion(String projectId, @Nullable String mcVersion,
                                                       @Nullable String loadersFilter) {
        HashMap<String, Object> params = new HashMap<>();
        if(loadersFilter != null) params.put("loaders", loadersFilter);
        if(mcVersion != null && !mcVersion.isEmpty()) params.put("game_versions", "[\"" + mcVersion + "\"]");
        String endpoint = "project/" + projectId + "/version";
        JsonArray versions = params.isEmpty()
                ? mApiHandler.get(endpoint, JsonArray.class)
                : mApiHandler.get(endpoint, params, JsonArray.class);
        if(versions == null || versions.size() == 0) return null;
        // The newest version comes first
        return GsonJsonUtils.getJsonObjectSafe(versions.get(0));
    }

    private static ModLoader createInfo(ModrinthIndex modrinthIndex) {
        if(modrinthIndex == null) return null;
        Map<String, String> dependencies = modrinthIndex.dependencies;
        String mcVersion = dependencies.get("minecraft");
        if(mcVersion == null) return null;
        String modLoaderVersion;
        if((modLoaderVersion = dependencies.get("forge")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_FORGE, modLoaderVersion, mcVersion);
        }
        if((modLoaderVersion = dependencies.get("fabric-loader")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_FABRIC, modLoaderVersion, mcVersion);
        }
        if((modLoaderVersion = dependencies.get("quilt-loader")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_QUILT, modLoaderVersion, mcVersion);
        }
        return null;
    }

    private ModLoader installMrpack(File mrpackFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(mrpackFile)){
            ModrinthIndex modrinthIndex = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "modrinth.index.json")),
                    ModrinthIndex.class);

            ModDownloader modDownloader = new ModDownloader(instanceDestination);
            for(ModrinthIndex.ModrinthIndexFile indexFile : modrinthIndex.files) {
                modDownloader.submitDownload(indexFile.fileSize, indexFile.path, indexFile.hashes.sha1, indexFile.downloads);
            }
            modDownloader.awaitFinish(new DownloaderProgressWrapper(R.string.modpack_download_downloading_mods, ProgressLayout.INSTALL_MODPACK));
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.modpack_download_applying_overrides, 1, 2);
            ZipUtils.zipExtract(modpackZipFile, "overrides/", instanceDestination);
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 50, R.string.modpack_download_applying_overrides, 2, 2);
            ZipUtils.zipExtract(modpackZipFile, "client-overrides/", instanceDestination);
            return createInfo(modrinthIndex);
        }
    }

    class ModrinthSearchResult extends SearchResult {
        int previousOffset;
    }
}
