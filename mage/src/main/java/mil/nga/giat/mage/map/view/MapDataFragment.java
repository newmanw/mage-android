package mil.nga.giat.mage.map.view;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.woxthebox.draglistview.DragItemAdapter;
import com.woxthebox.draglistview.DragListView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mil.nga.giat.mage.R;
import mil.nga.giat.mage.data.Resource;
import mil.nga.giat.mage.map.MapElementSpec;
import mil.nga.giat.mage.map.cache.MapLayerManager;

import static android.support.v7.widget.RecyclerView.NO_ID;

public class MapDataFragment extends Fragment implements DragListView.DragListListener, DragListView.DragListCallback {

    public interface MapDataListener {
        void onBaseMapChanged(IntrinsicMapDataControls change);
        void onObservationsVisibilityChanged(IntrinsicMapDataControls change);
        void onLocationsVisibilityChanged(IntrinsicMapDataControls change);
        void onMgrsVisibilityChanged(IntrinsicMapDataControls change);
    }

    public static class IntrinsicMapDataControls {

        public static class Builder {

            private int baseMapType;
            private boolean observationsVisible;
            private boolean locationsVisible;
            private boolean mgrsVisible;

            public Builder baseMapType(int x) {
                baseMapType = x;
                return this;
            }

            public Builder observationsVisible(boolean x) {
                observationsVisible = x;
                return this;
            }

            public Builder locationsVisible(boolean x) {
                locationsVisible = x;
                return this;
            }

            public Builder mgrsVisible(boolean x) {
                mgrsVisible = x;
                return this;
            }

            public IntrinsicMapDataControls finish() {
                return new IntrinsicMapDataControls(baseMapType, observationsVisible, locationsVisible, mgrsVisible);
            }
        }

        public static Builder create() {
            return new Builder();
        }

        static Builder from(IntrinsicMapDataControls init) {
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

        IntrinsicMapDataControls(int baseMapType, boolean observationsVisible, boolean locationsVisible, boolean mgrsVisible) {
            this.baseMapType = baseMapType;
            this.observationsVisible = observationsVisible;
            this.locationsVisible = locationsVisible;
            this.mgrsVisible = mgrsVisible;
        }

        public int getBaseMapType() {
            return baseMapType;
        }

        public boolean isObservationsVisible() {
            return observationsVisible;
        }

        public boolean isLocationsVisible() {
            return locationsVisible;
        }

        public boolean isMgrsVisible() {
            return mgrsVisible;
        }
    }

    private enum StaticControl {
        BaseMap {
            @Override
            void bindSpecific(LayerControlViewHolder holder, IntrinsicMapDataControls values) {
                holder.name.setText(R.string.map_data_base_map_label);
                holder.dataVisible.setVisibility(View.GONE);
                holder.dataChoice.setVisibility(View.VISIBLE);
                holder.dataChoice.setSelection(values.baseMapType - 1);
            }
        },
        Observations {
            @Override
            void bindSpecific(LayerControlViewHolder holder, IntrinsicMapDataControls values) {
                holder.name.setText(R.string.map_data_observations_label);
                holder.dataChoice.setVisibility(View.GONE);
                holder.dataVisible.setVisibility(View.VISIBLE);
                holder.dataVisible.setChecked(values.isObservationsVisible());
            }

            @Override
            IntrinsicMapDataControls onCheckedChanged(CompoundButton buttonView, boolean checked, IntrinsicMapDataControls values, MapDataListener listener) {
                IntrinsicMapDataControls update = IntrinsicMapDataControls.from(values).observationsVisible(checked).finish();
                if (listener != null) {
                    listener.onObservationsVisibilityChanged(update);
                }
                return update;
            }
        },
        Locations {
            @Override
            void bindSpecific(LayerControlViewHolder holder, IntrinsicMapDataControls values) {
                holder.name.setText(R.string.map_data_locations_label);
                holder.dataChoice.setVisibility(View.GONE);
                holder.dataVisible.setVisibility(View.VISIBLE);
                holder.dataVisible.setChecked(values.isLocationsVisible());
            }

            @Override
            IntrinsicMapDataControls onCheckedChanged(CompoundButton buttonView, boolean checked, IntrinsicMapDataControls values, MapDataListener listener) {
                IntrinsicMapDataControls update = IntrinsicMapDataControls.from(values).locationsVisible(checked).finish();
                if (listener != null) {
                    listener.onLocationsVisibilityChanged(update);
                }
                return update;
            }
        },
        MGRS {
            @Override
            void bindSpecific(LayerControlViewHolder holder, IntrinsicMapDataControls values) {
                holder.name.setText(R.string.map_data_mgrs_label);
                holder.dataChoice.setVisibility(View.GONE);
                holder.dataVisible.setVisibility(View.VISIBLE);
                holder.dataVisible.setChecked(values.isMgrsVisible());
            }

            @Override
            IntrinsicMapDataControls onCheckedChanged(CompoundButton buttonView, boolean checked, IntrinsicMapDataControls values, MapDataListener listener) {
                IntrinsicMapDataControls update = IntrinsicMapDataControls.from(values).mgrsVisible(checked).finish();
                if (listener != null) {
                    listener.onMgrsVisibilityChanged(update);
                }
                return update;
            }
        };

