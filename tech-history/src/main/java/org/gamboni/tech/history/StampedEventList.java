package org.gamboni.tech.history;

import org.gamboni.tech.web.js.JS;

import java.util.Collection;

@JS
public record StampedEventList<E>(
        long stamp,
        Collection<? extends E> updates) implements Stamped {
}
