package org.gamboni.tech.history.ui;

import lombok.RequiredArgsConstructor;
import org.gamboni.tech.history.ClientStateHandler;
import org.gamboni.tech.history.event.ElementRemovedEventValues;
import org.gamboni.tech.web.js.JavaScript;
import org.gamboni.tech.web.ui.*;
import org.gamboni.tech.web.ui.value.Value;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static lombok.AccessLevel.PRIVATE;
import static org.gamboni.tech.web.js.JavaScript.*;
import static org.gamboni.tech.web.ui.Html.attribute;

@RequiredArgsConstructor(access = PRIVATE)
public class ArrayElement<T> {

    private static final String DEFAULT_ELEMENT_KEY = "e";

    private final String eventKey;
    private final String elementKey;
    /** Note: parametrised by the elementKey */
    private final Function<String, ElementRenderer<T>> renderer;
    private final boolean supportsRemoval;

    public static <T> ArrayElement<T> addOnly(ElementRenderer<T> renderer) {
        return new ArrayElement<>("", DEFAULT_ELEMENT_KEY, __ -> renderer, false);
    }

public static <T> ArrayElement<T> withRemoval(Function<T, Value<String>> getId, ElementRenderer<T> renderer) {
        return new ArrayElement<T>("", DEFAULT_ELEMENT_KEY,
                idPrefix -> item -> renderer.render(item)
                        .plusAttribute(attribute("id",
                                Value.of(idPrefix +"-")
                                        .plus(getId.apply(item)))),
                true);
    }

    public ArrayElement<T> withElementKey(String elementKey) {
        return new ArrayElement<>(eventKey, elementKey, renderer, supportsRemoval);
    }

    public ArrayElement<T> withEventKey(String eventKey) {
        return new ArrayElement<>(eventKey, elementKey, renderer, supportsRemoval);
    }

    /** Note: parametrised by the idPrefix */
    private Function<String, DynamicPageMember<Object, ?>> newElementHandler = null;

    public enum AddAt {
        START {
            @Override
            JsExpression insert(JsHtmlElement parent, JsHtmlElement child) {
                return parent.prepend(child);
            }
        }, END {
            @Override
            JsExpression insert(JsHtmlElement parent, JsHtmlElement child) {
                return parent.insertToEnd(child);
            }
        };

        abstract JsExpression insert(JsHtmlElement parent, JsHtmlElement child);
    }

    public <E> ArrayElement<T> withNewElementHandler(
            BiFunction<JavaScript.JsExpression, ClientStateHandler.MatchCallback, E> matcher,
            Function<E, T> extractor, AddAt addAt) {
        this.newElementHandler = idPrefix -> {
            var renderer = this.renderer.apply(idPrefix);
            return new DynamicPageMember<>() {
                @Override
                public <P extends DynamicPage<?>> Object addTo(P page) {
                    return page.addHandler(matcher,
                            event -> {
                                var template = renderer.render(extractor.apply(event));

                                Optional<Html.Attribute> idAttribute = template.getAttribute("id");

                                JsStatement createElement = template.javascriptCreate(elt ->
                                        addAt.insert(getElementById(literal(idPrefix)), elt)
                                );

                                return
                                        // If there's an 'id' attribute,
                                        // only create an element if there wasn't already one with the same id
                                        idAttribute.<JsStatement>map(id -> _if(getElementById(id
                                                .getAttributeValue())
                                                .not(),
                                        createElement))
                                                // If no 'id' attribute, we were created with 'addOnly' and don't detect duplicates
                                        .orElse(createElement);
                            });
                }
            };
        };
        return this;
    }

    public <P extends DynamicPage<?>> Renderer<Stream<T>> addTo(P page) {
        String idPrefix = page.freshElementId(elementKey);
        newElementHandler.apply(idPrefix).addTo(page);
        var renderer = this.renderer.apply(idPrefix);

        if (supportsRemoval) {
            page.addHandler((event, callback) -> {
                ElementRemovedEventValues asElementRemoved = ElementRemovedEventValues.of(event);
                callback.expect(asElementRemoved.isThisType());
                callback.expect(event.dot("key").eq(literal(this.eventKey)));
                return asElementRemoved;
            }, event -> let(getElementById(literal(idPrefix + "-").plus(event.id())),
                    JavaScript.JsHtmlElement::new,
                    existing -> _if(existing, existing.remove())));
        }
        return stream -> new Element("div",
                List.of(attribute("id", idPrefix)),
                stream
                        .map(renderer::render)
                        .toArray(HtmlFragment[]::new));
    }
}
