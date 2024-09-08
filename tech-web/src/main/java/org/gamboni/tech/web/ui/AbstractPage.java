package org.gamboni.tech.web.ui;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import org.gamboni.tech.web.js.JavaScript;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.gamboni.tech.web.js.JavaScript.seq;

/**
 * @param <T> the data needed to render the page
 * @author tendays
 */
public abstract class AbstractPage<T> extends AbstractComponent implements Renderer<T> {
    private final Script script;

    private final JavaScript.FunN onLoad = new JavaScript.FunN("onLoad");
    private final Map<String, Function<T, JavaScript.JsExpression>> onLoadParameters = new HashMap<>();
    private final List<JavaScript.JsFragment> loadBody = new ArrayList<>();

    public interface OnLoad<T> {
        JavaScript.JsExpression addParameter(Function<T, JavaScript.JsExpression> value);

    }

    public void addToOnLoad(Function<OnLoad<T>, JavaScript.JsFragment> code) {
        if (loadBody.isEmpty()) {
            // first time we add something to onLoad, we generate the function declaration
            addToScript(onLoad.declare(__ -> seq(loadBody)));
        }
        loadBody.add(code.apply(paramValue -> {
            var paramName = onLoad.addParameter();
            onLoadParameters.put(paramName, paramValue);
            return JavaScript.JsExpression.of(paramName);
        }));
    }

    protected AbstractPage(Script script) {
        this.script = script;
    }

    /** Set the base path of this page. It is currently used to construct the script url. */
    protected void setBasePath(String basePath) {
        if (!basePath.endsWith("/")) {
            basePath += "/";
        }

        script.setUrl(basePath + "script.js");
    }

    protected HtmlElement html(T data, Iterable<Resource> dependencies, Iterable<Element> body) {
        Iterable<Resource> actualDependencies;
        if (script.isEmpty()) {
            actualDependencies = dependencies;
        } else {
            actualDependencies = Iterables.concat(dependencies,
                    List.of(getScript()));
        }

        var html = new HtmlElement(actualDependencies, body, ImmutableList.of());

        if (loadBody.isEmpty()) {
            return html;
        } else {
            return html
                    .onLoad(onLoad.invoke(Maps.transformValues(onLoadParameters,
                            fun -> fun.apply(data))));
        }
    }

    public void addToScript(ScriptMember... members) {
        for (var member : members) {
            script.add(member);
        }
    }

    /** @apiNote eventually, this will be removed, and render() will be implemented in AbstractPage.java itself.
     * For now, this must be used as parameter to the {@code html()} call, and you're supposed to know whether your page
     * actually has scripting elements. */
    protected Resource getScript() {
        return script;
    }

    protected static class HtmlElement extends Element {
        private final Iterable<Resource> dependencies;
        private final Iterable<Element> body;
        private final List<Html.Attribute> bodyAttributes;

        public HtmlElement(Iterable<Resource> dependencies, Iterable<Element> body, List<Html.Attribute> bodyAttributes) {
            super("html",
                    new Element("head", Iterables.transform(dependencies, Resource::asElement)),
                    new Element("body", bodyAttributes, body));
            this.dependencies = dependencies;
            this.body = body;
            this.bodyAttributes = bodyAttributes;
        }

        public HtmlElement onLoad(JavaScript.JsFragment code) {
            return new HtmlElement(dependencies, body, ImmutableList.<Attribute>builder()
            .addAll(bodyAttributes)
            .add(Html.attribute("onload", code))
            .build());
        }

        @Override
        public String toString() {
            return "<!DOCTYPE html>\n" + super.toString();
        }
    }
}
