package org.gamboni.tech.web.ui.value;

import org.gamboni.tech.web.js.JavaScript;
import org.gamboni.tech.web.ui.Css;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/** Abstracts over back-end and front-end values. In the back end, this object
 * simply wraps an actual value like a String or a Number. In the front end,
 * this object wraps a {@link JavaScript.JsExpression} for use when generating
 * JavaScript code. As the interface is the same for both case, this allows
 * defining expressions that are either evaluated at run time (for back-end
 * values, typically when doing server-side rendering) or produce equivalent
 * JavaScript expressions that will be shipped to the browser for later evaluation.
 */
public interface Value<T> extends JavaScript.JsExpression {
    /**
     * If the value is dynamic, throw an IllegalStateException. Otherwise, return the static/constant value.
     */
    default T assertStatic() {
        throw new IllegalStateException("Dynamic values are not supported here: " + format(JavaScript.Scope.FOR_TOSTRING));
    }

    default Optional<T> constantValue() {
        return Optional.empty();
    }

    default Optional<? extends Supplier<? extends T>> variableValue() {
        return Optional.empty();
    }

    default boolean isTimeDependent() {
        return false;
    }

    /** Apply a deterministic transformation on this value.
     *
     * @param valueMapper operator to use if this is constant or supplied
     * @param wrapper wrapper like {@code NumberValue} or {@code StringValue} to wrap the resulting value.
     * @param jsMapper operator to use if this is neither constant nor supplied
     * @return this, transformed using the given operator. Note that it is not necessarily wrapped using the provided wrapper!
     * @param <U> type wrapped by the value to return
     */
    default <U> Value<U> map(Function<? super T, U> valueMapper, Function<U, Value<U>> wrapper, Function<JavaScript.JsExpression, JavaScript.JsExpression> jsMapper) {
        return constantValue()
                .map(c -> wrapper.apply(valueMapper.apply(c))) // constant becomes constant
                .orElseGet(() -> variableValue()
                        .map(supplier -> Value.supplied(() ->
                                        valueMapper.apply(supplier.get()), // variable becomes variable
                                jsMapper.apply(this)))
                        .orElseGet(() -> {
                            var mapped = jsMapper.apply(this);
                            return isTimeDependent() ?
                                    timeDependent(mapped) : // time-dependent becomes time-dependent
                                    Value.of(mapped); // "other" becomes "other"
                        }));
    }

    static <T> Value<T> timeDependent(JavaScript.JsExpression jsValue) {
        return new Value<>() {

            @Override
            public String format(JavaScript.Scope s) {
                return jsValue.format(s);
            }

            @Override
            public JavaScript.Precedence getPrecedence() {
                return jsValue.getPrecedence();
            }

            @Override
            public List<JavaScript.Symbol> getFreeSymbols() {
                return jsValue.getFreeSymbols();
            }

            @Override
            public boolean isTimeDependent() {
                return true;
            }
        };
    }

    static <T> Value<T> supplied(Supplier<T> javaValue, JavaScript.JsExpression jsValue) {
        return new Value<>() {

            @Override
            public Optional<Supplier<T>> variableValue() {
                return Optional.of(javaValue);
            }

            @Override
            public T assertStatic() {
                return javaValue.get();
            }

            @Override
            public String format(JavaScript.Scope s) {
                return jsValue.format(s);
            }

            @Override
            public JavaScript.Precedence getPrecedence() {
                return jsValue.getPrecedence();
            }

            @Override
            public List<JavaScript.Symbol> getFreeSymbols() {
                return jsValue.getFreeSymbols();
            }

            @Override
            public boolean isTimeDependent() {
                return true;
            }
        };
    }

    static <T> Value<T> constant(T constant, JavaScript.JsExpression value) {
        record ConstantValue<T>(T constant, JavaScript.JsExpression value) implements Value<T> {

            @Override
            public T assertStatic() {
                return constant;
            }

            @Override
            public Optional<T> constantValue() {
                return Optional.of(constant);
            }

            @Override
            public Optional<Supplier<T>> variableValue() {
                return Optional.of(() -> constant);
            }

            @Override
            public String format(JavaScript.Scope s) {
                return value.format(s);
            }

            @Override
            public List<JavaScript.Symbol> getFreeSymbols() {
                return value.getFreeSymbols();
            }

            @Override
            public int hashCode() {
                return Objects.hashCode(constant);
            }

            @Override
            public boolean equals(Object obj) {
                // 'value' is supposed to be a literal representation of 'constant', so two literals with the same value
                // can be considered equal, so this/that    .value can be ignored
                return (obj instanceof ConstantValue<?> that) &&
                        Objects.equals(this.constant, that.constant);
            }

            @Override
            public JavaScript.Precedence getPrecedence() {
                return value.getPrecedence();
            }
        }
        return new ConstantValue<>(constant, value);
    }

    static <E extends Enum<E>> Value<E> of(E value) {
        return constant(value, JavaScript.literal(value.name()));
    }

    static StringValue of(String text) {
        return StringValue.of(text);
    }

    static NumberValue of(long value) {
        return NumberValue.of(value);
    }

    static NumberValue of(double value) {
        return NumberValue.of(value);
    }

    static DateValue of(Instant value) {
        return DateValue.of(value);
    }

    static Value<Css.ClassName> of(Css.ClassName value) {
        return constant(value, JavaScript.literal(value));
    }

    @SuppressWarnings("unchecked")
    static <T> Value<T> of(JavaScript.JsExpression expr) {
        if (expr instanceof Value) {
            return (Value<T>) expr;
        } else {
            return new Value<T>() {
                @Override
                public JavaScript.Precedence getPrecedence() {
                    return expr.getPrecedence();
                }

                @Override
                public String format(JavaScript.Scope s) {
                    return expr.format(s);
                }

                @Override
                public List<JavaScript.Symbol> getFreeSymbols() {
                    return expr.getFreeSymbols();
                }
            };
        }
    }
}
