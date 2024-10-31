package org.gamboni.tech.history.event;

import org.gamboni.tech.history.Stamped;
import org.gamboni.tech.web.js.JS;

import java.util.Collection;

@JS
public record StampedEventList(
        long stamp,
        Collection<? extends Event> updates) implements Stamped {
}
