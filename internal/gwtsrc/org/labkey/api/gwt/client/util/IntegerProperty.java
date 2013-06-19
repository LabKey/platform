/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.api.gwt.client.util;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * User: Matthew
 * Date: Apr 25, 2007
 * Time: 4:43:06 PM
 */
public class IntegerProperty implements IPropertyWrapper, IsSerializable
{
    Integer i;

    public IntegerProperty()
    {
        i = null;
    }

    public IntegerProperty(int i)
    {
        setInt(i);
    }

    public IntegerProperty(Integer i)
    {
        set(i);
    }

    public Object get()
    {
        return i;
    }

    public void set(Object o)
    {
        i = (Integer)o;
    }

    public Integer getInteger()
    {
        return i;
    }

    public void setInt(int i)
    {
        // this.i = Integer.valueOf(i);
        this.i = new Integer(i);
    }

    public int intValue()
    {
        return null==i ? 0 : i.intValue();
    }

    @Deprecated
    public int getInt()
    {
        return i.intValue();
    }

    public String toString()
    {
        return String.valueOf(i);
    }
}
