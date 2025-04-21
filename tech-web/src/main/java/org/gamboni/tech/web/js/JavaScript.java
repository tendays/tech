package org.gamboni.tech.web.js;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import org.gamboni.tech.web.ui.Css;
import org.gamboni.tech.web.ui.ScriptMember;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;
import static org.gamboni.tech.web.js.JavaScript.Precedence.ADDITION;
import static org.gamboni.tech.web.js.JavaScript.Precedence.ASSIGNMENT;
import static org.gamboni.tech.web.js.JavaScript.Precedence.ATOM;
import static org.gamboni.tech.web.js.JavaScript.Precedence.CONJUNCTION;
import static org.gamboni.tech.web.js.JavaScript.Precedence.DISJUNCTION;
import static org.gamboni.tech.web.js.JavaScript.Precedence.LOWEST;
import static org.gamboni.tech.web.js.JavaScript.Precedence.MULTIPLICATION;

public abstract class JavaScript {

    public static final JsExpression _Object = new JsAtom("Object");

    public static JsExpression literal(Css.ClassList className) {
        return className.getAttributeValue();
    }

    public static JsExpression literal(Enum<?> e) {
        return literal(e.name());
    }
    public static JsExpression literal(String text) {
        return new JsStringLiteral(text);
    }

    private record JsStringLiteral(String text) implements JsExpression {

        @Override
        public String format(Scope s) {
            return "'" + text.replace("\\", "\\\\")
                    .replace("'", "\\'")
                    + "'";
        }

        @Override
        public Precedence getPrecedence() {
            return ATOM;
        }

        @Override
        public List<Symbol> getFreeSymbols() {
            return List.of();
        }
        @Override
        public String toString() {
            return format(Scope.FOR_TOSTRING);
        }
    }

    /** Javascript literal for use with values whose {@code toString()} returns a valid JavaScript expression,
     * that is, numbers and booleans.
     * @param value the non-null value of this literal.
     */
    private record JsLiteral(Object value) implements JsExpression {

        @Override
        public String format(Scope s) {
            return value.toString();
        }

        @Override
        public Precedence getPrecedence() {
            return ATOM;
        }

        @Override
        public List<Symbol> getFreeSymbols() {
            return List.of();
        }
        @Override
        public String toString() {
            return format(Scope.FOR_TOSTRING);
        }
    }

    public static JsExpression literal(Number number) {
        return new JsLiteral(number);
    }

    public static JsExpression literal(boolean value) {
        return new JsLiteral(value);
    }

    /** Return an expression returning a JavaScript {@code Date} corresponding to the given {@code Instant}. */
    public static JsExpression literal(Instant instant) { return newDate(literal(instant.toString())); }

    public static JsStatement _return(JsExpression value) {
        return JsStatement.of(new JsUnary(ATOM, "return ", value, LOWEST));
    }

    public static JsStatement _return() {
        return JsKeywordStatement.RETURN;
    }

    private enum JsKeywordStatement implements JsStatement {
        RETURN;

        @Override
        public String toString() {
            return format(Scope.FOR_TOSTRING);
        }

        @Override
        public String format(Scope s) {
            return name().toLowerCase() +";";
        }

        @Override
        public List<Symbol> getFreeSymbols() {
            return List.of();
        }
    }

    public record JsGlobal(String name) implements JsExpression {
        public ScriptMember declare(JsExpression initialValue) {
            return () -> name +" = "+ initialValue.format(Scope.empty()) +";\n";
        }
        public ScriptMember declare(long initialValue) {
            return declare(literal(initialValue));
        }
        public JsStatement set(JsExpression newValue) {
            return JsStatement.of(new JsBinary(
                    ASSIGNMENT, this, ATOM, " = ", newValue, ASSIGNMENT));
        }

        public String toString() {
            return name;
        }

        @Override
        public String format(Scope s) {
            return name;
        }

        @Override
        public Precedence getPrecedence() {
            return ATOM;
        }

