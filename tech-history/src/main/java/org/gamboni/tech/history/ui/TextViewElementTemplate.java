package org.gamboni.tech.history.ui;

import com.google.common.collect.Iterables;
import lombok.RequiredArgsConstructor;
import org.gamboni.tech.history.event.TextEventValues;
import org.gamboni.tech.web.ui.Element;
import org.gamboni.tech.web.ui.ElementRenderer;
import org.gamboni.tech.web.ui.Html;
import org.gamboni.tech.web.ui.value.StringValue;
import org.gamboni.tech.web.ui.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.gamboni.tech.web.js.JavaScript.getElementById;
import static org.gamboni.tech.web.js.JavaScript.literal;
import static org.gamboni.tech.web.ui.Html.attribute;
import static org.gamboni.tech.web.ui.Html.escape;

@RequiredArgsConstructor
public class TextViewElementTemplate<D> implements DynamicPageMember<Object, ElementRenderer<D>> {
    private final String key; // TODO distinguish eventKey and elementKey like EnumViewElementTemplate
    private final Element base;
    private final Function<D, Value<?>> getId;
    private final Function<D, StringValue> getValue;

    @Override
    public <P extends DynamicPage<?>> ElementRenderer<D> addTo(P page) {
        String idPrefix = page.freshElementId(key) + "-";
        page.addHandler((event, callback) -> {
            var wrapped = TextEventValues.of(event);
            callback.expect(wrapped.key().eq(literal(key)));
            return wrapped;
        }, (TextEventValues event) -> getElementById(literal(idPrefix).plus(event.id()))
                .setInnerText(event.text()));

        return entity -> {
            List<Html.Attribute> attributes = new ArrayList<>(
                    Iterables.size(base.getAttributes()) + 1);
            base.getAttributes().forEach(attributes::add);
            attributes.add(attribute("id", idPrefix + getId.apply(entity).assertStatic()));
            return base.withAttributes(attributes).withContents(escape(getValue.apply(entity)));
        };
    }
}
