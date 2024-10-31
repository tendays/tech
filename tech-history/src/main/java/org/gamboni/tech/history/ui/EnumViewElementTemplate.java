package org.gamboni.tech.history.ui;

import lombok.RequiredArgsConstructor;
import org.gamboni.tech.history.event.JsNewStateEvent;
import org.gamboni.tech.history.event.NewStateEvent;
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
import static lombok.AccessLevel.PRIVATE;
import static org.gamboni.tech.web.js.JavaScript.*;
import static org.gamboni.tech.web.ui.Html.escape;

/** A self-updating element displaying the state of an entity, represented by an enum type.
 * <p>
 *     Usage: construct a single instance with {@link #ofStaticBase(Class, Function, Function, Element)}
 *     by giving the <em>base (static) element</em> (or {@link #ofDynamicBase(Class, Function, Function, ElementRenderer)}
 *     with a renderer for the bits not depending on the state),
 *     then specify variable parts (only style or contents for now) by calling {@code with*} methods as required.
 *     Then, {@linkplain #addTo(DynamicPage)} your instance into the state handler and call
 *     {@link Renderer#render(Object)} on the returned object for server-side rendering of your component.
 *     To actually trigger client-side changes you need to emit {@link NewStateEvent}s with the same key and id passed
 *     to the constructor of this object.
 * </p>
 *
 * @param <E> the state type displayed in this object.
 */
@RequiredArgsConstructor(access = PRIVATE)
public class EnumViewElementTemplate<D, E extends Enum<E>> implements DynamicPageMember<Object, IdentifiedElementRenderer<D>> {
    private final Class<E> enumType;
    private final Function<D, Value<?>> getId;
    private final Function<D, Value<E>> getState;
    private final String eventKey;
    private final String elementKey;
    private final ElementRenderer<D> base;
    private final AttributeCollection<E> attributes;
    private Optional<DynamicContent<E>> contents = Optional.empty();

    private abstract static class AttributeCollection<E> {
        final Map<String, DynamicAttribute<? super E>> attributes = new HashMap<>();

        abstract Element apply(Element base, Function<DynamicAttribute<? super E>, Attribute> attributeRenderer);

        public void put(String name, DynamicAttribute<? super E> value) {
            attributes.put(name, value);
        }

        public Stream<? extends JsStatement> update(JsHtmlElement elt, Value<E> newState) {
            return attributes.values().stream()
                    .map(dynamicAttribute -> dynamicAttribute.update(elt,
                            newState));

        }

        public DynamicAttribute<? super E> get(String key) {
            return attributes.get(key);
        }

        public abstract AttributeCollection<E> copy();
    }

    private static class ExtraAttributes<E> extends AttributeCollection<E> {

        @Override
        public Element apply(Element base, Function<DynamicAttribute<? super E>, Attribute> attributeRenderer) {
            return attributes
                    .values()
                    .stream()
                    .map(attributeRenderer)
                    .reduce(base,
                            Element::plusAttribute,
                            (__, ___) -> { throw new UnsupportedOperationException(); });
        }

        @Override
        public AttributeCollection<E> copy() {
            var copy = new ExtraAttributes<E>();
            copy.attributes.putAll(this.attributes);
            return copy;
        }
    }

    private static class AttributeReplacement<E> extends AttributeCollection<E> {

        public AttributeReplacement(Element base) {
            for (var baseAttr : base.getAttributes()) {
                this.put(baseAttr.getAttributeName(),
                        // by default, use constant attribute values that ignore the e value
                        new ConstantAttribute<>(baseAttr));
            }
        }

        private AttributeReplacement(Map<String, DynamicAttribute<? super E>> attributes) {
            this.attributes.putAll(attributes);
        }

        @Override
        public Element apply(Element base, Function<DynamicAttribute<? super E>, Attribute> attributeRenderer) {
            return base
                    .withAttributes(attributes
                            .values()
                            .stream()
                            .map(attributeRenderer)
                            .toList());
        }

        @Override
        public AttributeCollection<E> copy() {
            return new AttributeReplacement<E>(this.attributes);
        }
    }

    interface DynamicAttribute<E> {
        Attribute apply(Value<String> id, Value<? extends E> value);

        JsStatement update(JsHtmlElement elt, Value<? extends E> value);
    }

    public interface DynamicContent<E> {
        HtmlFragment apply(Value<E> value);

        JsStatement update(JsHtmlElement elt, Value<E> value);
    }

    private record ConstantAttribute<E>(Attribute attr) implements DynamicAttribute<E> {

        @Override
        public Attribute apply(Value<String> id, Value<? extends E> value) {
            return attr;
        }

        @Override
        public JsStatement update(JsHtmlElement elt, Value<? extends E> value) {
            return seq();
        }
    }

    private record DynamicCssClassAttribute<E extends Enum<E>>(Function<? super Value<? extends E>, Css.ClassList> function) implements DynamicAttribute<E> {

