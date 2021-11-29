package mil.nga.giat.mage.map.marker

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.bumptech.glide.Glide
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.android.synthetic.main.feed_list_item.view.*
import kotlinx.android.synthetic.main.feeditem_info_window.view.content
import mil.nga.geopackage.map.geom.GoogleMapShape
import mil.nga.geopackage.map.geom.GoogleMapShapeConverter
import mil.nga.geopackage.map.geom.GoogleMapShapeType
import mil.nga.giat.mage.R
import mil.nga.giat.mage.data.feed.Feed
import mil.nga.giat.mage.data.feed.FeedItem
import mil.nga.giat.mage.data.feed.FeedWithItems
import mil.nga.giat.mage.data.feed.ItemWithFeed
import mil.nga.giat.mage.glide.target.MarkerTarget
import mil.nga.giat.mage.network.Server
import mil.nga.giat.mage.utils.DateFormatFactory
import mil.nga.sf.GeometryType
import mil.nga.sf.util.GeometryUtils
import java.util.*

class FeedItemCollection(val context: Context, val map: GoogleMap) {

    private val feedMap = mutableMapOf<String, MutableMap<String, GoogleMapShape>>()
    private var feedItemIdWithInfoWindow: String? = null
    private val infoWindowAdapter: GoogleMap.InfoWindowAdapter = InfoWindowAdapter(context)
    private val dateFormat = DateFormatFactory.format("yyyy-MM-dd HH:mm zz", Locale.getDefault(), context)

    fun setItems(feedWithItems: FeedWithItems) {
        val shapes = feedMap[feedWithItems.feed.id] ?: mutableMapOf()
        feedMap[feedWithItems.feed.id] = shapes

        if (feedWithItems.feed.itemsHaveIdentity) {
            val update = mutableMapOf<String, Pair<FeedItem, GoogleMapShape>>()
            val add = mutableListOf<FeedItem>()
            feedWithItems.items.forEach { item ->
                val shape = shapes.remove(item.id)
                if (shape == null) add.add(item) else update[item.id] = Pair(item, shape)
            }

            shapes.values.forEach { it.remove() }
            shapes.clear()

            add.forEach { item ->
                add(feedWithItems.feed, item)?.let { shape ->
                    shapes[item.id] = shape
                }
            }

            update.values.forEach { (item, shape) ->
                update(item, shape)?.let {
                    shapes[item.id] = it
                }
            }
        } else {
            shapes.values.forEach { it.remove() }
            shapes.clear()

            feedWithItems.items.forEach { item ->
                add(feedWithItems.feed, item)?.let { shape ->
                    shapes[item.id] = shape
                }
            }
        }
    }

    private fun add(feed: Feed, feedItem: FeedItem): GoogleMapShape? {
        if (feedItem.geometry == null) return null

        val geometry = feedItem.geometry

        return if (geometry.geometryType == GeometryType.POINT) {
            val point = GeometryUtils.getCentroid(geometry)

            val marker = map.addMarker(MarkerOptions()
                .position(LatLng(point.y, point.x))
                .visible(false))

            marker?.tag = ItemWithFeed(feed, feedItem)
            if (feedItemIdWithInfoWindow == feedItem.id) {
                map.setInfoWindowAdapter(infoWindowAdapter)
                marker?.showInfoWindow()
            }

            val shape = GoogleMapShape(GeometryType.POINT, GoogleMapShapeType.MARKER, marker)

            if (feed.mapStyle?.iconStyle?.id != null) {
                Glide.with(context)
                    .asBitmap()
                    .load("${Server(context).baseUrl}/api/icons/${feed.mapStyle?.iconStyle?.id}/content")
                    .error(R.drawable.default_marker)
                    .into(MarkerTarget(context, marker, 24, 24))
            } else {
                Glide.with(context)
                    .asBitmap()
                    .load(R.drawable.default_marker)
                    .into(MarkerTarget(context, marker, 24, 24))
            }

            shape
        } else {
            // TODO set style
            GoogleMapShapeConverter.addShapeToMap(map, GoogleMapShapeConverter().toShape(geometry))
        }
    }

    fun update(item: FeedItem, shape: GoogleMapShape): GoogleMapShape? {
        val marker = shape.shape as? Marker ?: return null
        val geometry = item.geometry ?: return null

        return if (geometry.geometryType == GeometryType.POINT) {
            val point = GeometryUtils.getCentroid(geometry)
            marker.position = LatLng(point.y, point.x)
            shape
        } else {
            // TODO set style
            shape.remove()
            GoogleMapShapeConverter.addShapeToMap(map, GoogleMapShapeConverter().toShape(geometry))
        }
    }

    fun clear() {
        feedMap.values.forEach { feedShapes ->
            feedShapes.values.forEach { shape ->
                shape.remove()
            }

            feedShapes.clear()
        }

        feedMap.clear()
    }

    fun onMarkerClick(marker: Marker): Boolean {
        if (marker.tag is ItemWithFeed) {
            map.setInfoWindowAdapter(infoWindowAdapter)
            marker.showInfoWindow()
            feedItemIdWithInfoWindow = (marker.tag as ItemWithFeed).item.id

            return true
        }

        return false
    }

    fun onInfoWindowClose(marker: Marker): Boolean {
        if (marker.tag is ItemWithFeed) {
            feedItemIdWithInfoWindow = null
            return true
        }

        return false
    }

    fun itemForMarker(marker: Marker): FeedItem? {
        return (marker.tag as? ItemWithFeed)?.item
    }

    private inner class InfoWindowAdapter(val context: Context): GoogleMap.InfoWindowAdapter {
        private val layoutInflater = LayoutInflater.from(context)

        override fun getInfoContents(marker: Marker?): View? {
            return null
        }

        override fun getInfoWindow(marker: Marker?): View {
            val view = layoutInflater.inflate(R.layout.feeditem_info_window, null) as LinearLayout

            val itemWithFeed = marker?.tag as ItemWithFeed
            val feed = itemWithFeed.feed

            val properties = itemWithFeed.item.properties
            if (properties?.isJsonNull == false) {

                val temporalProperty = properties.asJsonObject.get(feed.itemTemporalProperty)
                if (feed.itemTemporalProperty != null && temporalProperty?.asString?.isEmpty() == false) {
                    view.overline.visibility = View.VISIBLE
                    view.overline.text = dateFormat.format(Date(temporalProperty.asLong))
                    view.content.visibility = View.VISIBLE
                }

                val primaryProperty = properties.asJsonObject.get(feed.itemPrimaryProperty)
                if (feed.itemPrimaryProperty != null && primaryProperty?.asString?.isEmpty() == false) {
                    view.primary.visibility = View.VISIBLE
                    view.primary.text = primaryProperty.asString
                    view.content.visibility = View.VISIBLE
                }

                val secondaryProperty = properties.asJsonObject.get(feed.itemSecondaryProperty)
                if (feed.itemSecondaryProperty != null && secondaryProperty?.asString?.isEmpty() == false) {
                    view.secondary.visibility = View.VISIBLE
                    view.secondary.text = secondaryProperty.asString
                    view.content.visibility = View.VISIBLE
                }
            }

            return view
        }
    }
}