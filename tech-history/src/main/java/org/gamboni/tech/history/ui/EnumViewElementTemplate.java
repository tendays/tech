package org.gamboni.tech.history.ui;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import lombok.RequiredArgsConstructor;
import org.gamboni.tech.history.ClientStateHandler;
import org.gamboni.tech.history.event.NewStateEvent;
import org.gamboni.tech.history.event.NewStateEventValues;
import org.gamboni.tech.web.js.JavaScript;
import org.gamboni.tech.web.ui.AbstractPage;
import org.gamboni.tech.web.ui.Css;
import org.gamboni.tech.web.ui.Element;
import org.gamboni.tech.web.ui.ElementRenderer;
import org.gamboni.tech.web.ui.Html;
import org.gamboni.tech.web.ui.Html.Attribute;
import org.gamboni.tech.web.ui.HtmlFragment;
import org.gamboni.tech.web.ui.IdentifiedElementRenderer;
import org.gamboni.tech.web.ui.Renderer;
import org.gamboni.tech.web.ui.value.StringValue;
import org.gamboni.tech.web.ui.value.Value;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static lombok.AccessLevel.PRIVATE;
import static org.gamboni.tech.web.js.JavaScript.EMPTY_IF_CHAIN;
import static org.gamboni.tech.web.js.JavaScript.JsExpression;
import static org.gamboni.tech.web.js.JavaScript.JsHtmlElement;
import static org.gamboni.tech.web.js.JavaScript.JsStatement;
import static org.gamboni.tech.web.js.JavaScript.getElementById;
import static org.gamboni.tech.web.js.JavaScript.let;
import static org.gamboni.tech.web.js.JavaScript.literal;
import static org.gamboni.tech.web.js.JavaScript.seq;
import static org.gamboni.tech.web.js.JavaScript.toSeq;
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
    private final BiFunction<JsExpression, ClientStateHandler.MatchCallback, EventData> eventMatcher;
    private final String elementKey;
    private final ElementRenderer<D> base;
    private final StyleAttribute<E> style;
    private Optional<DynamicContent<E>> contents = Optional.empty();

    private interface StyleAttribute<E> extends DynamicAttribute<E> {
        StyleAttribute<E> add(Function<? super Value<? extends E>, Css.ClassName> enumToStyle);
    }

    private record ConstantStyle<E>(Css.ClassList constantStyle) implements StyleAttribute<E> {
        @Override
        public StyleAttribute<E> add(Function<? super Value<? extends E>, Css.ClassName> enumToStyle) {
            return new FunctionStyle<>(val -> constantStyle.and(enumToStyle.apply(val)));
        }

        @Override
        public Element apply(Element base, Value<String> id, Value<? extends E> value) {
            // technically, we could just return 'base' as-is, but it's safer to override.
            // For instance in the future we might support adding constant styles, and it
            // would then be mandatory to override the attribute in this method.
            return base.withAttribute(constantStyle);
        }
    }
    private record FunctionStyle<E>(Function<? super Value<? extends E>, Css.ClassList> function) implements StyleAttribute<E> {
        @Override
        public StyleAttribute<E> add(Function<? super Value<? extends E>, Css.ClassName> enumToStyle) {
            return new FunctionStyle<>(val ->
                    function.apply(val)
                            .and(enumToStyle.apply(val)));
        }

        @Override
        public Element apply(Element base, Value<String> id, Value<? extends E> value) {
            return base.withAttribute(function.apply(value));
        }

        @Override
        public JsStatement update(JsHtmlElement elt, Value<? extends E> value, Class<E> enumType) {
            return elt.classList().set(literal(
                    function.apply(value)));
        }
    }
    private record PreserveStyle<E extends Enum<E>>() implements StyleAttribute<E> {
        @Override
        public StyleAttribute<E> add(Function<? super Value<? extends E>, Css.ClassName> enumToStyle) {
            return new ExtendStyle<>(v -> enumToStyle.apply(v));
        }
    }
    private record ExtendStyle<E extends Enum<E>>(Function<? super Value<? extends E>, Css.ClassList> function) implements StyleAttribute<E> {
        @Override
        public StyleAttribute<E> add(Function<? super Value<? extends E>, Css.ClassName> enumToStyle) {
            return new ExtendStyle<>(val ->
                    function.apply(val)
                            .and(enumToStyle.apply(val)));
        }

        @Override
        public Element apply(Element base, Value<String> id, Value<? extends E> value) {
            Css.ClassList extraClasses = function.apply(value);

            return base.withAttribute(base.getAttribute("class")
                    // if there are already classes in 'base', add the dynamic ones:
                    .map(baseClass -> extraClasses.addTo((Css.ClassList) baseClass))
                    // if there's no 'class' attribute in 'base', just return the dynamic ones:
                    .orElse(extraClasses));
        }

        @Override
        public JsStatement update(JsHtmlElement elt, Value<? extends E> value, Class<E> enumType) {
            // TODO 1. collect all possible classes, excluding those always generated by function
            // 2. generate code to remove those classes
            // 3. loop apply the function to put the correct one back (still excluding always-generated ones)


            // TODO use something like this when the object was created using ofDynamicBase
            /** Generate code updating the given class list to reflect the state in the jsPlayState variable. */
            //public JavaScript.JsStatement jsApplyStyle(/*Style style, */JavaScript.JsDOMTokenList classList, JavaScript.JsExpression jsPlayState) {
            Multimap<Css.ClassName, E> cases = LinkedHashMultimap.create();

            for (E ps : enumType.getEnumConstants()) {
                for (Css.ClassName css : function.apply(Value.of(ps))) {
                    cases.put(css, ps);
                }
            }

            var classList = elt.classList();

            return cases.asMap()
                    .entrySet()
                    .stream()
                    .reduce(EMPTY_IF_CHAIN,
                            (chain, entry) -> chain._elseIf(isOneOf(value, entry.getValue()),
                                    /* Remove other classes */
                                    cases.keySet()
                                            .stream()
                                            .filter(thatClass -> !thatClass.equals(entry.getKey()))
                                            .map(classList::remove)
                                            .collect(toSeq()),

                                    classList.add(entry.getKey())),
                            (x, y) -> {
                                throw new UnsupportedOperationException();
                            })

                    ._else(cases.keySet()
                            .stream()
                            .map(classList::remove));
        }
    }

        interface DynamicAttribute<E> {
            /** Return the attribute to apply to the given element. Return an empty Optional to
             * leave the current value as-is (to <em>remove</em> an attribute, return a {@linkplain
             * Attribute#isTrivial() trivial} one).
             *
             * @param base the element on which the returned attribute will be added. You may use this information
             *             to <em>modify</em> an existing attribute value.
             * @param id the id of the entity being represented by the object.
             * @param value the current state of the entity.
             * @return an optional attribute to add to the element.
             */
            default Element apply(Element base, Value<String> id, Value<? extends E> value) {
                return base;
            }

            default JsStatement update(JsHtmlElement elt, Value<? extends E> value, Class<E> enumType) {
                return seq();
            }
        }

        private static BiFunction<JsExpression, ClientStateHandler.MatchCallback, EventData> defaultEventMatcher(String eventKey) {
        return (event, callback) -> {
            var wrapped = callback.expectSameType(NewStateEventValues.of(event));
            callback.expect(wrapped.key().eq(literal(eventKey)));
            return new EventData(wrapped.id(), Value.of(wrapped.newState()));
        };
    }

    public interface DynamicContent<E> {
        HtmlFragment apply(Value<E> value);

        JsStatement update(JsHtmlElement elt, Value<E> value, Class<E> enumType);
    }

                            private static final DynamicAttribute<Object> ID_ATTRIBUTE = new DynamicAttribute<Object>() {
                                @Override
                                public Element apply(Element base, Value<String> id, Value<?> value) {
                                    return base.withAttribute(Html.attribute("id", id));
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
        return new EnumViewElementTemplate<>(enumType, getId, getState,
                defaultEventMatcher(""),
                "", __ -> base,
                new ConstantStyle<>(
                        base.getAttribute("class").map(attr -> (Css.ClassList) attr)
                                .orElse(Css.ClassList.EMPTY)
                ));
    }

    public static <D,E extends Enum<E>> EnumViewElementTemplate<D, E> ofDynamicBase(Class<E> enumType, Function<D, Value<?>> getId, Function<D, Value<E>> getState, ElementRenderer<D> base) {
        var result = new EnumViewElementTemplate<>(enumType, getId, getState,
                defaultEventMatcher(""),
                "", base,
                new PreserveStyle<>());
        if (base instanceof IdentifiedElementRenderer<D> identifiedBase) {
            result = result.withElementKey(identifiedBase.getElementKey());
        }
        return result;
    }

    /**
     * @param eventKey a string restricting which events this template will react to.
     */
    public EnumViewElementTemplate<D, E> withEventKey(String eventKey) {
        return new EnumViewElementTemplate<>(enumType, getId, getState,
                defaultEventMatcher(eventKey),
                elementKey, base, style);
    }

    public record EventData(JsExpression id, Value<? extends Enum<?>> state) {}

    /**
     * @param eventMatcher a replacement matcher flagging events this template should react to.
     */
    public EnumViewElementTemplate<D, E> withEventMatcher(BiFunction<JsExpression, ClientStateHandler.MatchCallback, EventData> eventMatcher) {
        return new EnumViewElementTemplate<>(enumType, getId, getState, eventMatcher, elementKey, base, style);
    }

    /**
     * @param elementKey a string identifying this particular template within the page.
     *            It will be appended to the id of HTML elements.
     */
    public EnumViewElementTemplate<D, E> withElementKey(String elementKey) {
        return new EnumViewElementTemplate<>(enumType, getId, getState, eventMatcher, elementKey, base, style);
    }

    /** Add dynamic styling varying in function of the enum value.
     *
     * @param map a map of enum values to the corresponding CSS class to use.
     * @return this, for chaining
     */
    public EnumViewElementTemplate<D, E> withStyle(Css.EnumToClassName<E> map) {
        return new EnumViewElementTemplate<>(enumType, getId, getState, eventMatcher, elementKey, base,
                this.style.add(map::get));
    }


    private static JavaScript.JsExpression isOneOf(JavaScript.JsExpression value, Collection<? extends Enum<?>> collection) {
        return collection.stream()
                .map(value::eq)
                .reduce(JavaScript.JsExpression::or)
                .orElseThrow();
    }

    public EnumViewElementTemplate<D, E> withContents(DynamicContent<E> contents) {
        // TODO it's weird that this one field is non-final
        this.contents = Optional.of(contents);
        return this;
    }

    /** Set the element to contain raw text varying in function of the enum value.
     *
     * @param renderer a map of enum values to the corresponding CSS class to use.
     * @return this, for chaining
     */
    public EnumViewElementTemplate<D, E> withContents(Function<E, String> renderer) {
        Map<E, StringValue> formats = Stream.of(enumType.getEnumConstants())
                .collect(toMap(
                        e -> e,
                        e -> StringValue.of(renderer.apply(e))
                ));
        return this.withContents(new DynamicContent<E>() {
            @Override
            public HtmlFragment apply(Value<E> value) {
                return escape(renderer.apply(value.assertStatic()));
            }

            @Override
            public JsStatement update(JsHtmlElement elt, Value<E> value, Class<E> enumType) {
                return
                        // if 'value' is constant: directly set the corresponding text
                        value.constantValue().map(constantValue ->
                                        Optional.ofNullable(formats.get(constantValue))
                                                .map(elt::setInnerText)
                                                .orElse(seq()))
                                // if 'value' is not constant, generate an if-else-if chain
                                .orElseGet(() ->
                                        formats.entrySet()
                                                .stream()
                                                .reduce(EMPTY_IF_CHAIN,
                                                        (chain, entry) ->
                                                                chain._elseIf(value.eq(entry.getKey()),
                                                                        elt.setInnerText(entry.getValue())),
                                                        (__, ___) -> {
                                                            throw new UnsupportedOperationException();
                                                        }));
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

        page.addHandler(eventMatcher, event -> let(getElementById(literal(idPrefix).plus(event.id())),
                JsHtmlElement::new,
                elt -> Stream.concat(
                                contents.stream()
                                        .map(c -> c.update(elt, Value.of(event.state()), enumType)),
                                Stream.of(style.update(elt, Value.of(event.state()), enumType)))
                        .collect(JavaScript.toSeq())));

        /* Create a renderer */
        return IdentifiedElementRenderer.of(elementKey, value -> {
            Value<E> enumValue = getState.apply(value);
            Value<String> eltId = Value.of(idPrefix).plus(getId.apply(value));
            Element withNewAttributes =
                    ID_ATTRIBUTE.apply(
                    style.apply(
                    base.render(value),
                    eltId,
                    enumValue), eltId, enumValue);

            return contents.map(c -> c.apply(enumValue))
                    .map(c -> withNewAttributes.withContents(List.of(c)))
                    .orElse(withNewAttributes);
        });
    }
}
