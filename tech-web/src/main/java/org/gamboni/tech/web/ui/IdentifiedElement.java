package org.gamboni.tech.web.ui;

import com.google.common.collect.Iterables;
import org.gamboni.tech.web.js.JavaScript;
import org.gamboni.tech.web.js.JavaScript.JsHtmlElement;

import java.util.List;

import static org.gamboni.tech.web.js.JavaScript.getElementById;
import static org.gamboni.tech.web.js.JavaScript.literal;

public class IdentifiedElement extends Element {

    public final String id;

    IdentifiedElement(String id, String name, Iterable<? extends Attribute> attributes,
                      Iterable<? extends Html> contents,
                      Iterable<? extends JavaScript.JsFragment> onload) {
        super(name, Iterables.concat(
                List.of(Html.attribute("id", id)), attributes), contents,
                onload);
        this.id = id;
    }

    /** Return a JavaScript expression finding this element in the current document. */
    public JsHtmlElement find() {
        return getElementById(literal(id));
    }
}
