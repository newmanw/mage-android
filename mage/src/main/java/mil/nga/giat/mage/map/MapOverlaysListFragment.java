package mil.nga.giat.mage.map;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.woxthebox.draglistview.DragItemAdapter;
import com.woxthebox.draglistview.DragListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mil.nga.giat.mage.R;
import mil.nga.giat.mage.map.cache.CacheOverlay;
import mil.nga.giat.mage.map.cache.OverlayOnMapManager;

import static android.support.v7.widget.RecyclerView.NO_ID;

public class MapOverlaysListFragment extends Fragment implements OverlayOnMapManager.OverlayOnMapListener, DragListView.DragListListener {

    private class OverlayItemAdapter extends DragItemAdapter<CacheOverlay, OverlayItemViewHolder> {

        private final Map<CacheOverlay, Long> itemIds = new HashMap<>();

        @Override
        public long getUniqueItemId(int i) {
            CacheOverlay overlay = getItemList().get(i);
            Long id = itemIds.get(overlay);
            return id == null ? NO_ID : id;
        }

        @Override
        public OverlayItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.cache_overlay_list_item, parent, false);
            return new OverlayItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(OverlayItemViewHolder holder, int position) {
            super.onBindViewHolder(holder, position);
            CacheOverlay overlay = getItemList().get(position);
            holder.setOverlay(overlay);
        }

        @Override
        public void setItemList(List<CacheOverlay> itemList) {
            for (int i = 0; i < itemList.size(); i++) {
                CacheOverlay overlay = itemList.get(i);
                itemIds.put(overlay, (long) i);
            }
            super.setItemList(itemList);
        }
    }

    private class OverlayItemViewHolder extends DragItemAdapter.ViewHolder implements CompoundButton.OnCheckedChangeListener {

        private final ImageView icon;
        private final TextView name;
        private final TextView detail;
        private final SwitchCompat enabled;
        private CacheOverlay overlay;

        public OverlayItemViewHolder(View itemView) {
            super(itemView, R.id.overlay_item_drag_icon, false);
            icon = (ImageView) itemView.findViewById(R.id.overlay_item_image);
            name = (TextView) itemView.findViewById(R.id.overlay_item_name);
            detail = (TextView) itemView.findViewById(R.id.overlay_item_detail);
            enabled = (SwitchCompat) itemView.findViewById(R.id.overlay_item_enabled);
        }

        private void setOverlay(CacheOverlay x) {
            overlay = x;
            Integer iconResourceId = overlay.getIconImageResourceId();
            if (iconResourceId != null) {
                icon.setImageResource(iconResourceId);
            }
            name.setText(overlay.getOverlayName());
            detail.setText(overlay.getCacheName());
            enabled.setOnCheckedChangeListener(null);
            enabled.setChecked(overlayManager.isOverlayVisible(overlay));
            // don't let the switches animate as the list scrolls
            enabled.jumpDrawablesToCurrentState();
            enabled.setOnCheckedChangeListener(this);
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked){
                overlayManager.showOverlay(overlay);
            }
            else {
                overlayManager.hideOverlay(overlay);
            }
        }
    }


    private OverlayOnMapManager overlayManager;
    private DragListView overlaysListView;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_map_layers_tiles, container, false);
        overlaysListView = (DragListView) root.findViewById(R.id.tile_overlays_list);
        overlaysListView.getRecyclerView().setVerticalScrollBarEnabled(true);
        overlaysListView.setLayoutManager(new LinearLayoutManager(getContext()));
        overlaysListView.setAdapter(new OverlayItemAdapter(), true);
        overlaysListView.setDragListListener(this);
//        overlaysListView.setScrollingEnabled(false);
        syncItemList();
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        overlayManager.removeOverlayOnMapListener(this);
    }

    public void setOverlayManager(OverlayOnMapManager x) {
        overlayManager = x;
        overlayManager.addOverlayOnMapListener(this);
    }

    @Override
    public void overlaysChanged() {
        syncItemList();
    }

    @Override
    public void onItemDragStarted(int position) {

    }

    @Override
    public void onItemDragging(int itemPosition, float x, float y) {

    }

    @Override
    public void onItemDragEnded(int fromPosition, int toPosition) {
        if (fromPosition != toPosition) {
            overlayManager.changeZOrder(fromPosition, toPosition);
        }
    }

    private void syncItemList() {
        OverlayItemAdapter adapter = (OverlayItemAdapter) overlaysListView.getAdapter();
        adapter.setItemList(overlayManager.getOverlays());
    }
}