        @Override
        public List<Symbol> getFreeSymbols() {
            // NOTE: we might want to create Symbols for globals, in case we
            // want to make sure a script included in a page doesn't use a
            // global defined in a script used by another page.
            return List.of();
        }
    }

    public record Fun(String name) {
        public ScriptMember declare(JsFragment body) {
            return () -> "function " + name + "() {\n" +
                    JsStatement.of(body)
                            .format(Scope.empty()) + "\n" +
                    "}";
        }

        public JsExpression invoke() {
            return new JsFunCall(new JsAtom(name), List.of());
        }
    }

    public record Fun1(String name) {

        public ScriptMember declare(Function<JsExpression,JsFragment> body) {
            String arg = "a";
            return () -> "function " + name + "(" + arg + ") {\n" +
                        JsStatement.of(body.apply(new JsAtom(arg)))
                                .format(Scope.empty()) + "\n" +
                        "}";
        }

        public JsExpression invoke(JsExpression value) {
            return new JsFunCall(name, value);
        }
    }

    public record Fun2(String name) {
        public ScriptMember declare(BiFunction<JsExpression, JsExpression, JsFragment> body) {
            String arg1 = "a";
            String arg2 = "b";
            return () -> "function " + name + "(" + arg1 +", "+ arg2 + ") {\n" +
                    JsStatement.of(body.apply(new JsAtom(arg1), new JsAtom(arg2)))
                            .format(Scope.empty())+ "\n" +
                    "}";
        }

        public JsExpression invoke(JsExpression value1, JsExpression value2) {
            return new JsFunCall(new JsAtom(name), List.of(value1, value2));
        }
    }

    @RequiredArgsConstructor
    public static class FunN {
        private final String name;
        private final List<JsExpression> parameters = new ArrayList<>();
        public JsExpression addParameter() {
            var parameter = new JsAtom("p" + (parameters.size()));
            this.parameters.add(parameter);
            return parameter;
        }
        public ScriptMember declare(Supplier<JsFragment> body) {
            return () -> {
                // to support adding parameters after declaration, create the map at actual render time.
                return "function " + name + "(" +
                        Joiner.on(", ").join(parameters) + ") {\n" +
                    JsStatement.of(body.get())
                            .format(Scope.empty())+ "\n" +
                    "}";};
        }

        public JsExpression invoke(Map<JsExpression, JsExpression> paramValues) {
            return new JsFunCall(new JsAtom(name),
                    parameters.stream().map(paramValues::get)
                            .peek(Preconditions::checkNotNull)
                            .toList());
        }
    }

    public enum Precedence {
        ATOM, MULTIPLICATION, ADDITION, CONJUNCTION, DISJUNCTION, ASSIGNMENT, LOWEST
    }

    public enum StatementPrecedence {
        BLOCK, CONTROL_STRUCTURE, SEQUENCE
    }

    public interface JsExpression extends JsFragment {

        JsExpression _this = new JsAtom("this");
        JsExpression _null = new JsAtom("null");
        JsExpression _undefined = new JsAtom("undefined");

        default JsExpression plus(JsExpression that) {
            return new JsBinary(ADDITION,
                    this, ADDITION, "+", that, ADDITION);
        }

        default JsExpression plus(String that) {
            return this.plus(literal(that));
        }

        default JsExpression minus(JsExpression that) {
            // Note rhs must have at most MULTIPLICATION precedence. If we pass an addition/subtraction it needs brackets.
            // Because x-(y-z) ≠ x-y-z

            // lhs is ADDITION because e.g. (x-y)-z = x-y-z.
            return new JsBinary(ADDITION,
                    this, ADDITION, "-", that, MULTIPLICATION);
        }

        default JsExpression times(JsExpression that) {
            return new JsBinary(
                    MULTIPLICATION, this, MULTIPLICATION, "*", that, MULTIPLICATION);
        }

        default JsExpression times(long that) {
            return this.times(literal(that));
        }
        default JsExpression divide(long that) { return this.divide(literal(that)); }
        default JsExpression divide(JsExpression that) {
            // see comment in minus()
            return new JsBinary(
                    MULTIPLICATION, this, MULTIPLICATION, "/", that, ATOM);
        }

