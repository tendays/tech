package org.gamboni.tech.web.ui;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.gamboni.tech.web.js.JavaScript;

import java.util.List;

/**
 * @author tendays
 */
public abstract class AbstractPage extends AbstractComponent {

    protected AbstractPage() {
        super(End.BACK); // pages are always rendered in the back end
    }

    protected HtmlElement html(Iterable<Resource> dependencies, Iterable<Element> body) {
        return new HtmlElement(dependencies, body, ImmutableList.of());
    }

    protected static class HtmlElement extends Element {
        private final Iterable<Resource> dependencies;
        private final Iterable<Element> body;
        private final List<Html.Attribute> bodyAttributes;

        public HtmlElement(Iterable<Resource> dependencies, Iterable<Element> body, List<Html.Attribute> bodyAttributes) {
            super("html",
                    new Element("head", Iterables.transform(dependencies, Resource::asElement)),
                    new Element("body", bodyAttributes, body));
            this.dependencies = dependencies;
            this.body = body;
            this.bodyAttributes = bodyAttributes;
        }

        public HtmlElement onLoad(JavaScript.JsFragment code) {
            return new HtmlElement(dependencies, body, ImmutableList.<Attribute>builder()
            .addAll(bodyAttributes)
            .add(Html.attribute("onload", code))
            .build());
        }

        @Override
        public String toString() {
            return "<!DOCTYPE html>\n" + super.toString();
        }
    }
}
