package org.gamboni.tech.web.ui;

import com.google.common.collect.Iterables;
import org.gamboni.tech.web.js.JavaScript;

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

    protected Element a(String href, Html... contents) {
        return new Element("a", List.of(attribute("href", href)), contents);
    }
    protected Element a(Css.ClassName style, String href, Html... contents) {
        return new Element("a", List.of(
                attribute("class", style.name),
                attribute("href", href)), contents);
    }

    protected Element button(String text, JavaScript.JsExpression onclick) {
        return new Element("button", List.of(attribute("onclick", onclick)), Html.escape(text));
    }

    protected Html img(Css.ClassName style, String src) {
        return new Tag("img", attribute("class", style.name),
                Html.attribute("src", src));
    }

    protected Html img(Css.ClassName style, Value<String> src) {
        return new Tag("img", attribute("class", style.name),
                attribute("src", src));
    }

    protected Html img(String src) {
        return new Tag("img",
                attribute("src", src));
    }

    protected Element div(List<? extends Html.Attribute> attributes, Html... content) {
        return new Element("div", attributes, content);
    }

    protected Element div(Html... content) {
        return new Element("div", content);
    }

    protected Element p(Html... contents) {
        return new Element("p", contents);
    }

    protected Element p(Iterable<Html.Attribute> attributes, Html... contents) {
        return new Element("p", attributes, contents);
    }

    /** Convert an Iterable to an HTML unnumbered list. */
    protected <T> Element ul(Iterable<T> list, Function<T, Html> renderer) {
        return new Element("ul", Iterables.transform(list,
                e -> new Element("li", renderer.apply(e))));
    }

    /** Convert an Iterable to an HTML unnumbered list, applying the given styles to the ul and li elements. */
    protected <T> Element ul(Css.ClassName ulStyle, Iterable<T> list, Css.ClassName liStyle, Function<T, Html> renderer) {
        return new Element("ul", List.of(ulStyle), Iterables.transform(list,
                e -> new Element("li", List.of(liStyle), renderer.apply(e))));
    }

}
