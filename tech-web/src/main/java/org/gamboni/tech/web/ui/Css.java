package org.gamboni.tech.web.ui;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.reflect.AbstractInvocationHandler;
import com.google.common.reflect.Reflection;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.gamboni.tech.web.js.JavaScript;
import org.gamboni.tech.web.ui.value.RatioValue;
import org.gamboni.tech.web.ui.value.StringValue;
import org.gamboni.tech.web.ui.value.Value;

import javax.annotation.CheckForNull;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.CaseFormat.*;
import static org.gamboni.tech.web.js.JavaScript.array;
import static org.gamboni.tech.web.js.JavaScript.literal;

/**
 * @author tendays
 */
public abstract class Css implements Resource {

    public interface EnumToClassName<T extends Enum<T>> {

        ClassName get(T key);

        ClassName get(Value<? extends T> key);


        public static <T extends Enum<T>> PageMember<Object, EnumToClassName<T>> forFunction(Class<T> enumType, Function<T, ClassName> fun) {

            Multimap<ClassName, T> values =
                    Multimaps.index(Arrays.asList(enumType.getEnumConstants()),
                    fun::apply);

            return page -> {

                var classNames = new JavaScript.JsGlobal(page.freshGlobal(
                        UPPER_CAMEL.to(LOWER_CAMEL, enumType.getSimpleName()) +"Class"));

                // Object.fromEntries(['a', 'b', 'c'].map(k => [k, 'toto']).concat(['d', 'e'].map(k => [k, 'titi'])))

                page.addToScript(classNames.declare(
                        JavaScript._Object.invoke("fromEntries",
                                values.asMap()
                                        .entrySet()
                                        .stream()
                                        .map(entry -> entry.getValue()
                                                .stream()
                                                .map(JavaScript::literal)
                                                .collect(JavaScript.toArray())
                                                .invoke("map", JavaScript.lambda("k", k -> array(k, literal(entry.getKey())))))
                                        .reduce((arrayLeft, arrayRight) -> arrayLeft.invoke("concat", arrayRight))
                                        .orElseThrow(() -> new IllegalArgumentException("Given enum type " + enumType.getSimpleName() +" should have at least one value")))));

                return new EnumToClassName<>() {
                    @Override
                    public ClassName get(T key) {
                        return fun.apply(key);
                    }

                    @Override
                    public ClassName get(Value<? extends T> key) {
                        return key.constantValue()
                                .map(constantKey ->
                                        this.get(constantKey))
                                .orElseGet(() ->
                                        new ClassName(StringValue.of(classNames.arrayGet(key))));
                    }
                };
            };
        }
    }

    public record OneCssClassPerEnumValue<T extends Enum<T>>(Class<T> enumType) implements EnumToClassName<T> {
        public Stream<ClassName> valueStream() {
            return Stream.of(enumType.getEnumConstants())
                    .map(this::get);
        }

        @Override
        public ClassName get(T key) {
            return new ClassName(key.name().toLowerCase());
        }

        @Override
        public ClassName get(Value<? extends T> key) {
            return key.constantValue()
                    .map(cst -> get(cst))
                    .orElseGet(() -> new ClassName(StringValue.of(key).toLowerCase()));
        }
    }

    public String getUrl() {
        return "/"+ UPPER_CAMEL.to(LOWER_HYPHEN, getClass().getSimpleName().toLowerCase()) +".css";
    }

    @Override
    public Html asElement() {
        return new Element("link", List.of(Html.attribute("rel", "stylesheet"),
                Html.attribute("href", getUrl())));
    }

    @Override
    public String getMime() {
        return "text/css";
    }

    protected String rule(Selector selector, Property... attributes) {
        return selector.renderSelector() +" {\n" +
                Stream.of(attributes).map(Property::render).collect(Collectors.joining())
                +"}\n";
    }

    public enum Cursor implements Property.AsEnum<Cursor> {
        POINTER
    }

    public enum Display implements Property.AsEnum<Display> {
        NONE,
        BLOCK,
        INLINE_BLOCK
    }

    public enum CssFloat implements Property.AsEnum<CssFloat> {
        LEFT, RIGHT;

        @Override
        public String key() {
            return "float";
        }
    }

    public enum FontFamily implements Property.AsEnum<FontFamily> {
        SANS, SERIF
    }

    public enum FontStyle implements Property.AsEnum<FontStyle> {
        ITALIC, NORMAL
    }
    public enum FontSynthesis implements Property.AsEnum<FontSynthesis> {
        NONE
    }

    public enum FontVariantCaps implements Property.AsEnum<FontVariantCaps> {
        SMALL_CAPS
    }

    public enum ObjectFit implements Property.AsEnum<ObjectFit> {
        COVER
    }

