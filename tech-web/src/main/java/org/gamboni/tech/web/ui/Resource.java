package org.gamboni.tech.web.ui;

/**
 * @author tendays
 */
public interface Resource {
    String render();
    String getUrl();
    Html asElement();
    String getMime();
}
