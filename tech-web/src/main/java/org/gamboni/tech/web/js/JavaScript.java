package org.gamboni.tech.web.js;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import org.gamboni.tech.web.ui.Css;
import org.gamboni.tech.web.ui.ScriptMember;
import org.gamboni.tech.web.ui.Value;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public abstract class JavaScript {

    public static final Joiner COMMA = Joiner.on(", ");
    public static JsExpression _null = s -> "null";

    public static JsExpression literal(Css.ClassList className) {
        return className.getAttributeValue().toExpression();
    }

    public static JsExpression literal(Enum<?> e) {
        return literal(e.name());
    }
    public static JsString literal(String text) {
        return new JsString(s -> "'" + text.replace("\\", "\\\\")
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
        public ScriptMember declare(JsExpression initialValue) {
            return () -> name +" = "+ initialValue.format(new Scope()) +";\n";
        }
        public ScriptMember declare(long initialValue) {
            return declare(literal(initialValue));
        }
        public JsStatement set(JsExpression newValue) {
            return s -> (name +" = "+ newValue.format(s) +";\n");
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
        public ScriptMember declare(JsFragment body) {
            return () -> "function " + name + "() {\n" +
                    JsStatement.of(body)
                            .format(new Scope()) + "\n" +
                    "}";
        }

        public JsExpression invoke() {
            return s -> name + "()";
        }
    }

    public record Fun1(String name) {
        public ScriptMember declare(Function<JsExpression,JsFragment> body) {
            String arg = "a";
            return () -> "function " + name + "(" + arg + ") {\n" +
                    JsStatement.of(body.apply(s -> arg))
                            .format(new Scope()) + "\n" +
                    "}";
        }

        public JsExpression invoke(JsExpression value) {
            return s -> name + "(" + value.format(s) + ")";
        }
    }

    public record Fun2(String name) {
        public ScriptMember declare(BiFunction<JsExpression, JsExpression, JsFragment> body) {
            String arg1 = "a";
            String arg2 = "b";
            return () -> "function " + name + "(" + arg1 +", "+ arg2 + ") {\n" +
                    JsStatement.of(body.apply(JsExpression.of(arg1), JsExpression.of(arg2)))
                            .format(new Scope())+ "\n" +
                    "}";
        }

        public JsExpression invoke(JsExpression value1, JsExpression value2) {
            return s -> name + "(" + value1.format(s) +", "+ value2 .format(s)+ ")";
        }
    }

    @RequiredArgsConstructor
    public static class FunN {
        private final String name;
        private final List<String> parameters = new ArrayList<>();
        public String addParameter() {
            String parameter = "p" + (parameters.size());
            this.parameters.add(parameter);
            return parameter;
        }
        public ScriptMember declare(Function<Map<String, JsExpression>, JsFragment> body) {
            return () -> {
                // to support adding parameters after declaration, create the map at actual render time.
                Map<String, JsExpression> paramMap = parameters.stream()
                        .collect(Collectors.toMap(varName -> varName, JsExpression::of));
                return "function " + name + "(" +
                    String.join(", ", parameters) + ") {\n" +
                    JsStatement.of(body.apply(paramMap))
                            .format(new Scope())+ "\n" +
                    "}";};
        }

        public JsExpression invoke(Map<String, JsExpression> paramValues) {
            return s -> name + "(" +
                    parameters.stream().map(paramValues::get)
                            .peek(Preconditions::checkNotNull)
                            .map(expr -> expr.format(s))
                            .collect(joining(", ")) + ")";
        }
    }

    public enum Precedence {
        ATOM, MULTIPLICATION, ADDITION, CONJUNCTION, DISJUNCTION, ASSIGNMENT
    }

    public enum StatementPrecedence {
        BLOCK, CONTROL_STRUCTURE, SEQUENCE
    }

    /** A {@link JsExpression} that defaults precedence to CONJUNCTION.
     * This interface exists to allow the use of lambda for non-atoms.
     */
    private interface JsAssignment extends JsExpression {
        @Override
        default Precedence getPrecedence() {
            return Precedence.ASSIGNMENT;
        }
    }

    /** A {@link JsExpression} that defaults precedence to CONJUNCTION.
     * This interface exists to allow the use of lambda for non-atoms.
     */
    private interface JsDisjunction extends JsExpression {
        @Override
        default Precedence getPrecedence() {
            return Precedence.DISJUNCTION;
        }
    }

    /** A {@link JsExpression} that defaults precedence to CONJUNCTION.
     * This interface exists to allow the use of lambda for non-atoms.
     */
    private interface JsConjunction extends JsExpression {
        @Override
        default Precedence getPrecedence() {
            return Precedence.CONJUNCTION;
        }
    }

    /** A {@link JsExpression} that defaults precedence to ADDITION.
     * This interface exists to allow the use of lambda for non-atoms.
     */
    private interface JsAddition extends JsExpression {
        @Override
        default Precedence getPrecedence() {
            return Precedence.ADDITION;
        }
    }

    /** A {@link JsExpression} that defaults precedence to MULTIPLICATION.
     * This interface exists to allow the use of lambda for non-atoms.
     */
    private interface JsMultiplication extends JsExpression {
        @Override
        default Precedence getPrecedence() {
            return Precedence.MULTIPLICATION;
        }
    }

    public interface JsExpression extends JsFragment {

        default Precedence getPrecedence() {
            return Precedence.ATOM;
        }

        JsExpression _this = s -> "this";
        JsExpression _null = s -> "null";
        JsExpression _undefined = s -> "undefined";

        public static JsExpression of(String code) {
            return s -> code;
        }

        default JsExpression plus(JsExpression that) {
            return (JsAddition) s -> this.format(s) +"+"+ that.format(s);
        }

        default JsString plus(String that) {
            return new JsString(this.plus(literal(that)));
        }

        default JsExpression minus(JsExpression that) {
            // Note rhs must have at most MULTIPLICATION precedence. If we pass an addition/subtraction it needs brackets.
            // Because x-(y-z) ≠ x-y-z

            // lhs is ADDITION because e.g. (x-y)-z = x-y-z.
            return (JsAddition) s -> this.format(s, Precedence.ADDITION) +"-"+ that.format(s, Precedence.MULTIPLICATION);
        }

        default JsExpression times(JsExpression that) {
            return (JsMultiplication) s -> this.format(s, Precedence.MULTIPLICATION) +"*"+ that.format(s, Precedence.MULTIPLICATION);
        }

        default JsExpression times(long that) {
            return this.times(literal(that));
        }
        default JsExpression divide(long that) { return this.divide(literal(that)); }
        default JsExpression divide(JsExpression that) {
            // see comment in minus()
            return (JsMultiplication) s -> this.format(s, Precedence.MULTIPLICATION) +"/"+ that.format(s, Precedence.ATOM);
        }

        /** Format this, making sure the resulting expression has at most the given target precedence, adding brackets if needed. */
        default String format(Scope s, Precedence targetPrecedence) {
            if (this.getPrecedence().compareTo(targetPrecedence) > 0) {
                return "(" + this.format(s) + ")";
            } else {
                return this.format(s);
            }
        }

        default JsExpression dot(String attr) {
            if (attr.length() > 0 &&
                    Character.isJavaIdentifierStart(attr.codePointAt(0)) &&
                    attr.chars()
                            .skip(1)
                            .allMatch(Character::isJavaIdentifierPart)) {
                return s -> this.format(s, Precedence.ATOM) +"."+ attr;
            } else {
                return this.arrayGet(literal(attr));
            }
        }

        default JsStatement set(JsExpression newValue) {
            return s -> this.format(s) + " = " + newValue.format(s) + ";\n";
        }

        default JsExpression invoke(String method, JsExpression... args) {
            return s -> this.format(s, Precedence.ATOM) +"."+ method +"("+
                    Stream.of(args)
                            .map(arg -> arg.format(s))
                            .collect(joining(", "))+")";
        }

        /** The "strict" equality operator: {@code this === that}. */
        default JsExpression eq(JsExpression value) {
            return s -> this.format(s, Precedence.ADDITION) +" === "+ value.format(s, Precedence.ADDITION);
        }

        default JsExpression eq(String value) {
            return this.eq(literal(value));
        }

        default JsExpression eq(long value) {
            return this.eq(literal(value));
        }

        /** The "strict" equality operator: {@code this === that}. */
        default JsExpression eq(Enum<?> value) {
            return this.eq(literal(value));
        }

        default JsExpression toLowerCase() {
            return new JsString(this.invoke("toLowerCase"));
        }

        /** The logical AND operator: {@code this && that}. */
        default JsExpression and(JsExpression rhs) {
            return (JsConjunction) s -> this.format(s, Precedence.CONJUNCTION) + " && " + rhs.format(s, Precedence.CONJUNCTION);
        }

        /** The logical OR operator: {@code this || that}. */
        default JsExpression or(JsExpression rhs) {
            return (JsDisjunction) s -> this.format(s) + " || " + rhs.format(s);
        }

        default JsExpression arrayGet(JsExpression key) {
            return s -> this.format(s) +"[" + key.format(s) +"]";
        }

        /** The logical negation {@code !this}. */
        default JsExpression not() {
            return s -> "!" + this.format(s, Precedence.ATOM);
        }

        /** The ternary operator {@code this ? ifTrue : ifFalse}. */
        default JsExpression cond(JsExpression ifTrue, JsExpression ifFalse) {
            // TODO add tests for this case in particular checking behaviour with nested ternaries which likely won't work as-is
            return (JsAssignment) s -> this.format(s, Precedence.DISJUNCTION) +"? "+
                    ifTrue.format(s, Precedence.DISJUNCTION) +" : "+
                    ifFalse.format(s, Precedence.DISJUNCTION);
        }
    }

    /** Generates calls to functions in the JavaScript {@code Math} object. */
    public static class JsMath {
        public static JsExpression min(JsExpression l, JsExpression r) {
            return s -> "Math.min(" + l.format(s) +", "+ r.format(s) +")";
        }
        public static JsExpression max(JsExpression l, JsExpression r) {
            return s -> "Math.max(" + l.format(s) +", "+ r.format(s) +")";
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

        public JsHtmlElement parentNode() {
            return new JsHtmlElement(this.dot("parentNode"));
        }

        public JsExpression insertBefore(JsHtmlElement newElement, JsHtmlElement sibling) {
            return this.invoke("insertBefore", newElement, sibling);
        }

        /** inserts a new child at the end. This calls {@code insertBefore()} with {@code null} as
         * second parameter */
        public JsExpression insertToEnd(JsHtmlElement newElement) {
            return this.invoke("insertBefore", newElement, _null);
        }

        public JsExpression prepend(JsHtmlElement child) {
            return this.invoke("prepend", child);
        }
        public JsStatement setInnerHtml(JsExpression value) {
            return this.dot("innerHTML").set(value);
        }
        public JsStatement setInnerText(JsExpression value) {
            return this.dot("innerText").set(value);
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

        @Override
        public Precedence getPrecedence() {
            return code.getPrecedence();
        }

        public JsString substring(int len) {
            return new JsString(this.invoke("substring", literal(len)));
        }
        public JsString slice(int from, int to) {
            return new JsString(this.invoke("slice", literal(from), literal(to)));
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
        public JsExpression remove(Css.ClassName item) {
            return this.remove(literal(item));
        }

        public JsExpression add(JsExpression item) {
            return this.invoke("add", item);
        }

        public JsExpression add(Css.ClassName item) {
            return this.add(literal(item));
        }
        @Override
        public String format(Scope s) {
            return code.format(s);
        }
    }

    public static JsStatement seq(JsFragment... statements) {
        return seq(Arrays.asList(statements));
    }

    /** A {@code Stream} collector collecting a stream of fragments into a statement sequence. */
    public static Collector<JsFragment, ?, JsStatement> toSeq() {
        return Collector.<JsFragment, List<JsFragment>, JsStatement>of(
                ArrayList::new,
                List::add,
                (left, right) -> { left.addAll(right); return left;},
                list -> seq(list));
    }

    public static JsStatement seq(List<? extends JsFragment> statements) {
        if (statements.size() == 1) { // important special case to avoid generating unnecessary braces around single statements
            return JsStatement.of(statements.get(0));
        } else {
            return (JsStatementSequence)s -> statements.stream()
                    .map(JsStatement::of)
                    .map(stm -> stm.format(s))
                    .collect(joining());
        }
    }

    public static <T extends JsExpression, U extends T> JsStatementSequence let(T value, Function<String, U> newInstance, Function<U, JsStatement> code) {

        return s -> {
            String var = s.freshVariableName();
            U instance = newInstance.apply(var);
            return "let " + var + " = " + value.format(s) + ";" +
                    code.apply(instance).format(s);
        };

        // not working in Quarkus (T)value.getClass().getDeclaredConstructor(String.class).newInstance(var));
    }

    public static JsExpression lambda(JsFragment body) {
        if (body instanceof JsStatement st) {
            // always put braces. For instance if statements do not generate blocks
            // when called with formatAsBlock but braces are needed for a lambda...
            return s -> ("() => {" + st.format(s) +"}");
        } else { // JsExpression
            return s -> ("() => " + body.format(s));
        }
    }

    public static JsExpression lambda(String varName, Function<JsExpression, ? extends JsFragment> body) {
        JsFragment bodyValue = body.apply(JsExpression.of(varName));
        String signature = "(" + varName + ") => ";
        if (bodyValue instanceof JsStatement st) {
            return s -> signature + "{" + st.format(s) +"}";
        } else { // JsExpression
            return s -> signature + bodyValue.format(s);
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
    public static JsExpression jsonStringify(JsExpression text) {
        return s -> "JSON.stringify(" + text.format(s) +")";
    }

    public static JsExpression consoleLog(JsExpression expr) {
        return s -> "console.log("+ expr.format(s) +")";
    }

    public static JsExpression setTimeout(JsFragment body, int delay) {
        return s -> "setTimeout(" + lambda(body).format(s) +", "+ delay +")";
    }

    public static JsExpression clearTimeout(JsExpression body) {
        return s -> "clearTimeout(" + body.format(s) +")";
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

    public static JsExpression getTime() {
        return s -> "new Date().getTime()";
    }

    public static IfBlock _if(JsExpression condition, JsFragment... body) {
        return new IfBlock(condition, seq(body));
    }

    public static JsStatement _forOf(JsExpression array, Function<JsExpression, JsFragment> body) {
        return s -> {
            String var = s.freshVariableName();
            return "for (const " + var +" of " + array.format(s) + ") " +
                    JsStatement.of(body.apply(JsExpression.of(var))).formatAsBlock(s);
        };
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

        default String formatAsBlock(Scope s) {
            return JavaScript.format(this, this.format(s), StatementPrecedence.BLOCK);
        }

        default StatementPrecedence getPrecedence() {
            return StatementPrecedence.BLOCK;
        }
    }

    private static String format(JsStatement st, String formatted, StatementPrecedence precedence) {
        return st.getPrecedence().compareTo(precedence) <= 0 ?
                formatted // already a block
                : ("{" + formatted + "}");
    }

    public interface JsStatementSequence extends JsStatement {
        @Override
        default StatementPrecedence getPrecedence() {
            return StatementPrecedence.SEQUENCE;
        }
    }

    /** Trivial/degenerate {@code if}-chain with no condition/code at all. Calling {@link IfLike#_elseIf(JsExpression, JsFragment...)}
     * will create an {@code if} statement, and calling {@link IfLike#_else(JsFragment...)} will simply emit the
     * code on its own.
     */
    public static final IfLike EMPTY_IF_CHAIN = new IfLike() {
        @Override
        public String format(Scope s) {
            return "";
        }

        @Override
        public IfBlock _elseIf(JsExpression condition, JsFragment... body) {
            return _if(condition, body);
        }

        @Override
        public JsStatement _else(JsFragment... body) {
            return seq(body);
        }
    };

    public interface IfLike extends JsStatement {

        IfBlock _elseIf(JsExpression condition, JsFragment... body);

        JsStatement _else(JsFragment... body);

        default IfBlock _elseIf(JsExpression condition, Stream<? extends JsFragment> body) {
            return _elseIf(condition, body.toArray(JsFragment[]::new));
        }

        default JsStatement _else(Stream<? extends JsFragment> body) {
            return _else(body.toArray(JsFragment[]::new));
        }
    }

    public static class IfBlock implements JsStatement, IfLike {
        private final JsExpression condition;
        private final JsStatement body;

        public IfBlock(JsExpression condition, JsStatement body) {
            this.condition = condition;
            this.body = body;
        }

        @Override
        public IfChain _elseIf(JsExpression condition, JsFragment... body) {
            return new IfChain(this, condition, seq(body));
        }

        @Override
        public JsStatement _else(JsFragment... body) {
            return new JsStatement() {
                @Override
                public String format(Scope s) {
                    return IfBlock.this.format(s) +" else "+ seq(body).formatAsBlock(s);
                }

                @Override
                public StatementPrecedence getPrecedence() {
                    return StatementPrecedence.CONTROL_STRUCTURE;
                }
            };
        }
        @Override
        public String format(Scope s) {
            return "if ("+ condition.format(s) +")"+ body.formatAsBlock(s);
        }

        @Override
        public StatementPrecedence getPrecedence() {
            return StatementPrecedence.CONTROL_STRUCTURE;
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
            return previous.format(s) +" else "+
                    JavaScript.format(this, super.format(s), StatementPrecedence.CONTROL_STRUCTURE);
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
     * @deprecated this is working with stringly-typed statements and would need to be migrated to use {@link JsFragment}
     *  instead.
     */
    @Deprecated
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

    public static JsExpression obj(String k1, JsExpression v1) {
        return obj(ImmutableMap.of(k1, v1));
    }

    public static JsExpression obj(String k1, JsExpression v1, String k2, JsExpression v2) {
        return obj(ImmutableMap.of(k1, v1, k2, v2));
    }

    public static JsExpression array(JsExpression... members) {
        return array(() -> Stream.of(members));
    }

    private static JsExpression array(Supplier<Stream<JsExpression>> members) {
        return s -> "[" + members.get()
                .map(expr -> expr.format(s))
                .collect(joining(", ")) +"]";
    }

    /** A Stream Collector turning a Stream of JsExpressions into an array of those expressions. */
    public static Collector<JsExpression, ?, JsExpression> toArray() {
        return Collector.<JsExpression, List<JsExpression>, JsExpression>of(
                ArrayList::new,
                List::add,
                (left, right) -> { left.addAll(right); return left;},
                list -> array(list::stream));
    }

    public static class Scope {
        private Set<String> used = new HashSet<>();

        /** A special Scope which does not allow declaring variables. */
        public static final Scope NO_DECLARATION = new Scope() {
            public String freshVariableNam() {
                throw new IllegalStateException("Variable declaration not allowed here");
            }
        };

        /** Return a new variable name. */
        public String freshVariableName() {
            return freshVariableName("v");
        }

        /** Return a new variable name starting with the given base (if the given name is already taken, a number is added
         * until a unique name is found.
         *
         * @param base the base name to use. Should be a valid JavaScript variable name.
         * @return a new unique variable name, which may or may not be equal to the given base name.
         */
        public String freshVariableName(String base) {
            String candidate = base;
            int counter = 1;
            while (!used.add(candidate)) { // as long as adding to 'used' doesn't change anything...
                candidate = base + (counter++);
            }
            return candidate;
        }
    }
}
