package org.gamboni.tech.history;

import com.google.common.collect.Streams;

public interface Stamped {
        long stamp();

        /** Utility method for computing the stamp from a collection of {@code Stamped} items
         *
         * @param iterable a collection of {@code Stamped} items
         * @return the highest stamp value, or zero if the collection is empty
         */
        static long stampCollection(Iterable<? extends Stamped> iterable) {
                return Streams.stream(iterable)
                        .mapToLong(Stamped::stamp)
                        .max().orElse(0);
        }
}
