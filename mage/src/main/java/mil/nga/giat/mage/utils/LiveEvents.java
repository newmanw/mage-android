package mil.nga.giat.mage.utils;

import android.arch.lifecycle.DefaultLifecycleObserver;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

@MainThread
public class LiveEvents<L> implements LifecycleObserver, DefaultLifecycleObserver {


    @FunctionalInterface
    protected interface EventProtocol<L> {

        void deliver(int what, @NonNull L listener, @Nullable Object data);
    }

    private final EventProtocol<L> protocol;
    private final Set<OwnerListener<L>> listeners = new LinkedHashSet<>();

    public LiveEvents(EventProtocol<L> protocol) {
        this.protocol = protocol;
    }

    /**
     *
     * @param owner
     * @param listener
     */
    public final void listen(LifecycleOwner owner, L listener) {
        if (owner.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
            return;
        }
        // this assumes the same observer does not get added twice, which is the case for LifecycleRegistry
        owner.getLifecycle().addObserver(this);
        listeners.add(new OwnerListener<>(owner, listener));
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        Iterator<OwnerListener<L>> cursor = listeners.iterator();
        while (cursor.hasNext()) {
            OwnerListener<L> ownerListener = cursor.next();
            if (ownerListener.owner == owner) {
                cursor.remove();
            }
        }
        owner.getLifecycle().removeObserver(this);
    }

    /**
     * Fire the given event data to active listeners.  Active listeners are those whose {@link LifecycleOwner}
     * is either {@link android.arch.lifecycle.Lifecycle.State#STARTED} or {@link Lifecycle.State#RESUMED}.
     * This method is left protected so only a particular event source can trigger events from its own subclass
     * of this class.  This is achieved by the subclass exposing a private method that calls {@code super.trigger(...)}.
     *
     * @param what the type of event
     * @param data any contextual data associated with this particular event
     */
    protected final void trigger(int what, @Nullable Object data) {
        for (OwnerListener<L> ownerListener : listeners) {
            if (ownerListener.owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                protocol.deliver(what, ownerListener.listener, data);
            }
        }
    }

    protected final void trigger(int what) {
        trigger(what, null);
    }

    private static class OwnerListener<L> {

        final LifecycleOwner owner;
        final L listener;

        OwnerListener(LifecycleOwner owner, L listener) {
            this.owner = owner;
            this.listener = listener;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof OwnerListener)) {
                return false;
            }
            OwnerListener l = (OwnerListener) other;
            return l.owner == owner && l.listener == listener;
        }

        @Override
        public int hashCode() {
            return owner.hashCode() ^ listener.hashCode();
        }
    }
}
