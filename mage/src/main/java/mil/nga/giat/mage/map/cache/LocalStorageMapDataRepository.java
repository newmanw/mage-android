package mil.nga.giat.mage.map.cache;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.NonNull;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import mil.nga.geopackage.GeoPackageConstants;
import mil.nga.geopackage.validate.GeoPackageValidate;
import mil.nga.giat.mage.R;
import mil.nga.giat.mage.sdk.utils.MediaUtility;
import mil.nga.giat.mage.sdk.utils.StorageUtility;

/**
 * Find <code>/MapDataResource</code> directories in storage roots using {@link StorageUtility},
 * as well as the application cache directory.
 */
public class LocalStorageMapDataRepository extends MapDataRepository {

    private static final String CACHE_DIRECTORY = "caches";

    /**
     * Get a writeable cache directory for saving cache files
     *
     * @param context
     * @return file directory or null
     */
    public static File getApplicationCacheDirectory(Application context) {
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

        @SuppressLint("StaticFieldLeak")
        private Application context;
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
        CopyCacheStreamTask(Application context, Uri uri, File cacheFile, String cacheName) {
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
                MapDataManager.getInstance().tryImportResource(cacheFile.toURI());
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
    public static void copyToCache(Application context, Uri uri, String path) {

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
                // TODO: dunno about this here - seems like MapDataManager responsibility
                // probably MapLayerDescriptor should have a source file member and track files
                // and mod dates that way
                MapDataManager.getInstance().removeCacheOverlay(cacheName);
                CopyCacheStreamTask task = new CopyCacheStreamTask(context, uri, cacheFile, cacheName);
                task.execute();
            }
        }
    }


    private final Application context;
    private Status status = Status.Success;

    public LocalStorageMapDataRepository(Application context) {
        this.context = context;
    }

    @NonNull
    @Override
    public Status getStatus() {
        return status;
    }

    @Nullable
    @Override
    public String getStatusMessage() {
        return null;
    }

    @Override
    public int getStatusCode() {
        return super.getStatusCode();
    }

    @Override
    public boolean ownsResource(URI resourceUri) {
        if (!"file".equals(resourceUri.getScheme())) {
            return false;
        }
        File path = new File(resourceUri.getPath());
        File cacheDir = getApplicationCacheDirectory(context);
        return path.getParentFile() != null && path.getParentFile().equals(cacheDir);
    }

    @Override
    public void refreshAvailableMapData(Executor executor) {
        status = Status.Loading;
        new RefreshTask().executeOnExecutor(executor);
    }

    private void onRefreshComplete(Set<MapDataResource> resources) {
        status = Status.Success;
        setValue(resources);
    }

    private class RefreshTask extends AsyncTask<Void, Void, Set<MapDataResource>> {

        @Override
        public Set<MapDataResource> doInBackground(Void... nothing) {
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
            Set<MapDataResource> potentialResources = new HashSet<>();
            for (File dir : dirs) {
                File[] files = dir.listFiles();
                for (File file : files) {
                    potentialResources.add(new MapDataResource(file.toURI()));
                }
            }
            return potentialResources;
        }

        @Override
        protected void onPostExecute(Set<MapDataResource> resources) {
            onRefreshComplete(resources);
        }
    }
}
