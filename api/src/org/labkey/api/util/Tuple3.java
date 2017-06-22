/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import java.io.Serializable;

/**
 * Simple wrapper around three other objects.
 * User: kevink
 * Date: 6/9/11
 */
public class Tuple3<T1, T2, T3> extends Pair<T1, T2> implements Serializable
{
    public T3 third;

    public Tuple3(T1 t1, T2 t2, T3 t3)
    {
        super(t1, t2);
        this.third = t3;
    }

    @Override
    public Tuple3<T1, T2, T3> copy()
    {
        return new Tuple3<>(first, second, third);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        Tuple3 tuple3 = (Tuple3) o;

        if (third != null ? !third.equals(tuple3.third) : tuple3.third != null) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = super.hashCode();
        result = 31 * result + (third != null ? third.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        String s = super.toString();
        return s.substring(0, s.length()-1) + "," + String.valueOf(third) + ")";
    }

    public static <T1, T2, T3> Tuple3<T1, T2, T3> of(T1 t1, T2 t2, T3 t3)
    {
        return new Tuple3<>(t1, t2, t3);
    }
}

