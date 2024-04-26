package org.gamboni.tech.web.js;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.gamboni.tech.web.js.JavaScript.literal;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verify that brackets are added when needed, and only when needed. */
public class PrecedenceTest {

    public static final JavaScript.JsExpression TWO = literal(2);
    public static final JavaScript.JsExpression THREE = literal(3);
    public static final JavaScript.JsExpression FOUR = literal(4);

    @Test
    void test() {
        assertFormat("(2+3)*4", TWO.plus(THREE).times(FOUR));

        assertFormat("2/(3+4)", TWO.divide(THREE.plus(FOUR)));

        assertFormat("2*3*4", TWO.times(THREE).times(FOUR));

        assertFormat("2*3*4", TWO.times(THREE.times(FOUR)));

        assertFormat("2-(3-4)", TWO.minus(THREE.minus(FOUR)));

        assertFormat("2-3*4", TWO.minus(THREE.times(FOUR)));

        assertFormat("2-3-4", TWO.minus(THREE).minus(FOUR));

        assertFormat("('2'+'3').substring(4)",
                literal("2").plus("3").substring(4));

        assertFormat("'hello'.substring(2)", literal("hello").substring(2));
    }

    private static void assertFormat(String expected, JavaScript.JsExpression expression) {
        assertEquals(expected,
                expression.format(JavaScript.Scope.NO_DECLARATION));
    }
}
