package mil.nga.giat.mage.map.cache;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import mil.nga.geopackage.GeoPackageConstants;
import mil.nga.geopackage.validate.GeoPackageValidate;
import mil.nga.giat.mage.R;
import mil.nga.giat.mage.sdk.utils.MediaUtility;
import mil.nga.giat.mage.sdk.utils.StorageUtility;

/**
 * Find <code>/MapCache</code> directories in storage roots using {@link StorageUtility},
 * as well as the application cache directory.
 */
public class DefaultCacheLocationProvider implements CacheManager.CacheLocationProvider {

    public static final String CACHE_DIRECTORY = "caches";

    /**
     * Get a writeable cache directory for saving cache files
     *
     * @param context
     * @return file directory or null
     */
    public static File getApplicationCacheDirectory(Context context) {
        File directory = context.getFilesDir();

        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            File externalDirectory = context.getExternalFilesDir(null);
            if (externalDirectory != null) {
                directory = externalDirectory;
            }
        }

        File cacheDirectory = new File(directory, CACHE_DIRECTORY);
        if (!cacheDirectory.exists()) {
            cacheDirectory.mkdir();
        }

        return cacheDirectory;
    }

    /**
     * Task for copying a cache file Uri stream to the cache folder location and importing the file as a cache.
     */
    public static class CopyCacheStreamTask extends AsyncTask<Void, Void, String> {

        private Context context;
        /**
         * Intent Uri used to launch MAGE
         */
        private Uri uri;
        private File cacheFile;
        // TODO: this is not used for anything
        private String cacheName;

        /**
         * Constructor
         *
         * @param context
         * @param uri       Uri containing stream
         * @param cacheFile copy to cache file location
         * @param cacheName cache name
         */
        public CopyCacheStreamTask(Context context, Uri uri, File cacheFile, String cacheName) {
            this.context = context;
            this.uri = uri;
            this.cacheFile = cacheFile;
            this.cacheName = cacheName;
        }

        /**
         * Copy the cache stream to cache file location
         *
         * @param params
         * @return
         */
        @Override
        protected String doInBackground(Void... params) {

            String error = null;

            final ContentResolver resolver = context.getContentResolver();
            try {
                InputStream stream = resolver.openInputStream(uri);
                MediaUtility.copyStream(stream, cacheFile);
            } catch (IOException e) {
                error = e.getMessage();
            }

            return error;
        }

        /**
         * Enable the new cache file and refresh the overlays
         *
         * @param result
         */
        @Override
        protected void onPostExecute(String result) {
            if (result == null) {
                CacheManager.getInstance().tryImportCacheFile(cacheFile);
            }
        }
    }

    /**
     * Copy the Uri to the cache directory in a background task
     *
     * @param context
     * @param uri
     * @param path
     *
     * TODO: not real sure why this is all geopackage-specific - investigate
     * i'm assuming this geopackage-specific logic is here because geopackages
     * are really the only URI-streamable cache files mage currently supports,
     * so this just handles that very specific case
     */
    public static void copyToCache(Context context, Uri uri, String path) {

        // Get a cache directory to write to
        File cacheDirectory = getApplicationCacheDirectory(context);
        if (cacheDirectory != null) {

            // Get the Uri display name, which should be the file name with extension
            String name = MediaUtility.getDisplayName(context, uri, path);

            // If no extension, add a GeoPackage extension
            String ext = MediaUtility.getFileExtension(name);
            if( ext == null){
                name += "." + GeoPackageConstants.GEOPACKAGE_EXTENSION;
            }

            // Verify that the file is a cache file by its extension
            File cacheFile = new File(cacheDirectory, name);
            if (GeoPackageValidate.hasGeoPackageExtension(cacheFile)) {
                if (cacheFile.exists()) {
                    cacheFile.delete();
                }
                String cacheName = MediaUtility.getFileNameWithoutExtension(cacheFile);
                // TODO: dunno about this here - seems like CacheManager responsibility
                // probably CacheOverlay should have a source file member and track files
                // and mod dates that way
                CacheManager.getInstance().removeCacheOverlay(cacheName);
                CopyCacheStreamTask task = new CopyCacheStreamTask(context, uri, cacheFile, cacheName);
                task.execute();
            }
        }
    }


    private final Context context;

    public DefaultCacheLocationProvider(Context context) {
        this.context = context;
    }

    @Override
    public List<File> getLocalSearchDirs() {
        List<File> dirs = new ArrayList<>();
        Map<StorageUtility.StorageType, File> storageLocations = StorageUtility.getReadableStorageLocations();
        for (File storageLocation : storageLocations.values()) {
            File root = new File(storageLocation, context.getString(R.string.overlay_cache_directory));
            if (root.exists() && root.isDirectory() && root.canRead()) {
                dirs.add(root);
            }
        }
        File applicationCacheDirectory = getApplicationCacheDirectory(context);
        if (applicationCacheDirectory != null && applicationCacheDirectory.exists()) {
            dirs.add(applicationCacheDirectory);
        }
        return dirs;
    }
}
