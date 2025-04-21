package org.gamboni.tech.web.ui;

import org.gamboni.tech.web.js.JavaScript;

import java.util.function.Function;

public interface Page<T> {

    interface OnLoad<T> {
        JavaScript.JsExpression addParameter(Function<T, JavaScript.JsExpression> value);
    }

    String freshGlobal(String base);
    String freshElementId(String base);
    void addToOnLoad(Function<AbstractPage.OnLoad<T>, JavaScript.JsFragment> code);
    void addToScript(ScriptMember... members);
}
