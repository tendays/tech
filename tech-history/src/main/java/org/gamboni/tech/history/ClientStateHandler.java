package org.gamboni.tech.history;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.gamboni.tech.history.event.JsStampedEventList;
import org.gamboni.tech.web.js.JsPersistentWebSocket;
import org.gamboni.tech.web.ui.AbstractPage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    private record ConditionKey(JsExpression expr) {
        public int hashCode() {
            return format().hashCode();
        }

        public boolean equals(Object obj) {
            return (obj instanceof ConditionKey that) &&
                    this.format().equals(that.format());
        }

        private String format() {
            return expr.format(Scope.NO_DECLARATION);
        }
    }

    private JsStatement applyUpdate(JsExpression event) {
        // do a single [else]if block for all handlers sharing the same condition
        Multimap<Set<ConditionKey>, JsFragment> evaluatedHandlers = LinkedHashMultimap.create();

        for (var h : handlers) {
            addHandlerToMultimap(h, event, evaluatedHandlers);
        }

        return evaluatedHandlers.asMap()
                .entrySet()
                .stream()
                .reduce(EMPTY_IF_CHAIN,
                        (chain, entry) ->
                                chain._elseIf(
                                        // entry.key: all conditions that must be true to enter this block
                                        entry.getKey()
                                                .stream()
                                                .map(ConditionKey::expr)
                                                .reduce(JsExpression::and)
                                                .orElse(literal(true)),
                                        // entry.value: all handlers triggered by this combination of conditions
                                        entry.getValue()
                                                .stream()
                                                .collect(toSeq())),
                        (__, ___) -> {
                            throw new IllegalStateException("Unnecessary in sequential streams");
                        });
    }

    private <E> void addHandlerToMultimap(EventHandler<E> handler, JsExpression event, Multimap<Set<ConditionKey>, JsFragment> evaluatedHandlers) {
        var conditions = new LinkedHashSet<ConditionKey>();
        var wrappedEvent = handler.matcher.apply(event, cond -> conditions.add(new ConditionKey(cond)));
        evaluatedHandlers.put(conditions, handler.handler().apply(wrappedEvent));
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
    public ClientStateHandler addTo(AbstractPage<?> page) {
        page.addToScript(stamp.declare(0)); // initialised by init()?
        return this;
    }

    public JsStatement init(JsExpression stampValue) {
        return stamp.set(stampValue);
    }
}
