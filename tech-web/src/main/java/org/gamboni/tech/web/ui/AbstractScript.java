package org.gamboni.tech.web.ui;

import com.google.common.base.CaseFormat;

import java.util.List;

import static org.gamboni.tech.web.ui.Html.attribute;

/**
 * @author tendays
 */
public abstract class AbstractScript implements Resource {

    @Override
    public String getUrl() {
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, getClass().getSimpleName()) +".js";
    }

    @Override
    public Html asElement() {
        return new Element("script",
                List.of(
                        attribute("type", "text/javascript"),
                        attribute("src", getUrl())
                ));
    }

    @Override
    public String getMime() {
        return "text/javascript";
    }
}
