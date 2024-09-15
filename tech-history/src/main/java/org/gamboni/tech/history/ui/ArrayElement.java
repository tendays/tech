package org.gamboni.tech.history.ui;

import lombok.RequiredArgsConstructor;
import org.gamboni.tech.history.ClientStateHandler;
import org.gamboni.tech.web.js.JavaScript;
import org.gamboni.tech.web.ui.Element;
import org.gamboni.tech.web.ui.HtmlFragment;
import org.gamboni.tech.web.ui.Renderer;
import org.gamboni.tech.web.ui.Value;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static lombok.AccessLevel.PRIVATE;
import static org.gamboni.tech.web.js.JavaScript.getElementById;
import static org.gamboni.tech.web.js.JavaScript.literal;
import static org.gamboni.tech.web.ui.Html.attribute;

@RequiredArgsConstructor(access = PRIVATE)
public class ArrayElement<T> {
    final String id;
    final Renderer<Value<T>> renderer;

    public JavaScript.JsStatement add(Value<T> element) {
        return renderer.render(element)
                .javascriptCreate(elt ->
                        getElementById(literal(id)).insertToEnd(elt)
                );
    }

    public Element render(Stream<Value<T>> stream) {
        return new Element("div",
                List.of(attribute("id", id)),
                stream
                        .map(renderer::render)
                        .toArray(HtmlFragment[]::new));
    }

    public interface Step1 {
        <T> Step2<T> renderedWith(Renderer<Value<T>> renderer);
    }

    public interface Step2<T> {
        <E> ArrayElement<T> usingHandler(ClientStateHandler handler,
                                         BiFunction<JavaScript.JsExpression, ClientStateHandler.MatchCallback, E> matcher,
                                         Function<E, Value<T>> extractor
                                         );
    }

    public static Step1 withId(String id) {
        return new Step1() {
            @Override
            public <T> Step2<T> renderedWith(Renderer<Value<T>> renderer) {
                return new Step2<T>() {
                    @Override
                    public <E> ArrayElement<T> usingHandler(ClientStateHandler handler, BiFunction<JavaScript.JsExpression, ClientStateHandler.MatchCallback, E> matcher, Function<E, Value<T>> extractor) {
                        var array = new ArrayElement<>(id, renderer);
                        handler.addHandler(matcher,
                                event -> array.add(extractor.apply(event)));
                        return array;
                    }
                };
            }
        };
    }
}
