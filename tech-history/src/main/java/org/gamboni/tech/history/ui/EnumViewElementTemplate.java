package org.gamboni.tech.history.ui;

import org.gamboni.tech.history.ClientStateHandler;
import org.gamboni.tech.web.js.JavaScript;
import org.gamboni.tech.web.ui.*;
import org.gamboni.tech.web.ui.Html.Attribute;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static org.gamboni.tech.web.js.JavaScript.*;
import static org.gamboni.tech.web.ui.Html.escape;

/** A self-updating element displaying the state of an entity, represented by an enum type.
 * <p>
 *     Usage: construct a single instance with {@link #EnumViewElementTemplate(Class, String, Element)}
 *     by giving the <em>base (static) element</em>
 *     then specify variable parts (only style or contents for now) by calling {@code with*} methods as required.
 *     Then, {@linkplain #register(ClientStateHandler) register} your instance into the state handler and call
 *     {@link Renderer#render(String, Enum)} on the returned object for server-side rendering of your component.
 *     To actually trigger client-side changes you need to emit {@link NewStateEvent}s with the same key and id passed
 *     to the constructor of this object.
 * </p>
 *
 * @param <E> the state type displayed in this object.
 */
public class EnumViewElementTemplate<E extends Enum<E>> {
    private final Class<E> enumType;
    private final String key;
    private final Element base;
    private final Map<String, DynamicAttribute<? super E>> attributes = new HashMap<>();
    private Optional<DynamicContent<E>> contents = Optional.empty();

    interface DynamicAttribute<E> {
        Attribute apply(String id, E value);

        JsStatement update(JsHtmlElement elt, Value<E> value);
    }

    public interface DynamicContent<E> {
        HtmlFragment apply(Value<E> value);

        JsStatement update(JsHtmlElement elt, Value<E> value);
    }

    private record ConstantAttribute<E>(Attribute attr) implements DynamicAttribute<E> {

        @Override
        public Attribute apply(String id, E value) {
            return attr;
        }

        @Override
        public JsStatement update(JsHtmlElement elt, Value<E> value) {
            return seq();
        }
    }

    private record DynamicCssClassAttribute<E extends Enum<E>>(Function<Value<E>, Css.ClassList> function) implements DynamicAttribute<E> {

        @Override
        public Attribute apply(String id, E value) {
            return function.apply(Value.of(value));
        }

        @Override
        public JsStatement update(JsHtmlElement elt, Value<E> value) {
            return elt.classList().set(literal(
                    function.apply(value)));
        }
    }

    private static final DynamicAttribute<Object> ID_ATTRIBUTE = new DynamicAttribute<>() {
        @Override
        public Attribute apply(String id, Object value) {
            return Html.attribute("id", id);
        }

        @Override
        public JsStatement update(JsHtmlElement elt, Value<Object> value) {
            return seq();
        }
    };

    /** Construct an instance (which serves as a <em>template</em> you can render multiple times in your
     * pages.
     * @param enumType the enum type.
     * @param key a string identifying this particular template within the page.
     *            It will be used in events sent from the back end as well as appended to the id of HTML elements.
     * @param base the static part of the element to use as a template.
     */
    public EnumViewElementTemplate(Class<E> enumType, String key, Element base) {
        this.enumType = enumType;
        this.key = key;
        this.base = base;

        for (var baseAttr : base.getAttributes()) {
            attributes.put(baseAttr.getAttributeName(),
                    // by default, use constant attribute values that ignore the e value
                    new ConstantAttribute<>(baseAttr));
        }
        attributes.put("id", ID_ATTRIBUTE);
    }

    /** Add dynamic styling varying in function of the enum value.
     *
     * @param map a map of enum values to the corresponding CSS class to use.
     * @return this, for chaining
     */
    public EnumViewElementTemplate<E> withStyle(Css.EnumToClassName<E> map) {
        // TODO maybe put an and() method in DynamicCssClassAttribute to avoid this mess over here
        DynamicAttribute<? super E> classAttribute = attributes.get("class");
        if (classAttribute == null) {
            putAttribute("class", new DynamicCssClassAttribute<>(map::get));
        } else if (classAttribute instanceof EnumViewElementTemplate.ConstantAttribute<? super E> constantAttribute) {
            var fixed = (Css.ClassList)constantAttribute.attr;
            putAttribute("class", new DynamicCssClassAttribute<>(e -> fixed.and(map.get(e))));
        } else {
            // type parameter is really <? super E>, but DynamicCssClassAttribute.function
            // does not declare wildcards on inputs.
            var previous = (DynamicCssClassAttribute<E>)classAttribute;
            putAttribute("class", new DynamicCssClassAttribute<E>(e -> previous.function.apply(
                    e)
                    .and(map.get(e))));
        }
        return this;
    }

    private void putAttribute(String attributeName, DynamicAttribute<E> attr) {
        this.attributes.put(attributeName, attr);
    }

    public EnumViewElementTemplate<E> withContents(DynamicContent<E> contents) {
        this.contents = Optional.of(contents);
        return this;
    }

    /** Set the element to contain raw text varying in function of the enum value.
     *
     * @param renderer a map of enum values to the corresponding CSS class to use.
     * @return this, for chaining
     */
    public EnumViewElementTemplate<E> withContents(Function<E, String> renderer) {
        Map<E, JsString> formats = Stream.of(enumType.getEnumConstants())
                .collect(toMap(
                        e -> e,
                        e -> literal(renderer.apply(e))
                ));
        return this.withContents(new DynamicContent<E>() {
            @Override
            public HtmlFragment apply(Value<E> value) {
                return escape(renderer.apply(value.assertStatic()));
            }

            @Override
            public JsStatement update(JsHtmlElement elt, Value<E> value) {
                return formats.entrySet()
                        .stream()
                        .reduce(EMPTY_IF_CHAIN,
                                (chain, entry) ->
                                chain._elseIf(value.toExpression().eq(entry.getKey()),
                                        elt.setInnerText(entry.getValue())),
                                (__, ___) -> {
                            throw new UnsupportedOperationException();
                                });
            }
        });
    }

    /** Register this template into the given state handler. This method should typically be called once
     * from a {@link AbstractPage} subclass constructor or post-construct method. It ensures template instance
     * get properly updated when a change event is dispatched.
     *
     * @param stateHandler the client state handler into which to register the change event handler.
     */
    public RendererWithId<E> register(ClientStateHandler stateHandler) {
        stateHandler.addHandler((event, callback) -> {
            var wrapped = new JsNewStateEvent(event);
            callback.expect(wrapped.key().eq(literal(key)));
            return wrapped;
        }, event -> let(getElementById(event.id().plus("-" + key)),
                JsHtmlElement::new,
                elt -> Stream.concat(
                                contents.stream()
                                        .map(c -> c.update(elt, Value.of(event.newState()))),
                                attributes.values().stream()
                                        .map(dynamicAttribute -> dynamicAttribute.update(elt,
                                                Value.of(event.newState()))))
                        .collect(JavaScript.toSeq())));

        /* Create a renderer */
        return (id, value) -> {
            String eltId = id + "-" + key;
            Element withNewAttributes = base.withAttributes(attributes
                    .values()
                    .stream()
                    .map(f -> f.apply(eltId, value))
                    .toList());

            return contents.map(c -> c.apply(Value.of(value)))
                    .map(c -> withNewAttributes.withContents(List.of(c)))
                    .orElse(withNewAttributes);
        };
    }
}