        abstract void bindSpecific(LayerControlViewHolder holder, IntrinsicMapDataControls values);
        IntrinsicMapDataControls onCheckedChanged(CompoundButton buttonView, boolean checked, IntrinsicMapDataControls values, MapDataListener listener) {
            return values;
        }

        final void bind(LayerControlViewHolder holder, IntrinsicMapDataControls values) {
            holder.icon.setVisibility(View.GONE);
            holder.mGrabView.setVisibility(View.GONE);
            holder.detail.setText("");
            bindSpecific(holder, values);
        }
    }

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_STATIC_LAYER = 1;
    private static final int VIEW_TYPE_SUPPLEMENTAL_LAYER = 2;

    private abstract class BindableViewHolder extends DragItemAdapter.ViewHolder {

        private BindableViewHolder(View itemView, int handleResId, boolean dragOnLongPress) {
            super(itemView, handleResId, dragOnLongPress);
        }

        abstract void bindTo(int position);
    }


    private class MapDataItemAdapter extends DragItemAdapter<Object, BindableViewHolder> {

        private final Map<Object, Long> itemIds = new HashMap<>();

        @Override
        public long getUniqueItemId(int i) {
            Object mapData = getItemList().get(i);
            Long id = itemIds.get(mapData);
            return id == null ? NO_ID : id;
        }

