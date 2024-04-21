package org.gamboni.tech.web.js;

import lombok.RequiredArgsConstructor;

import static lombok.AccessLevel.PROTECTED;
import static org.gamboni.tech.web.js.JavaScript.*;

@RequiredArgsConstructor(access = PROTECTED)
public abstract class JsPersistentWebSocket {

    private final String socketUrl;

    private static final int POLL_INTERVAL = 60000;

    /** Queued events, used when the connection is down. */
    private final JsGlobal queue = new JsGlobal("queue");

    /** Current websocket object. A new one is created every time the connection drops. */
    private final JsGlobal socket = new JsGlobal("socket");

    /** Submit an action to send to back-end */
    private final Fun1 submit = new Fun1("submit");

    private final Fun flushQueue = new Fun("flushQueue");

    private final Fun poll = new Fun("poll");

    /** Generate necessary machinery for the persistent websocket. This should be inserted into a script executed
     * at page load.
     * @return Javascript code
     */
    public String declare() {
        return queue.declare(JavaScript.array()) +
                socket.declare(JavaScript._null)  + // should likely immediately call the poll() function

                submit.declare(action ->
                        _if(socket.dot("readyState").eq(WebSocket.dot("OPEN")),
                                socket.invoke("send", serialise(action)))
                                ._else(queue.invoke("push", action))) +

                flushQueue.declare(() -> seq(
                        socket.invoke("send", helloString()),
                        let(queue,
                                JsExpression::of,
                                queueCopy -> seq(
                                        queue.set(array()),
                                        queueCopy.invoke("forEach", lambda(
                                                "item",
                                                submit::invoke
                                        ))
                                )
                        ))) +


                poll.declare(() -> seq(
                        socket.set(newWebSocket(
                                new JsString(s -> "((window.location.protocol === 'https:') ? 'wss://' : 'ws://')")
                                        .plus(s -> "window.location.host")
                                        .plus(literal(socketUrl)))),
                        /* If the websocket is already closed, we could not establish the connection, and try again later. */
                        _if(socket.dot("readyState").eq(WebSocket.dot("CLOSED")), block(
                                setTimeout(poll.invoke(), POLL_INTERVAL),
                                _return()
                        ))._elseIf(socket.dot("readyState").eq(WebSocket.dot("OPEN")),
                                flushQueue.invoke()),
                        socket.invoke("addEventListener", literal("open"),
                                lambda(flushQueue.invoke())),

                        socket.invoke("addEventListener", literal("message"),
                                lambda("event",
                                        event -> handleEvent(jsonParse(event.dot("data"))))),

                        let(/* close handler */
                                lambda("event", event ->
                                        seq(consoleLog(event),
                                                setTimeout(poll.invoke(), POLL_INTERVAL)
                                        )),
                                JsExpression::of,
                                closeHandler -> seq(
                                        // infinite loops trying to reconnect
                                        socket.invoke("addEventListener", literal("close"), closeHandler),

                                        socket.invoke("addEventListener", literal("error"), lambda("event",
                                                        event ->
                                                                seq(consoleLog(event),
                                                                        // when an open socket errors out we get both error and close events
                                                                        // ... but we don't want to run setTimeout twice.
                                                                        socket.invoke("removeEventListener", literal("close"), closeHandler),

                                                                        setTimeout(poll.invoke(), POLL_INTERVAL)
                                                                )
                                                )
                                        )
                                ))
                ));
    }

    public JsExpression poll() {
        return poll.invoke();
    }

    public JsExpression submit(JsExpression payload) {
        return submit.invoke(payload);
    }

    protected abstract JsExpression helloString();

    /** Convert an expression passed to submit() into an expression to send to the back end. */
    protected JsExpression serialise(JsExpression action) {
        return action;
    }

    protected abstract JsStatement handleEvent(JsExpression message);
}