    public enum Overflow implements Property.AsEnum<Overflow> {
        HIDDEN
    }

    public enum Position implements Property.AsEnum<Position> {
        ABSOLUTE,
        FIXED,
        RELATIVE
    }

    public enum TextAlign implements Property.AsEnum<TextAlign> {
        LEFT, CENTER, RIGHT
    }

    public enum TextDecoration implements Property.AsEnum<TextDecoration> {
        NONE
    }

    public enum VerticalAlign implements Property.AsEnum<VerticalAlign> {
        TOP
    }

    public enum WhiteSpace implements Property.AsEnum<WhiteSpace> {
        NOWRAP
    }

    private static String formatPropertyValue(String name, Object value) {
        if (name.equals("content")) {
            return "\"" + value +"\"";
        } else {
            return value.toString();
        }
    }

    /** A Property factory */
    public interface Properties {
        // TODO later: make Css be abstract and implement Properties instead
        Properties INSTANCE = Reflection.newProxy(Properties.class, (proxy, method, args) ->
                (args[0] instanceof Property) ? args[0] :
                new Property.AsRecord(
                        UPPER_CAMEL.to(LOWER_HYPHEN,
                                method.getName().startsWith("_")? method.getName().substring(1) : method.getName()),
                        formatPropertyValue(method.getName(), args[0]))
        );

        /** Vertical aligment in Flex containers. */
        Property alignItems(String value);
        Property backgroundColor(String value);
        Property border(String value);
        Property borderBottomRightRadius(String value);
        Property borderLeft(String value);
        Property borderRadius(String value);
        Property color(String value);
        Property content(String value);
        Property cursor(Cursor cursor);
        Property display(Display value);
        Property filter(String value);
        Property _float(CssFloat value);
        Property fontFamily(FontFamily value);
        Property fontSize(String value);
        Property fontStyle(FontStyle value);
        Property fontSynthesis(FontSynthesis value);
        Property fontVariantCaps(FontVariantCaps value);
        Property fontWeight(String value);
        Property height(String value);
        /** Horizontal alignment in Flex containers. */
        Property justifyContent(String value);
        Property left(String value);
        Property margin(String margin);
        Property marginBottom(String value);
        Property marginTop(String value);
        Property maxHeight(String value);
        Property maxWidth(String value);
        /** E.g. objectFit("cover") to enlarge images to fit some size. */
        Property objectFit(ObjectFit value);
        Property overflow(Overflow value);
        Property padding(String value);
        Property position(Position value);
        Property right(String value);
        Property textAlign(TextAlign value);
        Property textDecoration(TextDecoration value);
        Property top(String value);
        Property verticalAlign(VerticalAlign value);
        Property whiteSpace(WhiteSpace value);
        Property width(String value);
        Property zIndex(int value);
    }

    /** A css property (Something:something;) */
    public interface Property {
        String key();
        String value();

        default String render() {
            return "  "+ key() +": "+ value() +";\n";
        }

        record AsRecord(String key, String value) implements Property {}

        interface AsEnum<E extends Enum<E>> extends Property {
            String name(); // implemented by Enum
            default String key() {
                return UPPER_CAMEL.to(LOWER_HYPHEN, getClass().getSimpleName());
            }

            default String value() {
                return UPPER_UNDERSCORE.to(LOWER_HYPHEN, name());
            }
        }
    }

    /** Like {@link Property} but the value is a {@link StringValue} instead of
     * a plain {@code String}.
     *
     * @apiNote this may eventually replace the {@link Property} interface completely
     */
    public interface PropertyValue {
        String key();
        StringValue value();

        default JavaScript.JsStatement apply(JavaScript.JsHtmlElement element) {
            return element.style().dot(LOWER_HYPHEN.to(LOWER_CAMEL, key())).set(value());
        }

        default String render() {
            return key() +":"+ value().assertStatic() +";";
        }

        record AsRecord(String key, StringValue value) implements PropertyValue {}
    }

    /** Like {@link Properties} but the methods take and return {@code Values}.
     *
     * @apiNote this may eventually replace the {@link Properties} interface completely
     */
    public interface PropertyValues {
        PropertyValues INSTANCE = Reflection.newProxy(PropertyValues.class,
                new AbstractInvocationHandler() {
                    @CheckForNull
                    @Override
                    protected Object handleInvocation(Object proxy, Method method, @Nullable Object[] args) throws Throwable {
                        StringValue value;
                        if (args[0] instanceof RatioValue ratio) {
                            value = ratio.toPercentageString();
                        } else if (args[0] instanceof String string) {
                            value = Value.of(string);
                        } else {
                            throw new IllegalArgumentException(args[0].getClass().toString());
                        }

                        return new PropertyValue.AsRecord(
                                UPPER_CAMEL.to(LOWER_HYPHEN,
                                        method.getName().startsWith("_") ? method.getName().substring(1) : method.getName()),
                                value);
                    }

                    @Override
                    public String toString() {
                        return "Css.PropertyValues.INSTANCE";
                    }
                }
        );

