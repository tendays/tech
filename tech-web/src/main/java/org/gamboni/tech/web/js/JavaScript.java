package org.gamboni.tech.web.js;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import org.gamboni.tech.web.ui.Css;
import org.gamboni.tech.web.ui.Value;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public abstract class JavaScript {

    public static final Joiner COMMA = Joiner.on(", ");
    public static JsExpression _null = s -> "null";

    public static JsExpression literal(Css.ClassName className) {
        return className.name.toExpression();
    }

    public static JsExpression literal(Enum<?> e) {
        return literal(e.name());
    }
    public static JsExpression literal(String text) {
        return JsExpression.of("'" + text.replace("\\", "\\\\")
                .replace("'", "\\'")
                + "'");
    }

    public static JsExpression literal(long value) {
        return s -> String.valueOf(value);
    }
    public static JsExpression literal(boolean value) {
        return s -> String.valueOf(value);
    }

    public static JsStatement _return(JsExpression value) {
        return s -> "return " + value.format(s) + ";";
    }

    public static JsStatement _return() {
        return s -> "return;";
    }

    public record JsGlobal(String name) implements JsExpression {
        public String declare(JsExpression initialValue) {
            return name +" = "+ initialValue +";\n";
        }
        public JsStatement set(JsExpression newValue) {
            return s -> (name +" = "+ newValue +";\n");
        }

        public String toString() {
            return name;
        }

        @Override
        public String format(Scope s) {
            return name;
        }
    }

    public record Fun(String name) {
        public String declare(Supplier<JsFragment> body) {
            return "function " + name + "() {\n" +
                    JsStatement.of(body.get())
                            .format(new Scope()) + "\n" +
                    "}";
        }

        public JsExpression invoke() {
            return s -> name + "()";
        }
    }

    public record Fun1(String name) {
        public String declare(Function<JsExpression,JsFragment> body) {
            String arg = "a";
            return "function " + name + "(" + arg + ") {\n" +
                    JsStatement.of(body.apply(s -> arg))
                            .format(new Scope()) + "\n" +
                    "}";
        }

        public JsExpression invoke(JsExpression value) {
            return s -> name + "(" + value.format(s) + ")";
        }
    }

    public record Fun2(String name) {
        public String declare(BiFunction<JsExpression, JsExpression, JsFragment> body) {
            String arg1 = "a";
            String arg2 = "b";
            return "function " + name + "(" + arg1 +", "+ arg2 + ") {\n" +
                    JsStatement.of(body.apply(JsExpression.of(arg1), JsExpression.of(arg2)))
                            .format(new Scope())+ "\n" +
                    "}";
        }

        public JsExpression invoke(JsExpression value1, JsExpression value2) {
            return s -> name + "(" + value1.format(s) +", "+ value2 .format(s)+ ")";
        }
    }

    public interface JsExpression extends JsFragment {

        JsExpression _this = s -> "this";

        public static JsExpression of(String code) {
            return s -> code;
        }

        default JsExpression plus(JsExpression that) {
            return s -> this.format(s) +"+"+ that.format(s);
        }

        default JsString plus(String that) {
            return new JsString(this.plus(literal(that)));
        }

        default JsExpression dot(String attr) {
            return s -> this.format(s) +"."+ attr;
        }

        default JsStatement set(JsExpression newValue) {
            return s -> this.format(s) + " = " + newValue.format(s) + ";\n";
        }

        default JsExpression invoke(String method, JsExpression... args) {
            return s -> this.format(s) +"."+ method +"("+
                    Stream.of(args)
                            .map(arg -> arg.format(s))
                            .collect(joining(", "))+")";
        }

        default JsExpression eq(JsExpression value) {
            return s -> this.format(s) +" === "+ value.format(s);
        }

        default JsExpression toLowerCase() {
            // TODO bracket (this) if needed. e.g. (x+y).toLowerCase() should produce brackets
            return s -> this.format(s) +".toLowerCase()";
        }

        default JsExpression and(JsExpression rhs) {
            return s -> this.format(s) + " && " + rhs.format(s);
        }
    }

    public static class JsHtmlElement implements JsExpression {
        private final JsExpression code;
        public JsHtmlElement(String code) { this(JsExpression.of(code)); } // needed to work with let()
        public JsHtmlElement(JsExpression code) {
            this.code = code;
        }

        public JsDOMTokenList classList() {
            return new JsDOMTokenList(this.dot("classList"));
        }

        public JsString id() {
            return new JsString(this.dot("id"));
        }

        public JsExpression remove() {
            return this.invoke("remove");
        }

        public JsExpression style() {
            return this.dot("style");
        }

        public JsExpression prepend(JsHtmlElement child) {
            return this.invoke("prepend", child);
        }
        public JsStatement setInnerHtml(JsExpression value) {
            return this.dot("innerHTML").set(value);
        }

        @Override
        public String format(Scope s) {
            return code.format(s);
        }
    }

    public static class JsString implements JsExpression {
        private final JsExpression code;
        public JsString(String code) { this(JsExpression.of(code)); }
        public JsString(JsExpression code) {
            this.code = code;
        }

        public JsString substring(int len) {
            return new JsString(s -> code.format(s) +".substring("+ len +")");
        }
        @Override
        public String format(Scope s) {
            return code.format(s);
        }
    }

    public static class JsDOMTokenList implements JsExpression {
        private final JsExpression code;
        public JsDOMTokenList(String code) { this(JsExpression.of(code)); }
        public JsDOMTokenList(JsExpression code) {
            this.code = code;
        }

        public JsExpression contains(Css.ClassName className) {
            return this.invoke("contains", literal(className));
        }

        public JsString item(int index) {
            return new JsString(this.invoke("item", literal(index)));
        }

        public JsExpression remove(JsExpression item) {
            return this.invoke("remove", item);
        }

        public JsExpression add(JsExpression item) {
            return this.invoke("add", item);
        }
        @Override
        public String format(Scope s) {
            return code.format(s);
        }
    }

    public static JsStatement block(JsFragment... statements) {
        return s -> "{"+ seq(statements).format(s) +"}";
    }

    public static JsStatement seq(JsFragment... statements) {
        return seq(Stream.of(statements));
    }

    public static JsStatement seq(List<? extends JsFragment> statements) {
        return seq(statements.stream());
    }

    private static JsStatement seq(Stream<? extends JsFragment> statements) {
        return s -> statements
                .map(JsStatement::of)
                .map(stm -> stm.format(s))
                .collect(joining());
    }

    public static <T extends JsExpression, U extends T> JsStatement let(T value, Function<String, U> newInstance, Function<U, JsStatement> code) {

        return s -> {
            String var = s.freshVariableName();
            U instance = newInstance.apply(var);
            return "let " + var + " = " + value.format(s) + ";" +
                    code.apply(instance).format(s);
        };

        // not working in Quarkus (T)value.getClass().getDeclaredConstructor(String.class).newInstance(var));
    }

    public static JsExpression lambda(JsStatement body) {
        return s -> ("() => " +
                // TODO don't add braces if it's already a block (define type)
                 "{" + body.format(s) +"}");
    }
    public static JsExpression lambda(JsExpression body) {
        return s -> ("() => " + body.format(s));
    }

    public static JsExpression lambda(String varName, Function<JsExpression, ? extends JsFragment> body) {
        Object bodyValue = body.apply(JsExpression.of(varName));
        String signature = "(" + varName + ") => ";
        if (bodyValue instanceof JsStatement stat) {
            return s -> signature + "{" + stat.format(s) + "}";
        } else if (bodyValue instanceof JsExpression expr) {
            return s -> signature + expr.format(s);
        } else {
            throw new IllegalArgumentException(bodyValue.getClass() +" "+ bodyValue);
        }
    }

    public static JsHtmlElement getElementById(JsExpression id) {
        return new JsHtmlElement(s -> "document.getElementById("+ id.format(s) +")");
    }

    /** Return an expression evaluating to the {@code <body>} element. */
    public static JsHtmlElement getBodyElement() {
        return new JsHtmlElement((s -> "document.getElementsByTagName('body')[0]"));
    }

    public static JsExpression newWebSocket(JsExpression url) {
        return s -> "new WebSocket(" + url.format(s) +")";
    }

    /** The WebSocket class, can be used to access readyState values like OPEN, CLOSED, etc. */
    public static JsExpression WebSocket = s -> "WebSocket";

    public static JsExpression jsonParse(JsExpression text) {
        return s -> "JSON.parse(" + text.format(s) +")";
    }

    public static JsExpression consoleLog(JsExpression expr) {
        return s -> "console.log("+ expr.format(s) +")";
    }

    public static JsExpression setTimeout(JsExpression body, int delay) {
        return s -> "setTimeout(() => " + body.format(s) +", "+ delay +")";
    }

    public static JsExpression newXMLHttpRequest() {
        return s -> "new XMLHttpRequest()";
    }

    public static JsHtmlElement createElement(String tag) {
        return new JsHtmlElement(s -> "document.createElement("+ literal(tag).format(s) +")");
    }
    public static JsHtmlElement createTextNode(String contents) {
        return new JsHtmlElement(s -> "document.createTextNode("+ literal(contents).format(s) +")");
    }
    public static JsHtmlElement createTextNode(Value<String> contents) {
        return new JsHtmlElement(s -> "document.createTextNode("+ contents.toExpression().format(s) +")");
    }

    public static IfBlock _if(JsExpression condition, JsFragment body) {
        return new IfBlock(condition, JsStatement.of(body));
    }

    public interface JsFragment {
        String format(Scope s);
    }

    public interface JsStatement extends JsFragment {
        static JsStatement of(JsFragment obj) {
            if (obj instanceof JsStatement st) {
                return st;
            } else if (obj instanceof JsExpression expr) {
                return  s -> expr.format(s) +";";
            } else {
                throw new IllegalArgumentException(obj.getClass().getName() + " " + obj);
            }
        }
    }

    public static class IfBlock implements JsStatement {
        private final JsExpression condition;
        private final JsStatement body;

        public IfBlock(JsExpression condition, JsStatement body) {
            this.condition = condition;
            this.body = body;
        }

        public IfChain _elseIf(JsExpression condition, JsStatement body) {
            return new IfChain(this, condition, body);
        }

        public IfChain _elseIf(JsExpression condition, JsExpression body) {
            return new IfChain(this, condition, JsStatement.of(body));
        }

        public JsStatement _else(JsFragment body) {
            return s -> this.format(s) +" else "+JsStatement.of(body).format(s);
        }
        @Override
        public String format(Scope s) {
            return "if ("+ condition.format(s) +")"+ body.format(s);
        }
    }

    public static class IfChain extends IfBlock {
        private final IfBlock previous;

        public IfChain(IfBlock previous, JsExpression condition, JsStatement body) {
            super(condition, body);
            this.previous = previous;
        }

        @Override
        public String format(Scope s) {
            return previous.format(s) +" else "+ super.format(s);
        }
    }

    /**
     *
     * @param method HTTP method to use
     * @param url escaped URL
     * @param headers non-escaped header to escaped value
     * @param body escaped http body
     * @param callback escaped callback
     * @return code sending the request
     */
    public static String http(String method, String url, Map<String, String> headers, JsExpression body, Function<JsString, String> callback/*, Function<>*/) {
        String var = "x";
        return "let "+ var +" = new XMLHttpRequest();" +
                var +".onload = () => {" +
                callback.apply(new JsString(var+".responseText")) +
                "};" +
                var +".open("+ literal(method)+", "+ url +");" +
                headers.entrySet().stream().map(header -> var +".setRequestHeader("+ literal(header.getKey())+", "+ header.getValue() +");")
                        .collect(joining())+
                var +".send("+ body +");";
    }

    public static JsExpression obj(Map<String, JsExpression> values) {
        return s -> "{"+ values.entrySet().stream().map(e ->
                literal(e.getKey()).format(s) +":"+
                        e.getValue().format(s))
                .collect(joining(",")) +
                "}";
    }

    public static JsExpression obj(String k1, JsExpression v1, String k2, JsExpression v2) {
        return obj(ImmutableMap.of(k1, v1, k2, v2));
    }

    public static JsExpression array() {
        return s -> "[]";
    }

    public static class Scope {
        /** A special Scope which does not allow declaring variables. */
        public static final Scope NO_DECLARATION = new Scope() {
            public String freshVariableNam() {
                throw new IllegalStateException("Variable declaration not allowed here");
            }
        };
        private int next = 0;
        public String freshVariableName() {
            return "v" + (next++);
        }
    }
}
