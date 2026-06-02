/*
 * Copyright (c) 2025-2026 LabKey Corporation
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

import java.util.ArrayList;
import java.util.Collection;

public class IntArrayList extends ArrayList<Integer>
{
    private Integer _int(Object key)
    {
        if (null != key && key.getClass() != Integer.class)
            throw new IllegalStateException();
        return (Integer)key;
    }

    @Override
    public boolean contains(Object o)
    {
        return super.contains(_int(o));
    }

    @Override
    public int indexOf(Object o)
    {
        return super.indexOf(_int(o));
    }

    @Override
    public int lastIndexOf(Object o)
    {
        return super.lastIndexOf(_int(o));
    }

    @Override
    public boolean remove(Object o)
    {
        return super.remove(_int(o));
    }

    @Override
    public boolean removeAll(Collection<?> c)
    {
        assert c.stream().allMatch(o -> null == o || o.getClass() == Integer.class);
        return super.removeAll(c);
    }
}
