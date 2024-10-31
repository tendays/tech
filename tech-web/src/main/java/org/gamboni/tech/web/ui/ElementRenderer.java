package org.gamboni.tech.web.ui;

/** A {@link Renderer} constrained to return an {@link Element}. */
public interface ElementRenderer<T> extends Renderer<T> {
    @Override
    Element render(T value);
}
