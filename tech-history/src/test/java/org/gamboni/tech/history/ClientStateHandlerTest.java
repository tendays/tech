package org.gamboni.tech.history;

import org.gamboni.tech.history.event.Event;
import org.gamboni.tech.web.js.JavaScript;
import org.gamboni.tech.web.js.JsType;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import static org.gamboni.tech.web.js.JavaScript.seq;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClientStateHandlerTest {
    interface DummyJsType extends JsType<Event> {
        @Override
        default Class<Event> getBackendType() {
            return Event.class;
        }

        @Override
        default String format(JavaScript.Scope s) {
            return "";
        }
    }
    record A() implements DummyJsType {}
    record B() implements DummyJsType {}
    record C() implements DummyJsType {}
    record D() implements DummyJsType {}
    record E() implements DummyJsType {}

    record Tuple(DummyJsType x, DummyJsType y, DummyJsType z) implements Comparable<Tuple> {
        @Override
        public String toString() {
            return x.getClass().getSimpleName() + y.getClass().getSimpleName() + z.getClass().getSimpleName();
        }

        @Override
        public int compareTo(Tuple that) {
            return this.toString().compareTo(that.toString());
        }
    }

    record Single(DummyJsType x) implements Comparable<Single> {
        @Override
        public String toString() {
            return x.getClass().getSimpleName();
        }

        @Override
        public int compareTo(Single that) {
            return this.toString().compareTo(that.toString());
        }
    }

    @Test
    public void expectOneOfTest() {
        A a = new A();
        B b = new B();
        C c = new C();
        D d = new D();
        E e = new E();

        assertEquals(new TreeSet<>(Set.of(
                new Tuple(a, b, b),
                new Tuple(b, b, b),
                new Tuple(c, b, b),

                new Tuple(a, b, c),
                new Tuple(b, b, c),
                new Tuple(c, b, c),

                new Tuple(a, b, d),
                new Tuple(b, b, d),
                new Tuple(c, b, d),

                new Tuple(a, b, e),
                new Tuple(b, b, e),
                new Tuple(c, b, e)
        )), runTest(callback -> new Tuple(
                callback.expectOneOf(a, b, c),
                callback.expectOneOf(b),
                callback.expectOneOf(b, c, d, e))));

        // last element has only one value:
        assertEquals(new TreeSet<>(Set.of(
                new Tuple(a, b, e),
                new Tuple(b, b, e),
                new Tuple(c, b, e),

                new Tuple(a, c, e),
                new Tuple(b, c, e),
                new Tuple(c, c, e)
        )), runTest(callback -> new Tuple(
                callback.expectOneOf(a, b, c),
                callback.expectOneOf(b, c),
                callback.expectOneOf(e))));

        // first element has only one value:
        assertEquals(new TreeSet<>(Set.of(
                new Tuple(a, b, d),
                new Tuple(a, c, d),

                new Tuple(a, b, e),
                new Tuple(a, c, e)
        )), runTest(callback -> new Tuple(
                callback.expectOneOf(a),
                callback.expectOneOf(b, c),
                callback.expectOneOf(d, e))));

        // all elements have only one value:
        assertEquals(new TreeSet<>(Set.of(
                new Tuple(a, b, c)
        )), runTest(callback -> new Tuple(
                callback.expectOneOf(a),
                callback.expectOneOf(b),
                callback.expectOneOf(c))));

        // just one digit
        assertEquals(new TreeSet<>(Set.of(new Single(a), new Single(b), new Single(c))),
                runTest(callback -> new Single(callback.expectOneOf(a, b, c))));

        // just one digit with a single value
        assertEquals(new TreeSet<>(Set.of(new Single(a))),
                runTest(callback -> new Single(callback.expectOneOf(a))));
    }

    private static <T extends Comparable<T>> TreeSet<T> runTest(Function<ClientStateHandler.MatchCallback, T> matcher) {
        var actual = new TreeSet<T>();
        new ClientStateHandler() {
            @Override
            protected JavaScript.JsExpression helloValue(JavaScript.JsExpression stamp) {
                return null;
            }
        }.addHandler((event, callback) -> matcher.apply(callback),
        o -> {
            actual.add(o);
            return seq();
        }).handleEvent(JavaScript.JsExpression._undefined)
                .format(JavaScript.Scope.FOR_TOSTRING); // trigger rendering to force execution of handler logic

        return actual;
    }
}
