package org.gamboni.tech.web.ui;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.gamboni.tech.misc.LazyBuilder;
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
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class Element implements Html {
    private final String name;
    @Getter
    private final Iterable<? extends Attribute> attributes;
    private final Iterable<? extends Html> contents;
    @Getter
    private final Iterable<? extends JavaScript.JsFragment> onload;

    public Element(String name, Iterable<? extends Attribute> attributes, Iterable<? extends HtmlFragment> contents) {
        this(name,
                attributes,
                flatten(contents),
                extractOnLoad(List.of(), contents));
    }

    private static List<Html> flatten(Iterable<? extends HtmlFragment> contents) {
        return stream(contents)
                .flatMap(Streams::stream)
                .toList();
    }

    private static Iterable<? extends JavaScript.JsFragment> extractOnLoad(Iterable<? extends JavaScript.JsFragment> onLoad, Iterable<? extends HtmlFragment> contents) {
        LazyBuilder<JavaScript.JsFragment> builder = LazyBuilder.of(onLoad);
        for (var html : contents) {
            if (html instanceof Element elt) {
                for (var js : elt.onload) {
                    builder = builder.add(js);
                }
            }
        }
        return builder.build();
    }

    public Element(String name, HtmlFragment... contents) {
        this(name, List.of(), Arrays.asList(contents));
    }

    public Element(String name, Iterable<? extends HtmlFragment> contents) {
        this(name, List.of(), contents);
    }

    public Element(String name, Iterable<? extends Attribute> attributes, HtmlFragment... contents) {
        this(name, attributes, Arrays.asList(contents));
    }

    public Iterable<? extends HtmlFragment> getContents() {
        return contents;
    }

    /**
     * Add the given attribute to this object. NOTE: if an attribute with the same name aready exists,
     * the returned attribute will have <em>multiple</em> attributes with the same name.
     * <p>If you want to replace an existing attribute, use {@link #withAttribute(Attribute)} instead.</p>
     *
     * @param newAttribute the attribute to append to existing ones.
     * @return the element with the new attribute added.
     */
    public Element plusAttribute(Attribute newAttribute) {
        return this.withAttributes(append(attributes, newAttribute));
    }

    private static <T> ImmutableList<T> append(Iterable<? extends T> base, T toAdd) {
        return ImmutableList.<T>builderWithExpectedSize(1 + Iterables.size(base))
                .addAll(base)
                .add(toAdd)
                .build();
    }

    /**
     * Set the given attribute on this object, replacing any already-existing attribute(s) with the
     * same name. NOTE: if an attribute with the same name already exists,
     * the returned attribute will have <em>multiple</em> attributes with the same name.
     * <p>If you want to have multiple attributes with the same name, use {@link #plusAttribute(Attribute)} instead.</p>
     *
     * @param newAttribute the attribute to set on top of existing ones.
     * @return the element with the new attribute set.
     */
    public Element withAttribute(Attribute newAttribute) {
        ImmutableList.Builder<Attribute> attributeBuilder = ImmutableList.<Attribute>builderWithExpectedSize(1 + Iterables.size(attributes));
        for (Attribute existing : this.attributes) {
            if (!existing.getAttributeName().equals(newAttribute.getAttributeName())) {
                attributeBuilder.add(existing);
            }
        }
        return this.withAttributes(attributeBuilder
                .add(newAttribute)
                .build());
    }

    public Element withOnLoad(JavaScript.JsFragment code) {
        return new Element(name, attributes, contents, append(onload, code));
    }

    /**
     * Return a new element with the same name and contents as this one but where attributes are replaced by the given ones.
     */
    public Element withAttributes(Iterable<? extends Attribute> newAttributes) {
        return new Element(this.name, newAttributes, this.contents, this.onload);
    }

    public Element withContents(Iterable<? extends HtmlFragment> newContents) {
        Iterable<? extends Html> contents1 = flatten(newContents);
        return new Element(this.name, this.attributes, contents1, extractOnLoad(this.onload, newContents));
    }

    protected IdentifiedElement withId(String id) {
        return new IdentifiedElement(id, name, attributes, contents, onload);
    }

    public String toString() {
        return "<" + name + (Iterables.isEmpty(attributes) ? "" : " " + Tag.renderAttributes(attributes)) + ">" +

                Joiner.on("").join(contents)
                + "</" + name + ">";
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
