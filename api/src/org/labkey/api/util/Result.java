/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
        private final String _message;
        private final Exception _exception;

        Failure(String s)
        {
            _message = s;
            _exception = null;
        }

        Failure(Exception x)
        {
            _message = null;
            _exception = x;
        }

        @Override
        public T get()
        {
            if (null != _exception)
                throw _exception instanceof RuntimeException ?
                        (RuntimeException) _exception :
                        new RuntimeException(_exception);
            if (null != _message)
                throw new NoSuchElementException(_message);
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

    static <T> Result<T> success(T t)
    {
        return new Success<>(t);
    }

    static <T> Result<T> failure(String s)
    {
        return new Failure<T>(s);
    }

    static <T> Result<T> failure(Exception x)
    {
        return new Failure<>(x);
    }
}
