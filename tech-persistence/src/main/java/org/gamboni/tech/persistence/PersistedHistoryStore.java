package org.gamboni.tech.persistence;

import com.google.common.collect.Multimap;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.gamboni.tech.history.HistoryStore;
import org.gamboni.tech.history.Stamped;
import org.gamboni.tech.history.event.Event;
import org.gamboni.tech.history.event.StampedEventList;
import org.gamboni.tech.web.ws.BroadcastTarget;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
public abstract class PersistedHistoryStore<
        Q,
        S extends Stamped,
        T extends HistoryStore.AbstractUpdateSession> extends HistoryStore<Q,S,T> {
    @Getter
    @Inject
    protected EntityManager em;

    private final Map<BroadcastTarget, Q> sessions = new HashMap<>();

    @Override
    protected long incrementStamp() {
        return ((Number) em.createNativeQuery("select next value for versions").getSingleResult())
                .longValue();
    }

    @Override
    @Transactional
    public synchronized PerClientUpdates update(Consumer<T> work) {
        return super.update(work);
    }

    @Override
    @Transactional
    public StampedEventList addListener(BroadcastTarget client, Q query, long since) {
        log.debug("Adding {} to broadcast list", client);

        sessions.put(client, query);
        return super.addListener(client, query, since);
    }

    @Override
    public void removeListener(BroadcastTarget client) {
        sessions.remove(client);
    }

    public <T> TypedQuery<T> search(Class<T> entity, BiConsumer<CriteriaQuery<T>, Root<T>> criteria) {
        final CriteriaQuery<T> query = em.getCriteriaBuilder().createQuery(entity);
        final Root<T> root = query.from(entity);
        criteria.accept(query, root);
        return em.createQuery(query);
    }

    public <X> Optional<X> find(Class<X> entityType, Object id) {
        return Optional.ofNullable(em.find(entityType, id));
    }

    protected void notifyListeners(Multimap<BroadcastTarget, Event> notifications,
                                   Function<Q, Optional<? extends Event>> queryApplication) {
        sessions.forEach((target, query) ->
                queryApplication.apply(query)
                                .ifPresent(event ->
                notifications.put(target,
                event)));
    }
}
