package org.gamboni.tech.web.ui;

public interface Renderer<T> {
    Html render(T value);
}