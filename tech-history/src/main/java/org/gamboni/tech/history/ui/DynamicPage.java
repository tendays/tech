package org.gamboni.tech.history.ui;

import org.gamboni.tech.history.ClientStateHandler;
import org.gamboni.tech.history.Stamped;
import org.gamboni.tech.web.js.JavaScript;
import org.gamboni.tech.web.ui.Page;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface DynamicPage<T extends Stamped> extends Page<T> {
     <E> DynamicPage<T> addHandler(BiFunction<JavaScript.JsExpression, ClientStateHandler.MatchCallback, E> matcher,
                                             Function<E, JavaScript.JsFragment> handler) ;
}
