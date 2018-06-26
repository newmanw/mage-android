package mil.nga.giat.mage.test;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import mil.nga.giat.mage.map.cache.StaticFeatureLayerRepositoryTest;

public class TargetSuppliesPropertyValueMatcher<T, V> extends BaseMatcher<T> {

    public static <T, V> TargetSuppliesPropertyValueMatcher<T, V> withValueSuppliedBy(PropertyValueSupplier<T, V> valueProvider, Matcher<V> valueMatcher) {
        return new TargetSuppliesPropertyValueMatcher<>(valueProvider, valueMatcher);
    }

    @FunctionalInterface
    public interface PropertyValueSupplier<T, V> {
        V getValueFrom(T target);
    }

    final PropertyValueSupplier<T, V> supplier;
    final Matcher<V> valueMatcher;

    public TargetSuppliesPropertyValueMatcher(PropertyValueSupplier<T, V> supplier, Matcher<V> valueMatcher) {
        this.supplier = supplier;
        this.valueMatcher = valueMatcher;
    }

    @Override
    public boolean matches(Object item) {
        @SuppressWarnings("unchecked")
        T target = (T) item;
        V provided = supplier.getValueFrom(target);
        return valueMatcher.matches(provided);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("supplied property value ");
        description.appendDescriptionOf(valueMatcher);
    }
}
