package org.gamboni.tech.history;

import org.gamboni.tech.web.js.JsPersistentWebSocket;

import static org.gamboni.tech.web.js.JavaScript.*;

public abstract class ClientStateHandler {
    /**
     * Latest sequence number obtained from server.
     */
    private final JsGlobal stamp = new JsGlobal("stamp");

    protected abstract JsStatement applyUpdate(JsExpression event);

    protected abstract JsExpression helloValue(JsExpression stamp);

    private final JsPersistentWebSocket socket = new JsPersistentWebSocket() {

        @Override
        protected JsStatement handleEvent(JsExpression message) {
            return let(
                    new JsStampedEventList(message),
                    JsStampedEventList::new,
                    stampedEventList -> seq(
                            stamp.set(stampedEventList.stamp()),
                            _forOf(stampedEventList.updates(),
                                    item -> applyUpdate(item))
                    ));
        }

        @Override
        protected JsExpression helloValue() {
            return ClientStateHandler.this.helloValue(stamp);
        }

    };

    public String declare() {
        return stamp.declare(0) + // initialised by init()?
            socket.declare();
    }

    public JsStatement init(JsExpression stampValue) {
        return seq(
                stamp.set(stampValue),
                socket.poll());
    }

    public JsExpression submit(JsExpression payload) {
        return socket.submit(payload);
    }
}
