package net.kdt.pojavlaunch.modloaders.modpacks.models;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

public class ModDetail extends ModItem {
    /* A cheap way to map from the front facing name to the underlying id */
    public String[] versionNames;
    public String [] mcVersionNames;
    public String[] versionUrls;
    /* SHA 1 hashes, null if a hash is unavailable */
    public String[] versionHashes;
    /* Source specific version ids. Only filled in by sources that can use them (Modrinth), may be null */
    @Nullable public String[] versionIds;
    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames, String[] versionUrls, String[] hashes) {
        super(item.apiSource, item.contentType, item.id, item.title, item.description, item.imageUrl);
        this.filterMcVersion = item.filterMcVersion;
        this.filterLoader = item.filterLoader;
        this.versionNames = versionNames;
        this.mcVersionNames = mcVersionNames;
        this.versionUrls = versionUrls;
        this.versionHashes = hashes;

        // Add the mc version to the version model
        for (int i=0; i<versionNames.length; i++){
            // A source may not be able to tell which Minecraft version a file is for
            if (mcVersionNames[i] != null && !versionNames[i].contains(mcVersionNames[i]))
                versionNames[i] += " - " + mcVersionNames[i];
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "ModDetail{" +
                "versionNames=" + Arrays.toString(versionNames) +
                ", mcVersionNames=" + Arrays.toString(mcVersionNames) +
                ", versionIds=" + Arrays.toString(versionUrls) +
                ", id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", apiSource=" + apiSource +
                ", contentType=" + contentType +
                '}';
    }
}
