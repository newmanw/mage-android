package mil.nga.giat.mage.map.cache;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import mil.nga.geopackage.GeoPackageConstants;
import mil.nga.geopackage.validate.GeoPackageValidate;
import mil.nga.giat.mage.R;
import mil.nga.giat.mage.data.BasicResource;
import mil.nga.giat.mage.data.Resource;
import mil.nga.giat.mage.sdk.utils.MediaUtility;
import mil.nga.giat.mage.sdk.utils.StorageUtility;

import static java.util.Objects.requireNonNull;

/**
 * Find <code>/MapDataResource</code> directories in storage roots using {@link StorageUtility},
 * as well as the application cache directory.
 */
public class LocalStorageMapDataRepository extends MapDataRepository {

    private static final String CACHE_DIRECTORY = "caches";

    @SuppressLint("StaticFieldLeak")
    private static LocalStorageMapDataRepository instance;

    public static synchronized void initialize(Application context) {
        instance = new LocalStorageMapDataRepository(context);
    }

    // TODO: replace with dagger depenedency injection
    public static LocalStorageMapDataRepository getInstance() {
        return instance;
    }

    private final Application context;

    private LocalStorageMapDataRepository(Application context) {
        this.context = context;
    }

    @Override
    public boolean ownsResource(@NonNull URI resourceUri) {
        if (!"file".equals(resourceUri.getScheme())) {
            return false;
        }
        File path = new File(resourceUri.getPath());
        File cacheDir = getApplicationCacheDirectory();
        return path.getParentFile() != null && path.getParentFile().equals(cacheDir);
    }

    @Override
    public void refreshAvailableMapData(@NonNull Map<URI, MapDataResource> resolvedResources, @NonNull Executor executor) {
        setValue(BasicResource.loading());
        new RefreshTask(resolvedResources).executeOnExecutor(executor);
    }

    /**
     * Copy the content at the given URI to the cache directory in a background task
     *
     * @param uri a {@link Uri}
     * @param path
     *
     * TODO: not real sure why this is all geopackage-specific - investigate
     * i'm assuming this geopackage-specific logic is here because geopackages
     * are really the only URI-streamable cache files mage currently supports,
     * so this just handles that very specific case
     */
    public void copyToCache(Uri uri, String path) {

        // TODO: wait until a refresh is complete before modifying directory contents - maybe use a tmp file, then move operation
        // TODO: perform the delete operation in the async task
        // TODO: immediately update the resource set if a file gets deleted before copying
        // Get a cache directory to write to
        File cacheDirectory = getApplicationCacheDirectory();
        if (cacheDirectory != null) {

            // Get the Uri display name, which should be the file name with extension
            String name = MediaUtility.getDisplayName(context, uri, path);

            // If no extension, add a GeoPackage extension
            String ext = MediaUtility.getFileExtension(name);
            if (ext == null) {
                name += "." + GeoPackageConstants.GEOPACKAGE_EXTENSION;
            }

            // Verify that the file is a cache file by its extension
            File cacheFile = new File(cacheDirectory, name);
            if (GeoPackageValidate.hasGeoPackageExtension(cacheFile)) {
                if (cacheFile.exists()) {
                    cacheFile.delete();
                }
                // TODO: dunno about this here - seems like MapDataManager responsibility
                // probably MapDataResource should have a source file member and track files
                // and mod dates that way
                // TODO: test that MapDataManager correctly refreshes layers from the new file if MapDataResource already existed
                // String cacheName = MediaUtility.getFileNameWithoutExtension(cacheFile);
                // MapDataManager.getInstance().removeCacheOverlay(cacheName);
                CopyCacheStreamTask task = new CopyCacheStreamTask(uri, cacheFile);
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        }
    }

    private File getApplicationCacheDirectory() {
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

    private List<File> getSearchDirs() {
        List<File> dirs = new ArrayList<>();
        Map<StorageUtility.StorageType, File> storageLocations = StorageUtility.getReadableStorageLocations();
        for (File storageLocation : storageLocations.values()) {
            File root = new File(storageLocation, context.getString(R.string.overlay_cache_directory));
            if (root.exists() && root.isDirectory() && root.canRead()) {
                dirs.add(root);
            }
        }
        File applicationCacheDirectory = getApplicationCacheDirectory();
        if (applicationCacheDirectory != null && applicationCacheDirectory.exists()) {
            dirs.add(applicationCacheDirectory);
        }
        return dirs;
    }

    private void onRefreshComplete(Set<MapDataResource> newAndUpdatedResources) {
        Set<? extends MapDataResource> existing = requireNonNull(getValue()).getContent();
        if (!newAndUpdatedResources.isEmpty()) {
            if (existing != null) {
                newAndUpdatedResources.addAll(existing);
            }
        }
        setValue(new BasicResource<>(existing, Resource.Status.Success));
    }

    private void onResourceCopyComplete(File file) {
        MapDataResource newResource = new MapDataResource(file.toURI(), this, file.lastModified());
        Set<? extends MapDataResource> existing = requireNonNull(getValue()).getContent();
        if (existing == null) {
            existing = Collections.emptySet();
        }
        Set<MapDataResource> updated = new HashSet<>(existing);
        updated.add(newResource);
        setValue(new BasicResource<>(Collections.unmodifiableSet(updated), Resource.Status.Success));
    }

    @SuppressLint("StaticFieldLeak")
    private class RefreshTask extends AsyncTask<Void, Void, Set<MapDataResource>> {

        private final Map<URI, MapDataResource> existingResolved;

        private RefreshTask(Map<URI, MapDataResource> existingResolved) {
            this.existingResolved = existingResolved;
        }

        @Override
        public Set<MapDataResource> doInBackground(Void... nothing) {
            List<File> dirs = getSearchDirs();
            Set<MapDataResource> potentialResources = new HashSet<>();
            for (File dir : dirs) {
                File[] files = dir.listFiles();
                for (File file : files) {
                    URI uri = file.toURI();
                    MapDataResource existing = existingResolved.get(uri);
                    if (existing == null || file.lastModified() > existing.getContentTimestamp()) {
                        potentialResources.add(new MapDataResource(uri, LocalStorageMapDataRepository.this, file.lastModified()));
                    }
                }
            }
            return potentialResources;
        }

        @Override
        protected void onPostExecute(Set<MapDataResource> resources) {
            onRefreshComplete(resources);
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class CopyCacheStreamTask extends AsyncTask<Void, Void, String> {

        private Uri uri;
        private File cacheFile;

        private CopyCacheStreamTask(Uri uri, File cacheFile) {
            this.uri = uri;
            this.cacheFile = cacheFile;
        }

        @Override
        protected String doInBackground(Void... params) {
            String error = null;
            final ContentResolver resolver = context.getContentResolver();
            try {
                InputStream stream = resolver.openInputStream(uri);
                MediaUtility.copyStream(stream, cacheFile);
            }
            catch (IOException e) {
                error = e.getMessage();
            }
            return error;
        }

        @Override
        protected void onPostExecute(String error) {
            if (error == null) {
                onResourceCopyComplete(cacheFile);
            }
        }
    }
}
