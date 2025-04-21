package org.gamboni.tech.web.ui.value;


import org.gamboni.tech.web.js.JavaScript;

import static org.gamboni.tech.web.ui.value.Value.constant;

/** A number obtained by dividing a number by another one. */
public class RatioValue extends ValueWrapper<Number> implements Value<Number> {

    public static RatioValue of(JavaScript.JsExpression expr) {
        if (expr instanceof RatioValue ratio) {
            return ratio;
        } else {
            return new RatioValue(Value.of(expr));
        }
    }
    public static RatioValue of(Number literal) {
        return new RatioValue(constant(literal, JavaScript.literal(literal)));
    }

    private RatioValue(Value<? extends Number> delegate) {
        super(delegate);
    }

    public StringValue toPercentageString() {
        return StringValue.of(this.map(
                constant -> constant.doubleValue() * 100 + "%",
                StringValue::of,
                js -> js.times(100).plus("%")));
    }
}
