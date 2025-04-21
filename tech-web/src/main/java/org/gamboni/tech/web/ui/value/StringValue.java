package org.gamboni.tech.web.ui.value;

import org.gamboni.tech.web.js.JavaScript;

import java.util.Locale;

import static org.gamboni.tech.web.ui.value.Value.constant;

public class StringValue extends ValueWrapper<String> {
    public static StringValue of(JavaScript.JsExpression expr) {
        if (expr instanceof StringValue str) {
            return str;
        } else {
            return new StringValue(Value.of(expr));
        }
    }
    public static StringValue of(String literal) {
        return new StringValue(constant(literal, JavaScript.literal(literal)));
    }

    private StringValue(Value<String> delegate) {
        super(delegate);
    }

    public StringValue plus(JavaScript.JsExpression that) {
        return new StringValue(this.mapWith(Value.of(that),
                StringValue::of,
                (l, r) -> l + r,
                JavaScript.JsExpression::plus));
    }

    public StringValue substring(int len) {
        return StringValue.of(this.map(
                constant -> constant.substring(len),
                StringValue::of,
                js -> js.invoke("substring", JavaScript.literal(len))));
    }

    public StringValue slice(int from, int to) {
        return StringValue.of(this.map(
                constant -> constant.substring(from, to),
                StringValue::of,
                js -> js.invoke("slice", JavaScript.literal(from), JavaScript.literal(to))));
    }

    public StringValue toLowerCase() {
        return StringValue.of(this.map(
                constant -> constant.toLowerCase(Locale.ROOT),
                StringValue::of,
                js -> js.invoke("toLowerCase")));
    }
}
