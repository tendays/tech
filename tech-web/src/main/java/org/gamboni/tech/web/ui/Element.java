package org.gamboni.tech.web.ui;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.gamboni.tech.web.js.JavaScript;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * @author tendays
 */
public class Element implements Html {
    private final String name;
    private final Iterable<? extends Attribute> attributes;
    private final Iterable<? extends Html> contents;

    public Element(String name, Iterable<? extends Attribute> attributes, Iterable<? extends Html> contents) {
        this.name = name;
        this.attributes = attributes;
        this.contents = contents;
    }

    public Element(String name, Iterable<? extends Attribute> attributes, Html... contents) {
        this(name, attributes, Arrays.asList(contents));
    }

    protected IdentifiedElement withId(String id) {
        return new IdentifiedElement(id, name, attributes, contents);
    }

    public Element(String name, Html... contents) {
        this(name, List.of(), ImmutableList.copyOf(contents));
    }

    public Element(String name, Iterable<? extends Html> contents) {
        this(name, List.of(), contents);
    }

    public String toString() {
        return "<"+ name + (Iterables.isEmpty(attributes) ? "" : " "+ Tag.renderAttributes(attributes)) +">"+
                Joiner.on("").join(contents)
                +"</"+ name +">";
    }

    @Override
    public JavaScript.JsStatement javascriptCreate(Function<JavaScript.JsHtmlElement, JavaScript.JsStatement> continuation) {
        return JavaScript.let(JavaScript.createElement(name), JavaScript.JsHtmlElement::new,
                elt -> {
            var statements = new ArrayList<JavaScript.JsStatement>();
                    for (var attr : attributes) {
                        statements.add(attr.javascriptCreate(elt));
                    }
                    for (var child : contents) {
                        statements.add(child.javascriptCreate(
                                childRef ->
                                        JavaScript.JsStatement.of(elt.invoke("appendChild", childRef))
                        ));
                    }
                    statements.add(
                            continuation.apply(elt));
                    return JavaScript.seq(statements);
                });
    }
}
