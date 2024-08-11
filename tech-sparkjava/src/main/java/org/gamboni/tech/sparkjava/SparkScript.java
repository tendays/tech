package org.gamboni.tech.sparkjava;

import org.gamboni.tech.web.ui.Script;
import spark.Spark;

public class SparkScript extends Script {
    protected SparkScript() {
        Spark.get(getUrl(), (req, res) -> {
            res.header("Content-Type", getMime());
            return this.render();
        });
    }
}
