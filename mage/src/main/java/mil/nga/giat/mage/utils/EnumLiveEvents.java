package mil.nga.giat.mage.utils;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.SparseArray;

public class EnumLiveEvents<E extends Enum<E> & EnumLiveEvents.EnumEventType<L>, L> extends LiveEvents<L> {

    public interface EnumEventType<L> {
        void deliver(@NonNull L listener, @Nullable Object data);
    }

    private static final class EnumEventDiscriminator<E extends Enum<E> & EnumEventType<L>, L> implements EventProtocol<L> {

        private final SparseArray<E> eventTypeForOrdinal = new SparseArray<>();

        EnumEventDiscriminator(Class<E> eventTypes) {
            for (E eventType : eventTypes.getEnumConstants()) {
                eventTypeForOrdinal.put(eventType.ordinal(), eventType);
            }
        }

        @Override
        public void deliver(int what, @NonNull L listener, @Nullable Object data) {
            E eventType = eventTypeForOrdinal.get(what);
            eventType.deliver(listener, data);
        }
    }

    public EnumLiveEvents(Class<E> eventTypes) {
        super(new EnumEventDiscriminator<E, L>(eventTypes));
    }

    protected final void trigger(E eventType, Object data) {
        super.trigger(eventType.ordinal(), data);
    }
}


