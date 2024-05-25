package org.gamboni.tech.web.ui;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class FavIconResource implements Resource {
    private final String fileName;

    @Getter
    private final String mime;

    @Override
    public String render() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getUrl() {
        return "/" + fileName;
    }

    @Override
    public Html asElement() {
        return new Element("link",
                List.of(Html.attribute("rel", "icon"),
                        Html.attribute("href", getUrl())));
    }
}
