package mil.nga.giat.mage.map.cache

import mil.nga.giat.mage.map.cache.MapDataResource.Resolved
import java.net.URI

/**
 * A `MapLayerDescriptor` represents a logical grouping of data that appears on a
 * map as visual objects.  A [MapDataProvider] implementation will create instances
 * of its associated `MapLayerDescriptor` subclass.  Note that this class provides
 * default [.equals] and [.hashCode] implementations because [MapDataManager]
 * places `MapLayerDescriptor` instances in sets and they may also be used as
 * [java.util.HashMap] keys.  Subclasses must take care those methods work properly
 * if overriding those or other methods on which `equals()` and `hashCode()` depend.
 */
abstract class MapLayerDescriptor protected constructor(
        /**
         * @return name of this layer, unique whithin its containing [data resource][MapDataResource]
         */
        val layerName: String,
        /**
         * @return the URI of the [MapDataResource] that contains this layer's data
         */
        val resourceUri: URI,
        /**
         * @return the [type][MapDataProvider] of the [data resource][MapDataResource] that contains this layer's data
         *
         * TODO: this should probably be removed.  it's redundant w/respect to the provider class supplied to the owning MapDataResource
         */
        val dataType: Class<out MapDataProvider>) {

    val layerUri: URI = resourceUri.resolve(URI(null, null, layerName, null))

    /**
     * Return an optional human-readable alternative to the [layerName].
     * The default value is the [layerName].
     */
    open var layerTitle: String = layerName
        protected set

    /**
     * Return the icon image resource ID for this layer.
     * @return a [Android resource][android.content.res.Resources] ID or null
     */
    open val iconImageResourceId: Int?
        get() = null

    /**
     * Return detailed information about this layer to display
     * @return an info string or null
     */
    open val info: String?
        get() = null

    /**
     * Two `MapLayerDescriptor` instances are equal if they have the
     * same [name][.getLayerName] and their comprising caches' [name][.getResourceName]
     * and [type][.getDataType] are [equal][MapDataResource.equals] as well.
     * @param obj
     * @return
     */
    override fun equals(other: Any?): Boolean {
        if (other is MapLayerDescriptor) {
            return resourceUri == other.resourceUri && layerName == other.layerName
        }
        return false;
    }

    override fun hashCode(): Int {
        return layerName.hashCode()
    }
}