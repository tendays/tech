package org.gamboni.tech.web.ui;

import org.gamboni.tech.web.js.JavaScript;

import java.time.Duration;
import java.util.Map;

import static java.util.stream.Collectors.toMap;
import static org.gamboni.tech.web.js.JavaScript.JsExpression._null;
import static org.gamboni.tech.web.js.JavaScript.JsGlobal;
import static org.gamboni.tech.web.js.JavaScript.JsStatement;
import static org.gamboni.tech.web.js.JavaScript.Symbol;
import static org.gamboni.tech.web.js.JavaScript._if;
import static org.gamboni.tech.web.js.JavaScript.clearTimeout;
import static org.gamboni.tech.web.js.JavaScript.seq;
import static org.gamboni.tech.web.js.JavaScript.setTimeout;
import static org.gamboni.tech.web.js.JavaScript.toSeq;

/** A piece of JavaScript code repeating at scheduled intervals. */
public class RepeatingTask {

    private final JavaScript.JsGlobal timer;
    private final JavaScript.Fun function;
    private final Map<Symbol, JsGlobal> freeVariables;

    private RepeatingTask(JavaScript.JsGlobal timer, JavaScript.Fun function, Map<Symbol, JsGlobal> freeVariables) {
        this.timer = timer;
        this.function = function;
        this.freeVariables = freeVariables;
    }

    public static PageMember<Object, RepeatingTask> create(JavaScript.JsFragment body, Duration interval) {
        JsStatement bodyBlock = JsStatement.of(body);
        return page -> {
            Map<Symbol, JsGlobal> freeVariables = body.getFreeSymbols()
                    .stream()
                    .collect(toMap(
                            s -> s,
                    s -> {
                var globalVar = new JsGlobal(page.freshGlobal("c"));
                page.addToScript(globalVar.declare(_null));
                return globalVar;
                    }));

            var timer = new JavaScript.JsGlobal(page.freshElementId("timer"));
            var function = new JavaScript.Fun(page.freshElementId("loop"));

            page.addToScript(
                    timer.declare(_null),
                    function.declare(seq(
                            // This is just 'body' but where free symbols are replaced by the corresponding global
                            freeVariables.entrySet()
                                    .stream()
                                    .reduce(bodyBlock,
                                            (block, symbolEntry) ->
                                            symbolEntry.getKey().assignIn(symbolEntry.getValue(), block),
                                            (__, ___) -> {throw new UnsupportedOperationException();}),
                    timer.set(setTimeout(function.invoke(), interval.toMillis()))
            )));

            return new RepeatingTask(timer, function, freeVariables);
        };
    }

    public enum StartContext {
        ON_LOAD, GENERAL
    }

    public JavaScript.JsStatement start(StartContext context) {
        // the function will initialise 'timer' immediately (for its *next* execution)
        return seq(
                freeVariables.entrySet()
                        .stream()
                        .map(symbolEntry -> symbolEntry.getValue().set(symbolEntry.getKey()))
                        .collect(toSeq()),
                switch (context) {
                    case ON_LOAD -> function.invoke();
                    case GENERAL -> _if(timer.not(), function.invoke());
                });
    }

    public JavaScript.JsStatement stop() {
        return _if(timer, seq(
                clearTimeout(timer),
                timer.set(_null)));
    }
}
