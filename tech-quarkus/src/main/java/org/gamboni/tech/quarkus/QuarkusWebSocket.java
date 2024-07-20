package org.gamboni.tech.quarkus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gamboni.tech.web.ws.BroadcastTarget;
import org.gamboni.tech.web.ws.ClientCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
public abstract class QuarkusWebSocket {

    @Inject
    protected Vertx vertx;

    @Inject
    protected ObjectMapper json;

    private final ClientCollection<Session>  clients = new ClientCollection<>();


    @RequiredArgsConstructor
    public class SessionBroadcastTarget implements BroadcastTarget {
        private final Session session;

        /**
         * (Final but contents is mutable)
         */
        private final List<Runnable> onClose = new ArrayList<>();
        private volatile boolean open = true;

        @Override
        public void sendOrThrow(Object payload) {
            sendOrLog(payload); // no difference for now
        }

        @Override
        public void sendOrLog(Object payload) {
            session.getAsyncRemote()
                    .sendText(toJsonString(payload));
        }

        /**
         * Run the given task when this client gets closed. If this client is already closed, run the task
         * immediately, from the current thread, before returning.
         */
        @Override
        public void onClose(Runnable task) {
            synchronized (this) {
                if (open) {
                    onClose.add(task);
                    return;
                }
            }

            // if already closed: no synchronization needed, as we aren't modifying 'this'
            task.run();
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        public void markClosed() {
            synchronized (this) {
                Preconditions.checkState(open); // sanity check
                open = false;
            }

            for (var task : onClose) {
                try {
                    task.run();
                } catch (Throwable t) {
                    log.warn("Uncaught exception when running close listener", t);
                }
            }
            onClose.clear();
        }

        public int hashCode() {
            return session.hashCode();
        }

        public boolean equals(Object that) {
            return (that instanceof SessionBroadcastTarget t) &&
                    t.session.equals(this.session);
        }

        @Override
        public String toString() {
            return session.toString();
        }
    }

    @OnOpen
    public synchronized void onOpen(Session session) {
        log.debug("New session opened");
        clients.put(session, new SessionBroadcastTarget(session));
    }

    @OnClose
    public synchronized void onClose(Session session) {
        log.info("Session {} closing", session);
        clients.remove(session);
    }

    @OnError
    public synchronized void onError(Session session, Throwable error) {
        log.error("Session {} failed", session, error);
        clients.remove(session);
    }

    /** Broadcast customised information to all clients (e.g. filtering relevant/visible information to each).
     *
     * @param payload a function computing the payload to send to a given client.
     */
    public void broadcast(Function<BroadcastTarget, Optional<?>> payload) {
        clients.broadcast(payload);
    }

    protected String toJsonString(Object object) {
        try {
            return json.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Error stringifying {} instance.",
                    (object == null) ? "null" : object.getClass().getName(),
                    e);
            return "{}";
        }
    }
}
