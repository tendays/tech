package org.gamboni.tech.web.ui.value;

import org.gamboni.tech.web.js.JavaScript;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class ValueWrapper<T> implements Value<T> {
    protected final Value<? extends T> delegate;
    public ValueWrapper(Value<? extends T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public String format(JavaScript.Scope s) {
        return delegate.format(s);
    }

    @Override
    public JavaScript.Precedence getPrecedence() {
        return delegate.getPrecedence();
    }

    @Override
    @SuppressWarnings("unchecked") // we could also return Optional<? extends T> all the time but this may get annoying to callers
    public Optional<T> constantValue() {
        return (Optional<T>) delegate.constantValue();
    }

    @Override
    public Optional<? extends Supplier<? extends T>> variableValue() {
        return delegate.variableValue();
    }

    @Override
    public T assertStatic() {
        return delegate.assertStatic();
    }

    @Override
    public boolean isTimeDependent() {
        return delegate.isTimeDependent();
    }

    @Override
    public String format(JavaScript.Scope s, JavaScript.Precedence targetPrecedence) {
        return delegate.format(s, targetPrecedence);
    }

    @Override
    public List<JavaScript.Symbol> getFreeSymbols() {
        return delegate.getFreeSymbols();
    }

    @Override
    public <U> Value<U> map(Function<? super T, U> valueMapper, Function<U, Value<U>> wrapper, Function<JavaScript.JsExpression, JavaScript.JsExpression> jsMapper) {
        return delegate.map(valueMapper, wrapper, jsMapper);
    }

    @Override
    public String toString() {
        return format(JavaScript.Scope.NO_DECLARATION);
    }

    /** Combine {@code this} and {@code that} with some operation like addition or concatenation.
     *
     * @param that second operand
     * @param wrapper a wrapper like {@code NumberValue} or {@code StringValue} converting a constant to a {@code Value}
     * @param constantOperator operator working on constant values (it is also used when one or both operands are variable/supplied
     * @param expressionOperator operator working on JavaScript expressions
     * @return combination of {@code this} and {@code that}. Note that it is not necessarily an instance of the value wrapper
     * @param <R> value type of the second operand (a simple type like String or Date; NOT infrastructure types like {@code Value<X>})
     * @param <O> value type of the operation result (a simple type like String or Date; NOT infrastructure types like {@code Value<X>})
     */
    protected <R, O> Value<O> mapWith(Value<R> that, Function<O, Value<O>> wrapper, BiFunction<T, R, O> constantOperator,
                                      BinaryOperator<JavaScript.JsExpression> expressionOperator) {
        /* Constant | Variable (aka supplied) | Time-dependent | Other
         * Combination rules:
         * - Constant only remains constant when combined with constants
         * - Variable remains variable (supplied) when combined with variable or constant (because constant can act as supplied)
         * - "time-dependent" is just variable without a known back-end value. It states it needs to be recalculated constantly
         * therefore, time-dependent with anything is at always time-dependent. It can't become variable/supplied because a
         * parameter in back end is not known.
         * - other is a front-end value depending on a back-end-provided value, so can become time-dependent, but not supplied or constant.
         *
         *  *_|C_V_T_O_
         *  C |C V T O
         *  V |V V T T
         *  T |T T T T
         *  O |O T T O
         */

        Value<? extends R> thatUnwrapped = (that instanceof ValueWrapper<R> thatWrapper) ? thatWrapper.delegate : that;

        return this.constantValue().map( // C * x = x (map() preserves the category)
                lc -> that.map(
                                rc -> constantOperator.apply(lc, rc),
                        wrapper,
                        rc -> expressionOperator.apply(delegate, rc))
                )
                .orElseGet(() -> delegate.variableValue().flatMap(
                        lv -> that.variableValue().map(rv ->
                                // V * V|C = V (variableValue() is available for C as well)
                                Value.supplied(
                                        () -> constantOperator.apply(lv.get(), rv.get()),
                                        expressionOperator.apply(delegate, thatUnwrapped))
                        ))
                        .orElseGet(() -> {
                            // this block covers remaining T and O cases in the last three rows of the table above
                            var expr = expressionOperator.apply(delegate, thatUnwrapped);
                            // both T and V are "timeDependent", so
                            // the condition is true everywhere in the table except in the four corners
                            return (this.isTimeDependent() || that.isTimeDependent()) ?
                                    Value.timeDependent(expr) : Value.of(expr);
                        }));
    }
}
