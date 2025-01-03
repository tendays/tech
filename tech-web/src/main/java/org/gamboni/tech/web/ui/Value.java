package org.gamboni.tech.web.ui;

import org.gamboni.tech.web.js.JavaScript;

import java.util.Objects;

public interface Value<T> {
    JavaScript.JsExpression toExpression();

    static Value<String> concat(Value<String> lhs, Value<?> rhs) {
        if (lhs instanceof Constant<String> lhsC && (rhs instanceof Constant<?> rhsC)) {
            return Value.of(lhsC.getConstantValue() + rhsC.getConstantValue());
        /*
         this looks like a good idea but what if rhs is not a String? Need a .toStringValue() or something?
        } else if (lhs instanceof Constant<String> lhsC && lhsC.getConstantValue().isEmpty()) {

            return rhs; */
        } else {
            return Value.of(lhs.toExpression().plus(rhs.toExpression()));
        }
    }

    /** If the value is dynamic, throw an IllegalStateException. Otherwise, return the static/constant value. */
    default T assertStatic() {
        throw new IllegalStateException("Dynamic values are not supported here: " + this.toExpression().format(new JavaScript.Scope()));
    }

    interface Constant<T> extends Value<T> {
        T getConstantValue();

        static <T> Constant<T> of(T constant, Value<T> value) {
            record ConstantValue<T>(T constant, Value<T> value) implements Constant<T> {

                @Override
                public T getConstantValue() {
                    return constant;
                }

                public T assertStatic() {
                    return constant;
                }

                @Override
                public JavaScript.JsExpression toExpression() {
                    return value.toExpression();
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
            }
            return new ConstantValue<>(constant, value);
        }
    }

    static <E extends Enum<E>> Value<E> of(E value) {
        return Constant.of(value, () -> JavaScript.literal(value.name()));
    }
    static Value<String> of(String value) {
        return Constant.of(value, () -> JavaScript.literal(value));
    }

    static Value<Long> of(long value) {
        return Constant.of(value, () -> JavaScript.literal(value));
    }

    static Value<Css.ClassName> of(Css.ClassName value) {
        return Constant.of(value, () -> JavaScript.literal(value));
    }

    static <T> Value<T> of(JavaScript.JsExpression expr) {
        return () -> expr;
    }

}
