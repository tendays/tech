package org.gamboni.tech.web.ui.value;

import org.gamboni.tech.web.js.JavaScript;

import static org.gamboni.tech.web.ui.value.Value.constant;

public class NumberValue extends ValueWrapper<Number> {
    public static NumberValue of(JavaScript.JsExpression expr) {
        if (expr instanceof NumberValue number) {
            return number;
        } else {
            return new NumberValue(Value.of(expr));
        }
    }
    public static NumberValue of(Number literal) {
        return new NumberValue(constant(literal, JavaScript.literal(literal)));
    }

    private NumberValue(Value<? extends Number> delegate) {
        super(delegate);
    }

    public NumberValue minus(NumberValue that) {
        return NumberValue.of(this.<Number, Number>mapWith(that, NumberValue::of,
                (l, r) ->
                        l.doubleValue() - r.doubleValue(),
                JavaScript.JsExpression::minus));
    }

    public RatioValue divide(NumberValue that) {
        return RatioValue.of(this.<Number, Number>mapWith(that, RatioValue::of,
                (l, r) ->
                        l.doubleValue() / r.doubleValue(),
                JavaScript.JsExpression::divide));
    }
}
