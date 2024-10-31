package org.gamboni.tech.history.ui;

public interface DynamicPageMember<T, N> {
    <P extends DynamicPage<? extends T>> N addTo(P page);
}
