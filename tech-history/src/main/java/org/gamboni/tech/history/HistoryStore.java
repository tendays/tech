package org.gamboni.tech.history;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import lombok.RequiredArgsConstructor;
import org.gamboni.tech.web.ws.BroadcastTarget;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 *
 * @param <Q> query object
 * @param <S> response ("snapshot") object
 * @param <T> session object
 */
public abstract class HistoryStore<
        Q,
        S extends Stamped,
        T extends HistoryStore.AbstractUpdateSession> {

    /** Return the current stamp value. */
    protected abstract long getStamp();
    protected abstract long incrementStamp();

    public abstract S getSnapshot(Q query);


    public class PerClientUpdates {
        private final long stamp;
        private final Multimap<BroadcastTarget, Object> updates;

        public PerClientUpdates(long stamp, Multimap<BroadcastTarget, Object> updates) {
            this.stamp = stamp;
            this.updates = updates;
        }

        public Optional<StampedEventList> get(BroadcastTarget target) {
            Collection<Object> events = this.updates.get(target);
            if (events.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(new StampedEventList(
                        this.stamp,
                        events));
            }
        }

        public boolean hasEvents() {
            return !updates.isEmpty();
        }
    }

    @RequiredArgsConstructor
    public static class AbstractUpdateSession {
        protected final long stamp;
        protected final Multimap<BroadcastTarget, Object> notifications = HashMultimap.create();
    }

    protected abstract T newTransaction(long stamp);

    public synchronized PerClientUpdates update(Consumer<T> work) {
        long stamp = incrementStamp();
        T session = newTransaction(stamp);
        work.accept(session);
        return new PerClientUpdates(stamp, session.notifications);
    }

    public StampedEventList addListener(BroadcastTarget client, Q query, long since) {
        return new StampedEventList(getStamp(), internalAddListener(client, query, since));
    }

    protected abstract List<?> internalAddListener(BroadcastTarget client, Q query, long since);
}
