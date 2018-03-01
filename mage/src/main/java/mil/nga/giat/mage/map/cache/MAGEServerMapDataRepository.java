package mil.nga.giat.mage.map.cache;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Future;

public class MAGEServerMapDataRepository implements MapDataRepository {

    @Override
    public Future<List<URI>> retrieveMapDataResources() {
        return null;
    }
}
