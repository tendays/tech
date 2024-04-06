package org.gamboni.tech.web.ui;

import org.gamboni.tech.web.js.JavaScript;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author tendays
 */
public interface HttpRequest {

    void setHeader(String header, JavaScript.JsExpression value);

    public static class Impl implements HttpRequest {
        private final String method;
        private final JavaScript.JsExpression url;
        private final String body;
        private final Map<String, JavaScript.JsExpression> headers = new HashMap<>();

        public Impl(String method, JavaScript.JsExpression url, String body) {
            this.method = method;
            this.url = url;
            this.body = body;
        }
        public String subscribe(Function<String, String> callback) {
            String var = "x";
            return "let "+ var +" = new XMLHttpRequest();" +
                    var +".onreadystatechange = () => {" +
                    "if ("+ var+".readyState != 4) return;" +
                    callback.apply(var+".responseText") +
                    "};" +
                    var +".open("+ JavaScript.literal(method) +", "+ url +");" +
                    headers.entrySet().stream().map(header -> var +".setRequestHeader("+ JavaScript.literal(header.getKey())+", "+ header.getValue() +");")
                            .collect(Collectors.joining())+
                    var +".send("+ body +");";
        }

        @Override
        public void setHeader(String header, JavaScript.JsExpression value) {
            headers.put(header, value);
        }
    }


    public static abstract class Parametric<N extends HttpRequest> implements HttpRequest {
        private final Map<String, JavaScript.JsExpression> headers = new HashMap<>();
        private final List<N> requests = new ArrayList<>();
        protected N copyHeaders(N next) {
            headers.forEach(next::setHeader);
            requests.add(next);
            return next;
        }

        @Override
        public void setHeader(String header, JavaScript.JsExpression value) {
            headers.put(header, value);
            requests.forEach(r -> r.setHeader(header, value));
        }

        public abstract N param(JavaScript.JsExpression value);
    }

}
