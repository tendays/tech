package org.gamboni.tech.sparkjava;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.gamboni.tech.web.js.JavaScript;
import org.gamboni.tech.web.js.JsPersistentWebSocket;
import spark.Spark;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.gamboni.tech.web.js.JavaScript.*;

@Slf4j
public abstract class SparkWebSocket {

    private static final int KEEPALIVE_MILLIS = 20_000;
    public static final String KEEPALIVE_COMMAND = "ping";

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public class Client {
        private final Session session;
        /** (Final but contents is mutable) */
        private final List<Runnable> onClose = new ArrayList<>();
        private volatile boolean open = true;

        /**
         * Send the given payload to this client. If sending fails, throw a {@code RuntimeException}.
         */
        public void sendOrThrow(Object payload) {
            try {
                session.getRemote().sendString(mapper.writeValueAsString(payload));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Send the given payload to this client. If sending fails, just log a warning and proceed normally.
         */
        public void sendOrLog(Object payload) {
            try {
                session.getRemote().sendString(mapper.writeValueAsString(payload));
            } catch (IOException e) {
                log.warn("Sending message to {} failed", session, e);
            }
        }

        private void markClosed() {
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

        /** Run the given task when this client gets closed. If this client is already closed, run the task
         * immediately, from the current thread, before returning.
         */
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
        public String toString() {
            return session.toString();
        }
    }

    private final Map<Session, Client> clients = new HashMap<>();

    private final ObjectMapper mapper;

    public String getPath() {
        return "/ws";
    }

    public void init() {
        Spark.webSocket(getPath(), this);
    }

    protected SparkWebSocket(Supplier<ObjectMapper> mapping) {
        this.mapper = mapping.get();
    }

    @OnWebSocketConnect
    public synchronized void onConnect(Session session) throws Exception {
        log.info("New connection {}", session);
        clients.put(session, new Client(session));
    }

    protected abstract boolean handleMessage(Client client, String message);

    @OnWebSocketMessage
    public void onMessage(Session session, String message) throws IOException {
        log.info("Received '{}' from {}", message, session);

        if (message.equals(KEEPALIVE_COMMAND)) {
            return; // nothing, client is alive
        } else if (!handleMessage(clients.get(session), message)) {
            log.warn("Ignoring unhandled message '{}'", message);
        }
    }

    @OnWebSocketClose
    public synchronized void onClose(Session session, int statusCode, String reason) {
        clients.remove(session).markClosed();
        log.info("Connection {} terminated: {} {} ", session, statusCode, reason);
    }

    protected void broadcast(Object payload) {

        Set<Client> clientCopies;
        synchronized (this) {
            clientCopies = Set.copyOf(this.clients.values());
        }
        log.info("Broadcasting {} to {} sessions.", payload, clientCopies.size());
        for (var client : clientCopies) {
            if (client.session.isOpen()) {
                client.sendOrLog(payload);
            }
        }
    }

    /** Broadcast customised information to all clients (e.g. filtering relevant/visible information to each).
     *
     * @param payload a function computing the payload to send to a given client.
     */
    protected void broadcast(Function<Client, Object> payload) {
        Set<Client> clientCopies;
        synchronized (this) {
            clientCopies = Set.copyOf(this.clients.values());
        }
        log.info("Broadcasting data to {} sessions.", clientCopies.size());
        for (var client : clientCopies) {
            if (client.session.isOpen()) {
                client.sendOrLog(payload.apply(client));
            }
        }
    }

    public JsPersistentWebSocket createClient(
            JavaScript.JsExpression hello,
            Function<JavaScript.JsExpression, JavaScript.JsStatement> messageHandler) {
        JavaScript.JsGlobal keepAliveHandle = new JavaScript.JsGlobal("keepAliveHandle");
        JavaScript.Fun sendKeepAlive = new JavaScript.Fun("sendKeepAlive");
        return new JsPersistentWebSocket(getPath()) {
            @Override
            protected JsExpression helloValue() {
                return hello;
            }

            @Override
            protected JsStatement handleEvent(JsExpression message) {
                return messageHandler.apply(message);
            }

            @Override
            public String declare() {
                return keepAliveHandle.declare(_null) +
                        super.declare() +
                        sendKeepAlive.declare(() -> keepAliveHandle.set(
                                setTimeout(block(
                                        this.submitIfOpen(literal(KEEPALIVE_COMMAND)),
                                        sendKeepAlive.invoke()),
                                        KEEPALIVE_MILLIS)
                        ));
            }

            @Override
            protected JavaScript.JsFragment onOpen() {
                return seq(
                        super.onOpen(),
                        sendKeepAlive.invoke());
            }

            @Override
            protected JsFragment onClose(JsExpression event) {
                return seq(
                        _if(keepAliveHandle, block(
                                clearTimeout(keepAliveHandle),
                                keepAliveHandle.set(_null)
                        )),
                        super.onClose(event));
            }
        };
    }
}
