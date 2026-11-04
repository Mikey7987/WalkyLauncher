package net.kdt.pojavlaunch.modloaders.modpacks.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.ContentTarget;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ContentType;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.concurrent.Callable;

/**
 * Installs single-file content (a mod, a resource pack or a shader pack) by downloading it
 * into the matching folder of the target profile.
 */
public final class ContentInstaller {
    private ContentInstaller() {}

    /**
     * @return the folder the given content will be installed to, inside the target profile
     */
    @NonNull
    public static File getDestinationDirectory(int contentType) {
        String folder = ContentType.getFolderName(contentType);
        if (folder == null) throw new IllegalArgumentException("Content type " + contentType + " is not a single file");
        return new File(ContentTarget.getTargetGameDir(), folder);
    }

    /**
     * Download the selected version of the item into the target profile.
     * @return the file that was installed
     */
    @NonNull
    public static File installSelected(ModDetail detail, int selectedVersion) throws IOException {
        return downloadInto(getDestinationDirectory(detail.contentType),
                detail.versionUrls[selectedVersion],
                detail.versionHashes[selectedVersion],
                detail.title);
    }

    /**
     * Download one file into a directory. If the same file (verified by its hash) is already there,
     * nothing is downloaded. The file is first downloaded under a temporary name, so that a failed
     * or interrupted download never leaves a broken file in the game folder (a broken jar in the
     * mods folder would crash the game on startup).
     *
     * @param destDir the directory to put the file in
     * @param url where to download from
     * @param sha1 the expected SHA-1, or null if it isn't known
     * @param title what to show in the progress notification
     * @return the final file
     */
    @NonNull
    public static File downloadInto(File destDir, String url, @Nullable String sha1, String title) throws IOException {
        FileUtils.ensureDirectory(destDir);
        File finalFile = new File(destDir, getSafeFileName(url));
        if (sha1 != null && finalFile.isFile() && Tools.compareSHA1(finalFile, sha1)) {
            return finalFile; // already installed
        }

        File partFile = new File(destDir, finalFile.getName() + ".part");
        // ensureSha1() trusts an existing file when it doesn't know the hash, so make sure we never
        // pick up the remains of an earlier failed attempt
        if (partFile.exists() && !partFile.delete())
            throw new IOException("Unable to clear the leftover file " + partFile.getName());

        DownloaderProgressWrapper progress = new DownloaderProgressWrapper(
                R.string.content_install_downloading, ProgressLayout.INSTALL_MODPACK);
        progress.extraString = title;
        byte[] buffer = new byte[8192];
        try {
            DownloadUtils.ensureSha1(partFile, sha1, (Callable<Void>) () -> {
                DownloadUtils.downloadFileMonitored(url, partFile, buffer, progress);
                return null;
            });
            if (finalFile.exists() && !finalFile.delete())
                throw new IOException("Unable to replace " + finalFile.getName());
            if (!partFile.renameTo(finalFile))
                throw new IOException("Unable to move the downloaded file into place");
        } finally {
            if (partFile.exists()) //noinspection ResultOfMethodCallIgnored
                partFile.delete();
        }
        return finalFile;
    }

    /**
     * Turn the last part of a download URL into a file name that can't escape the target folder.
     */
    @NonNull
    static String getSafeFileName(String url) {
        String path = url;
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) path = path.substring(0, queryIndex);
        int slashIndex = path.lastIndexOf('/');
        String raw = slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
        String decoded;
        try {
            // URLDecoder turns '+' into a space, but in a file name a literal '+' is far more likely
            decoded = URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            decoded = raw;
        }
        return sanitizeFileName(decoded);
    }

    /**
     * Make a name safe to use as a file name inside a game folder: no path separators
     * and no characters that some file systems refuse.
     */
    @NonNull
    public static String sanitizeFileName(String name) {
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (sanitized.isEmpty() || sanitized.equals(".") || sanitized.equals("..")) return "download.bin";
        return sanitized;
    }
}
