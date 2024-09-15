package org.gamboni.tech.web.ui;

public interface RendererWithId<T> {
    /**
     * Render a template into the page.
     *
     * @param id    the id of the element to generate. The same id value must be used in change events to target this
     *              template instance.
     * @param value the current value.
     * @return an {@link Html} fragment ready to be inserted into a server-rendered page.
     */
    Element render(Object id, T value);
}