        @Override
        public Attribute apply(Value<String> id, Value<? extends E> value) {
            return function.apply(value);
        }

        @Override
        public JsStatement update(JsHtmlElement elt, Value<? extends E> value) {
            return elt.classList().set(literal(
                    function.apply(value)));
        }
    }

    private static final DynamicAttribute<Object> ID_ATTRIBUTE = new DynamicAttribute<>() {
        @Override
        public Attribute apply(Value<String> id, Value<?> value) {
            return Html.attribute("id", id);
        }

        @Override
        public JsStatement update(JsHtmlElement elt, Value<?> value) {
            return seq();
        }
    };

    /** Construct an instance (which serves as a <em>template</em> you can render multiple times in your
     * pages).
     * @param enumType the enum type.
     * @param base the static part of the element to use as a template.
     */
    public static <D, E extends Enum<E>> EnumViewElementTemplate<D, E> ofStaticBase(
            Class<E> enumType,
            Function<D, Value<?>> getId,
            Function<D, Value<E>> getState,
            Element base) {
        var result = new EnumViewElementTemplate<D, E>(enumType, getId, getState, "", "", __ -> base, new AttributeReplacement<>(base));
        result.attributes.put("id", ID_ATTRIBUTE);
        return result;
    }

    public static <D,E extends Enum<E>> EnumViewElementTemplate<D, E> ofDynamicBase(Class<E> enumType, Function<D, Value<?>> getId, Function<D, Value<E>> getState, ElementRenderer<D> base) {
        var result = new EnumViewElementTemplate<>(enumType, getId, getState, "", "", base, new ExtraAttributes<>());
        if (base instanceof IdentifiedElementRenderer<D> identifiedBase) {
            result = result.withElementKey(identifiedBase.getElementKey());
        }
        result.attributes.put("id", ID_ATTRIBUTE);
        return result;
    }

    /**
     * @param eventKey a string restricting which events this template will react to.
     */
    public EnumViewElementTemplate<D, E> withEventKey(String eventKey) {
        return new EnumViewElementTemplate<>(enumType, getId, getState, eventKey, elementKey, base, attributes.copy());
    }

    /**
     * @param elementKey a string identifying this particular template within the page.
     *            It will be appended to the id of HTML elements.
     */
    public EnumViewElementTemplate<D, E> withElementKey(String elementKey) {
        return new EnumViewElementTemplate<>(enumType, getId, getState, eventKey, elementKey, base, attributes.copy());
    }

    /** Add dynamic styling varying in function of the enum value.
     *
     * @param map a map of enum values to the corresponding CSS class to use.
     * @return this, for chaining
     */
    public EnumViewElementTemplate<D, E> withStyle(Css.EnumToClassName<E> map) {
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
            @SuppressWarnings("unchecked")
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

    public EnumViewElementTemplate<D, E> withContents(DynamicContent<E> contents) {
        this.contents = Optional.of(contents);
        return this;
    }

    /** Set the element to contain raw text varying in function of the enum value.
     *
     * @param renderer a map of enum values to the corresponding CSS class to use.
     * @return this, for chaining
     */
    public EnumViewElementTemplate<D, E> withContents(Function<E, String> renderer) {
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

    /**
     * Register this template into the given state handler. This method should typically be called once
     * from a {@link AbstractPage} subclass constructor or post-construct method. It ensures template instance
     * get properly updated when a change event is dispatched.
     *
     * @param page the client state handler into which to register the change event handler.
     */
    public <P extends DynamicPage<?>> IdentifiedElementRenderer<D> addTo(P page) {
        // Used for element ids. Note that a single event key can be linked to multiple
        // DOM elements, in case a piece of information impacts multiple elements.
        String idPrefix = page.freshElementId(elementKey) + "-";

        page.addHandler((event, callback) -> {
            var wrapped = new JsNewStateEvent(event);
            callback.expect(event.dot("@type").eq(literal(NewStateEvent.class.getSimpleName())));
            callback.expect(wrapped.key().eq(literal(eventKey)));
            return wrapped;
        }, event -> let(getElementById(literal(idPrefix).plus(event.id())),
                JsHtmlElement::new,
                elt -> Stream.concat(
                                contents.stream()
                                        .map(c -> c.update(elt, Value.of(event.newState()))),
                                attributes.update(elt, Value.of(event.newState())))
                        .collect(JavaScript.toSeq())));

        /* Create a renderer */
        return IdentifiedElementRenderer.of(elementKey, value -> {
            Value<E> enumValue = getState.apply(value);
            Value<String> eltId = Value.concat(Value.of(idPrefix), getId.apply(value));
            Element withNewAttributes = attributes.apply(
                    base.render(value),
                    attr -> attr.apply(eltId, enumValue));

            return contents.map(c -> c.apply(enumValue))
                    .map(c -> withNewAttributes.withContents(List.of(c)))
                    .orElse(withNewAttributes);
        });
    }
}
