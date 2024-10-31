package org.gamboni.tech.web.ui;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import lombok.Getter;
import org.gamboni.tech.web.js.JavaScript;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.google.common.collect.Streams.stream;

/**
 * @author tendays
 */
public class Element implements Html {
    private final String name;
    @Getter
    private final Iterable<? extends Attribute> attributes;
    private final Iterable<? extends Html> contents;

    public Element(String name, Iterable<? extends Attribute> attributes, Iterable<? extends HtmlFragment> contents) {
        this.name = name;
        this.attributes = attributes;
        this.contents = stream(contents)
                .flatMap(Streams::stream) // flatten HtmlFragments into Htmls
                .toList();
    }

    public Iterable<? extends HtmlFragment> getContents() {
        return contents;
    }

    public Element plusAttribute(Attribute newAttribute) {
        return this.withAttributes(ImmutableList.<Attribute>builderWithExpectedSize(1+ Iterables.size(attributes))
                .addAll(attributes)
                .add(newAttribute)
                .build());
    }

    /** Return a new element with the same name and contents as this one but where attributes are replaced by the given ones. */
    public Element withAttributes(Iterable<? extends Attribute> newAttributes) {
        return new Element(this.name, newAttributes, this.contents);
    }

    public Element withContents(Iterable<? extends HtmlFragment> newContents) {
        return new Element(this.name, this.attributes, newContents);
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

    public Optional<Attribute> getAttribute(String name) {
        return stream(attributes)
                .filter(attr -> attr.getAttributeName().equals(name))
                .<Attribute>map(a -> a) // silly conversion of "? extends Attribute" to just Attribute (because javac doesn't know Optional<> is covariant)
                .findFirst();
    }
}
