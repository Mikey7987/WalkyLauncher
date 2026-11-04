package net.kdt.pojavlaunch.modloaders.modpacks.models;

public abstract class ModSource {
    public int apiSource;
    /** Kept for older code paths, always equals (contentType == ContentType.MODPACK) */
    public boolean isModpack;
    /** One of the {@link ContentType} constants */
    public int contentType = ContentType.MODPACK;
}
