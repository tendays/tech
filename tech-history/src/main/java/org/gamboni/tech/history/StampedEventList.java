package org.gamboni.tech.history;

import org.gamboni.tech.web.js.JS;

import java.util.Collection;

@JS
public record StampedEventList(
        long stamp,
        Collection<?> updates) implements Stamped {
}
