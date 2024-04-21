package org.gamboni.tech.web.ui;

import com.google.common.base.CaseFormat;
import com.google.common.reflect.Reflection;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author tendays
 */
public abstract class Css implements Resource {

    public record EnumToClassName<T extends Enum<T>>(Class<T> enumType) {
        // Maps.toMap(Arrays.asList(State.values()),
        //            s -> new ClassName(s.name().toLowerCase()));
        public Stream<ClassName> valueStream() {
            return Stream.of(enumType.getEnumConstants())
                    .map(this::get);
        }

        public ClassName get(T key) {
            return new ClassName(key.name().toLowerCase());
        }

        public ClassName get(Value<T> key) {
            if (key instanceof Value.Constant<T> cst) {
                return get(cst.getConstantValue());
            } else {
                return new ClassName(Value.of(key.toExpression().toLowerCase()));
            }
        }
    }

    public String getUrl() {
        return "/"+ CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, getClass().getSimpleName().toLowerCase()) +".css";
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
                Stream.of(attributes).map(Objects::toString).collect(Collectors.joining())
                +"}\n";
    }

    /** A Property factory */
    public interface Properties {
        // TODO later: make Css be abstract and implement Properties instead
        Properties INSTANCE = Reflection.newProxy(Properties.class, (proxy, method, args) ->
                new Property(
                        CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN,
                                method.getName().startsWith("_")? method.getName().substring(1) : method.getName()),
                        args[0].toString())
        );

        Property backgroundColor(String value);
        Property border(String value);
        Property borderBottomRightRadius(String value);
        Property color(String value);
        Property content(String value);
        Property cursor(String cursor);
        Property display(String value);
        Property filter(String value);
        Property _float(String value);
        Property fontFamily(String value);
        Property fontSize(String value);
        Property fontVariantCaps(String value);
        Property fontWeight(String value);
        Property height(String value);
        Property left(String value);
        Property margin(String margin);
        Property marginTop(String value);
        Property maxWidth(String value);
        Property overflow(String value);
        Property padding(String value);
        Property position(String value);
        Property right(String value);
        Property top(String value);
        Property width(String value);
        Property zIndex(int value);
    }

    /** A css property (Something:something;) */
    protected static class Property {
        public final String name;
        public final String value;

        public Property(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public static Property width(String value) {
            return new Property("width", value);
        }

        public String toString() {
            return "  "+ name +": "+ value +";\n";
        }
    }

    public interface Selector {

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

        /** "this that" */
        default Selector child(Selector that) {
            return () -> this.renderSelector() +" "+ that.renderSelector();
        }

        /** "this::before" */
        default Selector before() {
            return () -> this.renderSelector() +"::before";
        }
    }

    public static class ClassName implements Selector, Html.Attribute {
        public final Value<String> name;
        public ClassName(String name) {
            this.name = Value.of(name);
        }
        public ClassName(Value<String> name) {
            this.name = name;
        }

        public String renderSelector() {
            return "."+ name.assertStatic();
        }

        public String toString() {
            return this.name instanceof Value.Constant<String> cst ? cst.getConstantValue() :
                    name.toExpression().toString();
        }

        @Override
        public String getAttributeName() {
            return "class";
        }

        @Override
        public Value<String> getAttributeValue() {
            return name;
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