        PropertyValue width(RatioValue width);
        PropertyValue width(String width);
    }

    public interface Selector {
        /** Matches elements with the given tag name. */
        static Selector ofTag(String tagName) {
            return () -> tagName;
        }

        Selector NOTHING = new Selector() {
            @Override
            public String renderSelector() {
                throw new UnsupportedOperationException();
            }

            public Selector or(Selector that) {
                return that;
            }
        };

        String renderSelector();

        /** this, that */
        default Selector or(Selector that) {
            return () -> this.renderSelector() +", "+ that.renderSelector();
        }

        /** thisthat (warning, might not work with complex selectors) */
        default Selector and(Selector that) {
            return () -> this.renderSelector() + that.renderSelector();
        }

        /** "this that" */
        default Selector child(Selector that) {
            return () -> this.renderSelector() +" "+ that.renderSelector();
        }

        /** "this::after" */
        default Selector after() {
            return () -> this.renderSelector() +"::after";
        }

        /** "this::before" */
        default Selector before() {
            return () -> this.renderSelector() +"::before";
        }

        /** "this:empty" */
        default Selector empty() {
            return () -> this.renderSelector() +":empty";
        }

        /** "this:not(:empty)" */
        default Selector notEmpty() {
            return () -> this.renderSelector() +":not(:empty)";
        }
    }

    public interface ClassList extends Html.Attribute, Iterable<ClassName> {
        ClassList EMPTY = new MultiClass(List.of());

        @Override default String getAttributeName() {
            return "class";
        }

        ClassList and(ClassName that);

        ClassList addTo(ClassList that);
    }

    private static class MultiClass implements ClassList {
        private final ImmutableList<StringValue> names;

        private MultiClass(List<StringValue> names) {
            this.names = ImmutableList.copyOf(names);
        }

        @Override
        public ClassList and(ClassName that) {
            return new MultiClass(ImmutableList.<StringValue>builder()
                    .addAll(this.names)
                    .add(that.name)
                    .build());
        }

        @Override
        public ClassList addTo(ClassList that) {
            return names.stream()
                    .reduce(that,
                            (list, name) ->
                            list.and(new ClassName(name)),
                            (__, ___) -> {
                        throw new UnsupportedOperationException();
                            });
        }

        @Override
        public StringValue getAttributeValue() {
            return names
                    .stream()
                    .reduce((l, r) ->
                                            l.plus(Value.of(" ")).plus(r))
                    .orElse(Value.of(""));

            // Note that we don't pass Value.of("") as 'identity' parameter of reduce()
            // because currently concat(of(""), x) will return ("" + x) instead of just (x) even if, like here,
            // we know that x is always a String. See comment in Value.concat()
        }

        @Override
        public boolean isTrivial() {
            return names.isEmpty();
        }

        @Override
        public Iterator<ClassName> iterator() {
            return Iterators.transform(
                    names.iterator(),
                    ClassName::new);
        }

        @Override
        public int hashCode() {
            return names.hashCode();
        }

        @Override
        public boolean equals(Object that) {
            return (that instanceof MultiClass mc) && mc.names.equals(this.names);
        }
    }

    public static class ClassName implements Selector, ClassList {
        public final StringValue name;
        public ClassName(String name) {
            this.name = Value.of(name);
        }
        public ClassName(StringValue name) {
            this.name = name;
        }

        public String renderSelector() {
            return "."+ name.assertStatic();
        }

        public String toString() {
            return this.name.toString();
        }

        @Override
        public StringValue getAttributeValue() {
            return name;
        }

        /** Combine this class name with another one, allowing to set multiple class names on a single element. */
        @Override
        public ClassList and(ClassName that) {
            return new MultiClass(ImmutableList.of(this.name, that.name));
        }

        @Override
        public ClassList addTo(ClassList that) {
            return that.and(this);
        }

        @Override
        public Iterator<ClassName> iterator() {
            return Iterators.singletonIterator(this);
        }

        public int hashCode() {
            return name.hashCode();
        }

        public boolean equals(Object that) {
            return (that instanceof ClassName cn) && cn.name.equals(this.name);
        }
    }

    {
        try {
            for (Field f : getClass().getDeclaredFields()) {
                if (f.getType() == ClassName.class && f.get(this) == null) {
                    f.set(this, new ClassName(f.getName()));
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
