package mil.nga.giat.mage.sdk.gson.serializer;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;

import mil.nga.giat.mage.sdk.datastore.location.Location;
import mil.nga.giat.mage.sdk.datastore.location.LocationProperty;
import mil.nga.giat.mage.sdk.datastore.observation.Observation;
import mil.nga.giat.mage.sdk.utils.DateUtility;
import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

/**
 * Used to convert a Location object into a json String.
 * 
 * @author travis
 * 
 */
public class LocationSerializer implements JsonSerializer<Collection<Location>> {

	public LocationSerializer(Context context) {
		super();
	}

	@Override
	public JsonElement serialize(Collection<Location> locations, Type locationType, JsonSerializationContext context) {
		// create required components
		JsonArray jsonLocations = new JsonArray();
		for (Location location : locations) {

			JsonObject jsonLocation = new JsonObject();
			JsonObject jsonProperties = new JsonObject();

			jsonLocation.add("geometry", new JsonParser().parse(GeometrySerializer.getGsonBuilder().toJson(location.getLocationGeometry().getGeometry())));
			jsonLocation.add("properties", jsonProperties);
			jsonProperties.add("timestamp", new JsonPrimitive(DateUtility.getISO8601().format(location.getTimestamp())));
			jsonProperties.add("timestampUnformattedDuringSerialization", new JsonPrimitive(location.getTimestamp().toString()));
			jsonProperties.add("timeNowDuringSerialization", new JsonPrimitive(System.currentTimeMillis()));
			jsonProperties.add("user", new JsonPrimitive(location.getUser().getId()));

			// properties
			for (LocationProperty property : location.getProperties()) {
				String key = property.getKey();
				Serializable value = property.getValue();

				conditionalAdd(key, value, jsonProperties);
			}
			// assemble final location array
			jsonLocations.add(jsonLocation);
		}

		return jsonLocations;
	}

	/**
	 * Convenience method for returning a Gson object with a registered GSon TypeAdaptor i.e. custom serializer.
	 * 
	 * @return A Gson object that can be used to convert {@link Observation} object into a JSON string.
	 */
	public static Gson getGsonBuilder(Context context) {
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapter(new TypeToken<List<Location>>(){}.getType(), new LocationSerializer(context));
		return gsonBuilder.create();
	}

	/**
	 * Utility used to ensure we don't add junk to the json string. For now, we skip null property values.
	 * 
	 * @param property
	 *            Property to add.
	 * @param toAdd
	 *            Property value to add.
	 * @param pJsonObject
	 *            Object to conditionally add to.
	 * @return A reference to json object.
	 */
	private JsonObject conditionalAdd(String property, Serializable toAdd, final JsonObject pJsonObject) {
		if (toAdd != null) {
			if (toAdd instanceof Double) {
				pJsonObject.add(property, new JsonPrimitive((Double) toAdd));
			} else if (toAdd instanceof Float) {
				pJsonObject.add(property, new JsonPrimitive((Float) toAdd));
			} else if (toAdd instanceof Boolean) {
				pJsonObject.add(property, new JsonPrimitive((Boolean) toAdd));
			} else {
				pJsonObject.add(property, new JsonPrimitive(toAdd.toString()));
			}
		}
		return pJsonObject;
	}

}
