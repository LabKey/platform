package org.labkey.api.util;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * Basically, this is a lot like java.util.Optional.
 * Unlike Optional, it can array along an exception (or exception message)
 *
 * If you get really inspired, this could be extended along the lines of
 * https://github.com/benjiman/expressions/blob/master/src/main/java/uk/co/benjiweber/expressions/exceptions/Result.java
 *
 * isPresent() implies success()
 * !success() implies !isPresent()
 * success() does not necessarily imply isPresent()
 *
 * @see java.util.Optional
 */

public interface Result<T> extends Supplier<T>
{
    @Override
    T get();
    boolean isPresent();
    boolean success();

    class Success<T> implements Result<T>
    {
        final T value;

        Success(T value)
        {
            this.value = value;
        }

        @Override
        public T get()
        {
            return value;
        }

        @Override
        public boolean isPresent()
        {
            return null != value;
        }

        @Override
        public boolean success()
        {
            return true;
        }
    }

    class Failure<T> implements Result<T>
    {
        final String message;
        final Exception exception;

        Failure(String s)
        {
            this.message = s;
            this.exception = null;
        }

        Failure(Exception x)
        {
            this.message = null;
            this.exception = x;
        }

        @Override
        public T get()
        {
            if (null != exception)
                throw exception instanceof RuntimeException ?
                        (RuntimeException)exception :
                        new RuntimeException(exception);
            if (null != message)
                throw new NoSuchElementException(message);
            throw new NoSuchElementException();
        }

        @Override
        public boolean isPresent()
        {
            return false;
        }

        @Override
        public boolean success()
        {
            return false;
        }
    }

    public static <T> Result<T> success(T t)
    {
        return new Success<T>(t);
    }

    public static Result failure(String s)
    {
        return new Failure(s);
    }

    public static Result failure(RuntimeException x)
    {
        return new Failure(x);
    }
}
