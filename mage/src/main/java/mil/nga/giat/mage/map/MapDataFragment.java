package mil.nga.giat.mage.map;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.woxthebox.draglistview.DragItemAdapter;
import com.woxthebox.draglistview.DragListView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mil.nga.giat.mage.R;
import mil.nga.giat.mage.map.cache.MapLayerDescriptor;
import mil.nga.giat.mage.map.cache.OverlayOnMapManager;

import static android.support.v7.widget.RecyclerView.NO_ID;

public class MapDataFragment extends Fragment implements OverlayOnMapManager.OverlayOnMapListener, DragListView.DragListListener, DragListView.DragListCallback {

    public interface MapDataListener {
        void onBaseMapChanged(BuiltinDataControlValues change);
        void onObservationsVisibilityChanged(BuiltinDataControlValues change);
        void onLocationsVisibilityChanged(BuiltinDataControlValues change);
        void onMgrsVisibilityChanged(BuiltinDataControlValues change);
    }

    public static class BuiltinDataControlValues {

        static class Builder {

            private int baseMapType;
            private boolean observationsVisible;
            private boolean locationsVisible;
            private boolean mgrsVisible;

            Builder baseMapType(int x) {
                baseMapType = x;
                return this;
            }

            Builder observationsVisible(boolean x) {
                observationsVisible = x;
                return this;
            }

            Builder locationsVisible(boolean x) {
                locationsVisible = x;
                return this;
            }

            Builder mgrsVisible(boolean x) {
                mgrsVisible = x;
                return this;
            }

            BuiltinDataControlValues finish() {
                return new BuiltinDataControlValues(baseMapType, observationsVisible, locationsVisible, mgrsVisible);
            }
        }

        static Builder create() {
            return new Builder();
        }

        static Builder from(BuiltinDataControlValues init) {
            return create()
                .baseMapType(init.getBaseMapType())
                .observationsVisible(init.isObservationsVisible())
                .locationsVisible(init.isLocationsVisible())
                .mgrsVisible(init.isMgrsVisible());
        }

        private final int baseMapType;
        private final boolean observationsVisible;
        private final boolean locationsVisible;
        private final boolean mgrsVisible;

        BuiltinDataControlValues(int baseMapType, boolean observationsVisible, boolean locationsVisible, boolean mgrsVisible) {
            this.baseMapType = baseMapType;
            this.observationsVisible = observationsVisible;
            this.locationsVisible = locationsVisible;
            this.mgrsVisible = mgrsVisible;
        }

        int getBaseMapType() {
            return baseMapType;
        }

        boolean isObservationsVisible() {
            return observationsVisible;
        }

        boolean isLocationsVisible() {
            return locationsVisible;
        }

        boolean isMgrsVisible() {
            return mgrsVisible;
        }
    }

    private enum StaticControl {
        BaseMap {
            @Override
            void bindSpecific(MapDataControlViewHolder holder, BuiltinDataControlValues values) {
                holder.name.setText(R.string.map_data_base_map_label);
                holder.dataVisible.setVisibility(View.GONE);
                holder.dataChoice.setVisibility(View.VISIBLE);
                holder.dataChoice.setSelection(values.baseMapType - 1);
            }
        },
        Observations {
            @Override
            void bindSpecific(MapDataControlViewHolder holder, BuiltinDataControlValues values) {
                holder.name.setText(R.string.map_data_observations_label);
                holder.dataChoice.setVisibility(View.GONE);
                holder.dataVisible.setVisibility(View.VISIBLE);
                holder.dataVisible.setChecked(values.isObservationsVisible());
            }

            @Override
            BuiltinDataControlValues onCheckedChanged(CompoundButton buttonView, boolean checked, BuiltinDataControlValues values, MapDataListener listener) {
                BuiltinDataControlValues update = BuiltinDataControlValues.from(values).observationsVisible(checked).finish();
                if (listener != null) {
                    listener.onObservationsVisibilityChanged(update);
                }
                return update;
            }
        },
        Locations {
            @Override
            void bindSpecific(MapDataControlViewHolder holder, BuiltinDataControlValues values) {
                holder.name.setText(R.string.map_data_locations_label);
                holder.dataChoice.setVisibility(View.GONE);
                holder.dataVisible.setVisibility(View.VISIBLE);
                holder.dataVisible.setChecked(values.isLocationsVisible());
            }

            @Override
            BuiltinDataControlValues onCheckedChanged(CompoundButton buttonView, boolean checked, BuiltinDataControlValues values, MapDataListener listener) {
                BuiltinDataControlValues update = BuiltinDataControlValues.from(values).locationsVisible(checked).finish();
                if (listener != null) {
                    listener.onLocationsVisibilityChanged(update);
                }
                return update;
            }
        },
        MGRS {
            @Override
            void bindSpecific(MapDataControlViewHolder holder, BuiltinDataControlValues values) {
                holder.name.setText(R.string.map_data_mgrs_label);
                holder.dataChoice.setVisibility(View.GONE);
                holder.dataVisible.setVisibility(View.VISIBLE);
                holder.dataVisible.setChecked(values.isMgrsVisible());
            }

            @Override
            BuiltinDataControlValues onCheckedChanged(CompoundButton buttonView, boolean checked, BuiltinDataControlValues values, MapDataListener listener) {
                BuiltinDataControlValues update = BuiltinDataControlValues.from(values).mgrsVisible(checked).finish();
                if (listener != null) {
                    listener.onMgrsVisibilityChanged(update);
                }
                return update;
            }
        },
        DataOverlaysHeader {
            @Override
            void bindSpecific(MapDataControlViewHolder holder, BuiltinDataControlValues values) {
                holder.name.setText(R.string.map_data_overlays_header);
                holder.dataVisible.setVisibility(View.GONE);
                holder.dataChoice.setVisibility(View.GONE);
            }
        };

