package net.kdt.pojavlaunch.modloaders.modpacks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Decides which profile the single-file content (mods, resource packs, shaders) is installed into
 * and where that profile's game folder is.
 *
 * The person can pick a target profile on the content screens. If they never did (or the profile
 * has since been deleted), the currently selected profile is used.
 */
public final class ContentTarget {
    private ContentTarget() {}

    public static final String PREF_KEY_TARGET_PROFILE = "contentTargetProfile";

    /** A profile that content can be installed to */
    public static final class Entry {
        public final String key;
        public final String label;

        Entry(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    private static Map<String, MinecraftProfile> profiles() {
        if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load();
        return LauncherProfiles.mainProfileJson.profiles;
    }

    /** @return a human readable name for the profile with the given key */
    public static String getLabel(@Nullable String key) {
        if (key == null) return "-";
        MinecraftProfile profile = profiles().get(key);
        if (profile == null) return key;
        if (Tools.isValidString(profile.name) && !"New".equalsIgnoreCase(profile.name)) return profile.name;
        if (Tools.isValidString(profile.lastVersionId)) return profile.lastVersionId;
        return key;
    }

    /** @return all the profiles, sorted by their displayed name */
    @NonNull
    public static List<Entry> getEntries() {
        List<Entry> entries = new ArrayList<>();
        for (String key : profiles().keySet()) {
            entries.add(new Entry(key, getLabel(key)));
        }
        Collections.sort(entries, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return entries;
    }

    /** @return the key of the profile that content will go to, or null if there are no profiles */
    @Nullable
    public static String getTargetKey() {
        Map<String, MinecraftProfile> profiles = profiles();
        String chosen = LauncherPreferences.DEFAULT_PREF.getString(PREF_KEY_TARGET_PROFILE, null);
        if (chosen != null && profiles.containsKey(chosen)) return chosen;
        String current = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if (current != null && profiles.containsKey(current)) return current;
        if (profiles.isEmpty()) return null;
        return profiles.keySet().iterator().next();
    }

    public static void setTargetKey(@NonNull String key) {
        LauncherPreferences.DEFAULT_PREF.edit().putString(PREF_KEY_TARGET_PROFILE, key).apply();
    }

    /** @return the game folder of the given profile, falling back to the default game folder */
    @NonNull
    public static File getGameDir(@Nullable String key) {
        if (key == null) return new File(Tools.DIR_GAME_NEW);
        MinecraftProfile profile = profiles().get(key);
        if (profile == null) return new File(Tools.DIR_GAME_NEW);
        return Tools.getGameDirPath(profile);
    }

    /** @return the game folder of the profile that content will be installed into */
    @NonNull
    public static File getTargetGameDir() {
        return getGameDir(getTargetKey());
    }
}
