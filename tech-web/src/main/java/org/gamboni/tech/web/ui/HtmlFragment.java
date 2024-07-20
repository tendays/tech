package org.gamboni.tech.web.ui;

import com.google.common.collect.Streams;

import java.util.stream.Stream;

public interface HtmlFragment extends Iterable<Html> {
    static HtmlFragment of(HtmlFragment... elements) {
        return () -> Stream.of(elements)
                .flatMap(Streams::stream)
                .iterator();
    }

    static HtmlFragment of(Stream<HtmlFragment> elements) {
        return of(elements.toArray(HtmlFragment[]::new));
    }
}