        default JsExpression dot(String attr) {
            if (attr.length() > 0 &&
                    Character.isJavaIdentifierStart(attr.codePointAt(0)) &&
                    attr.chars()
                            .skip(1)
                            .allMatch(Character::isJavaIdentifierPart)) {
                return new JsDotExpression(this, attr);
            } else {
                return this.arrayGet(literal(attr));
            }
        }

        default JsStatement set(JsExpression newValue) {
            return JsStatement.of(new JsBinary(
                    ASSIGNMENT, this, LOWEST, "=", newValue, LOWEST));
        }

        default JsExpression invoke(String method, JsExpression... args) {
            return new JsMethodCall(this, method, List.of(args));
        }

        /** The "strict" equality operator: {@code this === that}. */
        default JsExpression eq(JsExpression value) {
            return new JsBinary(ASSIGNMENT, this, ADDITION, " === ", value, ADDITION);
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

        /** The logical AND operator: {@code this && that}. */
        default JsExpression and(JsExpression rhs) {
            return new JsBinary(CONJUNCTION,
                    this, CONJUNCTION,
                    " && ",
                    rhs, CONJUNCTION);
        }

        /** The logical OR operator: {@code this || that}. */
        default JsExpression or(JsExpression rhs) {
            return new JsBinary(DISJUNCTION,
                    this, DISJUNCTION,
                    " || ",
                    rhs, DISJUNCTION);
        }

        default JsExpression arrayGet(JsExpression key) {
            return new JsArrayAccess(this, key);
        }

        /** The logical negation {@code !this}. */
        default JsExpression not() {
            return new JsUnary(ATOM, "!", this, ATOM);
        }

        /** The ternary operator {@code this ? ifTrue : ifFalse}. */
        default JsExpression cond(JsExpression ifTrue, JsExpression ifFalse) {
            return new JsTernary(this, ifTrue, ifFalse);
        }

        /** Format this, making sure the resulting expression has at most the given target precedence, adding brackets if needed. */
        default String format(Scope s, Precedence targetPrecedence) {
            if (this.getPrecedence().compareTo(targetPrecedence) > 0) {
                return "(" + this.format(s) + ")";
            } else {
                return this.format(s);
            }
        }

        Precedence getPrecedence();
    }

    private record JsAtom(String code) implements JsExpression {
        @Override
        public String format(Scope s) {
            return code;
        }
        @Override
        public String toString() {
            return code;
        }

        @Override
        public Precedence getPrecedence() {
            return ATOM;
        }

        @Override
        public List<Symbol> getFreeSymbols() {
            return List.of();
        }
    }

    @RequiredArgsConstructor(access = PROTECTED)
    public static abstract class JsExpressionDecorator implements JsExpression {
        protected final JsExpression delegate;

        @Override
        public Precedence getPrecedence() {
            return delegate.getPrecedence();
        }

        @Override
        public List<Symbol> getFreeSymbols() {
            return delegate.getFreeSymbols();
        }

        @Override
        public String toString() {
            return this.format(Scope.FOR_TOSTRING);
        }

        @Override
        public String format(Scope s) {
            return delegate.format(s);
        }
    }

    private record JsDotExpression(JsExpression lhs, String attr) implements JsExpression {

        @Override
        public String format(Scope s) {
            return lhs.format(s, ATOM) +"."+ attr;
        }

        @Override
        public Precedence getPrecedence() {
            return ATOM;
        }

        @Override
        public List<Symbol> getFreeSymbols() {
            return lhs.getFreeSymbols();
        }

        @Override
        public String toString() {
            return this.format(Scope.FOR_TOSTRING);
        }
    }

