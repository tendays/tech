package org.gamboni.tech.web.ui;

import org.gamboni.tech.web.js.JavaScript;

public interface Value<T> {
    JavaScript.JsExpression toExpression();

    static Value<String> concat(Value<String> lhs, Value<?> rhs) {
        if (lhs instanceof Constant<String> lhsC && (rhs instanceof Constant<?> rhsC)) {
            return Value.of(lhsC.getConstantValue() + rhsC.getConstantValue());
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
            return new Constant<>() {

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
            };
        }
    }

    static <E extends Enum<E>> Value<E> of(E value) {
        return Constant.of(value, () -> JavaScript.literal(value.name()));
    }
    static Value<String> of(String value) {
        return Constant.of(value, () -> JavaScript.literal(value));
    }

    static Value<Css.ClassName> of (Css.ClassName value) {
        return Constant.of(value, () -> JavaScript.literal(value));
    }

    static <T> Value<T> of(JavaScript.JsExpression expr) {
        return () -> expr;
    }

}
