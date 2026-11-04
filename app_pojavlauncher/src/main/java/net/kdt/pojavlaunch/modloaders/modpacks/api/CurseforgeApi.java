package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ContentType;
import net.kdt.pojavlaunch.modloaders.modpacks.models.CurseManifest;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.GsonJsonUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

public class CurseforgeApi implements ModpackApi{
    private static final Pattern sMcVersionPattern = Pattern.compile("([0-9]+)\\.([0-9]+)\\.?([0-9]+)?");
    private static final int ALGO_SHA_1 = 1;
    // Stolen from
    // https://github.com/AnzhiZhang/CurseForgeModpackDownloader/blob/6cb3f428459f0cc8f444d16e54aea4cd1186fd7b/utils/requester.py#L93
    private static final int CURSEFORGE_MINECRAFT_GAME_ID = 432;
    // The class ids of mods, resource packs, shaders and modpacks live in ContentType
    private static final int CURSEFORGE_SORT_RELEVANCY = 1;
    private static final int CURSEFORGE_PAGINATION_SIZE = 50;
    private static final int CURSEFORGE_PAGINATION_END_REACHED = -1;
    private static final int CURSEFORGE_PAGINATION_ERROR = -2;

    private final ApiHandler mApiHandler;
    public CurseforgeApi(String apiKey) {
        mApiHandler = new ApiHandler("https://api.curseforge.com/v1", apiKey);
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        CurseforgeSearchResult curseforgeSearchResult = (CurseforgeSearchResult) previousPageResult;

        HashMap<String, Object> params = new HashMap<>();
        params.put("gameId", CURSEFORGE_MINECRAFT_GAME_ID);
        params.put("classId", ContentType.getCurseforgeClassId(searchFilters.contentType));
        params.put("searchFilter", searchFilters.name);
        params.put("sortField", CURSEFORGE_SORT_RELEVANCY);
        params.put("sortOrder", "desc");
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            params.put("gameVersion", searchFilters.mcVersion);
        if(ContentType.usesLoaderFilter(searchFilters.contentType)) {
            int loaderType = ContentType.getCurseforgeLoaderType(searchFilters.loader);
            if(loaderType != 0) params.put("modLoaderType", loaderType);
        }
        if(previousPageResult != null)
            params.put("index", curseforgeSearchResult.previousOffset);

        JsonObject response = mApiHandler.get("mods/search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray dataArray = response.getAsJsonArray("data");
        if(dataArray == null) return null;
        JsonObject paginationInfo = response.getAsJsonObject("pagination");
        ArrayList<ModItem> modItemList = new ArrayList<>(dataArray.size());
        for(int i = 0; i < dataArray.size(); i++) {
            JsonObject dataElement = dataArray.get(i).getAsJsonObject();
            JsonElement allowModDistribution = GsonJsonUtils.getElementSafe(dataElement, "allowModDistribution");
            // Gson automatically casts null to false, which leans to issues
            // So, only check the distribution flag if it is non-null
            if(allowModDistribution != null && !allowModDistribution.getAsBoolean()) {
                Log.i("CurseforgeApi", "Skipping " + dataElement.get("name").getAsString() + " because its author disallows distribution");
                continue;
            }
            // Not every project has a logo. The empty string makes the icon loader fail cleanly.
            String logoUrl = GsonJsonUtils.getStringSafe(
                    GsonJsonUtils.getJsonObjectSafe(dataElement, "logo"), "thumbnailUrl");
            ModItem modItem = new ModItem(Constants.SOURCE_CURSEFORGE,
                    searchFilters.contentType,
                    dataElement.get("id").getAsString(),
                    dataElement.get("name").getAsString(),
                    dataElement.get("summary").getAsString(),
                    logoUrl != null ? logoUrl : "");
            modItem.filterMcVersion = searchFilters.mcVersion;
            modItem.filterLoader = searchFilters.loader;
            modItemList.add(modItem);
        }
        if(curseforgeSearchResult == null) curseforgeSearchResult = new CurseforgeSearchResult();
        curseforgeSearchResult.results = modItemList.toArray(new ModItem[0]);
        curseforgeSearchResult.totalResultCount = paginationInfo.get("totalCount").getAsInt();
        curseforgeSearchResult.previousOffset += dataArray.size();
        return curseforgeSearchResult;

    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        ArrayList<JsonObject> allModDetails = new ArrayList<>();
        int index = 0;
        while(index != CURSEFORGE_PAGINATION_END_REACHED &&
                index != CURSEFORGE_PAGINATION_ERROR) {
            index = getPaginatedDetails(allModDetails, index, item);
        }
        if(index == CURSEFORGE_PAGINATION_ERROR) return null;

        ArrayList<String> versionNames = new ArrayList<>(allModDetails.size());
        ArrayList<String> mcVersionNames = new ArrayList<>(allModDetails.size());
        ArrayList<String> versionUrls = new ArrayList<>(allModDetails.size());
        ArrayList<String> hashes = new ArrayList<>(allModDetails.size());
        for(JsonObject modDetail : allModDetails) {
            // A file without a download URL is one whose author disabled downloads from outside
            // of CurseForge. We honor that and don't offer the file.
            String downloadUrl = GsonJsonUtils.getStringSafe(modDetail, "downloadUrl");
            if(downloadUrl == null) continue;

            String displayName = GsonJsonUtils.getStringSafe(modDetail, "displayName");
            versionNames.add(displayName != null ? displayName : item.title);
            versionUrls.add(downloadUrl);
            mcVersionNames.add(pickGameVersion(modDetail, item.filterMcVersion));
            hashes.add(getSha1FromModData(modDetail));
        }
        // Nothing here also means "nothing compatible with the filters"
        if(versionNames.isEmpty()) return null;
        return new ModDetail(item,
                versionNames.toArray(new String[0]),
                mcVersionNames.toArray(new String[0]),
                versionUrls.toArray(new String[0]),
                hashes.toArray(new String[0]));
    }

    /** The Minecraft version to show for a file: the one that was searched for, or the first listed */
    private static @Nullable String pickGameVersion(JsonObject fileData, @Nullable String preferred) {
        JsonArray gameVersions = GsonJsonUtils.getJsonArraySafe(fileData, "gameVersions");
        if(gameVersions == null) return null;
        String first = null;
        for(JsonElement jsonElement : gameVersions) {
            String gameVersion = jsonElement.getAsString();
            if(!sMcVersionPattern.matcher(gameVersion).matches()) continue;
            if(preferred != null && preferred.equals(gameVersion)) return gameVersion;
            if(first == null) first = gameVersion;
        }
        return first;
    }

    @Override
    public ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException{
        if(ContentType.isSingleFile(modDetail.contentType)) {
            // Note that unlike on Modrinth, dependencies of mods are not installed automatically
            ContentInstaller.installSelected(modDetail, selectedVersion);
            return null; // nothing else to install afterwards
        }
        return ModpackInstaller.installModpack(modDetail, selectedVersion, this::installCurseforgeZip);
    }


    private int getPaginatedDetails(ArrayList<JsonObject> objectList, int index, ModItem item) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("index", index);
        params.put("pageSize", CURSEFORGE_PAGINATION_SIZE);
        if(item.filterMcVersion != null && !item.filterMcVersion.isEmpty())
            params.put("gameVersion", item.filterMcVersion);
        if(ContentType.usesLoaderFilter(item.contentType)) {
            int loaderType = ContentType.getCurseforgeLoaderType(item.filterLoader);
            if(loaderType != 0) params.put("modLoaderType", loaderType);
        }

        JsonObject response = mApiHandler.get("mods/"+item.id+"/files", params, JsonObject.class);
        JsonArray data = GsonJsonUtils.getJsonArraySafe(response, "data");
        if(data == null) return CURSEFORGE_PAGINATION_ERROR;

        for(int i = 0; i < data.size(); i++) {
            JsonObject fileInfo = data.get(i).getAsJsonObject();
            JsonElement isServerPack = GsonJsonUtils.getElementSafe(fileInfo, "isServerPack");
            if(isServerPack != null && isServerPack.getAsBoolean()) continue;
            objectList.add(fileInfo);
        }
        if(data.size() < CURSEFORGE_PAGINATION_SIZE) {
            return CURSEFORGE_PAGINATION_END_REACHED; // we read the remainder! yay!
        }
        return index + data.size();
    }

