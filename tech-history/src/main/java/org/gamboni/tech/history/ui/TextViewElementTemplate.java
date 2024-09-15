package org.gamboni.tech.history.ui;

import com.google.common.collect.Iterables;
import org.gamboni.tech.history.ClientStateHandler;
import org.gamboni.tech.web.ui.Element;
import org.gamboni.tech.web.ui.Html;
import org.gamboni.tech.web.ui.RendererWithId;

import java.util.ArrayList;
import java.util.List;

import static org.gamboni.tech.web.js.JavaScript.getElementById;
import static org.gamboni.tech.web.js.JavaScript.literal;
import static org.gamboni.tech.web.ui.Html.attribute;
import static org.gamboni.tech.web.ui.Html.escape;

public class TextViewElementTemplate {
    private final String key;
    private final Element base;
    public TextViewElementTemplate(String key, Element base) {
        this.key = key;
        this.base = base;
    }

    public RendererWithId<String> register(ClientStateHandler stateHandler) {
        stateHandler.addHandler((event, callback) -> {
            var wrapped = new JsTextEvent(event);
            callback.expect(wrapped.key().eq(literal(key)));
            return wrapped;
        }, event -> getElementById(event.id().plus("-" + key))
                    .setInnerText(event.text()));

        return (Object id, String text) -> {
            List<Html.Attribute> attributes = new ArrayList<>(
                    Iterables.size(base.getAttributes()) + 1);
            base.getAttributes().forEach(attributes::add);
            attributes.add(attribute("id", id + "-" + key));
            return base.withAttributes(attributes).withContents(escape(text));
        };
    }
}
