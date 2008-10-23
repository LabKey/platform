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
package org.labkey.common.util;

import java.util.Map;

public class Pair<Type1, Type2> implements Map.Entry<Type1, Type2>, java.io.Serializable
{
    public Pair(Type1 first, Type2 second)
    {
        this.first = first;
        this.second = second;
    }

    public Type1 first;
    public Type2 second;

    public Type1 getKey()
    {
        return first;
    }

    public Type2 getValue()
    {
        return second;
    }

    public Type2 setValue(Type2 arg0)
    {
        Type2 v = second;
        second = arg0;
        return v;
    }

    public boolean equals(Object o)
    {
        if (! (o instanceof Map.Entry))
            return false;
        Map.Entry that = (Map.Entry) o;
        return (this.getKey() == null ? that.getKey() == null : this.getKey().equals(that.getKey()))
                && (this.getValue() == null ? that.getValue() == null : this.getValue().equals(that.getValue()));
    }

    public int hashCode()
    {
        return (getKey() == null ? 0 : getKey().hashCode()) ^ (getValue() == null ? 0 : getValue().hashCode());
    }

    public String toString()
    {
        return super.toString() + " (" + String.valueOf(first) + "," + String.valueOf(second) + ")";
    }

    public Pair<Type1, Type2> copy()
    {
        return new Pair<Type1, Type2>(first, second);
    }

    static public <T1, T2> Pair<T1, T2> of(T1 first, T2 second)
    {
        return new Pair<T1, T2>(first, second);
    }
}
