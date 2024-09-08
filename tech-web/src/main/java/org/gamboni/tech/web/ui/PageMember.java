package org.gamboni.tech.web.ui;

public interface PageMember<T> {
    void addTo(AbstractPage<? extends T> page);
}
