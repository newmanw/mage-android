package mil.nga.giat.mage.map.view;

import android.arch.lifecycle.LifecycleOwner;
import android.view.View;

import com.google.android.gms.maps.GoogleMap;

public interface MapOwner extends LifecycleOwner {
    GoogleMap getMap();
    View getMapView();
}
