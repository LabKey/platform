/*
 * Copyright (c) 2016 LabKey Corporation
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

import org.apache.commons.collections4.multimap.AbstractListValuedMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Intends to act like a case-insensitive version of
 * org.apache.commons.collections4.multimap.ArrayListValuedHashMap
 *
 * Created by Nick on 10/1/2016.
 */
public class CaseInsensitiveArrayListValuedMap<V> extends AbstractListValuedMap<String, V>
{
    public CaseInsensitiveArrayListValuedMap()
    {
        super(new CaseInsensitiveHashMap<>());
    }

    @Override
    protected List<V> createCollection()
    {
        return new ArrayList<>();
    }

    /**
     * Trims the capacity of all value collections to their current size.
     */
    public void trimToSize()
    {
        for (Collection<V> coll : getMap().values())
        {
            final ArrayList<V> list = (ArrayList<V>) coll;
            list.trimToSize();
        }
    }
}
