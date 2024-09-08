package org.gamboni.tech.sparkjava;

import org.gamboni.tech.web.ui.AbstractPage;

public abstract class SparkPage<T> extends AbstractPage<T> {
    protected SparkPage() {
        super(new SparkScript());
    }
}
