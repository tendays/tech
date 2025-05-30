package org.gamboni.tech.web.ui;

import org.gamboni.tech.web.js.JavaScript;
import org.gamboni.tech.web.js.JavaScript.JsExpression;
import org.gamboni.tech.web.js.JavaScript.JsHtmlElement;
import org.gamboni.tech.web.js.JavaScript.JsStatement;
import org.gamboni.tech.web.ui.value.StringValue;
import org.gamboni.tech.web.ui.value.Value;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import static org.gamboni.tech.web.js.JavaScript.lambda;
import static org.gamboni.tech.web.js.JavaScript.literal;
import static org.gamboni.tech.web.js.JavaScript.seq;

/**
 * @author tendays
 */
public interface Html extends HtmlFragment {
    Html EMPTY = new EmptyHtml();

    final class EmptyHtml implements Html {
        private EmptyHtml() {}
        @Override
        public JsStatement javascriptCreate(Function<JsHtmlElement, ? extends JavaScript.JsFragment> continuation) {
            return seq();
        }

        public String toString() { return ""; }
        public int hashCode() { return 9; }
        public boolean equals(Object that) {
            return (that instanceof EmptyHtml);
        }
    };

    static Html escape(String text) {
        return new Html() {
            @Override
            public JsStatement javascriptCreate(Function<JsHtmlElement, ? extends JavaScript.JsFragment> continuation) {
                return JsStatement.of(continuation.apply(JavaScript.createTextNode(text)));
            }

            public String toString() {
                return text.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;");
            }
        };
    }

    static Html escape(StringValue text) {
        return new Html() {
            @Override
            public JsStatement javascriptCreate(Function<JsHtmlElement, ? extends JavaScript.JsFragment> continuation) {
                return JsStatement.of(continuation.apply(JavaScript.createTextNode(text)));
            }

            public String toString() {
                // TODO distinguish static and dynamic html. Don't support generating static html for potentially dynamic input?
                return text.assertStatic().replace("&", "amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;");
            }
        };
    }

    default Iterator<Html> iterator() {
        return List.of(this).iterator();
    }

    /** Fluent interface to create an IdentifiedElement. Usage: {@code setId(id).to(...)}. */
    static IdentifiedElementBuilder setId(String id) {
        return new IdentifiedElementBuilder(id);
    }

    record IdentifiedElementBuilder(String id) {
        public IdentifiedElement to(Element element) {
            return element.withId(id);
        }
    }

    static String quote(String attribute) {
        return '"' + attribute
                .replace("&", "&amp;")
                .replace("\"", "&quot;") + '"';
    }

    static Attribute attribute(String name, Value<String> value) {
        return new Attribute() {
            @Override
            public String getAttributeName() {
                return name;
            }

            @Override
            public Value<String> getAttributeValue() {
                return value;
            }
        };
    }

    static Attribute eventHandler(String name, Function<JsExpression, JsExpression> handler) {
        return new Attribute() {
            @Override
            public String getAttributeName() {
                return name;
            }

            @Override
            public Value<String> getAttributeValue() {
                return Value.of(handler.apply(JsExpression._this));
            }

            @Override
            public String render() {
                String text = getAttributeValue().format(JavaScript.Scope.NO_DECLARATION);
                return getAttributeName() +"=\""+ text
                        .replace("&", "&amp;")
                        .replace("\"", "&quot;")
                        + "\"";
            }

            @Override
            public JsStatement javascriptCreate(JsExpression elt) {
                return elt.dot(name).set(lambda(handler.apply(elt)));
            }
        };
    }

    /** Set an attribute to a Javascript expression (typical for event handlers). */
    static Attribute attribute(String name, JavaScript.JsFragment expr) {
        return attribute(name, Value.of(expr.format(JavaScript.Scope.NO_DECLARATION)));
    }

    static Attribute attribute(String name, String value) {
        return attribute(name, Value.of(value));
    }

    /** Generate code constructing this fragment.
     *
     * @param continuation what to do with the constructed DOM node (typically, insert it somewhere in the document).
     * @return the JavaScript code constructing this fragment, including continuation code.
     */
    JsStatement javascriptCreate(Function<JsHtmlElement, ? extends JavaScript.JsFragment> continuation);

    interface Attribute {
        String getAttributeName();
        Value<String> getAttributeValue();

        /** Return {@code true} if this attribute holds a default/null value and can be omitted completely. */
        default boolean isTrivial() {
            return false;
        }

        default JsStatement javascriptCreate(JsExpression elt) {
                return JsStatement.of(
                        elt.invoke("setAttribute",
                                literal(getAttributeName()), getAttributeValue()));
        }

        default String render() {
            String text = getAttributeValue().assertStatic();
            return getAttributeName() +"=\""+ text
                    .replace("&", "&amp;")
                    .replace("\"", "&quot;")
                    + "\"";
        }
    }
}
