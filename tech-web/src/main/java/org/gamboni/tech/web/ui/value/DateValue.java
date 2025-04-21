package org.gamboni.tech.web.ui.value;

import org.gamboni.tech.web.js.JavaScript;

import java.time.Instant;

import static org.gamboni.tech.web.ui.value.Value.constant;

public class DateValue extends ValueWrapper<Instant> {
    public static DateValue of(JavaScript.JsExpression expr) {
        if (expr instanceof DateValue date) {
            return date;
        } else {
            return new DateValue(Value.of(expr));
        }
    }

    public static DateValue of(Instant instant) {
        return new DateValue(constant(
                instant,
                JavaScript.literal(instant)));
    }

    public static DateValue now() {
        return new DateValue(Value.supplied(
                Instant::now,
                JavaScript.newDate()));
    }

    private DateValue(Value<Instant> delegate) {
        super(delegate);
    }

    public NumberValue getTime() {
        return NumberValue.of(this.<Number>map(
                Instant::toEpochMilli,
                NumberValue::of,
                js -> js.invoke("getTime")));
    }

    public NumberValue minus(DateValue that) {
        return this.getTime().minus(that.getTime());
    }
}
