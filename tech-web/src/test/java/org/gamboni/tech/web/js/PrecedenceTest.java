package org.gamboni.tech.web.js;

import org.junit.jupiter.api.Test;

import static org.gamboni.tech.web.js.JavaScript.*;
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

    @Test
    void testStatementPrecedence() {
        Fun poll = new Fun("poll");
        Fun flushQueue = new Fun("flushQueue");
        JsExpression socket = JsExpression.of("socket");
        assertFormat("if (socket.readyState === WebSocket.CLOSED){setTimeout(() => poll(), 60000);return;}",
        _if(socket.dot("readyState").eq(WebSocket.dot("CLOSED")),
                setTimeout(poll.invoke(), 60_000),
                _return()
        ));

        assertFormat("if (socket.readyState === WebSocket.CLOSED){setTimeout(() => poll(), 60000);return;} else if (socket.readyState === WebSocket.OPEN)flushQueue();",
                _if(socket.dot("readyState").eq(WebSocket.dot("CLOSED")),
                        setTimeout(poll.invoke(), 60_000),
                        _return()
                )
                ._elseIf(socket.dot("readyState").eq(WebSocket.dot("OPEN")),
                        flushQueue.invoke()));
    }

    private static void assertFormat(String expected, JavaScript.JsFragment expression) {
        assertEquals(expected,
                expression.format(JavaScript.Scope.NO_DECLARATION));
    }
}
