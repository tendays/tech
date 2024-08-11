package org.gamboni.tech.sparkjava;

import org.gamboni.tech.web.ui.AbstractPage;

public abstract class SparkPage extends AbstractPage {
    protected SparkPage() {
        super(new SparkScript());
    }
}
