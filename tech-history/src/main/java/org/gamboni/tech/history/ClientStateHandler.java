package org.gamboni.tech.history;

import org.gamboni.tech.web.js.JsPersistentWebSocket;
import org.gamboni.tech.web.ui.AbstractPage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.gamboni.tech.web.js.JavaScript.*;

public abstract class ClientStateHandler implements JsPersistentWebSocket.Handler {
    public interface MatchCallback {
        void expect(JsExpression toBeTruthy);
    }

    private record EventHandler<T>(
            BiFunction<JsExpression, MatchCallback, T> matcher,
            Function<T, JsFragment> handler) {}

    /**
     * Latest sequence number obtained from server.
     */
    private final JsGlobal stamp = new JsGlobal("stamp");

    private final List<EventHandler<?>> handlers = new ArrayList<>();

    public <E> ClientStateHandler addHandler(BiFunction<JsExpression, MatchCallback, E> matcher,
                                             Function<E, JsFragment> handler) {
        this.handlers.add(new EventHandler<>(matcher, handler));
        return this;
    }

    private JsStatement applyUpdate(JsExpression event) {
        if (handlers.isEmpty()) { return seq(); } // degenerate case
        IfLike chain = EMPTY_IF_CHAIN;

        for (var h : handlers.subList(0, handlers.size() - 1)) {
            chain = doElseIf(h, event, chain);
        }
        return chain._else(doElse(handlers.get(handlers.size() - 1), event));
    }

    private <E> IfLike doElseIf(EventHandler<E> handler, JsExpression event, IfLike chain) {
        var conditions = new ArrayList<JsExpression>();
        var wrappedEvent = handler.matcher.apply(event, conditions::add);
        return chain._elseIf(
                conditions.stream()
                        .reduce(JsExpression::and)
                        .orElse(literal(true)),
                handler.handler().apply(wrappedEvent));
    }

    private <E> JsFragment doElse(EventHandler<E> lastHandler, JsExpression event) {
        return lastHandler.handler.apply(
                lastHandler
                        .matcher.apply(event, ignored -> {
                        }));
    }

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
    public void addTo(AbstractPage<?> page) {
        page.addToScript(stamp.declare(0)); // initialised by init()?
    }

    public JsStatement init(JsExpression stampValue) {
        return stamp.set(stampValue);
    }
}