    private ModLoader installCurseforgeZip(File zipFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(zipFile)){
            CurseManifest curseManifest = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "manifest.json")),
                    CurseManifest.class);
            if(!verifyManifest(curseManifest)) {
                Log.i("CurseforgeApi","manifest verification failed");
                return null;
            }
            ModDownloader modDownloader = new ModDownloader(new File(instanceDestination,"mods"), true);
            int fileCount = curseManifest.files.length;
            for(int i = 0; i < fileCount; i++) {
                final CurseManifest.CurseFile curseFile = curseManifest.files[i];
                modDownloader.submitDownload(()->{
                    String url = getDownloadUrl(curseFile.projectID, curseFile.fileID);
                    if(url == null && curseFile.required)
                        throw new IOException("Failed to obtain download URL for "+curseFile.projectID+" "+curseFile.fileID);
                    else if(url == null) return null;
                    return new ModDownloader.FileInfo(url, FileUtils.getFileName(url), getDownloadSha1(curseFile.projectID, curseFile.fileID));
                });
            }
            modDownloader.awaitFinish((c,m)->
                    ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, (int) Math.max((float)c/m*100,0), R.string.modpack_download_downloading_mods_fc, c, m)
            );
            String overridesDir = "overrides";
            if(curseManifest.overrides != null) overridesDir = curseManifest.overrides;
            ZipUtils.zipExtract(modpackZipFile, overridesDir, instanceDestination);
            return createInfo(curseManifest.minecraft);
        }
    }

    private ModLoader createInfo(CurseManifest.CurseMinecraft minecraft) {
        CurseManifest.CurseModLoader primaryModLoader = null;
        for(CurseManifest.CurseModLoader modLoader : minecraft.modLoaders) {
            if(modLoader.primary) {
                primaryModLoader = modLoader;
                break;
            }
        }
        if(primaryModLoader == null) primaryModLoader = minecraft.modLoaders[0];
        String modLoaderId = primaryModLoader.id;
        int dashIndex = modLoaderId.indexOf('-');
        String modLoaderName = modLoaderId.substring(0, dashIndex);
        String modLoaderVersion = modLoaderId.substring(dashIndex+1);
        Log.i("CurseforgeApi", modLoaderId + " " + modLoaderName + " "+modLoaderVersion);
        int modLoaderTypeInt;
        switch (modLoaderName) {
            case "forge":
                modLoaderTypeInt = ModLoader.MOD_LOADER_FORGE;
                break;
            case "fabric":
                modLoaderTypeInt = ModLoader.MOD_LOADER_FABRIC;
                break;
            default:
                return null;
            //TODO: Quilt is also Forge? How does that work?
        }
        return new ModLoader(modLoaderTypeInt, modLoaderVersion, minecraft.version);
    }

    private String getDownloadUrl(long projectID, long fileID) {
        // First try the official api endpoint
        JsonObject response = mApiHandler.get("mods/"+projectID+"/files/"+fileID+"/download-url", JsonObject.class);
        if (response != null && !response.get("data").isJsonNull())
            return response.get("data").getAsString();

        // Otherwise, fallback to building an edge link
        JsonObject fallbackResponse = mApiHandler.get(String.format("mods/%s/files/%s", projectID, fileID), JsonObject.class);
        if (fallbackResponse != null && !fallbackResponse.get("data").isJsonNull()){
            JsonObject modData = fallbackResponse.get("data").getAsJsonObject();
            int id = modData.get("id").getAsInt();
            return String.format("https://edge.forgecdn.net/files/%s/%s/%s", id/1000, id % 1000, modData.get("fileName").getAsString());
        }

        return null;
    }

    private @Nullable String getDownloadSha1(long projectID, long fileID) {
        // Try the api endpoint, die in the other case
        JsonObject response = mApiHandler.get("mods/"+projectID+"/files/"+fileID, JsonObject.class);
        JsonObject data = GsonJsonUtils.getJsonObjectSafe(response, "data");
        if(data == null) return null;
        return getSha1FromModData(data);
    }

    private String getSha1FromModData(@NonNull JsonObject object) {
        JsonArray hashes = GsonJsonUtils.getJsonArraySafe(object, "hashes");
        if(hashes == null) return null;
        for (JsonElement jsonElement : hashes) {
            // The sha1 = 1; md5 = 2;
            JsonObject jsonObject = GsonJsonUtils.getJsonObjectSafe(jsonElement);
            if(GsonJsonUtils.getIntSafe(
                    jsonObject,
                    "algo",
                    -1) == ALGO_SHA_1) {
                return GsonJsonUtils.getStringSafe(jsonObject, "value");
            }
        }
        return null;
    }

    private boolean verifyManifest(CurseManifest manifest) {
        if(!"minecraftModpack".equals(manifest.manifestType)) return false;
        if(manifest.manifestVersion != 1) return false;
        if(manifest.minecraft == null) return false;
        if(manifest.minecraft.version == null) return false;
        if(manifest.minecraft.modLoaders == null) return false;
        return manifest.minecraft.modLoaders.length >= 1;
    }

    static class CurseforgeSearchResult extends SearchResult {
        int previousOffset;
    }
}
