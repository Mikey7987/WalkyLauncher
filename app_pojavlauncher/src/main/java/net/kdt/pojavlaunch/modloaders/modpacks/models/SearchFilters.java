package net.kdt.pojavlaunch.modloaders.modpacks.models;

import org.jetbrains.annotations.Nullable;

/**
 * Search filters, passed to APIs
 */
public class SearchFilters {
    /** One of the {@link ContentType} constants */
    public int contentType = ContentType.MODPACK;
    public String name;
    @Nullable public String mcVersion;
    /** One of the ContentType.LOADER_* names, or null for "any loader" */
    @Nullable public String loader;

}
