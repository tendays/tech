package org.gamboni.tech.web.ui;

import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import org.gamboni.tech.web.js.JavaScript;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author tendays
 */
public class Tag implements Html {
    private final String name;
    private final Iterable<? extends Attribute> attributes;

    public Tag(String name, Iterable<? extends Attribute> attributes) {
        this.name = name;
        this.attributes = attributes;
    }
    public Tag(String name, Attribute... attributes) {
        this(name, Arrays.asList(attributes));
    }

    public String toString() {
        return "<"+ name + (Iterables.isEmpty(attributes) ? "" : " "+ renderAttributes(attributes)) +">";
    }

    @Override
    public JavaScript.JsStatement javascriptCreate(Function<JavaScript.JsHtmlElement, ? extends JavaScript.JsFragment> continuation) {
        return JavaScript.let(JavaScript.createElement(name), JavaScript.JsHtmlElement::new,
                elt -> {
                    var statements = new ArrayList<JavaScript.JsStatement>();
                    for (var attr : attributes) {
                        statements.add(attr.javascriptCreate(elt));
                    }
                    statements.add(
                            JavaScript.JsStatement.of(continuation.apply(elt)));
                    return JavaScript.seq(statements);
                });
    }

    static String renderAttributes(Iterable<? extends Attribute> attributes) {
        return Streams.stream(attributes).map(Attribute::render).collect(Collectors.joining(" "));
    }
}
