/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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

import java.util.Collection;
import java.util.HashSet;

/**
 * Created by IntelliJ IDEA.
 * User: arauch
 * Date: Dec 25, 2004
 * Time: 4:07:57 PM
 * To change this template use File | Settings | File Templates.
 */

// Simple case-insensitive verion of HashSet -- simpy forces all Strings to lowercase before adding, removing,
// or searching.  Could easily extend this to preserve the case... just add a lowercase version to uppercase version map.
public class CaseInsensitiveHashSet extends HashSet<String>
{
    public CaseInsensitiveHashSet()
    {
        super();
    }

    public CaseInsensitiveHashSet(int count)
    {
        super(count);
    }

    public CaseInsensitiveHashSet(String... values)
    {
        super(values.length);
        for (String value : values)
        {
            add(value);
        }
    }

    public CaseInsensitiveHashSet(Collection<String> col)
    {
        super(col.size());
        addAll(col);
    }

    public boolean remove(Object o)
    {
        return super.remove(((String) o).toLowerCase());
    }

    public boolean add(String s)
    {
        return super.add(s.toLowerCase());
    }

    public boolean contains(Object o)
    {
        return super.contains(((String) o).toLowerCase());
    }
}
