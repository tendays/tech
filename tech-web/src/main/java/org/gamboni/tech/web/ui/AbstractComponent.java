package org.gamboni.tech.web.ui;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import org.gamboni.tech.web.js.JavaScript;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.gamboni.tech.web.ui.Html.attribute;

public abstract class AbstractComponent {

    public enum End {
        FRONT, BACK
    }

    protected final End end;

    protected AbstractComponent(End end) {
        this.end = end;
    }

    protected static Element a(String href, HtmlFragment... contents) {
        return new Element("a", List.of(attribute("href", href)), contents);
    }
    protected static Element a(Css.ClassList style, String href, HtmlFragment... contents) {
        return new Element("a", List.of(
                style,
                attribute("href", href)), contents);
    }
    protected static Element a(List<? extends Html.Attribute> attributes, String href, HtmlFragment... contents) {
        return new Element("a", ImmutableList.<Html.Attribute>builder()
                .addAll(attributes)
                .add(attribute("href", href))
                .build(),
                contents);
    }

    protected static Element button(Css.ClassList css, String text, JavaScript.JsExpression onclick) {
        return new Element("button", List.of(css, attribute("onclick", onclick)), Html.escape(text));
    }

    protected static Element button(String text, JavaScript.JsExpression onclick) {
        return new Element("button", List.of(attribute("onclick", onclick)), Html.escape(text));
    }

    protected static Html img(Css.ClassList style, String src) {
        return new Tag("img", style,
                Html.attribute("src", src));
    }

    protected static Html img(Css.ClassList style, Value<String> src) {
        return new Tag("img", style,
                attribute("src", src));
    }

    protected static Html img(String src) {
        return new Tag("img",
                attribute("src", src));
    }

    protected static Element div(List<? extends Html.Attribute> attributes, HtmlFragment... content) {
        return new Element("div", attributes, content);
    }

    protected static Element div(HtmlFragment... content) {
        return new Element("div", content);
    }

    protected static Element input(Html.Attribute... attributes) {
        return new Element("input", Arrays.asList(attributes));
    }

    protected static Element p(HtmlFragment... contents) {
        return new Element("p", contents);
    }

    protected static Element p(Iterable<Html.Attribute> attributes, HtmlFragment... contents) {
        return new Element("p", attributes, contents);
    }

    protected static Element span(Css.ClassList style, HtmlFragment... contents) {
        return new Element("span", List.of(style), contents);
    }

    protected static Element span(Iterable<Html.Attribute> attributes, HtmlFragment... contents) {
        return new Element("span", attributes, contents);
    }

    /** Convert an Iterable to an HTML unnumbered list. */
    protected static <T> Element ul(Iterable<T> list, Function<T, Html> renderer) {
        return new Element("ul", Iterables.transform(list,
                e -> new Element("li", renderer.apply(e))));
    }

    /** Convert an Iterable to an HTML unnumbered list, applying the given styles to the ul and li elements.
     * The {@code renderer} may return {@link Html#EMPTY} to indicate that an element should be omitted from
     * the returned list.
     */
    protected static <T> Element ul(Css.ClassList ulStyle, Iterable<T> list, Css.ClassList liStyle, Function<T, Html> renderer) {
        return new Element("ul", List.of(ulStyle),
                Streams.stream(list)
                        .map(renderer)
                        .filter(h -> !h.equals(Html.EMPTY))
                        .map(h -> new Element("li", List.of(liStyle), h))
                        .toList());
    }

}
