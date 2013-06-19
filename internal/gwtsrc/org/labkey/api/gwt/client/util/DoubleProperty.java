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
public class DoubleProperty implements IPropertyWrapper, IsSerializable
{
    Double d;

    public DoubleProperty()
    {
        d = null;
    }

    public DoubleProperty(double d)
    {
        setDbl(d);
    }

    public DoubleProperty(Double d)
    {
        set(d);
    }

    public Object get()
    {
        return d;
    }

    public void set(Object o)
    {
        d = (Double)o;
    }

    public Double getDouble()
    {
        return d;
    }

    public void setDbl(double d)
    {
        //this.d = Double.valueOf(d);
        this.d = new Double(d);
    }

    public double getDbl()
    {
        return d.doubleValue();
    }

    public String toString()
    {
        return String.valueOf(d);
    }
}
