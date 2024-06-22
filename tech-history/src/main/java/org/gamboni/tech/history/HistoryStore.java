package org.gamboni.tech.history;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.gamboni.tech.web.BroadcastTarget;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 *
 * @param <Q> query object
 * @param <S> response ("snapshot") object
 * @param <T> session object
 * @param <E> event object
 */
public abstract class HistoryStore<
        Q,
        S extends Stamped,
        T extends HistoryStore.AbstractUpdateSession<E>,
        E> {
    /**
     * Ever-increasing version/generation/stamp number. Every change in play state is associated with a new higher stamp value.
     * Clients can request changes since a given stamp value.
     */
    private volatile long stamp = 0;

    protected long getStamp() {
        return this.stamp;
    }

    public abstract S getSnapshot(Q query);


    public class PerClientUpdates {
        private final long stamp;
        private final Multimap<BroadcastTarget, E> updates;

        public PerClientUpdates(long stamp, Multimap<BroadcastTarget, E> updates) {
            this.stamp = stamp;
            this.updates = updates;
        }

        public Optional<StampedEventList<E>> get(BroadcastTarget target) {
            Collection<E> events = this.updates.get(target);
            if (events.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(new StampedEventList<>(
                        this.stamp,
                        events));
            }
        }

        public boolean hasEvents() {
            return !updates.isEmpty();
        }
    }

    public static class AbstractUpdateSession<E> {
        protected final Multimap<BroadcastTarget, E> notifications = HashMultimap.create();
    }

    protected abstract T newTransaction();

    public synchronized PerClientUpdates update(Consumer<T> work) {
        stamp++;
        T session = newTransaction();
        work.accept(session);
        return new PerClientUpdates(stamp, session.notifications);
    }

    public StampedEventList<E> addListener(BroadcastTarget client, Q query, long since) {
        return new StampedEventList<>(stamp, internalAddListener(client, query, since));
    }

    protected abstract List<E> internalAddListener(BroadcastTarget client, Q query, long since);
}
