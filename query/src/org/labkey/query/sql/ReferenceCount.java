/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.query.sql;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * User: matthewb
 * Date: 2012-11-07
 * Time: 3:07 PM
 */
public class ReferenceCount
{
    private final IdentityHashMap<Object,Object> _refs = new IdentityHashMap<>();
    private final Set<Class> _legal;

    public ReferenceCount(Set<Class> legal)
    {
        _legal = legal;
    }

    public int increment(@NotNull Object referant)
    {
        assert null==_legal || _legal.contains(referant.getClass());
        _refs.put(referant,referant);
        return _refs.size();
    }

    public int decrement(@NotNull Object referant)
    {
        assert null==_legal || _legal.contains(referant.getClass());
        _refs.remove(referant);
        return _refs.size();
    }

    public int count()
    {
        return _refs.size();
    }

    public boolean isReferencedBy(Object referant)
    {
        return _refs.containsKey(referant);
    }

    @Override
    public String toString()
    {
        return "" + count() + " reference(s)";
    }
}
