package org.gamboni.tech.web.ui;

/** Something that needs to add stuff into a page.
 *
 * @param <T> data associated with the page.
 *           This is typically an interface that the page data must implement in order for this component to function.
 * @param <N> this component after registration. You may keep your {@code PageMember} implementation itself here, but it
 *           is a good idea to only expose your API in this parameter so your users don't forget to register your component in a page.
 */
public interface PageMember<T, N> {
    N addTo(Page<? extends T> page);
}