        abstract void bindSpecific(MapDataControlViewHolder holder, BuiltinDataControlValues values);
        BuiltinDataControlValues onCheckedChanged(CompoundButton buttonView, boolean checked, BuiltinDataControlValues values, MapDataListener listener) {
            return values;
        }

        final void bind(MapDataControlViewHolder holder, BuiltinDataControlValues values) {
            holder.icon.setVisibility(View.GONE);
            holder.mGrabView.setVisibility(View.GONE);
            holder.detail.setText("");
            bindSpecific(holder, values);
        }
    }

    private class MapDataItemAdapter extends DragItemAdapter<Object, MapDataControlViewHolder> {

        private final Map<Object, Long> itemIds = new HashMap<>();

        @Override
        public long getUniqueItemId(int i) {
            Object mapData = getItemList().get(i);
            Long id = itemIds.get(mapData);
            return id == null ? NO_ID : id;
        }

        @Override
        public MapDataControlViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.map_data_control_item, parent, false);
            return new MapDataControlViewHolder(view);
        }

        @Override
        public void onBindViewHolder(MapDataControlViewHolder holder, int position) {
            super.onBindViewHolder(holder, position);
            Object item = getItemList().get(position);
            holder.bindMapDataItem(item);
        }

        @Override
        public void setItemList(List<Object> itemList) {
            for (int i = 0; i < itemList.size(); i++) {
                Object data = itemList.get(i);
                itemIds.put(data, (long) i);
            }
            super.setItemList(itemList);
        }

        @Override
        public int getItemViewType(int position) {
            return super.getItemViewType(position);
        }
    }



    private class MapDataControlViewHolder extends DragItemAdapter.ViewHolder implements CompoundButton.OnCheckedChangeListener, AdapterView.OnItemSelectedListener {

        private final ImageView icon;
        private final TextView name;
        private final TextView detail;
        private final Spinner dataChoice;
        private final SwitchCompat dataVisible;
        private Object mapData;

        MapDataControlViewHolder(View itemView) {
            super(itemView, R.id.map_data_drag_icon, false);
            icon = (ImageView) itemView.findViewById(R.id.map_data_icon);
            name = (TextView) itemView.findViewById(R.id.map_data_name);
            detail = (TextView) itemView.findViewById(R.id.map_data_detail);
            dataVisible = (SwitchCompat) itemView.findViewById(R.id.map_data_visible);
            dataChoice = (Spinner) itemView.findViewById(R.id.map_data_choice);
            // base map is the only control that uses the spinner, so just set it here instead of during binding
            dataChoice.setAdapter(baseMapChoices);
        }

        private void bindMapDataItem(Object x) {
            mapData = x;
            dataVisible.setOnCheckedChangeListener(null);
            dataChoice.setOnItemSelectedListener(null);
            if (mapData instanceof StaticControl) {
                bindStaticControl();
            }
            else if (mapData instanceof MapLayerDescriptor) {
                bindCacheOverlay();
            }
            // don't let the switches animate as the list scrolls
            dataVisible.jumpDrawablesToCurrentState();
            dataVisible.setOnCheckedChangeListener(this);
            dataChoice.setOnItemSelectedListener(this);
        }

        private void bindStaticControl() {
            StaticControl x = (StaticControl) mapData;
            x.bind(this, builtinDataControlValues);
        }

        private void bindCacheOverlay() {
            dataChoice.setVisibility(View.GONE);
            icon.setVisibility(View.VISIBLE);
            mGrabView.setVisibility(View.VISIBLE);
            dataVisible.setVisibility(View.VISIBLE);
            MapLayerDescriptor x = (MapLayerDescriptor) mapData;
            Integer iconResourceId = x.getIconImageResourceId();
            if (iconResourceId != null) {
                icon.setImageResource(iconResourceId);
            }
            name.setText(x.getOverlayName());
            detail.setText(x.getCacheName());
            dataVisible.setChecked(overlayManager.isOverlayVisible(x));
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (mapData instanceof StaticControl) {
                builtinDataControlValues = ((StaticControl) mapData).onCheckedChanged(buttonView, isChecked, builtinDataControlValues, listener);
            }
            else if (mapData instanceof MapLayerDescriptor) {
                if (isChecked){
                    overlayManager.showOverlay((MapLayerDescriptor) mapData);
                }
                else {
                    overlayManager.hideOverlay((MapLayerDescriptor) mapData);
                }
            }
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            // it's always base map control, so just do this here and cut out the middle man
            builtinDataControlValues = BuiltinDataControlValues.from(builtinDataControlValues).baseMapType(position + 1).finish();
            if (listener != null) {
                listener.onBaseMapChanged(builtinDataControlValues);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {}
    }

    private static final int LAST_STATIC_CONTROL_POS = StaticControl.values().length - 1;

    private DragListView mapControlList;
    private BuiltinDataControlValues builtinDataControlValues;
    private OverlayOnMapManager overlayManager;
    private ArrayAdapter<CharSequence> baseMapChoices;
    private MapDataListener listener;

    public MapDataFragment() {
    }

    public void setDataSources(BuiltinDataControlValues builtinDataControlValues, OverlayOnMapManager overlayManager) {
        this.builtinDataControlValues = builtinDataControlValues;
        this.overlayManager = overlayManager;
    }

    public void setMapDataListener(MapDataListener x) {
        listener = x;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        baseMapChoices = ArrayAdapter.createFromResource(context, R.array.baseLayerEntries, android.R.layout.simple_spinner_item);
        baseMapChoices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_map_data, container, false);
        mapControlList = (DragListView) root.findViewById(R.id.local_data_list);
        mapControlList.getRecyclerView().setVerticalScrollBarEnabled(true);
        LinearLayoutManager linearLayout = new LinearLayoutManager(getContext());
        mapControlList.setLayoutManager(linearLayout);
        mapControlList.getRecyclerView().setNestedScrollingEnabled(true);
        mapControlList.setAdapter(new MapDataItemAdapter(), false);
        mapControlList.setDragListListener(this);
        mapControlList.setDragListCallback(this);
        mapControlList.setSnapDragItemToTouch(false);
        // TODO: add loading status getter to OverlayOnMapManager
        overlayManager.addOverlayOnMapListener(this);
        syncDataList();
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        overlayManager.removeOverlayOnMapListener(this);
        mapControlList.setDragListListener(null);
        mapControlList = null;
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void overlaysChanged() {
        syncDataList();
    }

    @Override
    public void onItemDragStarted(int position) {}

    @Override
    public void onItemDragging(int itemPosition, float x, float y) {}

    @Override
    public void onItemDragEnded(int fromPosition, int toPosition) {
        // TODO: technically there should be an event dispatched from overlay manager
        // that z-order changed, but this just assumes the re-ordering worked
        if (fromPosition == toPosition) {
            return;
        }
        int lastIndex = mapControlList.getAdapter().getItemList().size() - 1;
        int fromReversed = lastIndex - fromPosition;
        int toReversed = lastIndex - toPosition;
        if (!overlayManager.moveZIndex(fromReversed, toReversed)) {
            Toast.makeText(getContext(), R.string.z_index_move_failed, Toast.LENGTH_SHORT).show();
            syncDataList();
        }
    }

    @Override
    public boolean canDragItemAtPosition(int dragPosition) {
        return dragPosition > LAST_STATIC_CONTROL_POS;
    }

    @Override
    public boolean canDropItemAtPosition(int dropPosition) {
        return dropPosition > LAST_STATIC_CONTROL_POS;
    }

    private void syncDataList() {
        MapDataItemAdapter adapter = (MapDataItemAdapter) mapControlList.getAdapter();
        List<MapLayerDescriptor> overlays = overlayManager.getOverlaysInZOrder();
        Collections.reverse(overlays);
        List<Object> mapDataItems = new ArrayList<Object>(Arrays.asList(StaticControl.values()));
        mapDataItems.addAll(overlays);
        adapter.setItemList(mapDataItems);
    }
}
