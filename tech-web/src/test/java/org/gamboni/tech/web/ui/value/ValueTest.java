package org.gamboni.tech.web.ui.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ValueTest {

    enum ValueCategory {
        CONSTANT, VARIABLE, TIME_DEPENDENT, OTHER;

        static ValueCategory of(Value<?> v) {
            if (v.constantValue().isPresent()) {
                return CONSTANT;
            } else if (v.variableValue().isPresent()) {
                return VARIABLE;
            } else if (v.isTimeDependent()) {
                return TIME_DEPENDENT;
            } else {
                return OTHER;
            }
        }

        StringValue sample() {
            return switch (this) {
                case CONSTANT -> Value.of("HELLO");
                case VARIABLE -> Value.of("The time is ").plus(DateValue.now());
                case TIME_DEPENDENT -> StringValue.of(Value.timeDependent(s -> "y"));
                case OTHER -> StringValue.of(s -> "x");
            };
        }
    }

    @Test
    public void testUnaryOperatorPreservesCategory() {
        for (var c : ValueCategory.values()) {
            assertEquals(c,
                    ValueCategory.of(
                            c.sample().toLowerCase()
                    ));
        }
    }

    @Test
    public void testOperatorMatrix() {
        // This test verifies the operator matrix documented in ValueWrapper.mapWith
        // is correctly implemented

        // abbreviation
        var c = ValueCategory.CONSTANT;
        var v = ValueCategory.VARIABLE;
        var t = ValueCategory.TIME_DEPENDENT;
        var o = ValueCategory.OTHER;

        var matrix = new ValueCategory[][] {
                new ValueCategory[]{c, v, t, o},
                new ValueCategory[]{v, v, t, t},
                new ValueCategory[]{t, t, t, t},
                new ValueCategory[]{o, t, t, o}
        };

        for (var l : ValueCategory.values()) {
            for (var r : ValueCategory.values()) {
                var actual = ValueCategory.of(l.sample().plus(r.sample()));
                assertEquals(matrix[r.ordinal()][l.ordinal()], actual, "for " + l +" * " + r);
            }
        }
    }
}