    /** Binary expressions. */
    private record JsBinary(Precedence precedence,
                            JsExpression lhs, Precedence lPrecedence,
                            String op,
                            JsExpression rhs, Precedence rPrecedence) implements JsExpression {

        @Override
        public Precedence getPrecedence() {
            return precedence;
        }

        @Override
        public String format(Scope s) {
            return lhs.format(s, lPrecedence) + op + rhs.format(s, rPrecedence);
        }

        @Override
        public List<Symbol> getFreeSymbols() {
            return JavaScript.getFreeSymbols(lhs, rhs);
        }

        @Override
        public String toString() {
            return this.format(Scope.FOR_TOSTRING);
        }
    }

    /** Unary expressions. */
    private record JsUnary(Precedence precedence,
                            String operator,
                            JsExpression argument, Precedence argPrecedence) implements JsExpression {

        @Override
        public Precedence getPrecedence() {
            return precedence;
        }

        @Override
        public String format(Scope s) {
            return operator + argument.format(s, argPrecedence);
        }

        @Override
        public List<Symbol> getFreeSymbols() {
            return argument.getFreeSymbols();
        }

        @Override
        public String toString() {
            return this.format(Scope.FOR_TOSTRING);
        }
    }

    private record JsTernary(JsExpression cond, JsExpression ifTrue, JsExpression ifFalse) implements JsExpression {
        @Override
        public String format(Scope s) {
            // TODO add tests for this case in particular checking behaviour with nested ternaries which likely won't work as-is
            return cond.format(s, Precedence.DISJUNCTION) +"? "+
                    ifTrue.format(s, Precedence.DISJUNCTION) +" : "+
                    ifFalse.format(s, Precedence.DISJUNCTION);
        }

        @Override
        public List<Symbol> getFreeSymbols() {
            var condSyms = cond.getFreeSymbols();
            var tSyms = ifTrue.getFreeSymbols();
            var fSyms = ifFalse.getFreeSymbols();
            if (condSyms.isEmpty() && tSyms.isEmpty()) {
                return fSyms;
            } else if (condSyms.isEmpty() && fSyms.isEmpty()) {
                return tSyms;
            } else if (fSyms.isEmpty() && tSyms.isEmpty()) {
                return condSyms;
            } else {
                return ImmutableList.<Symbol>builderWithExpectedSize(
                        condSyms.size() + fSyms.size() + tSyms.size())
                        .addAll(condSyms)
                        .addAll(fSyms)
                        .addAll(tSyms)
                        .build();
            }
        }

        @Override
        public Precedence getPrecedence() {
            return ASSIGNMENT;
        }

        @Override
        public String toString() {
            return format(Scope.FOR_TOSTRING);
        }
    }

    private record JsMethodCall(JsExpression object, String name, List<JsExpression> args) implements JsExpression {

        @Override
        public String format(Scope s) {
            String comma = "";
            var result = new StringBuilder(object.format(s, ATOM))
                    .append(".").append(name).append("(");
            for (var arg : args) {
                result.append(comma).append(arg.format(s));
                comma = ", ";
            }
            return result.append(")").toString();
        }
        @Override
        public Precedence getPrecedence() {
            return ATOM;
        }

        @Override
        public List<Symbol> getFreeSymbols() {
            return JavaScript.getFreeSymbols(args);
        }

        @Override
        public String toString() {
            return format(Scope.FOR_TOSTRING);
        }
    }

    private record JsArrayAccess(JsExpression array, JsExpression index) implements JsExpression {

        @Override
        public String format(Scope s) {
            return array.format(s, ATOM) +"["+ index.format(s) +"]";
        }

        @Override
        public Precedence getPrecedence() {
            return ATOM;
        }

        @Override
        public List<Symbol> getFreeSymbols() {
            return JavaScript.getFreeSymbols(array, index);
        }

        @Override
        public String toString() {
            return format(Scope.FOR_TOSTRING);
        }
    }

    public static JsExpression invoke(JsExpression function, JsExpression... args) {
        return new JsFunCall(function, Arrays.asList(args));
    }

    private record JsFunCall(JsExpression name, List<JsExpression> args) implements JsExpression {
        JsFunCall(String name, JsExpression... args) {
            this(new JsAtom(name), List.of(args));
        }

