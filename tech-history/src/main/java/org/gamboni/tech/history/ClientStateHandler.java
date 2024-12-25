package org.gamboni.tech.history;

import com.google.common.base.Preconditions;
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
        /** Check that the event has the same type as the given object. This method returns its argument.
         * This allows, if you only need to check the type, to implement a matcher with a single expression                                 like this:
         * <pre>{@code (event, callback) -> callback.expectSameType(new JsYourEventType(event))}</pre>
         * @param event the event, wrapped in a Js* type
         * @return {@code event}.
         */
        <T extends JsExpression> T expectSameType(T event);
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
        var wrappedEvent = handler.matcher.apply(event,
                new MatchCallback() {
                    @Override
                    public void expect(JsExpression cond) {
                        conditions.add(new ConditionKey(cond));
                    }

                    // Note: we'd like to restrict this to "JsEvent" types, but
                    // currently the JS annotation processor does not allow tracking that information.

                    // Two options: 1. actually generate a JsEvent marker interface and let JSProcessor
                    // add implements JsEvent when processing Event implementors
                    // 2. on all Js* types add a Represents<?> marker interface pointing back to the
                    // @JS-annotated type. Then we can do {@code <T extends Represents<? extends Event>>} here
                    @Override
                    public <T extends JsExpression> T expectSameType(T event) {
                        String className = event.getClass().getSimpleName();
                        Preconditions.checkArgument(className.startsWith("Js"));
                        expect(event.dot("@type").eq(className.substring(2)));
                        return event;
                    }
                });
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
                                this::applyUpdate)
                ));
    }

    @Override
    public JsExpression helloValue() {
        return ClientStateHandler.this.helloValue(stamp);
    }

    @Override
    public ClientStateHandler addTo(AbstractPage<?> page) {
        page.addToScript(stamp.declare(0)); // initialised by init()
        return this;
    }

    public JsStatement init(JsExpression stampValue) {
        return stamp.set(stampValue);
    }
}
