package org.gamboni.tech.quarkus;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.gamboni.tech.web.ui.AbstractPage;
import org.gamboni.tech.web.ui.Script;

public abstract class QuarkusPage extends AbstractPage {

    protected QuarkusPage() {
        super(new Script());

        /* Look for a @Path annotation. We need to look at superclasses because
         * Quarkus may create subclasses to intercept stuff.
         */
        Class<?> pointer = this.getClass();
        while (pointer != null) {
            var pathAnn = pointer.getAnnotation(Path.class);
            if (pathAnn != null) {
                setBasePath(pathAnn.value());
                break;
            }
            pointer = pointer.getSuperclass();
        }
    }

    @GET
    @Path("script.js")
    @Produces("text/javascript")
    public String script() {
        return getScript().render();
    }
}
