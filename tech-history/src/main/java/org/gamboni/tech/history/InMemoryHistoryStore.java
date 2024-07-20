package org.gamboni.tech.history;

public abstract class InMemoryHistoryStore<Q,
        S extends Stamped,
        T extends HistoryStore.AbstractUpdateSession<E>,
        E>
        extends HistoryStore<Q, S, T, E> {


    /**
     * Ever-increasing version/generation/stamp number. Every change in play state is associated with a new higher stamp value.
     * Clients can request changes since a given stamp value.
     */
    private volatile long stamp = 0;

    @Override
    protected long getStamp() {
        return this.stamp;
    }

    @Override
    protected synchronized long incrementStamp() {
        return ++stamp;
    }
}
