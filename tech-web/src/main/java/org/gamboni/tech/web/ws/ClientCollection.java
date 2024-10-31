package org.gamboni.tech.web.ws;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@Slf4j
public class ClientCollection<S> {
    private final Map<S, BroadcastTarget> clients = new HashMap<>();


    public void put(S session, BroadcastTarget client) {
        clients.put(session, client);
    }

    public BroadcastTarget get(S session) {
        return clients.get(session);
    }

    public BroadcastTarget remove(S session) {
        BroadcastTarget client = clients.remove(session);
        if (client != null) {
            client.markClosed();
        }
        return client;
    }

    public void broadcast(Object payload) {
        Set<BroadcastTarget> clientCopies;
        synchronized (this) {
            clientCopies = Set.copyOf(this.clients.values());
        }
        log.info("Broadcasting {} to {} sessions.", payload, clientCopies.size());
        for (var client : clientCopies) {
            if (client.isOpen()) {
                client.sendOrLog(payload);
            }
        }
    }

    /** Broadcast customised information to all clients (e.g. filtering relevant/visible information to each).
     *
     * @param payload a function computing the payload to send to a given client.
     */
    public void broadcast(Function<BroadcastTarget, Optional<?>> payload) {
        Set<BroadcastTarget> clientCopies;
        synchronized (this) {
            clientCopies = Set.copyOf(this.clients.values());
        }
        log.info("Broadcasting data to {} sessions.", clientCopies.size());
        for (var client : clientCopies) {
            if (client.isOpen()) {
                payload.apply(client).ifPresent(client::sendOrLog);
            }
        }
    }
}
