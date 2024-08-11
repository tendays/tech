package org.gamboni.tech.web.ui;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.gamboni.tech.web.js.JavaScript;

import java.util.List;

/**
 * @author tendays
 */
public abstract class AbstractPage extends AbstractComponent {
    private final Script script;

    protected AbstractPage(Script script) {
        super(End.BACK); // pages are always rendered in the back end

        this.script = script;
    }

    /** Set the base path of this page. It is currently used to construct the script url. */
    protected void setBasePath(String basePath) {
        if (!basePath.endsWith("/")) {
            basePath += "/";
        }

        script.setUrl(basePath + "script.js");
    }

    protected HtmlElement html(Iterable<Resource> dependencies, Iterable<Element> body) {
        return new HtmlElement(dependencies, body, ImmutableList.of());
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