        @Override
        public String format(Scope s) {
            String comma = "";
            var result = new StringBuilder(name.format(s, ATOM)).append("(");
            for (var arg : args) {
                result.append(comma).append(arg.format(s));
                comma = ", ";
            }
            return result.append(")").toString();
        }
        @Override
        public Precedence getPrecedence() {
            return ATOM;
        }

        @Override
        public List<Symbol> getFreeSymbols() {
            return union(name.getFreeSymbols(), JavaScript.getFreeSymbols(args));
        }

        @Override
        public String toString() {
            return format(Scope.FOR_TOSTRING);
        }
    }

    private static List<Symbol> getFreeSymbols(JsFragment first, JsFragment second) {
        return union(
                first.getFreeSymbols(),
                second.getFreeSymbols());
    }

    private static <T> List<T> union(List<T> lSymbols, List<T> rSymbols) {
        if (lSymbols.isEmpty()) {
            return rSymbols;
        } else if (rSymbols.isEmpty()) {
            return lSymbols;
        } else {
            return ImmutableList.<T>builderWithExpectedSize(lSymbols.size() + rSymbols.size())
                    .addAll(lSymbols)
                    .addAll(rSymbols)
                    .build();
        }
    }

    private static List<Symbol> getFreeSymbols(Iterable<? extends JsFragment> expressions) {
            var result = List.<Symbol>of();
            var iterator = expressions.iterator();
            while (iterator.hasNext()) {
                List<Symbol> argSymbols = iterator.next().getFreeSymbols();
                if (result.isEmpty()) {
                    result = argSymbols;
                } else if (!argSymbols.isEmpty()) {
                    // slow case: need to construct a new List. We'll finish the iteration here and return
                    var builder = ImmutableList.<Symbol>builder()
                            .addAll(result)
                            .addAll(argSymbols);
                    while (iterator.hasNext()) {
                        builder.addAll(iterator.next().getFreeSymbols());
                    }
                    return builder.build();
                }
            }
            // finished iterating without encountering the "slow case"
            return result;

    }

    /** Generates calls to functions in the JavaScript {@code Math} object. */
    public static class JsMath {
        public static JsExpression min(JsExpression l, JsExpression r) {
            return new JsFunCall("Math.min", l, r);
        }
        public static JsExpression max(JsExpression l, JsExpression r) {
            return new JsFunCall("Math.max", l, r);
        }
    }

    public static class JsHtmlElement extends JsExpressionDecorator {
        public JsHtmlElement(JsExpression code) {
            super(code);
        }

        public JsDOMTokenList classList() {
            return new JsDOMTokenList(this.dot("classList"));
        }

        public JsExpression id() {
            return this.dot("id");
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
    }

    public static class JsDOMTokenList extends JsExpressionDecorator {
        public JsDOMTokenList(JsExpression code) {
            super(code);
        }

        public JsExpression contains(Css.ClassName className) {
            return this.invoke("contains", literal(className));
        }

        public JsExpression item(int index) {
            return this.invoke("item", literal(index));
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
            return new JsStatementSequence() {
                @Override
                public List<Symbol> getFreeSymbols() {
                    return JavaScript.getFreeSymbols(statements);
                }

                @Override
                public String format(Scope s) {
                    return statements.stream()
                            .map(JsStatement::of)
                            .map(stm -> stm.format(s))
                            .collect(joining());
                }
            };
        }
    }

    public static JsStatementSequence let(JsExpression value, Function<JsExpression, JsStatement> code) {
        return let(value, x -> x, code);
    }

    public static <T extends JsExpression, U extends T> JsStatementSequence let(T value, Function<JsExpression, U> newInstance, Function<U, JsStatement> code) {
        var symbol = Symbol.create();
        JsStatement body = code.apply(newInstance.apply(symbol));

        return new JsStatementSequence() {
            @Override
            public String format(Scope s) {
                String var = s.freshVariableName();

                return "let " + var + " = " + value.format(s) + ";" +
                        body.format(s.withSymbolValue(symbol, new JsAtom(var)));
            }

            @Override
            public List<Symbol> getFreeSymbols() {

                return Stream.concat(
                        value.getFreeSymbols().stream(),
                        body.getFreeSymbols().stream()
                                .filter(sym -> !sym.equals(symbol))
                ).toList();
            }
        };
    }

