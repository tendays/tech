package org.gamboni.tech.misc;

import com.google.common.collect.ImmutableList;

// TODO move this out
public interface LazyBuilder<T> {
    LazyBuilder<T> add(T item);

    Iterable<? extends T> build();

    static <T> LazyBuilder<T> of(ImmutableList.Builder<T> delegate) {
        return new LazyBuilder<T>() {
            @Override
            public LazyBuilder<T> add(T item) {
                delegate.add(item);
                return this;
            }

            @Override
            public Iterable<T> build() {
                return delegate.build();
            }
        };
    }

    static <T> LazyBuilder<T> of(Iterable<? extends T> base) {
        return new LazyBuilder<T>() {
            @Override
            public LazyBuilder<T> add(T item) {
                return of(ImmutableList.<T>builder()
                        .addAll(base)
                        .add(item));
            }

            @Override
            public Iterable<? extends T> build() {
                return base;
            }
        };
    }
}