        @NonNull
        @Override
        public BindableViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_HEADER) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.map_data_supplemental_header, parent, false);
                return new HeaderViewHolder(view);
            }
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.map_data_control_item, parent, false);
            if (viewType == VIEW_TYPE_STATIC_LAYER) {
                return new StaticLayerViewHolder(view);
            }
            else if (viewType == VIEW_TYPE_SUPPLEMENTAL_LAYER) {
                return new SupplementalLayerViewHolder(view);
            }
            throw new IllegalArgumentException("invalid view type: " + viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull BindableViewHolder holder, int position) {
            super.onBindViewHolder(holder, position);
            holder.bindTo(position);
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
            if (position == SUPPLEMENTAL_HEADER_POS) {
                return SUPPLEMENTAL_HEADER_POS;
            }
            return -1;
        }
    }

    private class HeaderViewHolder extends BindableViewHolder implements View.OnClickListener {

        private final ImageButton refreshButton;
        private final ProgressBar progressBar;

        HeaderViewHolder(View view) {
            super(view, R.id.map_refresh_layers_button, false);
            refreshButton = view.findViewById(R.id.map_refresh_layers_button);
            progressBar = view.findViewById(R.id.map_refresh_layers_progress);
            refreshButton.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            refreshButton.setClickable(false);
            progressBar.setVisibility(View.VISIBLE);
            model.refreshLayers();
        }

        @Override
        void bindTo(int pos) {
            if (model.getLayersInZOrder().getValue() == null || model.getLayersInZOrder().getValue().getStatus() == Resource.Status.Loading) {
                progressBar.setVisibility(View.VISIBLE);
                refreshButton.setClickable(false);
            }
            else {
                progressBar.setVisibility(View.INVISIBLE);
                refreshButton.setClickable(true);
            }
        }
    }

    private abstract class LayerControlViewHolder extends BindableViewHolder implements CompoundButton.OnCheckedChangeListener, AdapterView.OnItemSelectedListener {

        final ImageView icon;
        final TextView name;
        final TextView detail;
        final Spinner dataChoice;
        final SwitchCompat dataVisible;
        final ProgressBar dataLoading;
        Object mapData;

        LayerControlViewHolder(View itemView) {
            super(itemView, R.id.map_data_drag_icon, false);
            icon = itemView.findViewById(R.id.map_data_icon);
            name = itemView.findViewById(R.id.map_data_name);
            detail = itemView.findViewById(R.id.map_data_detail);
            dataVisible = itemView.findViewById(R.id.map_data_visible);
            dataChoice = itemView.findViewById(R.id.map_data_choice);
            dataLoading = itemView.findViewById(R.id.map_data_layer_progress);
            // base map is the only control that uses the spinner, so just set it here instead of during binding
            dataChoice.setAdapter(baseMapChoices);
        }

        @Override
        @CallSuper
        void bindTo(int pos) {
            mapData = listAdapter.getItemList().get(pos);
            dataVisible.jumpDrawablesToCurrentState();
            dataVisible.setOnCheckedChangeListener(this);
            dataChoice.setOnItemSelectedListener(this);
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            // it's always base map control, so just do this here and cut out the middle man
            intrinsicMapDataControls = IntrinsicMapDataControls.from(intrinsicMapDataControls).baseMapType(position + 1).finish();
            if (listener != null) {
                listener.onBaseMapChanged(intrinsicMapDataControls);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {}
    }

    private class StaticLayerViewHolder extends LayerControlViewHolder {

        StaticLayerViewHolder(View itemView) {
            super(itemView);
        }

        @Override
        void bindTo(int pos) {
            super.bindTo(pos);
            StaticControl control = (StaticControl) mapData;
            control.bind(this, intrinsicMapDataControls);
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            intrinsicMapDataControls = ((StaticControl) mapData).onCheckedChanged(buttonView, isChecked, intrinsicMapDataControls, listener);
        }
    }

    private class SupplementalLayerViewHolder extends LayerControlViewHolder {

        SupplementalLayerViewHolder(View itemView) {
            super(itemView);
        }

        @Override
        void bindTo(int pos) {
            super.bindTo(pos);
            dataChoice.setVisibility(View.GONE);
            icon.setVisibility(View.VISIBLE);
            mGrabView.setVisibility(View.VISIBLE);
            dataVisible.setVisibility(View.VISIBLE);
            MapLayersViewModel.Layer layer = (MapLayersViewModel.Layer) mapData;
            Integer iconResourceId = layer.getDesc().getIconImageResourceId();
            if (iconResourceId != null) {
                icon.setImageResource(iconResourceId);
            }
            name.setText(layer.getDesc().getLayerTitle());
            detail.setText(layer.getResourceName());
            dataVisible.setChecked(layer.isVisible());
            LiveData<Resource<Map<Object, MapElementSpec>>> liveElements = model.elementsForLayer(layer);
            Resource<?> layerResource = liveElements.getValue();
            dataLoading.setVisibility(layerResource != null && layerResource.getStatus() == Resource.Status.Loading ?
                View.VISIBLE : View.INVISIBLE);
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            model.setLayerVisibility((MapLayersViewModel.Layer) mapData, isChecked);
        }
    }

    private static final int SUPPLEMENTAL_HEADER_POS = StaticControl.values().length - 1;

    private MapLayersViewModel model;
    private DragListView mapControlList;
    private IntrinsicMapDataControls intrinsicMapDataControls;
    private MapLayerManager layerManager;
    private ArrayAdapter<CharSequence> baseMapChoices;
    private MapDataListener listener;
    private MapDataItemAdapter listAdapter;

    public MapDataFragment() {
    }

    public void setDataSources(IntrinsicMapDataControls intrinsicMapDataControls, MapLayerManager overlayManager) {
        this.intrinsicMapDataControls = intrinsicMapDataControls;
        this.layerManager = overlayManager;
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
        model = ViewModelProviders.of(requireActivity()).get(MapLayersViewModel.class);
        model.getLayersInZOrder().observe(this, layersResource -> {
            // TODO: update the list
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_map_data, container, false);
        mapControlList = root.findViewById(R.id.local_data_list);
        mapControlList.getRecyclerView().setVerticalScrollBarEnabled(true);
        listAdapter = new MapDataItemAdapter();
        LinearLayoutManager linearLayout = new LinearLayoutManager(getContext());
        mapControlList.setLayoutManager(linearLayout);
        mapControlList.getRecyclerView().setNestedScrollingEnabled(true);
        mapControlList.setAdapter(listAdapter, false);
        mapControlList.setDragListListener(this);
        mapControlList.setDragListCallback(this);
        mapControlList.setSnapDragItemToTouch(false);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
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
        if (!model.moveZIndex(fromReversed, toReversed)) {
            Toast.makeText(getContext(), R.string.z_index_move_failed, Toast.LENGTH_SHORT).show();
            // TODO: restore old list
        }
    }

    @Override
    public boolean canDragItemAtPosition(int dragPosition) {
        return dragPosition > SUPPLEMENTAL_HEADER_POS;
    }

    @Override
    public boolean canDropItemAtPosition(int dropPosition) {
        return dropPosition > SUPPLEMENTAL_HEADER_POS;
    }

    private void syncDataList() {
        MapDataItemAdapter adapter = (MapDataItemAdapter) mapControlList.getAdapter();
        // TODO: diff util
//        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
//            @Override
//            public int getOldListSize() {
//                return 0;
//            }
//
//            @Override
//            public int getNewListSize() {
//                return 0;
//            }
//
//            @Override
//            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
//                return false;
//            }
//
//            @Override
//            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
//                return false;
//            }
//        }, true);
//        diff.dispatchUpdatesTo(listAdapter);
    }
}