    public static JsExpression lambda(JsFragment body) {
        return new JsLambda(List.of(), body);
    }

    public static JsExpression lambda(String varName, Function<JsExpression, ? extends JsFragment> body) {
        JsAtom variable = new JsAtom(varName);
        JsFragment bodyValue = body.apply(variable);
        return new JsLambda(List.of(variable), bodyValue);
    }

    private record JsLambda(List<JsAtom> parameters, JsFragment body) implements JsExpression {
        @Override
        public String format(Scope s) {
            String signature = "(" +
                    parameters.stream()
                            .map(p -> p.format(s))
                            .collect(joining(", "))+ ") => ";
            if (body instanceof JsStatement st) {
                // always put braces. For instance if statements do not generate blocks
                // when called with formatAsBlock but braces are needed for a lambda...
                return signature + "{" + st.format(s) +"}";
            } else { // JsExpression
                return signature + body.format(s);
            }
        }

        @Override
        public Precedence getPrecedence() {
            return null;
        }

        @Override
        public List<Symbol> getFreeSymbols() {
            return List.of();
        }

        @Override
        public String toString() {
            return "";
        }
    }

    public static JsHtmlElement getElementById(JsExpression id) {
        return new JsHtmlElement(new JsFunCall("document.getElementById", id));
    }

    /** Return an expression evaluating to the {@code <body>} element. */
    public static JsHtmlElement getBodyElement() {
        return new JsHtmlElement(new JsFunCall("document.getElementsByTagName", literal("body")).arrayGet(literal(0)));
    }

    public static final JsExpression window = new JsAtom("window");

    public static JsExpression newWebSocket(JsExpression url) {
        return new JsFunCall("new WebSocket", url);
    }

    /** The WebSocket class, can be used to access readyState values like OPEN, CLOSED, etc. */
    public static final JsExpression WebSocket = new JsAtom("WebSocket");

    public static JsExpression newDate(JsExpression value) { return new JsFunCall("new Date", value); }

    public static JsExpression jsonParse(JsExpression text) {
        return new JsFunCall("JSON.parse", text);
    }
    public static JsExpression jsonStringify(JsExpression text) {
        return new JsFunCall("JSON.stringify", text);
    }

    public static JsExpression consoleLog(JsExpression expr) {
        return new JsFunCall("console.log", expr);
    }

    public static JsExpression setTimeout(JsFragment body, long delay) {
        return new JsFunCall("setTimeout", lambda(body), literal(delay));
    }

    public static JsExpression clearTimeout(JsExpression body) {
        return new JsFunCall("clearTimeout", body);
    }

    public static JsExpression newXMLHttpRequest() {
        return new JsFunCall("new XMLHttpRequest");
    }

    public static JsHtmlElement createElement(String tag) {
        return new JsHtmlElement(new JsFunCall("document.createElement", literal(tag)));
    }
    public static JsHtmlElement createTextNode(String contents) {
        return createTextNode(literal(contents));
    }
    public static JsHtmlElement createTextNode(JsExpression contents) {
        return new JsHtmlElement(new JsFunCall("document.createTextNode", contents));
    }

    public static JsExpression newDate() {
        return new JsFunCall("new Date");
    }

    public static IfBlock _if(JsExpression condition, JsFragment... body) {
        return new IfBlock(condition, seq(body));
    }

    public static JsStatement _forOf(JsExpression array, Function<JsExpression, JsFragment> body) {
        Symbol item = Symbol.create();
        JsFragment bodyFragment = body.apply(item);
        return new JsStatement() {
            @Override
            public String format(Scope s) {
                String var = s.freshVariableName();
                return "for (const " + var +" of " + array.format(s) + ") " +
                        JsStatement.of(bodyFragment).formatAsBlock(s.withSymbolValue(item, new JsAtom(var)));
            }

            @Override
            public List<Symbol> getFreeSymbols() {
                return Stream.concat(
                        array.getFreeSymbols().stream(),
                        bodyFragment.getFreeSymbols().stream()
                                .filter(sym -> !sym.equals(item))
                ).toList();
            }
        };
    }

