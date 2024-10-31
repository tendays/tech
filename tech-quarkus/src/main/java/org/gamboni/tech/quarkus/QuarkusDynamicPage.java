package org.gamboni.tech.quarkus;

import jakarta.annotation.PostConstruct;
import org.gamboni.tech.history.ClientStateHandler;
import org.gamboni.tech.history.Stamped;
import org.gamboni.tech.history.ui.DynamicPage;
import org.gamboni.tech.web.js.JavaScript;
import org.gamboni.tech.web.js.JsPersistentWebSocket;

import java.util.function.BiFunction;
import java.util.function.Function;

import static org.gamboni.tech.web.js.JavaScript.literal;

public abstract class QuarkusDynamicPage<T extends Stamped> extends QuarkusPage<T> implements DynamicPage<T> {

    protected final ClientStateHandler stateHandler = new ClientStateHandler() {
        @Override
        protected JavaScript.JsExpression helloValue(JavaScript.JsExpression stamp) {
            return QuarkusDynamicPage.this.helloValue(stamp);
        }
    };

    private final JsPersistentWebSocket socket = new JsPersistentWebSocket(stateHandler);

    protected abstract JavaScript.JsExpression helloValue(JavaScript.JsExpression stamp);


    @PostConstruct
    public void postConstructDynamicPage() {
        socket.addTo(this);
        addToOnLoad(onLoad -> stateHandler.init(onLoad.addParameter(
                data -> literal(data.stamp()))));
    }

    public <E> DynamicPage<T> addHandler(BiFunction<JavaScript.JsExpression, ClientStateHandler.MatchCallback, E> matcher,
                                             Function<E, JavaScript.JsFragment> handler) {
        stateHandler.addHandler(matcher, handler);
        return this;
    }

    public JavaScript.JsExpression submitMessage(JavaScript.JsExpression payload) {
        return socket.submit(payload);
    }
}
