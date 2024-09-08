package org.gamboni.tech.web.js;

import lombok.RequiredArgsConstructor;
import org.gamboni.tech.web.ui.AbstractPage;
import org.gamboni.tech.web.ui.PageMember;

import static lombok.AccessLevel.PROTECTED;
import static org.gamboni.tech.web.js.JavaScript.*;

@RequiredArgsConstructor(access = PROTECTED)
public class JsPersistentWebSocket implements PageMember<Object> {

    public interface Handler extends PageMember<Object> {
        /** This method may be overridden to declare any functions or globals needed by this handler.
         * Default implementation does nothing. */
        @Override
        default void addTo(AbstractPage<?> page) {
        }

        /** Expression sent to the back end as soon as a connection is established. It may be any expression, e.g. refer
         * to some variables to subscribe only to events relevant to the current page. */
        JsExpression helloValue();
        /** Handle a message coming from the server.
         *
         * @param message an expression holding the JavaScript object corresponding to the server-sent
         *                object. You should typically pass it to the constructor of a {@link JS @JS}-annotated
         *                object to access properties of the object in a type-safe way.
         * @return the event-handling code, for instance a {@link JavaScript#seq(JsFragment...)} invocation if
         * you need to do many things in sequence.
         */
        JsStatement handleEvent(JsExpression message);
    }

    public static final String DEFAULT_URL = "/sock";

    private final String socketUrl;
    private final Handler handler;

    public JsPersistentWebSocket(Handler handler) {
        this(DEFAULT_URL, handler);
    }

    private static final int POLL_INTERVAL = 60000;

    /** Queued events, used when the connection is down. */
    protected final JsGlobal queue = new JsGlobal("queue");

    /** Current websocket object. A new one is created every time the connection drops. */
    protected final JsGlobal socket = new JsGlobal("socket");

    /** Submit an action to send to back-end */
    protected final Fun1 submit = new Fun1("submit");

    protected final Fun flushQueue = new Fun("flushQueue");

    protected final Fun poll = new Fun("poll");

    /** Generate necessary machinery for the persistent websocket. This should be inserted into a script executed
     * at page load.
     */
    @Override
    public void addTo(AbstractPage<?> page) {
        page.addToScript(queue.declare(JavaScript.array()),
                socket.declare(JavaScript._null), // should likely immediately call the poll() function
                submit.declare(action ->
                        submitIfOpen(serialise(action))
                                ._else(queue.invoke("push", action))),
                flushQueue.declare(seq(
                        socket.invoke("send", serialise(handler.helloValue())),
                        let(queue,
                                JsExpression::of,
                                queueCopy -> seq(
                                        queue.set(array()),
                                        queueCopy.invoke("forEach", lambda(
                                                "item",
                                                submit::invoke
                                        ))
                                )
                        ))),
                poll.declare(seq(
                        socket.set(newWebSocket(
                                new JsString(s -> "((window.location.protocol === 'https:') ? 'wss://' : 'ws://')")
                                        .plus(s -> "window.location.host")
                                        .plus(literal(socketUrl)))),
                        /* If the websocket is already closed, we could not establish the connection, and try again later. */
                        _if(socket.dot("readyState").eq(WebSocket.dot("CLOSED")),
                                setTimeout(poll.invoke(), POLL_INTERVAL),
                                _return()
                        )._elseIf(socket.dot("readyState").eq(WebSocket.dot("OPEN")),
                                flushQueue.invoke()),
                        socket.invoke("addEventListener", literal("open"),
                                lambda(this.onOpen())),

                        socket.invoke("addEventListener", literal("message"),
                                lambda("event",
                                        this::onMessage)),

                        let(/* close handler */
                                lambda("event", this::onClose),
                                JsExpression::of,
                                closeHandler -> seq(
                                        // infinite loops trying to reconnect
                                        socket.invoke("addEventListener", literal("close"), closeHandler),

                                        socket.invoke("addEventListener", literal("error"), lambda("event",
                                                        event -> this.onError(event, closeHandler)
                                                )
                                        )
                                ))
                )));

        handler.addTo(page);
    }

    protected IfBlock submitIfOpen(JsExpression payload) {
        return _if(socket.dot("readyState").eq(WebSocket.dot("OPEN")),
                socket.invoke("send", payload));
    }

    protected JsFragment onOpen() {
        return flushQueue.invoke();
    }

    /** Return the OnMessage event handler. Note: this method receives a raw OnMessage event
     * and delegates to {@link Handler#handleEvent}. You should normally override handleEvent which
     * handles the payload object. */
    protected JsFragment onMessage(JsExpression event) {
        return handler.handleEvent(jsonParse(event.dot("data")));
    }

    /** Socket close event handling logic.
     *
     * @param event The WebSocket {@code close} event.
     * @return close handling logic.
     */
    protected JsFragment onClose(JsExpression event) {
        return seq(consoleLog(event),
                        setTimeout(poll.invoke(), POLL_INTERVAL)
                );
    }

    /** Socket error handling logic.
     *
     * @param event the WebSocket {@code error} event.
     * @param closeHandler the close event handler. As an error event is normally followed by a close event, the error handler
     *                     should remove the close handler to avoid running similar logic twice.
     * @return socket error handling logic.
     */
    protected JsFragment onError(JsExpression event, JsExpression closeHandler) {
        return seq(consoleLog(event),
                // when an open socket errors out we get both error and close events
                // ... but we don't want to run setTimeout twice.
                socket.invoke("removeEventListener", literal("close"), closeHandler),

                setTimeout(poll.invoke(), POLL_INTERVAL)
        );
    }

    public JsExpression poll() {
        return poll.invoke();
    }

    public JsExpression submit(JsExpression payload) {
        return submit.invoke(payload);
    }

    /** Convert an expression passed to submit() into an expression to send to the back end. */
    protected JsExpression serialise(JsExpression action) {
        return jsonStringify(action);
    }

}