    public interface JsFragment {
        String format(Scope s);

        List<Symbol> getFreeSymbols(); // TODO should use Sets instead
    }

    public interface JsStatement extends JsFragment {
        static JsStatement  of(JsFragment obj) {
            if (obj instanceof JsStatement st) {
                return st;
            } else if (obj instanceof JsExpression expr) {
                return new JsStatement() {
                    @Override
                    public String format(Scope s) {
                        return expr.format(s) +";";
                    }

                    @Override
                    public List<Symbol> getFreeSymbols() {
                        return expr.getFreeSymbols();
                    }
                };
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

        @Override
        public StatementPrecedence getPrecedence() {
            return StatementPrecedence.SEQUENCE;
        }

        @Override
        public List<Symbol> getFreeSymbols() {
            return List.of();
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

                @Override
                public List<Symbol> getFreeSymbols() {
                    return JavaScript.getFreeSymbols(Arrays.asList(body));
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

        @Override
        public List<Symbol> getFreeSymbols() {
            return JavaScript.getFreeSymbols(condition, body);
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

    public static JsExpression obj(Map<String, JsExpression> values) {
        return new JsObjectLiteral(values);
    }

    private record JsObjectLiteral(Map<String, JsExpression> map) implements JsExpression {
        @Override
        public String format(Scope s) {
            return "{" + map.entrySet().stream().map(e ->
                            literal(e.getKey()).format(s) + ":" +
                                    e.getValue().format(s))
                    .collect(joining(",")) +
                    "}";
        }

        @Override
        public Precedence getPrecedence() {
            return ATOM;
        }

        @Override
        public List<Symbol> getFreeSymbols() {
            return JavaScript.getFreeSymbols(map.values());
        }

        @Override
        public String toString() {
            return format(Scope.FOR_TOSTRING);
        }
    }

    public static JsExpression obj() {
        return obj(ImmutableMap.of());
    }

    public static JsExpression obj(String k1, JsExpression v1) {
        return obj(ImmutableMap.of(k1, v1));
    }

    public static JsExpression obj(String k1, JsExpression v1, String k2, JsExpression v2) {
        return obj(ImmutableMap.of(k1, v1, k2, v2));
    }

    private static JsExpression array(Supplier<Stream<JsExpression>> members) {
        return new JsArray(members.get().toList());
    }

    public static JsExpression array(JsExpression... members) {
        return array(List.of(members));
    }

    private static JsExpression array(List<JsExpression> members) {
        return new JsArray(members);
    }

    private record JsArray(List<JsExpression> elements) implements JsExpression {

        @Override
        public List<Symbol> getFreeSymbols() {
            return JavaScript.getFreeSymbols(elements);
        }

        @Override
        public Precedence getPrecedence() {
            return ATOM;
        }

        @Override
        public String format(Scope s) {
            return "[" + elements.stream()
                    .map(expr -> expr.format(s))
                    .collect(joining(", ")) +"]";
        }
        
        @Override
        public String toString() {
            return format(Scope.FOR_TOSTRING);
        }
    }

    /** A Stream Collector turning a Stream of JsExpressions into an array of those expressions. */
    public static Collector<JsExpression, ?, JsExpression> toArray() {
        return Collector.<JsExpression, List<JsExpression>, JsExpression>of(
                ArrayList::new,
                List::add,
                (left, right) -> { left.addAll(right); return left;},
                list -> array(list));
    }

    /** Symbols are a facility which allows constructing expressions in which some
     * subexpression has not been constructed yet.
     */
    @RequiredArgsConstructor(access = PRIVATE)
    public static class Symbol implements JsExpression {
        private static long next = 1;
        private final long id;

        public static Symbol create() {
            return new Symbol(next++);
        }

        public JsStatement assignIn(JsExpression value, JsStatement body) {
            return new JsStatement() {
                @Override
                public String format(Scope s) {
                    return body.format(s.withSymbolValue(Symbol.this, value));
                }

                @Override
                public List<Symbol> getFreeSymbols() {
                    return List.of();
                }

                @Override
                public StatementPrecedence getPrecedence() {
                    return body.getPrecedence();
                }
            };
        }

        @Override
        public String format(Scope s) {
            return s.resolve(this).format(s);
        }

        @Override
        public String format(Scope s, Precedence targetPrecedence) {
            return s.resolve(this).format(s, targetPrecedence);
        }

        @Override
        public Precedence getPrecedence() {
            return ATOM; // symbols are supposed to be replaced with variables
        }

        @Override
        public List<Symbol> getFreeSymbols() {
            return List.of(this);
        }

        @Override
        public String toString() {
            return "Symbol#"+ id;
        }
    }

    public static JsStatement dynamicStatement(Supplier<JsStatement> supplier) {
        return new JsStatement() {
            @Override
            public String format(Scope s) {
                return supplier.get().format(s);
            }

            @Override
            public List<Symbol> getFreeSymbols() {
                return supplier.get().getFreeSymbols();
            }
        };
    }
    public static JsExpression dynamicExpression(Supplier<JsExpression> supplier) {
        return new JsExpression() {
            @Override
            public String format(Scope s) {
                return supplier.get().format(s);
            }

            @Override
            public List<Symbol> getFreeSymbols() {
                return supplier.get().getFreeSymbols();
            }

            @Override
            public Precedence getPrecedence() {
                return supplier.get().getPrecedence();
            }
        };
    }

    public interface Scope {
        /** A special Scope which does not allow declaring variables. */
        Scope NO_DECLARATION = base -> {
            throw new IllegalStateException("Variable declaration not allowed here");
        };

        /** Scope used to implement toString() for debugging. Does not return
         * valid variable names.
         */
        Scope FOR_TOSTRING = base -> "{" + base +"}";

        /** Create a new empty scope. */
        static Scope empty() {
            final Set<String> used = new HashSet<>();

            return base -> {
                String candidate = base;
                int counter = 1;
                while (!used.add(candidate)) { // as long as adding to 'used' doesn't change anything...
                    candidate = base + (counter++);
                }
                return candidate;
            };
        }

        /** Return a new variable name starting with the given base (if the given name is already taken, a number is added
         * until a unique name is found).
         *
         * @param base the base name to use. Should be a valid JavaScript variable name.
         * @return a new unique variable name, which may or may not be equal to the given base name.
         */
        String freshVariableName(String base);

        /** Return a new variable name. */
        default String freshVariableName() {
            return freshVariableName("v");
        }

        default Scope withSymbolValue(Symbol symbol, JsExpression value) {
            return new ScopeWithSymbolValues(this, Map.of(symbol, value));
        }

        default JsExpression resolve(Symbol sym)  {
            return new JsAtom("(unresolved " + sym + ")");
        }
    }

    private record ScopeWithSymbolValues(Scope delegate, Map<Symbol, JsExpression> symbols) implements Scope {
        @Override
        public String freshVariableName(String base) {
            return delegate.freshVariableName();
        }

        @Override
        public Scope withSymbolValue(Symbol symbol, JsExpression value) {
            if (symbols.containsKey(symbol)) {
                throw new IllegalStateException("Multiple assignments to symbol " + symbol);
            }

            return new ScopeWithSymbolValues(delegate,
            ImmutableMap.<Symbol, JsExpression>builderWithExpectedSize(this.symbols.size() + 1)
                            .putAll(this.symbols)
                                    .put(symbol, value)
                                            .build());
        }

        @Override
        public JsExpression resolve(Symbol sym) {
            JsExpression resolved = symbols.get(sym);
            if (resolved == null) {
                return delegate.resolve(sym);
            } else {
                return resolved;
            }
        }
    }
}
