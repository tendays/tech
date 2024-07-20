package org.gamboni.tech.persistence;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import org.gamboni.tech.history.HistoryStore;
import org.gamboni.tech.history.Stamped;
import org.gamboni.tech.history.StampedEventList;
import org.gamboni.tech.web.ws.BroadcastTarget;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class PersistedHistoryStore<
        Q,
        S extends Stamped,
        T extends HistoryStore.AbstractUpdateSession<E>,
        E> extends HistoryStore<Q,S,T,E> {
    @Inject
    protected EntityManager em;

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
    public StampedEventList<E> addListener(BroadcastTarget client, Q query, long since) {
        return super.addListener(client, query, since);
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

    public EntityManager getEm() {
        return em;
    }
}
