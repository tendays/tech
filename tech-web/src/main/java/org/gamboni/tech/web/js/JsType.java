package org.gamboni.tech.web.js;

/** <p>Front-end equivalent of the back-end type {@code B}.</p>
 *
 * <p>This interface is implemented by {@code Js*} classes generated from {@link JS @JS}-annotated types.</p>.
 *
 * <p>Hint: although back-end class hierarchy is not reflected into front-end types you can have a weak approximation
 * by writing methods like {@code myMethod(JsType<? extends SomeInterface> expr)}</p>
 *
 * @param <B>
 */
public interface JsType<B> extends JavaScript.JsExpression {
    Class<B> getBackendType();

    /** <p>Return a JavaScript expression evaluating to {@code true} if this expression is indeed of type {@code B}.
     * This is necessary because, for a {@code JsSomething} type you can always write</p>
     * <p>{@code var jsSomething = new JsSomething(someExpression);}</p>
     * <p>without any automatic verification that {@code someExpression} is actually a "{@code Something}".
     * <p>So, before accessing fields in the {@code JsSomething}, you would need to do something like</p>
     * <p>{@code _if(jsSomething.isThisType(), {jsSomething is safe to use here})}</p>
     */
    default JavaScript.JsExpression isThisType() {
        String className = getClass().getSimpleName();
        return this.dot("@type").eq(className.substring(2));
    }
}
