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
 * Time: 4:44:09 PM
 */
public class StringProperty implements IPropertyWrapper, IsSerializable
{
    String s;

    public StringProperty()
    {
        s = null;
    }

    public StringProperty(String s)
    {
        this.s = s;
    }

    public Object get()
    {
        return s;
    }

    public void set(Object o)
    {
        s = (String)o;
    }

    public String getString()
    {
        return s;
    }

    public String toString()
    {
        return s;
    }
}
