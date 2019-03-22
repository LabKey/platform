/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.collections;

import java.util.Enumeration;
import java.util.function.Predicate;

/**
 * Wraps an Enumeration, filtering it to include only elements that pass the Predicate's test() method
 *
 * Created by adam on 6/5/2017.
 */
public class FilteredEnumeration<E> implements Enumeration<E>
{
    private final Enumeration<E> _enumeration;
    private final Predicate<E> _predicate;

    private E _next = null;

    public FilteredEnumeration(Enumeration<E> enumeration, Predicate<E> predicate)
    {
        _enumeration = enumeration;
        _predicate = predicate;
    }

    @Override
    public boolean hasMoreElements()
    {
        while (_enumeration.hasMoreElements())
        {
            _next = _enumeration.nextElement();

            if (_predicate.test(_next))
                return true;
        }

        return false;
    }

    @Override
    public E nextElement()
    {
        return _next;
    }
}
