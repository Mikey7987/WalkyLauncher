package net.kdt.pojavlaunch.modloaders.modpacks.models;

import androidx.annotation.Nullable;

/**
 * The kinds of downloadable content the launcher can browse and install.
 * Modpacks create a whole new profile, everything else is a single file that
 * is dropped into a folder of an existing profile.
 */
public final class ContentType {
    private ContentType() {}

    public static final int MODPACK = 0;
    public static final int MOD = 1;
    public static final int RESOURCE_PACK = 2;
    public static final int SHADER = 3;

    /* CurseForge class ids, see https://api.curseforge.com/v1/categories?gameId=432 */
    private static final int CURSEFORGE_CLASS_MOD = 6;
    private static final int CURSEFORGE_CLASS_RESOURCE_PACK = 12;
    private static final int CURSEFORGE_CLASS_MODPACK = 4471;
    private static final int CURSEFORGE_CLASS_SHADER = 6552;

    /* Mod loader names as Modrinth spells them */
    public static final String LOADER_FABRIC = "fabric";
    public static final String LOADER_FORGE = "forge";
    public static final String LOADER_NEOFORGE = "neoforge";
    public static final String LOADER_QUILT = "quilt";

    public static String getModrinthProjectType(int contentType) {
        switch (contentType) {
            case MOD: return "mod";
            case RESOURCE_PACK: return "resourcepack";
            case SHADER: return "shader";
            case MODPACK:
            default: return "modpack";
        }
    }

    public static int getCurseforgeClassId(int contentType) {
        switch (contentType) {
            case MOD: return CURSEFORGE_CLASS_MOD;
            case RESOURCE_PACK: return CURSEFORGE_CLASS_RESOURCE_PACK;
            case SHADER: return CURSEFORGE_CLASS_SHADER;
            case MODPACK:
            default: return CURSEFORGE_CLASS_MODPACK;
        }
    }

    /**
     * @return the name of the folder (inside the game directory) that holds this kind of content,
     * or null for modpacks, which are not a single file.
     */
    @Nullable
    public static String getFolderName(int contentType) {
        switch (contentType) {
            case MOD: return "mods";
            case RESOURCE_PACK: return "resourcepacks";
            case SHADER: return "shaderpacks";
            default: return null;
        }
    }

    public static boolean isSingleFile(int contentType) {
        return contentType != MODPACK;
    }

    /** Whether the mod loader filter makes sense for this kind of content */
    public static boolean usesLoaderFilter(int contentType) {
        return contentType == MOD || contentType == MODPACK;
    }

    /**
     * CurseForge identifies loaders with an integer.
     * @return the CurseForge modLoaderType, or 0 (any) if the loader is null or unknown
     */
    public static int getCurseforgeLoaderType(@Nullable String loader) {
        if (loader == null) return 0;
        switch (loader) {
            case LOADER_FORGE: return 1;
            case LOADER_FABRIC: return 4;
            case LOADER_QUILT: return 5;
            case LOADER_NEOFORGE: return 6;
            default: return 0;
        }
    }
}
