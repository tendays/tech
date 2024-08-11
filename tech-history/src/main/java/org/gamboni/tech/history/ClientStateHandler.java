package org.gamboni.tech.history;

import org.gamboni.tech.web.js.JsPersistentWebSocket;
import org.gamboni.tech.web.ui.AbstractPage;

import static org.gamboni.tech.web.js.JavaScript.*;

public abstract class ClientStateHandler implements JsPersistentWebSocket.Handler {
    /**
     * Latest sequence number obtained from server.
     */
    private final JsGlobal stamp = new JsGlobal("stamp");

    protected abstract JsStatement applyUpdate(JsExpression event);

    protected abstract JsExpression helloValue(JsExpression stamp);


    @Override
    public JsStatement handleEvent(JsExpression message) {
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
    public JsExpression helloValue() {
        return ClientStateHandler.this.helloValue(stamp);
    }

    @Override
    public void addTo(AbstractPage page) {
        page.addToScript(stamp.declare(0)); // initialised by init()?
    }

    public JsStatement init(JsExpression stampValue) {
        return stamp.set(stampValue);
    }
}
