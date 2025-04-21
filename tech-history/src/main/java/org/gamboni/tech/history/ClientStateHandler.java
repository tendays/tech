package org.gamboni.tech.history;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.gamboni.tech.history.event.Event;
import org.gamboni.tech.history.event.StampedEventListValues;
import org.gamboni.tech.web.js.JavaScript;
import org.gamboni.tech.web.js.JsPersistentWebSocket;
import org.gamboni.tech.web.js.JsType;
import org.gamboni.tech.web.ui.Page;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.gamboni.tech.web.js.JavaScript.*;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ClientStateHandler implements JsPersistentWebSocket.Handler {
    public interface MatchCallback {
        void expect(JsExpression toBeTruthy);
        /** Check that the event has the same type as the given object. This method returns its argument.
         * This allows, if you only need to check the type, to implement a matcher with a single expression
         * like this:
         * <pre>{@code (event, callback) -> callback.expectSameType(new JsYourEventType(event))}</pre>
         * @param event the event, wrapped in a Js* type
         * @return {@code event}.
         */
        <T extends JsType<? extends Event>> T expectSameType(T event);
        @SuppressWarnings("unchecked") // "unsafe [generic] varargs" are unchecked
        <T extends JsType<? extends Event>> T expectOneOf(T... possibilities);
    }

    private record EventHandler<T>(
            BiFunction<JsExpression, MatchCallback, T> matcher,
            Function<T, JsFragment> handler) {}

    /**
     * Latest sequence number obtained from server.
     */
    private final JsGlobal stamp = new JsGlobal("stamp");
    public static final Symbol EVENT_SYMBOL = Symbol.create();

    Multimap<Set<ConditionKey>, JsFragment> evaluatedHandlers = LinkedHashMultimap.create();
    //private final List<EventHandler<?>> handlers = new ArrayList<>();

    public <E> ClientStateHandler addHandler(BiFunction<JsExpression, MatchCallback, E> matcher,
                                             Function<E, JsFragment> handler) {
        addHandlerToMultimap(new EventHandler<>(matcher, handler), evaluatedHandlers);
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
        return EVENT_SYMBOL.assignIn(event, JavaScript.dynamicStatement(() ->
                evaluatedHandlers.asMap()
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
                        })));
    }

    private <E> void addHandlerToMultimap(EventHandler<E> handler, Multimap<Set<ConditionKey>, JsFragment> evaluatedHandlers) {
        class MatchCallbackImpl implements MatchCallback {
            final Set<ConditionKey> conditions = new LinkedHashSet<>();
            final List<Integer> toExpand = new ArrayList<>();
            /**
             * Index in the 'toExpand' list.
             */
            int branchIndex = 0;
            boolean incrementing = true;
            boolean lastRun = false;

            @Override
            public void expect(JsExpression cond) {
                conditions.add(new ConditionKey(cond));
            }

            @Override
            public <T extends JsType<? extends Event>> T expectSameType(T event) {
                expect(event.isThisType());
                return event;
            }

            @SafeVarargs
            @Override
            public final <T extends JsType<? extends Event>> T expectOneOf(T... possibilities) {
                if (branchIndex >= toExpand.size()) {
                    // first execution: put zeroes everywhere
                    toExpand.add(0);
                } else if (incrementing) {
                    /* We traverse digits from the least to the most significant digit.
                     * Writing '9' for the last index of each possibility array (actual maximal digits will be
                     * different), to increment from
                     * 9,9,9,3,8,2 we need to go to
                     * 0,0,0,4,8,2.
                     *
                     * so we replace each maximal digit ("9") with 0 until we find a non-maximal digit
                     * (3 in the example), increment that and set 'incrementing' to false because we're done.
                     */
                    int nextDigitValue = toExpand.get(branchIndex) + 1;
                    if (nextDigitValue < possibilities.length) {
                        // found a non-maximal digit
                        toExpand.set(branchIndex, nextDigitValue);
                        incrementing = false;
                    } else {
                        // maximal digit
                        toExpand.set(branchIndex, 0);
                        // and keep incrementing
                    }
                }

                int thisDigit = toExpand.get(branchIndex);
                if (thisDigit + 1 < possibilities.length) {
                    // when generating at least one non-maximal digit we can run again.
                    // NOTE: not using equality in test because need to account for possibility
                    // that the event handler code is non-deterministic, so this is a best-effort
                    // to avoid "undefined behaviour" to mean "infinite loops"
                    lastRun = false;
                }
                T result = possibilities[thisDigit];
                branchIndex++;
                return expectSameType(result);
            }
        }

        var matchCallback = new MatchCallbackImpl();

        while (!matchCallback.lastRun) {
            matchCallback.lastRun = true;
            matchCallback.branchIndex = 0;
            matchCallback.incrementing = true;
            matchCallback.conditions.clear();

            var wrappedEvent = handler.matcher.apply(EVENT_SYMBOL, matchCallback);
            evaluatedHandlers.put(Set.copyOf(matchCallback.conditions), handler.handler.apply(wrappedEvent));
        }
    }

    protected abstract JsExpression helloValue(JsExpression stamp);


    @Override
    public JsStatement handleEvent(JsExpression event) {
        return let(
                StampedEventListValues.of(event),
                StampedEventListValues::of,
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
    public ClientStateHandler addTo(Page<?> page) {
        page.addToScript(stamp.declare(0)); // initialised by init()
        return this;
    }

    public JsStatement init(JsExpression stampValue) {
        return stamp.set(stampValue);
    }
}
