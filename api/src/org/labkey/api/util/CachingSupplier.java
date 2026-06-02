/*
 * Copyright (c) 2023-2026 LabKey Corporation
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

import java.util.function.Supplier;

/**
 * Wraps another Supplier, invoking it lazily and caching its results for subsequent calls to get().
 */
public class CachingSupplier<T> implements Supplier<T>
{
    private final Supplier<T> _factory;
    private boolean _invoked = false;
    private T _value;

    public CachingSupplier(Supplier<T> factory)
    {
        _factory = factory;
    }

    @Override
    public T get()
    {
        if (!_invoked)
        {
            _value = _factory.get();
            _invoked = true;
        }
        return _value;
    }
}
