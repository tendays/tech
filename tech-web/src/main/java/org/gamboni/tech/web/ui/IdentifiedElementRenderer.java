package org.gamboni.tech.web.ui;

import org.gamboni.tech.web.js.JavaScript;

/** An {@link org.gamboni.tech.web.ui.ElementRenderer} which includes an {@code id} property
 * in rendered elements. The id is obtained by concatenating a fixed string (which can be
 * retrieved with {@link #getElementKey()}) to the id of the {@code T} entity being rendered.
 *
 * @param <T> the data being rendered by this object.
 */
public interface IdentifiedElementRenderer<T> extends ElementRenderer<T> {
    String getElementKey();

    /** Given a reference to an element rendered by this object, return the id
     * of the original {@code T} instance that was used to render it.
     * <p>Note that
     * the returned object will be a {@code string} even if the original id was of another type like {@code number}.
     * <p>Also note that no check is done that the element was really produced by this renderer</p>
     *
     * @param elt an element that was rendered by this object (either from the server or client-side).
     *
     * @return an expression holding the id extracted from the element.
     */
    default JavaScript.JsString getIdFromElement(JavaScript.JsHtmlElement elt) {
        return elt.id().substring(getElementKey().length()+1);
    }

    static <T> IdentifiedElementRenderer<T> of(String elementKey, ElementRenderer<T> renderer) {
        return new IdentifiedElementRenderer<T>() {
            @Override
            public String getElementKey() {
                return elementKey;
            }

            @Override
            public Element render(T value) {
                return renderer.render(value);
            }
        };
    }
}
