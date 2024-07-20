package org.gamboni.tech.web.ui;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
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

    public Element(String name, Iterable<? extends Attribute> attributes, Iterable<? extends HtmlFragment> contents) {
        this.name = name;
        this.attributes = attributes;
        this.contents = Streams.stream(contents)
                .flatMap(Streams::stream) // flatten HtmlFragments into Htmls
                .toList();
    }

    public Element(String name, Iterable<? extends Attribute> attributes, HtmlFragment... contents) {
        this(name, attributes, Arrays.asList(contents));
    }

    protected IdentifiedElement withId(String id) {
        return new IdentifiedElement(id, name, attributes, contents);
    }

    public Element(String name, HtmlFragment... contents) {
        this(name, List.of(), Arrays.asList(contents));
    }

    public Element(String name, Iterable<? extends HtmlFragment> contents) {
        this(name, List.of(), contents);
    }

    public String toString() {
        return "<"+ name + (Iterables.isEmpty(attributes) ? "" : " "+ Tag.renderAttributes(attributes)) +">"+

                Joiner.on("").join(contents)
                +"</"+ name +">";
    }

    @Override
    public JavaScript.JsStatement javascriptCreate(Function<JavaScript.JsHtmlElement, ? extends JavaScript.JsFragment> continuation) {
        return JavaScript.let(JavaScript.createElement(name), JavaScript.JsHtmlElement::new,
                elt -> {
            var statements = new ArrayList<JavaScript.JsStatement>();
                    for (var attr : attributes) {
                        statements.add(attr.javascriptCreate(elt));
                    }
                    for (var fragment : contents) {
                        for (var child : fragment) {
                            statements.add(child.javascriptCreate(
                                    childRef ->
                                            JavaScript.JsStatement.of(elt.invoke("appendChild", childRef))
                            ));
                        }
                    }
                    statements.add(
                            JavaScript.JsStatement.of(continuation.apply(elt)));
                    return JavaScript.seq(statements);
                });
    }
}
